package com.carin.noraebang.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 사용자가 새로 고른 사진을 앱 내부 저장소로 가져온다.
 *
 * 처리 순서 (백그라운드 스레드):
 * 1) 크기 확인 — 이미지가 아니거나 너무 크면(픽셀 수 제한) 거부
 * 2) 축소 디코딩 — 긴 변이 {@link #MAX_SIDE} 이상 남도록 inSampleSize를 정한 뒤 정확히 MAX_SIDE로 맞춤
 * 3) 방향 보정 — EXIF Orientation(회전·반전)을 픽셀에 적용 (JPEG로 다시 저장하면 EXIF가 사라지므로)
 * 4) JPEG 저장 — 임시 파일(.tmp_*.jpg)에 쓰고 성공했을 때만 최종 이름으로 바꿈. 실패하면 임시 파일 삭제
 *
 * 기존 사진의 일괄 변환이나 고아 파일 정리는 하지 않는다. 지우는 것은 호출 측이 넘긴
 * "교체되어 더 이상 참조되지 않는" 앱 소유 파일과, 저장에 실패한 새 파일뿐이다.
 */
public final class PhotoImporter {

    /** 가져오기 결과 종류 */
    public enum Status { OK, INVALID_IMAGE, TOO_LARGE, READ_FAILED, OUT_OF_MEMORY, WRITE_FAILED }

    /** 가져오기 결과. 성공하면 앱 내부 file:// URI */
    public static final class Result {
        public final Status status;
        public final Uri uri;
        public final int width;
        public final int height;
        public final int orientation;

        Result(Status status, Uri uri, int width, int height, int orientation) {
            this.status = status;
            this.uri = uri;
            this.width = width;
            this.height = height;
            this.orientation = orientation;
        }

        public boolean ok() {
            return status == Status.OK && uri != null;
        }

        /** 사용자 안내 문구 */
        public String message() {
            switch (status) {
                case INVALID_IMAGE:
                    return "이미지 파일로 읽을 수 없는 사진이에요. 다른 사진을 골라 주세요.";
                case TOO_LARGE:
                    return "사진이 너무 커서 불러올 수 없어요 (최대 " + (MAX_PIXELS / 1_000_000) + "MP).";
                case OUT_OF_MEMORY:
                    return "사진이 너무 커서 처리하지 못했어요. 더 작은 사진을 골라 주세요.";
                case WRITE_FAILED:
                    return "사진을 저장하지 못했어요. 저장 공간을 확인해 주세요.";
                case READ_FAILED:
                    return "사진을 읽지 못했어요. 다시 시도해 주세요.";
                default:
                    return "";
            }
        }
    }

    /** 메인 스레드에서 호출되는 결과 콜백 */
    public interface Callback {
        void onResult(Result result);
    }

    /** 저장 사진의 긴 변 (차량 화면 2560px 배경에 충분하면서 용량을 줄이는 값) */
    public static final int MAX_SIDE = 1600;
    /** 원본 픽셀 수 상한 (디코딩 메모리 보호) */
    public static final long MAX_PIXELS = 100_000_000L;
    private static final int JPEG_QUALITY = 85;
    private static final String TAG = "CARIN_PHOTO";
    private static final String DIR = "photos";
    private static final String TEMP_PREFIX = ".tmp_";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** debug 빌드 검증용: true면 JPEG 저장 단계를 실패로 처리 (release에서는 설정 경로가 없음) */
    public static volatile boolean debugFailWrite = false;

    private interface StreamOpener {
        InputStream open() throws IOException;
    }

    private PhotoImporter() {
    }

