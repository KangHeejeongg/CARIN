package com.carin.noraebang.karaoke;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 사진 선택 인텐트 선택.
 *
 * AAOS(Pleos Connect)에서는 OPEN_DOCUMENT/GET_CONTENT가 "파일 관리 미지원" 스텁으로 연결되므로
 * 실제 처리 앱이 있는 인텐트만 고른다. 선택한 사진의 축소·방향 보정·JPEG 저장은
 * {@link com.carin.noraebang.photo.PhotoImporter}가 담당한다.
 */
public final class PhotoPicker {

    private static final String TAG = "CARIN_PHOTO";
    private static final String STUB_PACKAGE = "com.android.car.frameworkpackagestubs";

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
}
