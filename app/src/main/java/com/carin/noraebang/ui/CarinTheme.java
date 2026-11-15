package com.carin.noraebang.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.text.LineBreakConfig;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.carin.noraebang.R;

/**
 * CAR IN 브랜드 팔레트(노을 로즈 · 다크 모드)와 공통 뷰 팩토리.
 * 색상은 브랜드 컬러 팔레트 시안(Charcoal Black · Deep Vinyl Black · Glass White · Light Rose · Coral Pink)을 따른다.
 */
public final class CarinTheme {

    public static final int DEEP_VINYL = 0xFF1A191E;
    public static final int CHARCOAL = 0xFF2B2B31;
    public static final int SURFACE = 0xFF37343D;
    public static final int GLASS_WHITE = 0xFFFAF8F9;
    public static final int LIGHT_ROSE = 0xFFFFAEB5;
    public static final int CORAL = 0xFFFF7F88;
    public static final int CORAL_DEEP = 0xFFF2646F;
    public static final int MUTED = 0xFFB9ADB4;
    /** 보조 문구. 가장 밝은 표면(SURFACE)에서도 대비 4.5:1 이상 (Pleos UI 가이드) */
    public static final int FAINT = 0xFFA79CA4;
    public static final int AMBER = 0xFFFFC98A;
    public static final int STROKE = 0x29FFFFFF;
    public static final int GLASS_FILL = 0xB82B2B31;

    /** 사진이 없을 때 쓰는 노을 그라디언트 (보라 → 로즈 → 피치) */
    public static final int[] SUNSET = {0xFF2E2838, 0xFF5B3A55, 0xFFB86475, 0xFFF1A08A};

    public static final int LIGHT = 0;
    public static final int REGULAR = 1;
    public static final int MEDIUM = 2;
    public static final int BOLD = 3;

    private CarinTheme() {
    }

    /** dp 값을 픽셀로 변환한다 */
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 색상의 알파만 바꾼 값을 반환한다 */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 굵기 상수에 맞는 서체를 반환한다 */
    public static Typeface typeface(int weight) {
        switch (weight) {
            case LIGHT:
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case MEDIUM:
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case BOLD:
                return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
            default:
                return Typeface.SANS_SERIF;
        }
    }

