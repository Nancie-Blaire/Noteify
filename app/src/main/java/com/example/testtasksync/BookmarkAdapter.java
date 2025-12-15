package com.example.testtasksync;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {

    // ✅ Updated to use BookmarkWithPosition
    private List<BookmarkWithPosition> bookmarks = new ArrayList<>();
    private Context context;
    private OnBookmarkClickListener listener;

    public interface OnBookmarkClickListener {
        // ✅ Updated to use BookmarkWithPosition
        void onBookmarkClick(BookmarkWithPosition bookmark);
        void onBookmarkMenuClick(BookmarkWithPosition bookmark);
    }

    public BookmarkAdapter(Context context, OnBookmarkClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    // ✅ Updated setter
    public void setBookmarks(List<BookmarkWithPosition> bookmarks) {
        this.bookmarks = bookmarks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bookmark, parent, false);
        return new BookmarkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        BookmarkWithPosition item = bookmarks.get(position);
        Bookmark bookmark = item.bookmark; // ✅ Extract bookmark from wrapper

        holder.bookmarkText.setText(bookmark.getText());

        // Apply left border color based on user's choice
        try {
            int color = Color.parseColor(bookmark.getColor());
            holder.leftBorder.setBackgroundColor(color);
        } catch (Exception e) {
            holder.leftBorder.setBackgroundColor(Color.parseColor("#FFF9C4"));
        }

        // Remove text background highlight - only show left border
        holder.bookmarkText.setBackgroundColor(Color.TRANSPARENT);

        // Show note if available
        if (bookmark.getNote() != null && !bookmark.getNote().trim().isEmpty()) {
            holder.bookmarkNote.setVisibility(View.VISIBLE);
            holder.bookmarkNote.setText(bookmark.getNote());
        } else {
            holder.bookmarkNote.setVisibility(View.GONE);
        }

        // ✅ Pass BookmarkWithPosition to listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(item);
            }
        });

        // ✅ Three dots menu - pass BookmarkWithPosition
        holder.menuBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkMenuClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView bookmarkText;
        TextView bookmarkNote;
        ImageView menuBtn;
        View leftBorder;

        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            bookmarkText = itemView.findViewById(R.id.bookmarkText);
            bookmarkNote = itemView.findViewById(R.id.bookmarkNote);
            menuBtn = itemView.findViewById(R.id.bookmarkMenuBtn);
            leftBorder = itemView.findViewById(R.id.leftBorder);
        }
    }
}