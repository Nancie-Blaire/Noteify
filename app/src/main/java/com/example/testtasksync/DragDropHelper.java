package com.example.testtasksync;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class DragDropHelper extends ItemTouchHelper.Callback {

    private final DragListener listener;
    private int dragFrom = -1;
    private int dragTo = -1;
    private Paint dividerPaint;
    private Paint shadowPaint;

    public interface DragListener {
        void onItemMoved(int fromPosition, int toPosition);
        void onDragFinished();
    }

    public DragDropHelper(DragListener listener) {
        this.listener = listener;

        // Setup paint for visual feedback
        dividerPaint = new Paint();
        dividerPaint.setColor(Color.parseColor("#4CAF50"));
        dividerPaint.setStrokeWidth(4f);

        shadowPaint = new Paint();
        shadowPaint.setColor(Color.parseColor("#40000000"));
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
        // Enable drag up and down
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;

        // Enable swipe left to delete (optional)
        int swipeFlags = ItemTouchHelper.START;

        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        if (dragFrom == -1) {
            dragFrom = fromPosition;
        }
        dragTo = toPosition;

        listener.onItemMoved(fromPosition, toPosition);
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Handle swipe to delete
        if (direction == ItemTouchHelper.START) {
            // You can implement delete here
        }
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Item picked up
            if (viewHolder != null) {
                viewHolder.itemView.setAlpha(0.8f);
                viewHolder.itemView.setScaleX(1.05f);
                viewHolder.itemView.setScaleY(1.05f);
                viewHolder.itemView.setElevation(8f);
            }
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            // Item released
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                listener.onDragFinished();
            }
            dragFrom = -1;
            dragTo = -1;
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        // Reset visual state
        viewHolder.itemView.setAlpha(1.0f);
        viewHolder.itemView.setScaleX(1.0f);
        viewHolder.itemView.setScaleY(1.0f);
        viewHolder.itemView.setElevation(0f);
    }

    @Override
    public void onChildDraw(@NonNull Canvas c,
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY,
                            int actionState,
                            boolean isCurrentlyActive) {

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Draw drop indicator line
            View itemView = viewHolder.itemView;

            // Draw a line where the item will be placed
            if (isCurrentlyActive) {
                float top = itemView.getTop() + dY;

                c.drawLine(
                        itemView.getLeft() + 20,
                        top,
                        itemView.getRight() - 20,
                        top,
                        dividerPaint
                );
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true; // Enable long press to start drag
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false; // Disable swipe for now
    }

    @Override
    public float getMoveThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.3f; // Move threshold (30% of item height)
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.7f;
    }
}