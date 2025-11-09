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

    private List<Bookmark> bookmarks = new ArrayList<>();
    private Context context;
    private OnBookmarkClickListener listener;

    public interface OnBookmarkClickListener {
        void onBookmarkClick(Bookmark bookmark);
        void onBookmarkMenuClick(Bookmark bookmark, View anchorView);
    }

    public BookmarkAdapter(Context context, OnBookmarkClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setBookmarks(List<Bookmark> bookmarks) {
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
        Bookmark bookmark = bookmarks.get(position);

        holder.bookmarkText.setText(bookmark.getText());

        // Apply left border color based on user's choice
        int color = Color.parseColor(bookmark.getColor());
        holder.leftBorder.setBackgroundColor(color);

        // Remove text background highlight - only show left border
        holder.bookmarkText.setBackgroundColor(Color.TRANSPARENT);

        // Show note if available
        if (bookmark.getNote() != null && !bookmark.getNote().trim().isEmpty()) {
            holder.bookmarkNote.setVisibility(View.VISIBLE);
            holder.bookmarkNote.setText(bookmark.getNote());
        } else {
            holder.bookmarkNote.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(bookmark);
            }
        });

        holder.menuBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkMenuClick(bookmark, v);
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