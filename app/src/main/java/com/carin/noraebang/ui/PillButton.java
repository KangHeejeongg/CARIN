package com.carin.noraebang.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 아이콘 + 라벨을 가운데 정렬한 알약 모양 버튼 (주 버튼 · 보조 버튼 · 탭) */
public class PillButton extends LinearLayout {

    /** 버튼 스타일 */
    public enum Style { PRIMARY, GHOST, TAB }

    private final Style style;
    private final ImageView iconView;
    private final CarinIcon icon;
    private final TextView label;
    private boolean tabSelected = false;

    /** 라벨·아이콘(null 가능)·스타일로 생성한다 */
    public PillButton(Context context, CharSequence text, CarinIcon.Type iconType, Style style) {
        super(context);
        this.style = style;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        int pad = CarinTheme.dp(context, 24);
        setPadding(pad, 0, pad, 0);

        icon = new CarinIcon(iconType == null ? CarinIcon.Type.PLAY : iconType, Color.WHITE);
        iconView = new ImageView(context);
        iconView.setImageDrawable(icon);
        LayoutParams iconParams = new LayoutParams(CarinTheme.dp(context, 24), CarinTheme.dp(context, 24));
        iconParams.rightMargin = CarinTheme.dp(context, 10);
        addView(iconView, iconParams);
        iconView.setVisibility(iconType == null ? GONE : VISIBLE);

        label = CarinTheme.text(context, text, 19, Color.WHITE, CarinTheme.MEDIUM);
        label.setSingleLine(true);
        addView(label);

        setForeground(CarinTheme.ripple(CarinTheme.rounded(context, Color.WHITE, 999)));
        applyStyle();
    }

    /** 라벨을 바꾼다 */
    public void setText(CharSequence text) {
        label.setText(text);
    }

    /**
     * 아이콘을 바꾼다.
     *
     * @param type 새 아이콘. null이면 아이콘을 숨긴다
     */
    public void setIcon(CarinIcon.Type type) {
        if (type == null) {
            iconView.setVisibility(GONE);
            return;
        }
        icon.setType(type);
        iconView.setVisibility(VISIBLE);
    }

    /** 라벨 글자 크기와 아이콘 크기를 함께 바꾼다 */
    public void setTextSize(float sp) {
        label.setTextSize(sp);
        int size = CarinTheme.dp(getContext(), sp * 1.25f);
        LayoutParams params = (LayoutParams) iconView.getLayoutParams();
        params.width = size;
        params.height = size;
        iconView.setLayoutParams(params);
    }

    /** 탭 스타일에서 선택 상태를 표시한다 */
    public void setTabSelected(boolean selected) {
        tabSelected = selected;
        applyStyle();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.45f);
    }

    private void applyStyle() {
        Context c = getContext();
        int textColor;
        int iconColor;
        switch (style) {
            case PRIMARY:
                GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[] {0xFFFF9AA2, CarinTheme.CORAL});
                bg.setCornerRadius(CarinTheme.dp(c, 999));
                setBackground(bg);
                CarinTheme.glow(this, CarinTheme.CORAL, 10);
                // 코랄 위 흰 글자는 대비 2.3:1 → 딥 바이닐로 4.5:1 이상 확보 (Pleos UI 가이드)
                textColor = CarinTheme.DEEP_VINYL;
                iconColor = CarinTheme.DEEP_VINYL;
                break;
            case TAB:
                if (tabSelected) {
                    GradientDrawable selectedBg = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[] {0x66FF7F88, 0x26FF7F88});
                    selectedBg.setCornerRadius(CarinTheme.dp(c, 999));
                    selectedBg.setStroke(CarinTheme.dp(c, 1.5f), 0xB3FF7F88);
                    setBackground(selectedBg);
                    CarinTheme.glow(this, CarinTheme.CORAL, 8);
                    textColor = Color.WHITE;
                    iconColor = CarinTheme.CORAL;
                } else {
                    setBackground(CarinTheme.roundedStroke(c, 0x14FFFFFF, 0x1FFFFFFF, 1, 999));
                    setElevation(0);
                    textColor = CarinTheme.MUTED;
                    iconColor = CarinTheme.MUTED;
                }
                break;
            default:
                setBackground(CarinTheme.roundedStroke(c, 0x1AFFFFFF, 0x33FFFFFF, 1, 999));
                textColor = CarinTheme.GLASS_WHITE;
                iconColor = CarinTheme.LIGHT_ROSE;
                break;
        }
        label.setTextColor(textColor);
        icon.setColor(iconColor);
    }

    /** 사용하지 않는 공간을 채우는 빈 뷰 (행 정렬용) */
    public static View spacer(Context context) {
        return new View(context);
    }
}
