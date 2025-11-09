package com.example.testtasksync;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.LineBackgroundSpan;

public class LeftBorderSpan implements LineBackgroundSpan {
    private int color;
    private float borderWidth = 8f; // Width of the left border
    private float paddingLeft = 16f; // Space from left edge

    public LeftBorderSpan(int color) {
        this.color = color;
    }

    @Override
    public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline, int bottom, CharSequence text, int start, int end, int lineNumber) {
        Paint borderPaint = new Paint();
        borderPaint.setColor(color);
        borderPaint.setStyle(Paint.Style.FILL);

        // Draw vertical bar on the left side
        canvas.drawRect(left + paddingLeft, top, left + paddingLeft + borderWidth, bottom, borderPaint);
    }
}