package com.example.testtasksync;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DividerSpan extends ReplacementSpan {
    private final String style;
    private final int color;

    public DividerSpan(String style, int color) {
        this.style = style;
        this.color = color;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        // Return 0 width since we'll draw full width in draw()
        return 0;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        paint.setColor(color);
        paint.setStrokeWidth(2f);

        float width = canvas.getWidth();
        float centerY = (top + bottom) / 2f;

        switch (style) {
            case "solid":
                canvas.drawLine(0, centerY, width, centerY, paint);
                break;
            case "dashed":
                paint.setStrokeWidth(3f);
                float dashX = 0;
                while (dashX < width) {
                    canvas.drawLine(dashX, centerY, Math.min(dashX + 20, width), centerY, paint);
                    dashX += 30;
                }
                break;
            case "dotted":
                paint.setStrokeWidth(4f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                float dotX = 0;
                while (dotX < width) {
                    canvas.drawPoint(dotX, centerY, paint);
                    dotX += 15;
                }
                break;
            case "double":
                canvas.drawLine(0, centerY - 3, width, centerY - 3, paint);
                canvas.drawLine(0, centerY + 3, width, centerY + 3, paint);
                break;
            case "arrows":
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStyle(Paint.Style.FILL);
                // Use centerY instead of y for proper vertical alignment
                canvas.drawText("→→→→→→→ ✱ ←←←←←←←", width / 2, centerY + 7, paint);
                break;
            case "stars":
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStyle(Paint.Style.FILL);
                // Use centerY instead of y for proper vertical alignment
                canvas.drawText("✦✦✦✦✦ ❋ ✦✦✦✦✦", width / 2, centerY + 7, paint);
                break;
            case "wave":
                paint.setStrokeWidth(3f);
                paint.setStyle(Paint.Style.STROKE);
                float waveX = 0;
                while (waveX < width) {
                    canvas.drawLine(waveX, centerY - 5, waveX + 10, centerY + 5, paint);
                    canvas.drawLine(waveX + 10, centerY + 5, waveX + 20, centerY - 5, paint);
                    waveX += 20;
                }
                break;
            case "diamond":
                paint.setTextSize(20f);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStyle(Paint.Style.FILL);
                // Use centerY instead of y for proper vertical alignment
                canvas.drawText("◈◈◈◈◈◈ ◆ ◈◈◈◈◈◈", width / 2, centerY + 7, paint);
                break;
        }
    }
}