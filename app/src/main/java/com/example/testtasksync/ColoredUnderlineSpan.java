package com.example.testtasksync;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/**
 * Custom span for colored underlines that preserves text color
 */
public class ColoredUnderlineSpan extends ReplacementSpan {
    private int underlineColor;
    private float strokeWidth = 4f; // Thickness of underline

    public ColoredUnderlineSpan(int color) {
        this.underlineColor = color;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return (int) paint.measureText(text, start, end);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        // Draw the text with original color (preserved)
        canvas.drawText(text, start, end, x, y, paint);

        // Draw colored underline below text
        Paint underlinePaint = new Paint();
        underlinePaint.setColor(underlineColor);
        underlinePaint.setStrokeWidth(strokeWidth);
        underlinePaint.setStyle(Paint.Style.STROKE);

        // Calculate underline position
        float textWidth = paint.measureText(text, start, end);
        float underlineY = y + paint.descent() + 2; // Slight offset below text

        canvas.drawLine(x, underlineY, x + textWidth, underlineY, underlinePaint);
    }
}