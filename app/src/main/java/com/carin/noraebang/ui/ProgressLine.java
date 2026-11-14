package com.carin.noraebang.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** 재생 진행 바 (표시 전용, 탐색 기능 없음) */
public final class ProgressLine extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean knob;
    private float progress = 0f;

    /** @param knob 현재 위치에 동그란 손잡이를 그릴지 여부 */
    public ProgressLine(Context context, boolean knob) {
        super(context);
        this.knob = knob;
        track.setColor(0x33FFFFFF);
        knobPaint.setColor(0xFFFFFFFF);
    }

    /** 0~1 사이 진행률을 설정한다 */
    public void setProgress(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - progress) < 0.001f) return;
        progress = clamped;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float thickness = 4 * density;
        float inset = knob ? 8 * density : 0;
        float left = inset;
        float right = w - inset;
        float cy = h / 2f;

        rect.set(left, cy - thickness / 2f, right, cy + thickness / 2f);
        canvas.drawRoundRect(rect, thickness, thickness, track);

        float x = left + (right - left) * progress;
        fill.setShader(new LinearGradient(left, 0, right, 0,
                CarinTheme.LIGHT_ROSE, CarinTheme.CORAL, Shader.TileMode.CLAMP));
        rect.set(left, cy - thickness / 2f, x, cy + thickness / 2f);
        canvas.drawRoundRect(rect, thickness, thickness, fill);

        if (knob) {
            canvas.drawCircle(x, cy, 8 * density, knobPaint);
        }
    }
}
