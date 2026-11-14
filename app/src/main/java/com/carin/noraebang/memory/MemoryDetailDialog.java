package com.carin.noraebang.memory;

import android.app.Activity;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.carin.noraebang.ui.CarinDialog;
import com.carin.noraebang.ui.CarinIcon;
import com.carin.noraebang.ui.CarinTheme;
import com.carin.noraebang.ui.ImageLoader;
import com.carin.noraebang.ui.PillButton;
import com.carin.noraebang.ui.RouteLineView;

/** 추억 상세: 사진(+장식 경로선)과 날짜·장소·노래 메타, 재생·노래방·사진 연결·삭제 */
public final class MemoryDetailDialog {

    /** 상세 화면에서 발생하는 동작 */
    public interface Listener {
        /** 해당 추억의 노래를 기존 플레이어로 재생 */
        void onPlay(Memory memory);

        /** 이 추억의 노래와 사진으로 카인 노래방 진입 */
        void onKaraoke(Memory memory);

        /** 사진 선택기를 띄워 이 추억에 연결. 시작했으면 true (주행 제한 등으로 거부되면 false) */
        boolean onPickPhoto(Memory memory);

        /** 삭제 요청 (주행 제한 확인과 확인 다이얼로그는 호출한 쪽이 처리) */
        void onDelete(Memory memory);
    }

    private MemoryDetailDialog() {
    }

    /** 상세 다이얼로그를 띄우고 반환한다 (삭제 후 닫기용) */
    public static CarinDialog show(Activity activity, Memory memory, Listener listener) {
        String subtitle = memory.place + " · " + CarinTheme.displayDate(memory.travelDate)
                + (memory.demo ? " · 데모 데이터" : "");
        CarinDialog dialog = new CarinDialog(activity, memory.title, subtitle).setWidthFraction(0.62f);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);

        // 사진 + 장식 경로선 (실제 GPS 경로 아님)
        FrameLayout photo = new FrameLayout(activity);
        photo.setBackground(CarinTheme.sunset(activity, 26));
        photo.setClipToOutline(true);
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.addView(image, new FrameLayout.LayoutParams(-1, -1));
        RouteLineView route = new RouteLineView(activity);
        route.setLabels("", memory.place);
        FrameLayout.LayoutParams routeParams = new FrameLayout.LayoutParams(-1, dp(activity, 220), Gravity.BOTTOM);
        routeParams.bottomMargin = dp(activity, 24);
        photo.addView(route, routeParams);
        content.addView(photo, new LinearLayout.LayoutParams(0, dp(activity, 420), 1.5f));

        // 메타 열
        LinearLayout meta = new LinearLayout(activity);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(metaRow(activity, CarinIcon.Type.CALENDAR, CarinTheme.displayDate(memory.travelDate),
                CarinTheme.GLASS_WHITE, false));
        meta.addView(metaRow(activity, CarinIcon.Type.PIN, memory.place, CarinTheme.GLASS_WHITE, true));
        meta.addView(metaRow(activity, CarinIcon.Type.NOTE, SongCatalog.titleOf(memory.songId),
                CarinTheme.LIGHT_ROSE, true));
        meta.addView(metaRow(activity, CarinIcon.Type.SPARKLE, memory.demo ? "데모 데이터" : "실제 기록",
                memory.demo ? CarinTheme.AMBER : CarinTheme.GLASS_WHITE, true));

        TextView registered = CarinTheme.text(activity, "등록 "
                        + android.text.format.DateFormat.format("yyyy.MM.dd HH:mm", memory.createdAt),
                15, CarinTheme.FAINT, CarinTheme.REGULAR);
        LinearLayout.LayoutParams registeredParams = new LinearLayout.LayoutParams(-1, -2);
        registeredParams.topMargin = dp(activity, 28);
        meta.addView(registered, registeredParams);

        TextView photoState = CarinTheme.text(activity, "", 15, CarinTheme.MUTED, CarinTheme.REGULAR);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.topMargin = dp(activity, 6);
        meta.addView(photoState, stateParams);

        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(0, -1, 1f);
        metaParams.leftMargin = dp(activity, 36);
        content.addView(meta, metaParams);
        dialog.setContent(content);

        Uri photoUri = memory.photoUri == null ? null : Uri.parse(memory.photoUri);
        ImageLoader.load(image, photoUri, ImageLoader.SLOT_MEMORY, 1600, bitmap -> {
            if (photoUri == null) {
                photoState.setText("연결된 사진 없음 · 주차 중 '사진 연결'로 추가할 수 있어요");
            } else if (bitmap == null) {
                photoState.setText("사진을 열 수 없어요 (삭제되었거나 손상됨)");
                photoState.setTextColor(CarinTheme.AMBER);
            } else {
                photoState.setText("연결된 사진");
            }
        });

        dialog.addButton("삭제", CarinIcon.Type.TRASH, PillButton.Style.GHOST,
                () -> listener.onDelete(memory));
        dialog.addButton("사진 연결", CarinIcon.Type.PHOTO, PillButton.Style.GHOST, () -> {
            // 선택기가 실제로 열린 경우에만 닫는다 (주행 제한이면 토스트만)
            if (listener.onPickPhoto(memory)) dialog.dismiss();
        });
        dialog.addButton("노래방", CarinIcon.Type.MIC, PillButton.Style.GHOST, () -> {
            dialog.dismiss();
            listener.onKaraoke(memory);
        });
        dialog.addButton("그날의 노래", CarinIcon.Type.PLAY, PillButton.Style.PRIMARY, () -> {
            dialog.dismiss();
            listener.onPlay(memory);
        });
        dialog.show();
        return dialog;
    }

    private static View metaRow(Activity activity, CarinIcon.Type type, String value, int color, boolean margin) {
        LinearLayout row = CarinTheme.iconRow(activity, type, CarinTheme.LIGHT_ROSE,
                CarinTheme.text(activity, value, 22, color, CarinTheme.MEDIUM), 28);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (margin) params.topMargin = dp(activity, 22);
        row.setLayoutParams(params);
        return row;
    }

    private static int dp(Activity activity, float value) {
        return CarinTheme.dp(activity, value);
    }
}
