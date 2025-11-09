package com.example.testtasksync;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.LineBackgroundSpan;

public class CustomUnderlineSpan implements LineBackgroundSpan {
    private int color;
    private float strokeWidth = 4f;
    private int startIndex;
    private int endIndex;

    public CustomUnderlineSpan(int color, int startIndex, int endIndex) {
        this.color = color;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    @Override
    public void drawBackground(Canvas canvas, Paint paint, int left, int right,
                               int top, int baseline, int bottom,
                               CharSequence text, int start, int end, int lineNumber) {
        // Calculate the intersection of the line range and the span range
        int spanStart = Math.max(start, startIndex);
        int spanEnd = Math.min(end, endIndex);

        if (spanStart < spanEnd) {
            // Save original paint color
            int originalColor = paint.getColor();
            float originalStrokeWidth = paint.getStrokeWidth();

            // Set underline color and style
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);

            // Measure text to get underline position
            float startX = left + paint.measureText(text, start, spanStart);
            float endX = left + paint.measureText(text, start, spanEnd);

            // Calculate underline position
            float underlineY = baseline + paint.descent() + 2f;

            // Draw the underline
            canvas.drawLine(startX, underlineY, endX, underlineY, paint);

            // Restore original paint properties
            paint.setColor(originalColor);
            paint.setStrokeWidth(originalStrokeWidth);
        }
    }
}