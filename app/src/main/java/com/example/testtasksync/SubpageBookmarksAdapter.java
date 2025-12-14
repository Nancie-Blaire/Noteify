package com.example.testtasksync;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SubpageBookmarksAdapter extends RecyclerView.Adapter<SubpageBookmarksAdapter.ViewHolder> {

    private List<BookmarkWithPosition> bookmarks;
    private OnBookmarkClickListener listener;

    public interface OnBookmarkClickListener {
        void onBookmarkClick(BookmarkWithPosition bookmark);
        void onBookmarkMenuClick(BookmarkWithPosition bookmark);
    }

    public SubpageBookmarksAdapter(List<BookmarkWithPosition> bookmarks, OnBookmarkClickListener listener) {
        this.bookmarks = bookmarks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BookmarkWithPosition item = bookmarks.get(position);
        Bookmark bookmark = item.bookmark;

        // Set text
        holder.bookmarkText.setText(bookmark.getText());

        // Set note if exists
        if (bookmark.getNote() != null && !bookmark.getNote().isEmpty()) {
            holder.bookmarkNote.setText(bookmark.getNote());
            holder.bookmarkNote.setVisibility(View.VISIBLE);
        } else {
            holder.bookmarkNote.setVisibility(View.GONE);
        }

        // Set left border color based on bookmark color
        try {
            int color = Color.parseColor(bookmark.getColor());
            holder.leftBorder.setBackgroundColor(color);
        } catch (Exception e) {
            holder.leftBorder.setBackgroundColor(Color.parseColor("#FFF9C4"));
        }

        // Click listener - go to bookmark location
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(item);
            }
        });

        // ✅ Three dots menu button click
        if (holder.menuBtn != null) {
            holder.menuBtn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookmarkMenuClick(item);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView bookmarkText;
        TextView bookmarkNote;
        View leftBorder;
        ImageView menuBtn;

        ViewHolder(View view) {
            super(view);
            bookmarkText = view.findViewById(R.id.bookmarkText);
            bookmarkNote = view.findViewById(R.id.bookmarkNote);
            leftBorder = view.findViewById(R.id.leftBorder);
            menuBtn = view.findViewById(R.id.bookmarkMenuBtn); // ✅ Use existing button
        }
    }
}