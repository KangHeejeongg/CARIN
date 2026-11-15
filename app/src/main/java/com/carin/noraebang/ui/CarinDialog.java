package com.carin.noraebang.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * CAR IN 다크 로즈 톤의 공통 다이얼로그.
 * 시스템 AlertDialog(밝은 테마) 대신 사용해 앱 전체의 디자인을 통일한다.
 */
public final class CarinDialog {

    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout body;
    private final LinearLayout buttonRow;
    private float widthFraction = 0.5f;

    /** 제목과 부제(null 가능)로 다이얼로그를 만든다 */
    public CarinDialog(Activity activity, CharSequence title, CharSequence subtitle) {
        this.activity = activity;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(36);
        card.setPadding(pad, dp(30), pad, dp(30));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF37313B, CarinTheme.CHARCOAL, 0xFF222127});
        bg.setCornerRadius(dp(32));
        bg.setStroke(dp(1), CarinTheme.STROKE);
        card.setBackground(bg);

        // 헤더: 제목 · 부제 · 닫기
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(CarinTheme.text(activity, title, 28, CarinTheme.GLASS_WHITE, CarinTheme.BOLD));
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = CarinTheme.text(activity, subtitle, 17, CarinTheme.MUTED, CarinTheme.REGULAR);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
            subParams.topMargin = dp(6);
            titles.addView(sub, subParams);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        CircleButton close = new CircleButton(activity, CarinIcon.Type.CLOSE, CircleButton.Style.GLASS, 24);
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        closeParams.leftMargin = dp(16);
        header.addView(close, closeParams);
        card.addView(header);

        // 본문: 내용이 길면 이 영역만 스크롤된다
        ScrollView scroll = new ScrollView(activity);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(24);
        card.addView(scroll, scrollParams);

        buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(28);
        card.addView(buttonRow, rowParams);

        dialog.setContentView(card);
        CarinTheme.installFocusRings(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.65f);
        }
    }

    /** 화면 너비 대비 다이얼로그 너비 비율 (기본 0.5) */
    public CarinDialog setWidthFraction(float fraction) {
        widthFraction = fraction;
        return this;
    }

    /** 본문에 뷰를 추가한다 */
    public CarinDialog setContent(View content) {
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return this;
    }

    /** 본문에 안내 문구를 추가한다 */
    public CarinDialog setMessage(CharSequence message) {
        TextView view = CarinTheme.text(activity, message, 20, CarinTheme.GLASS_WHITE, CarinTheme.REGULAR);
        view.setLineSpacing(0, 1.3f);
        return setContent(view);
    }

    /** 하단에 버튼을 추가한다. 다이얼로그를 닫을지는 onClick에서 직접 정한다 */
    public PillButton addButton(CharSequence label, CarinIcon.Type icon,
                                PillButton.Style style, Runnable onClick) {
        PillButton button = new PillButton(activity, label, icon, style);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(64));
        params.leftMargin = dp(12);
        buttonRow.addView(button, params);
        return button;
    }

    /** 닫힐 때 실행할 동작 */
    public CarinDialog setOnDismiss(Runnable action) {
        dialog.setOnDismissListener(d -> action.run());
        return this;
    }

    /** 다이얼로그를 표시한다 */
    public void show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            int width = Math.max((int) (screen * widthFraction), Math.min(dp(720), screen - dp(48)));
            window.setLayout(Math.min(width, screen - dp(48)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 다이얼로그를 닫는다 */
    public void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    private int dp(float value) {
        return CarinTheme.dp(activity, value);
    }
}
