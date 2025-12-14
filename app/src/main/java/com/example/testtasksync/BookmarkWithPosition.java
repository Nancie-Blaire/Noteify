package com.example.testtasksync;

public class BookmarkWithPosition {
    public Bookmark bookmark;
    public int blockPosition;

    public BookmarkWithPosition(Bookmark bookmark, int blockPosition) {
        this.bookmark = bookmark;
        this.blockPosition = blockPosition;
    }
}