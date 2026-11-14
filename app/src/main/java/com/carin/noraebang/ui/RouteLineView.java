package com.carin.noraebang.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

/**
 * 추억 카드 위에 그리는 장식용 경로선 (출발 핀 → 도착 핀).
 * 실제 GPS 경로가 아니며, 도착 라벨에는 추억의 장소만 표시한다.
 */
public final class RouteLineView extends View {

    private final Paint glowWide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowMid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final CarinIcon pin = new CarinIcon(CarinIcon.Type.PIN, 0xFFFFFFFF);
    private String startLabel = "";
    private String endLabel = "";

    /** 장식용 경로선 뷰를 만든다 */
    public RouteLineView(Context context) {
        super(context);
        for (Paint p : new Paint[] {glowWide, glowMid, core}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        glowWide.setColor(CarinTheme.withAlpha(CarinTheme.CORAL, 0x38));
        glowWide.setStrokeWidth(px(16));
        glowMid.setColor(CarinTheme.withAlpha(CarinTheme.LIGHT_ROSE, 0x80));
        glowMid.setStrokeWidth(px(7));
        core.setColor(0xFFFFEDEF);
        core.setStrokeWidth(px(2.5f));
        dotFill.setColor(0xF2FFFFFF);
        dotRing.setStyle(Paint.Style.STROKE);
        dotRing.setColor(CarinTheme.CORAL);
        dotRing.setStrokeWidth(px(3));
        labelPaint.setColor(CarinTheme.GLASS_WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(CarinTheme.typeface(CarinTheme.MEDIUM));
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18,
                getResources().getDisplayMetrics()));
        labelPaint.setShadowLayer(px(6), 0, 0, 0xAA000000);
    }

    /** 출발·도착 라벨 (빈 문자열이면 표시하지 않음) */
    public void setLabels(String start, String end) {
        startLabel = start == null ? "" : start;
        endLabel = end == null ? "" : end;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float sx = w * 0.08f;
        float sy = h * 0.74f;
        float ex = w * 0.90f;
        float ey = h * 0.30f;

        path.reset();
        path.moveTo(sx, sy);
        path.cubicTo(w * 0.28f, h * 0.95f, w * 0.40f, h * 0.48f, w * 0.55f, h * 0.58f);
        path.cubicTo(w * 0.68f, h * 0.68f, w * 0.78f, h * 0.42f, ex, ey);
        canvas.drawPath(path, glowWide);
        canvas.drawPath(path, glowMid);
        canvas.drawPath(path, core);

        drawStop(canvas, sx, sy, startLabel);
        drawStop(canvas, ex, ey, endLabel);
    }

    private void drawStop(Canvas canvas, float x, float y, String label) {
        canvas.drawCircle(x, y, px(8), dotFill);
        canvas.drawCircle(x, y, px(8), dotRing);
        float size = px(34);
        pin.setBounds(Math.round(x - size / 2f), Math.round(y - size - px(12)),
                Math.round(x + size / 2f), Math.round(y - px(12)));
        pin.draw(canvas);
        if (!label.isEmpty()) {
            canvas.drawText(label, x, y + px(36), labelPaint);
        }
    }

    private float px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