    /** 백그라운드에서 가져오고 결과를 메인 스레드로 전달한다 */
    public static void importAsync(Context context, Uri source, Callback callback) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Result result = importBlocking(app, source);
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /** 가져오기 본체 (백그라운드 스레드에서 호출) */
    static Result importBlocking(Context context, Uri source) {
        StreamOpener opener = () -> {
            InputStream in = context.getContentResolver().openInputStream(source);
            if (in == null) throw new IOException("openInputStream null: " + source);
            return in;
        };

        // 1) 크기 확인
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = opener.open()) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception e) {
            Log.w(TAG, "사진 열기 실패: " + source, e);
            return fail(Status.READ_FAILED);
        }
        int width = bounds.outWidth;
        int height = bounds.outHeight;
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "이미지가 아님: " + source + " mime=" + bounds.outMimeType);
            return fail(Status.INVALID_IMAGE);
        }
        if ((long) width * height > MAX_PIXELS) {
            Log.w(TAG, "너무 큰 사진: " + width + "x" + height);
            return fail(Status.TOO_LARGE);
        }

        int orientation = readOrientation(opener);

        // 2) 축소 디코딩
        Bitmap decoded;
        try (InputStream in = opener.open()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = PhotoSizing.sampleSizeFor(width, height, MAX_SIDE);
            decoded = BitmapFactory.decodeStream(in, null, options);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "디코딩 메모리 부족: " + width + "x" + height, e);
            return fail(Status.OUT_OF_MEMORY);
        } catch (Exception e) {
            Log.w(TAG, "디코딩 실패: " + source, e);
            return fail(Status.READ_FAILED);
        }
        if (decoded == null) return fail(Status.INVALID_IMAGE);

        // 3) 정확한 크기 + 방향 보정
        Bitmap output;
        try {
            output = scaleAndOrient(decoded, orientation, MAX_SIDE);
        } catch (OutOfMemoryError e) {
            decoded.recycle();
            return fail(Status.OUT_OF_MEMORY);
        }
        if (output != decoded) decoded.recycle();

        // 4) 임시 파일에 JPEG 저장 → 성공 시 최종 이름으로
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            output.recycle();
            return fail(Status.WRITE_FAILED);
        }
        String name = UUID.randomUUID().toString() + ".jpg";
        File temp = new File(dir, TEMP_PREFIX + name);
        File target = new File(dir, name);
        boolean written = false;
        try (FileOutputStream out = new FileOutputStream(temp)) {
            written = !debugFailWrite && output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            out.flush();
            out.getFD().sync();
        } catch (Exception e) {
            Log.w(TAG, "JPEG 저장 실패", e);
            written = false;
        }
        int outWidth = output.getWidth();
        int outHeight = output.getHeight();
        output.recycle();

        if (!written || temp.length() == 0 || !temp.renameTo(target)) {
            if (temp.exists() && !temp.delete()) Log.w(TAG, "임시 파일 삭제 실패: " + temp.getName());
            Log.w(TAG, "사진 저장 실패 → 임시 파일 정리" + (debugFailWrite ? " (debug 실패 주입)" : ""));
            return fail(Status.WRITE_FAILED);
        }
        Log.d(TAG, "사진 가져오기: " + width + "x" + height + " orientation=" + orientation
                + " → " + outWidth + "x" + outHeight + " " + target.length() + "B " + target.getName());
        return new Result(Status.OK, Uri.fromFile(target), outWidth, outHeight, orientation);
    }

    /**
     * 앱이 만든 사진 파일(files/photos/*.jpg)이면 삭제한다. 그 외 경로는 절대 지우지 않는다.
     *
     * @return 실제로 삭제했으면 true
     */
    public static boolean deleteOwnedPhoto(Context context, String uriString) {
        File file = ownedFile(context, uriString);
        if (file == null || !file.exists()) return false;
        boolean deleted = file.delete();
        Log.d(TAG, "사진 파일 삭제 " + (deleted ? "성공" : "실패") + ": " + file.getName());
        return deleted;
    }

    /** files/photos 안의 앱 소유 파일이면 File, 아니면 null */
    static File ownedFile(Context context, String uriString) {
        if (uriString == null) return null;
        Uri uri = Uri.parse(uriString);
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return null;
        try {
            File dir = new File(context.getFilesDir(), DIR).getCanonicalFile();
            File file = new File(uri.getPath()).getCanonicalFile();
            return dir.equals(file.getParentFile()) ? file : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static int readOrientation(StreamOpener opener) {
        try (InputStream in = opener.open()) {
            return new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            // PNG 등 EXIF가 없거나 읽을 수 없으면 회전 없음으로 처리
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap scaleAndOrient(Bitmap source, int orientation, int maxSide) {
        int[] size = PhotoSizing.targetSize(source.getWidth(), source.getHeight(), maxSide);
        Matrix matrix = new Matrix();
        matrix.postScale(size[0] / (float) source.getWidth(), size[1] / (float) source.getHeight());
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(-90);
                break;
            default:
                break;
        }
        if (matrix.isIdentity()) return source;
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static Result fail(Status status) {
        return new Result(status, null, 0, 0, ExifInterface.ORIENTATION_NORMAL);
    }
}
