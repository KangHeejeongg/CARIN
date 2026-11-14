package com.carin.noraebang.karaoke;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 사진 선택 인텐트 선택과 앱 내부 저장소 복사.
 *
 * AAOS(Pleos Connect)에서는 OPEN_DOCUMENT/GET_CONTENT가 "파일 관리 미지원" 스텁으로 연결되므로
 * 실제 처리 앱이 있는 인텐트만 고른다. 선택한 사진은 URI 권한 만료와 무관하게 다시 볼 수 있도록
 * 축소본을 앱 내부 저장소에 복사하고 file:// URI를 보관한다.
 */
public final class PhotoPicker {

    private static final String TAG = "CARIN_PHOTO";
    private static final String STUB_PACKAGE = "com.android.car.frameworkpackagestubs";
    private static final int MAX_SIDE = 1600;

    private PhotoPicker() {
    }

    /** 이 기기에서 실제로 동작하는 사진 선택 인텐트. 없으면 null */
    public static Intent buildIntent(Context context) {
        List<Intent> candidates = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            candidates.add(new Intent("android.provider.action.PICK_IMAGES").setType("image/*"));
        }
        candidates.add(new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"));
        candidates.add(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"));
        candidates.add(new Intent(Intent.ACTION_PICK).setType("image/*"));

        PackageManager pm = context.getPackageManager();
        for (Intent intent : candidates) {
            for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                if (info.activityInfo == null) continue;
                if (STUB_PACKAGE.equals(info.activityInfo.packageName)) continue;
                Log.d(TAG, "사진 선택기: " + intent.getAction() + " → " + info.activityInfo.packageName);
                return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
        return null;
    }

    /**
     * 선택한 사진을 축소하여 앱 내부 저장소에 복사한다.
     *
     * @return 복사본의 file:// URI. 읽기/디코딩에 실패하면 null
     */
    public static Uri importToAppStorage(Context context, Uri source) {
        Bitmap bitmap = decodeScaled(context, source);
        if (bitmap == null) return null;

        File dir = new File(context.getFilesDir(), "photos");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File target = new File(dir, UUID.randomUUID().toString() + ".jpg");

        try (FileOutputStream out = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (Exception e) {
            Log.w(TAG, "사진 복사 실패", e);
            return null;
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(target);
    }

    private static Bitmap decodeScaled(Context context, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            // 축소 후에도 긴 변이 MAX_SIDE 이상 남는 범위에서만 줄여 화질 저하를 막는다
            int longSide = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longSide / (sample * 2) >= MAX_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            Log.w(TAG, "사진 디코딩 실패: " + uri, e);
            return null;
        }
    }
}
