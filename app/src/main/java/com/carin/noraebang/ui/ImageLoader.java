package com.carin.noraebang.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 사진을 백그라운드에서 축소 디코딩해 ImageView에 표시한다.
 *
 * 우선순위: 사용자 사진 URI → {@code assets/backgrounds/<slot>.*} → {@code assets/backgrounds/default.*}.
 * 모두 없으면 이미지를 비워 두고, 뷰 배경(노을 그라디언트)이 그대로 보이게 한다.
 */
public final class ImageLoader {

    /** 로딩 결과 콜백 (메인 스레드). 실패하면 null */
    public interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    /** 메인 화면 추억 카드(히어로) 기본 사진 슬롯 */
    public static final String SLOT_HERO = "hero";
    /** 노래방 기본 배경 슬롯 */
    public static final String SLOT_KARAOKE = "karaoke";
    /** 카인 추억 목록·상세 기본 사진 슬롯 */
    public static final String SLOT_MEMORY = "memory";

    private static final String TAG = "CARIN_IMAGE";
    private static final String ASSET_DIR = "backgrounds/";
    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(32 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private interface StreamOpener {
        InputStream open() throws IOException;
    }

    private ImageLoader() {
    }

    /**
     * 사진을 비동기로 읽어 target에 표시한다. 같은 뷰에 더 최근 요청이 있으면 이전 결과는 버린다.
     *
     * @param uri  사용자 사진 (null이면 assets 기본 사진 슬롯 사용)
     * @param slot assets 기본 사진 슬롯 이름 (SLOT_*)
     */
    public static void load(ImageView target, Uri uri, String slot, int maxSide, Callback callback) {
        final Context app = target.getContext().getApplicationContext();
        final String key = uri != null ? uri.toString() : "asset:" + slot;
        target.setTag(key);

        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            if (callback != null) callback.onLoaded(cached);
            return;
        }

        target.setImageDrawable(null);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = uri != null ? decodeUri(app, uri, maxSide) : decodeAssetSlot(app, slot, maxSide);
            if (bitmap != null) CACHE.put(key, bitmap);
            MAIN.post(() -> {
                if (!key.equals(target.getTag())) return;
                target.setImageBitmap(bitmap);
                if (callback != null) callback.onLoaded(bitmap);
            });
        });
    }

    /** 앱에 번들된 사진(데모 추억·곡 커버)을 가리키는 URI 접두사 */
    public static final String ASSET_URI_PREFIX = "file:///android_asset/";

    /**
     * assets 안의 파일을 가리키는 URI를 만든다.
     *
     * @param path assets 기준 경로 (예: demo/gangneung.jpg)
     * @return file:///android_asset/{path}
     */
    public static Uri assetUri(String path) {
        return Uri.parse(ASSET_URI_PREFIX + path);
    }

    private static Bitmap decodeUri(Context context, Uri uri, int maxSide) {
        try {
            String value = uri.toString();
            if (value.startsWith(ASSET_URI_PREFIX)) {
                // ContentResolver는 android_asset을 열지 못하므로 AssetManager로 직접 연다
                String path = value.substring(ASSET_URI_PREFIX.length());
                return decode(() -> context.getAssets().open(path), maxSide);
            }
            return decode(() -> context.getContentResolver().openInputStream(uri), maxSide);
        } catch (Exception e) {
            Log.w(TAG, "사진 로딩 실패: " + uri, e);
            return null;
        }
    }

    private static Bitmap decodeAssetSlot(Context context, String slot, int maxSide) {
        for (String name : new String[] {slot, "default"}) {
            for (String ext : EXTENSIONS) {
                final String path = ASSET_DIR + name + ext;
                try {
                    Bitmap bitmap = decode(() -> context.getAssets().open(path), maxSide);
                    if (bitmap != null) {
                        Log.d(TAG, "기본 사진 사용: assets/" + path);
                        return bitmap;
                    }
                } catch (IOException ignored) {
                    // 해당 파일이 없으면 다음 후보를 찾는다
                }
            }
        }
        return null;
    }

    private static Bitmap decode(StreamOpener opener, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = opener.open()) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        // 축소 후에도 긴 변이 maxSide 이상 남는 범위에서만 줄인다 (1672px → 836px처럼 흐려지지 않도록)
        int longSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longSide / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream in = opener.open()) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }
}
