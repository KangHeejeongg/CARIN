package com.carin.noraebang.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 이미지 리소스 없이 코드로 그리는 라인 아이콘 (24×24 좌표계).
 * 이모지 대신 사용해 차량 화면에서 일관된 톤을 유지한다.
 */
public final class CarinIcon extends Drawable {

    /** 지원하는 아이콘 종류 */
    public enum Type {
        PLAY, PAUSE, PREV, NEXT, LIST, MIC, SPARKLE, PIN, CLOSE, PHOTO, PLUS,
        CALENDAR, NOTE, NAV, TRASH, CHEVRON_DOWN, CHEVRON_UP
    }

    private Type type;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    /** 아이콘 종류와 색으로 생성한다 */
    public CarinIcon(Type type, int color) {
        this.type = type;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(2f);
        fill.setStyle(Paint.Style.FILL);
        fill.setStrokeJoin(Paint.Join.ROUND);
        setColor(color);
    }

    /** 아이콘 색을 바꾼다 */
    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    /** 아이콘 종류를 바꾼다 (재생 ↔ 일시정지 등) */
    public void setType(Type type) {
        this.type = type;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float size = Math.min(b.width(), b.height());
        if (size <= 0) return;

        canvas.save();
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
        canvas.scale(size / 24f, size / 24f);
        path.reset();

        switch (type) {
            case PLAY:
                path.moveTo(8, 5);
                path.lineTo(19, 12);
                path.lineTo(8, 19);
                path.close();
                canvas.drawPath(path, fill);
                break;
            case PAUSE:
                rect.set(6.5f, 5, 10.5f, 19);
                canvas.drawRoundRect(rect, 1.3f, 1.3f, fill);
                rect.set(13.5f, 5, 17.5f, 19);
                canvas.drawRoundRect(rect, 1.3f, 1.3f, fill);
                break;
            case PREV:
                path.moveTo(18, 6);
                path.lineTo(9.5f, 12);
                path.lineTo(18, 18);
                path.close();
                canvas.drawPath(path, fill);
                rect.set(6, 6, 8.4f, 18);
                canvas.drawRoundRect(rect, 1, 1, fill);
                break;
            case NEXT:
                path.moveTo(6, 6);
                path.lineTo(14.5f, 12);
                path.lineTo(6, 18);
                path.close();
                canvas.drawPath(path, fill);
                rect.set(15.6f, 6, 18, 18);
                canvas.drawRoundRect(rect, 1, 1, fill);
                break;
            case LIST:
                for (int y = 7; y <= 17; y += 5) {
                    canvas.drawLine(9, y, 20, y, stroke);
                    canvas.drawCircle(4.8f, y, 1.3f, fill);
                }
                break;
            case MIC:
                rect.set(9, 3, 15, 14);
                canvas.drawRoundRect(rect, 3, 3, stroke);
                rect.set(5.5f, 4.5f, 18.5f, 16.5f);
                path.addArc(rect, 0, 180);
                canvas.drawPath(path, stroke);
                canvas.drawLine(12, 16.5f, 12, 20.5f, stroke);
                canvas.drawLine(8.5f, 20.5f, 15.5f, 20.5f, stroke);
                break;
            case SPARKLE:
                path.moveTo(10, 4);
                path.quadTo(10.8f, 12.2f, 19, 13);
                path.quadTo(10.8f, 13.8f, 10, 22);
                path.quadTo(9.2f, 13.8f, 1, 13);
                path.quadTo(9.2f, 12.2f, 10, 4);
                path.close();
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(19, 1.5f);
                path.quadTo(19.3f, 4.2f, 22, 4.5f);
                path.quadTo(19.3f, 4.8f, 19, 7.5f);
                path.quadTo(18.7f, 4.8f, 16, 4.5f);
                path.quadTo(18.7f, 4.2f, 19, 1.5f);
                path.close();
                canvas.drawPath(path, fill);
                break;
            case PIN:
                path.moveTo(12, 21.5f);
                path.cubicTo(7.5f, 16, 5, 12.8f, 5, 9.5f);
                rect.set(5, 2.5f, 19, 16.5f);
                path.arcTo(rect, 180, 180);
                path.cubicTo(19, 12.8f, 16.5f, 16, 12, 21.5f);
                path.close();
                canvas.drawPath(path, stroke);
                canvas.drawCircle(12, 9.5f, 2.4f, stroke);
                break;
            case CLOSE:
                canvas.drawLine(6, 6, 18, 18, stroke);
                canvas.drawLine(18, 6, 6, 18, stroke);
                break;
            case PHOTO:
                rect.set(3, 5, 21, 19);
                canvas.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                canvas.drawCircle(8.5f, 10, 1.6f, fill);
                path.moveTo(3.5f, 17);
                path.lineTo(9, 12);
                path.lineTo(13, 15.5f);
                path.lineTo(16, 13);
                path.lineTo(20.5f, 16.5f);
                canvas.drawPath(path, stroke);
                break;
            case PLUS:
                canvas.drawLine(12, 5, 12, 19, stroke);
                canvas.drawLine(5, 12, 19, 12, stroke);
                break;
            case CALENDAR:
                rect.set(4, 6, 20, 20);
                canvas.drawRoundRect(rect, 2, 2, stroke);
                canvas.drawLine(4, 10.5f, 20, 10.5f, stroke);
                canvas.drawLine(8.5f, 4, 8.5f, 8, stroke);
                canvas.drawLine(15.5f, 4, 15.5f, 8, stroke);
                break;
            case NOTE:
                canvas.drawCircle(7.5f, 17.5f, 2.5f, fill);
                canvas.drawCircle(17, 15.5f, 2.5f, fill);
                path.moveTo(10, 17.5f);
                path.lineTo(10, 5.5f);
                path.lineTo(19.5f, 3.5f);
                path.lineTo(19.5f, 15.5f);
                canvas.drawPath(path, stroke);
                break;
            case NAV:
                path.moveTo(12, 3);
                path.lineTo(19.5f, 20);
                path.lineTo(12, 16);
                path.lineTo(4.5f, 20);
                path.close();
                canvas.drawPath(path, fill);
                break;
            case TRASH:
                canvas.drawLine(4, 7, 20, 7, stroke);
                path.moveTo(9, 7);
                path.lineTo(9, 4.5f);
                path.lineTo(15, 4.5f);
                path.lineTo(15, 7);
                canvas.drawPath(path, stroke);
                rect.set(6, 7, 18, 20);
                canvas.drawRoundRect(rect, 2, 2, stroke);
                canvas.drawLine(10, 11, 10, 16, stroke);
                canvas.drawLine(14, 11, 14, 16, stroke);
                break;
            case CHEVRON_DOWN:
                path.moveTo(6, 9);
                path.lineTo(12, 15);
                path.lineTo(18, 9);
                canvas.drawPath(path, stroke);
                break;
            case CHEVRON_UP:
                path.moveTo(6, 15);
                path.lineTo(12, 9);
                path.lineTo(18, 15);
                canvas.drawPath(path, stroke);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        stroke.setColorFilter(colorFilter);
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
