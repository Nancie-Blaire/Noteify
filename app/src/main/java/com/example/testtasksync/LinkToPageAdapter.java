package com.example.testtasksync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LinkToPageAdapter extends RecyclerView.Adapter<LinkToPageAdapter.ViewHolder> {

    private List<LinkableItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(LinkableItem item);
    }

    public LinkToPageAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateItems(List<LinkableItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_link_to_page_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView itemIcon;
        TextView itemTitle;
        TextView itemType;

        ViewHolder(View view) {
            super(view);
            itemIcon = view.findViewById(R.id.itemIcon);
            itemTitle = view.findViewById(R.id.itemTitle);
            itemType = view.findViewById(R.id.itemType);

            view.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(items.get(pos));
                }
            });
        }

        void bind(LinkableItem item) {
            itemTitle.setText(item.getTitle().isEmpty() ? "Untitled" : item.getTitle());
            itemType.setText(item.getType());

            // Set icon based on type
            switch (item.getType()) {
                case "note":
                    itemIcon.setImageResource(R.drawable.ic_fab_notes);
                    itemIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#8daaa6")));
                    break;
                case "todo":
                    itemIcon.setImageResource(R.drawable.ic_fab_todo);
                    itemIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#FFF3E0")));
                    break;
                case "weekly":
                    itemIcon.setImageResource(R.drawable.ic_calendar);
                    itemIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#F3E5F5")));
                    break;
            }
        }
    }
}