    /** 브랜드 서체·색상을 적용한 TextView를 만든다 */
    public static TextView text(Context context, CharSequence value, float sp, int color, int weight) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(typeface(weight));
        view.setLineSpacing(0, 1.12f);
        // 한국어가 "흘러나옵/니다"처럼 어절 중간에서 끊기지 않도록 어절(구) 단위 줄바꿈 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            view.setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
        }
        return view;
    }

    /** 단색 둥근 배경 */
    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** 테두리가 있는 둥근 배경 */
    public static GradientDrawable roundedStroke(Context context, int fill, int stroke,
                                                 float strokeDp, float radiusDp) {
        GradientDrawable drawable = rounded(context, fill, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, strokeDp)), stroke);
        return drawable;
    }

    /** 그라디언트 둥근 배경 */
    public static GradientDrawable gradient(Context context, GradientDrawable.Orientation orientation,
                                            int[] colors, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** 반투명 유리 카드 배경 */
    public static GradientDrawable glass(Context context, float radiusDp) {
        return roundedStroke(context, GLASS_FILL, STROKE, 1, radiusDp);
    }

    /** 사진이 없을 때의 기본 노을 배경 */
    public static GradientDrawable sunset(Context context, float radiusDp) {
        return gradient(context, GradientDrawable.Orientation.TL_BR, SUNSET, radiusDp);
    }

    /** 앱 전체 배경: 딥 바이닐 블랙 + 상단의 은은한 코랄 빛 */
    public static Drawable appBackground(Context context) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF2C2330, DEEP_VINYL, DEEP_VINYL});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[] {0x38FF7F88, 0x00FF7F88});
        glow.setGradientRadius(dp(context, 900));
        glow.setGradientCenter(0.5f, 0f);
        return new LayerDrawable(new Drawable[] {base, glow});
    }

    /** 눌림 효과. mask는 버튼 모양과 같은 drawable */
    public static RippleDrawable ripple(Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask);
    }

    /** 코랄 빛 그림자(글로우)를 준다 */
    public static void glow(View view, int color, float elevationDp) {
        view.setElevation(dp(view.getContext(), elevationDp));
        view.setOutlineAmbientShadowColor(color);
        view.setOutlineSpotShadowColor(color);
    }

    /** 브랜드 아이콘을 담은 ImageView */
    public static ImageView icon(Context context, CarinIcon.Type type, int color) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(new CarinIcon(type, color));
        return view;
    }

    /** 상태 표시용 알약 모양 칩 */
    public static TextView chip(Context context, CharSequence value, int color) {
        TextView view = text(context, value, 15, color, MEDIUM);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        styleChip(view, color);
        return view;
    }

    /** 칩의 색상을 바꾼다 */
    public static void styleChip(TextView chip, int color) {
        chip.setTextColor(color);
        chip.setBackground(roundedStroke(chip.getContext(),
                withAlpha(color, 0x22), withAlpha(color, 0x66), 1, 999));
    }

    /** 아이콘 + 텍스트 한 줄 */
    public static LinearLayout iconRow(Context context, CarinIcon.Type type, int iconColor,
                                       TextView label, float iconDp) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = icon(context, type, iconColor);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(context, iconDp), dp(context, iconDp));
        iconParams.rightMargin = dp(context, 10);
        row.addView(icon, iconParams);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    /** "2025-08-15" → "2025.08.15" */
    public static String displayDate(String isoDate) {
        return isoDate == null ? "" : isoDate.replace('-', '.');
    }
    // ── 로터리(CCP)·방향키 포커스 표시 ──

    /** 포커스 테두리를 이미 붙인 뷰 표시용 태그 키 */
    private static final int FOCUS_RING_TAG = R.id.carin_focus_ring;

    /**
     * 뷰 트리의 모든 클릭 가능한 뷰에 포커스 테두리를 붙이고, 이후 동적으로 추가되는 뷰에도
     * 레이아웃이 바뀔 때마다 붙인다. Pleos CCP 가이드: 로터리로 옮긴 포커스가 눈에 보여야 한다.
     *
     * @param root 감시할 루트 뷰 (Activity 콘텐츠 또는 다이얼로그 카드)
     */
    public static void installFocusRings(ViewGroup root) {
        applyFocusRings(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                (ViewTreeObserver.OnGlobalLayoutListener) () -> applyFocusRings(root));
    }

    /**
     * 하위 뷰 중 클릭 가능한 뷰에 포커스 테두리를 한 번씩 붙인다.
     * {@code setFocusable(false)}로 명시한 뷰(터치 전용 영역)는 건너뛴다.
     *
     * @param view 검사할 뷰
     */
    public static void applyFocusRings(View view) {
        if (view.isClickable() && view.isFocusable() && view.getTag(FOCUS_RING_TAG) == null) {
            float radius = (view instanceof PillButton || view instanceof CircleButton) ? 999 : 24;
            addFocusRing(view, radius);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyFocusRings(group.getChildAt(i));
            }
        }
    }

    /**
     * 포커스를 받았을 때만 보이는 라이트 로즈 테두리를 전경에 겹친다. 기존 전경(리플)은 유지한다.
     *
     * @param view     대상 뷰
     * @param radiusDp 테두리 모서리 반경 (dp, 999면 알약·원)
     */
    public static void addFocusRing(View view, float radiusDp) {
        Context context = view.getContext();
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x26FFAEB5);
        ring.setStroke(dp(context, 3), LIGHT_ROSE);
        ring.setCornerRadius(dp(context, radiusDp));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, ring);
        Drawable existing = view.getForeground();
        view.setForeground(existing == null ? states : new LayerDrawable(new Drawable[] {existing, states}));
        // 시스템 기본 포커스 강조(옅은 회색)는 거의 보이지 않으므로 끄고 위 테두리를 쓴다
        view.setDefaultFocusHighlightEnabled(false);
        view.setTag(FOCUS_RING_TAG, Boolean.TRUE);
    }
}
