package com.carin.noraebang.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 첫 실행 안내 3장 (노래 듣기 → 추억 남기기 → 같은 길에서 다시 듣기).
 *
 * MainActivity 위에 겹쳐 띄우는 오버레이다. 한 장에 그림 하나와 짧은 문장만 두고(Pleos 가이드: 1~2초 안에 파악),
 * 언제든 건너뛸 수 있다. 마지막 장에서는 데모 추억이 있는 목적지로 바로 체험해 볼 수 있다.
 */
public final class OnboardingPanel extends FrameLayout {

    /** 안내가 끝났을 때 MainActivity에 알린다 */
    public interface Listener {
        /**
         * @param tryDemo 마지막 장의 "강릉역으로 체험하기"를 눌렀으면 true
         */
        void onFinished(boolean tryDemo);
    }

    /** 한 장의 그림(assets 경로)·제목·설명 */
    private static final String[][] STEPS = {
            {"backgrounds/default.jpg", "노래를 틀고, 출발하세요",
                    "곡 목록이나 아래 플레이어를 누르면 카인 플레이리스트가 재생돼요. 앱을 닫아도 음악은 이어져요."},
            {"backgrounds/memory.jpg", "여행의 노래를 추억으로 남겨요",
                    "장소·날짜·사진과 그날 들은 노래를 함께 저장해요. 기록은 차를 세웠을 때만 할 수 있어요."},
            {"backgrounds/hero.jpg", "같은 길에서, 그 노래가 먼저 찾아와요",
                    "목적지를 고르면 지난 추억과 그날의 노래를 찾아드려요. 데모 추억이 있는 강릉역으로 바로 해 볼까요?"},
    };

    private final Listener listener;
    private final ImageView image;
    private final TextView stepLabel;
    private final TextView title;
    private final TextView body;
    private final LinearLayout dots;
    private final PillButton skipButton;
    private final PillButton nextButton;
    private int step = 0;

    /**
     * @param context  Activity Context
     * @param listener 안내 종료 콜백
     */
    public OnboardingPanel(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        setBackgroundColor(0xEB1A191E);
        setClickable(true);   // 아래 화면으로 터치가 새지 않도록
        setFocusable(false);  // 판 자체는 로터리 포커스 대상이 아니다

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(36), dp(36), dp(36), dp(36));
        card.setBackground(CarinTheme.glass(context, 36));
        LayoutParams cardParams = new LayoutParams(dp(landscape ? 1280 : 760), -2, Gravity.CENTER);
        addView(card, cardParams);

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(CarinTheme.sunset(context, 28));
        image.setClipToOutline(true);
        card.addView(image, landscape
                ? new LinearLayout.LayoutParams(0, dp(440), 1.1f)
                : new LinearLayout.LayoutParams(-1, dp(340)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = landscape
                ? new LinearLayout.LayoutParams(0, -2, 1f)
                : new LinearLayout.LayoutParams(-1, -2);
        if (landscape) textParams.leftMargin = dp(40);
        else textParams.topMargin = dp(28);
        card.addView(text, textParams);

        stepLabel = CarinTheme.text(context, "", 17, CarinTheme.CORAL, CarinTheme.BOLD);
        stepLabel.setLetterSpacing(0.12f);
        text.addView(stepLabel);
        title = CarinTheme.text(context, "", 32, CarinTheme.GLASS_WHITE, CarinTheme.BOLD);
        text.addView(title, margins(-1, -2, 14));
        body = CarinTheme.text(context, "", 20, CarinTheme.MUTED, CarinTheme.REGULAR);
        body.setLineSpacing(0, 1.35f);
        text.addView(body, margins(-1, -2, 16));

        dots = new LinearLayout(context);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(dots, margins(-2, dp(16), 28));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        skipButton = new PillButton(context, "건너뛰기", null, PillButton.Style.GHOST);
        skipButton.setOnClickListener(v -> finish(false));
        buttons.addView(skipButton, new LinearLayout.LayoutParams(-2, dp(64)));
        nextButton = new PillButton(context, "다음", CarinIcon.Type.NEXT, PillButton.Style.PRIMARY);
        nextButton.setOnClickListener(v -> {
            if (step < STEPS.length - 1) show(step + 1);
            else finish(true);
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-2, dp(64));
        nextParams.leftMargin = dp(14);
        buttons.addView(nextButton, nextParams);
        text.addView(buttons, margins(-1, -2, 28));

        setVisibility(GONE);
    }

    /** 첫 장부터 안내를 보여 준다 */
    public void start() {
        show(0);
        setVisibility(VISIBLE);
        nextButton.requestFocus();
    }

    /** 안내가 열려 있는지 */
    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    /** 안내를 닫는다 (뒤로가기 등). 체험 이동은 하지 않는다 */
    public void dismiss() {
        finish(false);
    }

    private void show(int index) {
        step = index;
        String[] s = STEPS[index];
        ImageLoader.load(image, ImageLoader.assetUri(s[0]), ImageLoader.SLOT_HERO, 1200, null);
        stepLabel.setText((index + 1) + " / " + STEPS.length);
        title.setText(s[1]);
        body.setText(s[2]);
        boolean last = index == STEPS.length - 1;
        nextButton.setText(last ? "강릉역으로 체험하기" : "다음");
        nextButton.setIcon(last ? CarinIcon.Type.PIN : CarinIcon.Type.NEXT);
        skipButton.setText(last ? "닫기" : "건너뛰기");

        dots.removeAllViews();
        for (int i = 0; i < STEPS.length; i++) {
            View dot = new View(getContext());
            boolean current = i == index;
            dot.setBackground(CarinTheme.rounded(getContext(),
                    current ? CarinTheme.CORAL : CarinTheme.withAlpha(CarinTheme.GLASS_WHITE, 0x55), 999));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(current ? 32 : 12), dp(12));
            params.rightMargin = dp(10);
            dots.addView(dot, params);
        }
    }

    private void finish(boolean tryDemo) {
        setVisibility(GONE);
        listener.onFinished(tryDemo);
    }

    private LinearLayout.LayoutParams margins(int width, int height, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(float value) {
        return CarinTheme.dp(getContext(), value);
    }
}
