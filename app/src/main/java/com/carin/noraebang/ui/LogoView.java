package com.carin.noraebang.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** CAR IN 워드마크: 자동차 지붕 라인 + 자간 넓은 "CAR IN" + 이퀄라이저 바 */
public final class LogoView extends View {

    private static final String WORDMARK = "CAR IN";
    private static final float[] BAR_HEIGHTS = {0.35f, 0.65f, 1f, 0.75f, 1f, 0.6f, 0.35f};

    private final Paint roofPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path roof = new Path();
    private final RectF bar = new RectF();

    /** 크기는 LayoutParams로 정하며, 글자·라인은 높이에 비례한다 */
    public LogoView(Context context) {
        super(context);
        roofPaint.setStyle(Paint.Style.STROKE);
        roofPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setColor(CarinTheme.GLASS_WHITE);
        textPaint.setTypeface(CarinTheme.typeface(CarinTheme.MEDIUM));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setLetterSpacing(0.32f);
        barPaint.setColor(CarinTheme.CORAL);
        rulePaint.setColor(CarinTheme.withAlpha(CarinTheme.CORAL, 0x88));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f;

        textPaint.setTextSize(h * 0.34f);
        float tw = Math.min(textPaint.measureText(WORDMARK), w * 0.8f);

        // 1. 자동차 지붕 라인
        float left = cx - tw * 0.62f;
        float right = cx + tw * 0.66f;
        roof.reset();
        roof.moveTo(left, h * 0.42f);
        roof.cubicTo(left + tw * 0.18f, h * 0.38f, left + tw * 0.30f, h * 0.08f, cx - tw * 0.10f, h * 0.07f);
        roof.lineTo(cx + tw * 0.12f, h * 0.07f);
        roof.cubicTo(cx + tw * 0.36f, h * 0.08f, cx + tw * 0.42f, h * 0.30f, right, h * 0.36f);
        roofPaint.setStrokeWidth(Math.max(2f, h * 0.022f));
        roofPaint.setShader(new LinearGradient(left, 0, right, 0,
                new int[] {0x00FFFFFF, 0xDDFFFFFF, CarinTheme.CORAL, 0x00FF7F88},
                new float[] {0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(roof, roofPaint);

        // 2. 워드마크
        canvas.drawText(WORDMARK, cx, h * 0.70f, textPaint);

        // 3. 이퀄라이저 바와 양옆 가는 선
        float barW = Math.max(2f, h * 0.024f);
        float gap = h * 0.03f;
        float maxBar = h * 0.14f;
        float centerY = h * 0.89f;
        float total = BAR_HEIGHTS.length * barW + (BAR_HEIGHTS.length - 1) * gap;
        float x = cx - total / 2f;
        for (float ratio : BAR_HEIGHTS) {
            float bh = maxBar * ratio;
            bar.set(x, centerY - bh / 2f, x + barW, centerY + bh / 2f);
            canvas.drawRoundRect(bar, barW / 2f, barW / 2f, barPaint);
            x += barW + gap;
        }
        rulePaint.setStrokeWidth(Math.max(1f, h * 0.01f));
        float ruleGap = total / 2f + gap * 2f;
        canvas.drawLine(cx - ruleGap - tw * 0.28f, centerY, cx - ruleGap, centerY, rulePaint);
        canvas.drawLine(cx + ruleGap, centerY, cx + ruleGap + tw * 0.28f, centerY, rulePaint);
    }
}
