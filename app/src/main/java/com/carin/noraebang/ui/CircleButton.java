package com.carin.noraebang.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** 원형 아이콘 버튼 (재생 컨트롤 · 닫기 등) */
public final class CircleButton extends FrameLayout {

    /** 버튼 스타일: 코랄 주 버튼 / 투명 / 유리 */
    public enum Style { PRIMARY, GHOST, GLASS }

    private final CarinIcon icon;

    /**
     * @param iconDp 가운데 아이콘 크기(dp). 버튼 크기는 LayoutParams로 정한다
     */
    public CircleButton(Context context, CarinIcon.Type type, Style style, int iconDp) {
        super(context);
        setClickable(true);
        setFocusable(true);

        // 코랄 배경 위 흰색은 대비 2.3:1이라 Pleos 기준(4.5:1)에 못 미친다 → 딥 바이닐(7.2:1)
        icon = new CarinIcon(type, style == Style.PRIMARY ? CarinTheme.DEEP_VINYL : CarinTheme.GLASS_WHITE);
        ImageView iconView = new ImageView(context);
        iconView.setImageDrawable(icon);
        int size = CarinTheme.dp(context, iconDp);
        addView(iconView, new LayoutParams(size, size, Gravity.CENTER));

        GradientDrawable bg;
        if (style == Style.PRIMARY) {
            bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {0xFFFF9AA2, CarinTheme.CORAL, CarinTheme.CORAL_DEEP});
            CarinTheme.glow(this, CarinTheme.CORAL, 14);
        } else if (style == Style.GLASS) {
            bg = new GradientDrawable();
            bg.setColor(0x1FFFFFFF);
            bg.setStroke(CarinTheme.dp(context, 1), CarinTheme.STROKE);
        } else {
            bg = new GradientDrawable();
            bg.setColor(Color.TRANSPARENT);
        }
        bg.setShape(GradientDrawable.OVAL);
        setBackground(bg);

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        setForeground(CarinTheme.ripple(mask));
    }

    /** 아이콘을 바꾼다 (재생 ↔ 일시정지) */
    public void setIcon(CarinIcon.Type type) {
        icon.setType(type);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.45f);
    }
}
