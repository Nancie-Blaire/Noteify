package com.example.testtasksync;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import org.json.JSONObject;
import org.json.JSONException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;
import java.util.HashMap;
import java.util.Map;


public class NoteBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Map<String, List<Bookmark>> blockBookmarksMap = new HashMap<>();

    private List<NoteBlock> blocks;
    private OnBlockChangeListener listener;
    private String noteId;

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_HEADING = 1;
    private static final int TYPE_BULLET = 2;
    private static final int TYPE_NUMBERED = 3;
    private static final int TYPE_CHECKBOX = 4;
    private static final int TYPE_IMAGE = 5;
    private static final int TYPE_DIVIDER = 6;
    private static final int TYPE_SUBPAGE = 7;
    private static final int TYPE_LINK = 8;

    public interface OnBlockChangeListener {
        void onBlockChanged(NoteBlock block);
        void onBlockDeleted(int position);
        void onBlockTypeChanged(int position, NoteBlock.BlockType newType);
        void onImageClick(String imageId);
        void onSubpageClick(String subpageId);
        void onLinkClick(String url);
        void onDividerClick(int position);
        void onEnterPressed(int position, String textBeforeCursor, String textAfterCursor);
        void onBackspaceOnEmptyBlock(int position);
        void onBackspaceAtStart(int position, String currentText);

    }

    public NoteBlockAdapter(List<NoteBlock> blocks, OnBlockChangeListener listener, String noteId) {
        this.blocks = blocks;
        this.listener = listener;
        this.noteId = noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    @Override
    public int getItemViewType(int position) {
        NoteBlock block = blocks.get(position);
        switch (block.getType()) {
            case HEADING_1:
            case HEADING_2:
            case HEADING_3:
                return TYPE_HEADING;
            case BULLET:
                return TYPE_BULLET;
            case NUMBERED:
                return TYPE_NUMBERED;
            case CHECKBOX:
                return TYPE_CHECKBOX;
            case IMAGE:
                return TYPE_IMAGE;
            case DIVIDER:
                return TYPE_DIVIDER;
            case SUBPAGE:
                return TYPE_SUBPAGE;
            case LINK:
                return TYPE_LINK;
            default:
                return TYPE_TEXT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case TYPE_HEADING:
                return new HeadingViewHolder(inflater.inflate(R.layout.item_block_heading, parent, false));
            case TYPE_BULLET:
                return new BulletViewHolder(inflater.inflate(R.layout.item_block_bullet, parent, false));
            case TYPE_NUMBERED:
                return new NumberedViewHolder(inflater.inflate(R.layout.item_block_numbered, parent, false));
            case TYPE_CHECKBOX:
                return new CheckboxViewHolder(inflater.inflate(R.layout.item_block_checkbox, parent, false));
            case TYPE_IMAGE:
                return new ImageViewHolder(inflater.inflate(R.layout.item_block_image, parent, false));
            case TYPE_DIVIDER:
                return new DividerViewHolder(inflater.inflate(R.layout.item_block_divider, parent, false));
            case TYPE_SUBPAGE:
                return new SubpageViewHolder(inflater.inflate(R.layout.item_block_subpage, parent, false));
            case TYPE_LINK:
                return new LinkViewHolder(inflater.inflate(R.layout.item_block_link, parent, false));
            default:
                return new TextViewHolder(inflater.inflate(R.layout.item_block_text, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NoteBlock block = blocks.get(position);

        if (holder instanceof TextViewHolder) {
            ((TextViewHolder) holder).bind(block);
        } else if (holder instanceof HeadingViewHolder) {
            ((HeadingViewHolder) holder).bind(block);
        } else if (holder instanceof BulletViewHolder) {
            ((BulletViewHolder) holder).bind(block);
        } else if (holder instanceof NumberedViewHolder) {
            ((NumberedViewHolder) holder).bind(block);
        } else if (holder instanceof CheckboxViewHolder) {
            ((CheckboxViewHolder) holder).bind(block);
        } else if (holder instanceof ImageViewHolder) {
            ((ImageViewHolder) holder).bind(block);
        } else if (holder instanceof DividerViewHolder) {
            ((DividerViewHolder) holder).bind(block);
        } else if (holder instanceof SubpageViewHolder) {
            ((SubpageViewHolder) holder).bind(block);
        } else if (holder instanceof LinkViewHolder) {
            ((LinkViewHolder) holder).bind(block);
        }
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    public void moveBlock(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(blocks, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(blocks, i, i - 1);
            }
        }

        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).setPosition(i);
        }

        notifyItemMoved(fromPosition, toPosition);
    }

    // ✅ Helper: Convert dp to px
    private int dpToPx(int dp, View view) {
        return (int) (dp * view.getContext().getResources().getDisplayMetrics().density);
    }

    class TextViewHolder extends RecyclerView.ViewHolder {
        EditText contentEdit;
        TextWatcher textWatcher;

        TextViewHolder(View view) {
            super(view);
            contentEdit = view.findViewById(R.id.contentEdit);

            // ✅ SIMPLE: Setup custom selection menu only
            setupCustomSelectionMenu();
        }

        private void setupCustomSelectionMenu() {
            contentEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    menu.add(0, android.R.id.button1, 0, "📌 Bookmark")
                            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    try {
                        menu.removeItem(android.R.id.shareText);
                    } catch (Exception e) {}
                    return true;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    if (item.getItemId() == android.R.id.button1) {
                        handleBookmarkSelection();
                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });
        }

        private void handleBookmarkSelection() {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            NoteBlock block = blocks.get(pos);
            int start = contentEdit.getSelectionStart();
            int end = contentEdit.getSelectionEnd();

            if (start >= 0 && end > start && end <= contentEdit.getText().length()) {
                String selectedText = contentEdit.getText().toString().substring(start, end);

                if (listener instanceof NoteActivity) {
                    ((NoteActivity) listener).showBookmarkBottomSheet(
                            selectedText,
                            block.getId(),
                            start,
                            end
                    );
                }
            }
        }

        void bind(NoteBlock block) {
            String content = block.getContent();
            if (content == null) content = "";

            // ✅ REMOVE old TextWatcher
            if (textWatcher != null) {
                contentEdit.removeTextChangedListener(textWatcher);
            }

            // ✅ Get bookmarks for this block
            List<Bookmark> bookmarks = blockBookmarksMap.get(block.getId());

            // ✅ Apply bookmarks to the text
            if (bookmarks != null && !bookmarks.isEmpty()) {
                android.text.Editable editable = android.text.Editable.Factory.getInstance().newEditable(content);

                for (Bookmark bookmark : bookmarks) {
                    int start = bookmark.getStartIndex();
                    int end = bookmark.getEndIndex();

                    if (start >= 0 && end <= content.length() && start < end) {
                        int color = Color.parseColor(bookmark.getColor());

                        if ("highlight".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        } else if ("underline".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            editable.setSpan(
                                    new UnderlineSpan(),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                contentEdit.setText(editable);
            } else {
                contentEdit.setText(content);
            }

            // ✅ SET INPUT TYPE (same as Subpage)
            contentEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            contentEdit.setHorizontallyScrolling(false);
            contentEdit.setMaxLines(Integer.MAX_VALUE);

            // ✅ Direct selection (NO POST)
            contentEdit.setSelection(contentEdit.getText().length());

            // ✅ ADD NEW TextWatcher
            textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int after) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        NoteBlock block = blocks.get(pos);
                        block.setContent(s.toString());
                        listener.onBlockChanged(block);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            };
            contentEdit.addTextChangedListener(textWatcher);

            // ✅ SET KEY LISTENER
            contentEdit.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    EditText editText = (EditText) v;
                    int cursorPosition = editText.getSelectionStart();
                    String currentText = editText.getText().toString();

                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        String textBeforeCursor = currentText.substring(0, cursorPosition);
                        String textAfterCursor = currentText.substring(cursorPosition);
                        listener.onEnterPressed(pos, textBeforeCursor, textAfterCursor);
                        return true;
                    }
                    else if (keyCode == KeyEvent.KEYCODE_DEL) {
                        if (cursorPosition == 0 && !currentText.isEmpty()) {
                            listener.onBackspaceAtStart(pos, currentText);
                            return true;
                        }
                    }
                }
                return false;
            });

            // Apply indent
            int marginLeft = dpToPx(block.getIndentLevel() * 24);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);

            // Apply font style
            applyFontStyle(contentEdit, block.getFontStyle(), block.getFontColor());
        }

        private int dpToPx(int dp) {
            return (int) (dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }

    class HeadingViewHolder extends RecyclerView.ViewHolder {
        EditText contentEdit;

        HeadingViewHolder(View view) {
            super(view);
            contentEdit = view.findViewById(R.id.contentEdit);

            // ✅ Set custom action mode callback for text selection
            contentEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    menu.add(0, android.R.id.button1, 0, "📌 Bookmark")
                            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    try {
                        menu.removeItem(android.R.id.shareText);
                        menu.removeItem(android.R.id.selectAll);
                    } catch (Exception e) {
                    }
                    return true;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    if (item.getItemId() == android.R.id.button1) {
                        int pos = getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return false;

                        NoteBlock block = blocks.get(pos);
                        int start = contentEdit.getSelectionStart();
                        int end = contentEdit.getSelectionEnd();

                        if (start >= 0 && end > start && end <= contentEdit.getText().length()) {
                            String selectedText = contentEdit.getText().toString().substring(start, end);

                            if (listener instanceof NoteActivity) {
                                ((NoteActivity) listener).showBookmarkBottomSheet(
                                        selectedText,
                                        block.getId(),
                                        start,
                                        end
                                );
                            }
                        }

                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {
                }
            });

            contentEdit.addTextChangedListener(new TextWatcher() {
                private boolean isUpdatingText = false;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int after) {
                    if (isUpdatingText) return;

                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        NoteBlock block = blocks.get(pos);
                        block.setContent(s.toString());
                        listener.onBlockChanged(block);

                        // ✅ Reapply bookmarks
                        isUpdatingText = true;
                        reapplyBookmarksToView(contentEdit, block);
                        isUpdatingText = false;
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        void bind(NoteBlock block) {
            String content = block.getContent();
            if (content == null) content = "";

            // ✅ Get bookmarks for this block
            List<Bookmark> bookmarks = blockBookmarksMap.get(block.getId());

            // ✅ CRITICAL: Ensure EditText is enabled and focusable FIRST
            contentEdit.setEnabled(true);
            contentEdit.setFocusable(true);
            contentEdit.setFocusableInTouchMode(true);
            contentEdit.setCursorVisible(true);
            contentEdit.setLongClickable(true);

            // ✅ Set input type (same as Subpage)
            contentEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

            // ✅ Apply bookmarks to the text
            if (bookmarks != null && !bookmarks.isEmpty()) {
                android.text.Editable editable = android.text.Editable.Factory.getInstance().newEditable(content);

                for (Bookmark bookmark : bookmarks) {
                    int start = bookmark.getStartIndex();
                    int end = bookmark.getEndIndex();

                    if (start >= 0 && end <= content.length() && start < end) {
                        int color = Color.parseColor(bookmark.getColor());

                        if ("highlight".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        } else if ("underline".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            editable.setSpan(
                                    new UnderlineSpan(),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                contentEdit.setText(editable);
            } else {
                contentEdit.setText(content);
            }

            // ✅ NO POST - Direct selection like SubpageAdapter
            contentEdit.setSelection(contentEdit.getText().length());

            // Apply indent
            int marginLeft = dpToPx(block.getIndentLevel() * 24);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);

            // Apply font style
            applyFontStyle(contentEdit, block.getFontStyle(), block.getFontColor());
        }

        private int dpToPx(int dp) {
            return (int) (dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }

    class BulletViewHolder extends RecyclerView.ViewHolder {
        TextView bulletIcon;
        EditText contentEdit;
        private boolean isProcessingEnter = false;
        private boolean isProcessingBackspace = false;

        BulletViewHolder(View view) {
            super(view);
            bulletIcon = view.findViewById(R.id.bulletIcon);
            contentEdit = view.findViewById(R.id.contentEdit);

            // ✅ Set custom action mode callback for text selection
            contentEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    menu.add(0, android.R.id.button1, 0, "📌 Bookmark")
                            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    try {
                        menu.removeItem(android.R.id.shareText);
                        menu.removeItem(android.R.id.selectAll);
                    } catch (Exception e) {
                    }
                    return true;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    if (item.getItemId() == android.R.id.button1) {
                        int pos = getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return false;

                        NoteBlock block = blocks.get(pos);
                        int start = contentEdit.getSelectionStart();
                        int end = contentEdit.getSelectionEnd();

                        if (start >= 0 && end > start && end <= contentEdit.getText().length()) {
                            String selectedText = contentEdit.getText().toString().substring(start, end);

                            if (listener instanceof NoteActivity) {
                                ((NoteActivity) listener).showBookmarkBottomSheet(
                                        selectedText,
                                        block.getId(),
                                        start,
                                        end
                                );
                            }
                        }

                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {
                }
            });

            contentEdit.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    EditText editText = (EditText) v;
                    int cursorPosition = editText.getSelectionStart();
                    String currentText = editText.getText().toString();

                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        String textBeforeCursor = currentText.substring(0, cursorPosition);
                        String textAfterCursor = currentText.substring(cursorPosition);

                        listener.onEnterPressed(pos, textBeforeCursor, textAfterCursor);
                        return true;
                    }
                    else if (keyCode == KeyEvent.KEYCODE_DEL) {
                        if (currentText.isEmpty()) {
                            listener.onBackspaceOnEmptyBlock(pos);
                            return true;
                        }
                        else if (cursorPosition == 0) {
                            listener.onBackspaceAtStart(pos, currentText);
                            return true;
                        }
                    }
                }
                return false;
            });

            contentEdit.addTextChangedListener(new TextWatcher() {
                private String textBeforeChange = "";
                private int cursorBeforeChange = 0;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    textBeforeChange = s.toString();
                    cursorBeforeChange = contentEdit.getSelectionStart();
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int after) {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;

                    if (after == 1 && before == 0 && s.length() > start && s.charAt(start) == '\n' && !isProcessingEnter) {
                        isProcessingEnter = true;

                        String textWithoutNewline = s.toString().substring(0, start) + s.toString().substring(start + 1);
                        contentEdit.setText(textWithoutNewline);
                        contentEdit.setSelection(start);

                        String textBefore = textWithoutNewline.substring(0, start);
                        String textAfter = start < textWithoutNewline.length() ? textWithoutNewline.substring(start) : "";

                        NoteBlock block = blocks.get(pos);
                        block.setContent(textBefore);

                        listener.onEnterPressed(pos, textBefore, textAfter);
                        isProcessingEnter = false;
                        return;
                    }

                    NoteBlock block = blocks.get(pos);
                    block.setContent(s.toString());
                    listener.onBlockChanged(block);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            bulletIcon.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    int maxIndent = 2;

                    if (block.getIndentLevel() < maxIndent) {
                        block.setIndentLevel(block.getIndentLevel() + 1);
                    } else {
                        block.setIndentLevel(0);
                    }

                    notifyItemChanged(pos);
                    listener.onBlockChanged(block);
                }
            });
        }

        void bind(NoteBlock block) {
            String content = block.getContent();
            if (content == null) content = "";

            List<Bookmark> bookmarks = blockBookmarksMap.get(block.getId());

            if (bookmarks != null && !bookmarks.isEmpty()) {
                // ✅ Use Editable
                android.text.Editable editable = android.text.Editable.Factory.getInstance().newEditable(content);

                for (Bookmark bookmark : bookmarks) {
                    int start = bookmark.getStartIndex();
                    int end = bookmark.getEndIndex();

                    if (start >= 0 && end <= content.length() && start < end) {
                        int color = Color.parseColor(bookmark.getColor());

                        if ("highlight".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        } else if ("underline".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            editable.setSpan(
                                    new UnderlineSpan(),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                contentEdit.setText(editable);
            } else {
                contentEdit.setText(content);
            }

            // ✅ Set cursor to end
            contentEdit.post(() -> {
                contentEdit.setSelection(contentEdit.getText().length());
            });


            contentEdit.setHint("List item");

            String bullet = "●";
            if (block.getIndentLevel() == 1) bullet = "○";
            else if (block.getIndentLevel() >= 2) bullet = "■";
            bulletIcon.setText(bullet);

            int marginLeft = dpToPx(block.getIndentLevel() * 24);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);

            applyFontStyle(contentEdit, block.getFontStyle(), block.getFontColor());
        }

        private int dpToPx(int dp) {
            return (int) (dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }

    class NumberedViewHolder extends RecyclerView.ViewHolder {
        TextView numberText;
        EditText contentEdit;
        private boolean isProcessingEnter = false;

        NumberedViewHolder(View view) {
            super(view);
            numberText = view.findViewById(R.id.numberText);
            contentEdit = view.findViewById(R.id.contentEdit);

            // ✅ Set custom action mode callback for text selection
            contentEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    menu.add(0, android.R.id.button1, 0, "📌 Bookmark")
                            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    try {
                        menu.removeItem(android.R.id.shareText);
                        menu.removeItem(android.R.id.selectAll);
                    } catch (Exception e) {
                    }
                    return true;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    if (item.getItemId() == android.R.id.button1) {
                        int pos = getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return false;

                        NoteBlock block = blocks.get(pos);
                        int start = contentEdit.getSelectionStart();
                        int end = contentEdit.getSelectionEnd();

                        if (start >= 0 && end > start && end <= contentEdit.getText().length()) {
                            String selectedText = contentEdit.getText().toString().substring(start, end);

                            if (listener instanceof NoteActivity) {
                                ((NoteActivity) listener).showBookmarkBottomSheet(
                                        selectedText,
                                        block.getId(),
                                        start,
                                        end
                                );
                            }
                        }

                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {
                }
            });

            contentEdit.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    EditText editText = (EditText) v;
                    int cursorPosition = editText.getSelectionStart();
                    String currentText = editText.getText().toString();

                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        String textBeforeCursor = currentText.substring(0, cursorPosition);
                        String textAfterCursor = currentText.substring(cursorPosition);

                        listener.onEnterPressed(pos, textBeforeCursor, textAfterCursor);
                        return true;
                    }
                    else if (keyCode == KeyEvent.KEYCODE_DEL) {
                        if (currentText.isEmpty()) {
                            listener.onBackspaceOnEmptyBlock(pos);
                            return true;
                        }
                        else if (cursorPosition == 0) {
                            listener.onBackspaceAtStart(pos, currentText);
                            return true;
                        }
                    }
                }
                return false;
            });

            contentEdit.addTextChangedListener(new TextWatcher() {
                private boolean isUpdatingText = false;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int after) {
                    if (isUpdatingText) return;

                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;

                    if (after == 1 && before == 0 && s.length() > start && s.charAt(start) == '\n' && !isProcessingEnter) {
                        isProcessingEnter = true;

                        String textWithoutNewline = s.toString().substring(0, start) + s.toString().substring(start + 1);
                        contentEdit.setText(textWithoutNewline);
                        contentEdit.setSelection(start);

                        String textBefore = textWithoutNewline.substring(0, start);
                        String textAfter = start < textWithoutNewline.length() ? textWithoutNewline.substring(start) : "";

                        NoteBlock block = blocks.get(pos);
                        block.setContent(textBefore);

                        listener.onEnterPressed(pos, textBefore, textAfter);
                        isProcessingEnter = false;
                        return;
                    }

                    NoteBlock block = blocks.get(pos);
                    block.setContent(s.toString());
                    listener.onBlockChanged(block);

                    // ✅ Reapply bookmarks
                    isUpdatingText = true;
                    reapplyBookmarksToView(contentEdit, block);
                    isUpdatingText = false;
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        void bind(NoteBlock block) {
            String content = block.getContent();
            if (content == null) content = "";

            List<Bookmark> bookmarks = blockBookmarksMap.get(block.getId());

            if (bookmarks != null && !bookmarks.isEmpty()) {
                // ✅ Use Editable
                android.text.Editable editable = android.text.Editable.Factory.getInstance().newEditable(content);

                for (Bookmark bookmark : bookmarks) {
                    int start = bookmark.getStartIndex();
                    int end = bookmark.getEndIndex();

                    if (start >= 0 && end <= content.length() && start < end) {
                        int color = Color.parseColor(bookmark.getColor());

                        if ("highlight".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        } else if ("underline".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            editable.setSpan(
                                    new UnderlineSpan(),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                contentEdit.setText(editable);
            } else {
                contentEdit.setText(content);
            }

            // ✅ Set cursor to end
            contentEdit.post(() -> {
                contentEdit.setSelection(contentEdit.getText().length());
            });


            String numberFormat;
            switch (block.getIndentLevel()) {
                case 0:
                    numberFormat = block.getListNumber() + ".";
                    break;
                case 1:
                    numberFormat = ((char)('a' + block.getListNumber() - 1)) + ".";
                    break;
                case 2:
                    numberFormat = toRoman(block.getListNumber()) + ".";
                    break;
                default:
                    numberFormat = block.getListNumber() + ".";
                    break;
            }
            numberText.setText(numberFormat);

            int marginLeft = dpToPx(block.getIndentLevel() * 24);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);

            applyFontStyle(contentEdit, block.getFontStyle(), block.getFontColor());
        }

        private String toRoman(int number) {
            String[] romans = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
            if (number > 0 && number <= romans.length) {
                return romans[number - 1];
            }
            return "i";
        }

        private int dpToPx(int dp) {
            return (int) (dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }

    class CheckboxViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkbox;
        EditText contentEdit;
        private boolean isProcessingEnter = false;

        CheckboxViewHolder(View view) {
            super(view);
            checkbox = view.findViewById(R.id.checkbox);
            contentEdit = view.findViewById(R.id.contentEdit);

            // ✅ Set custom action mode callback for text selection
            contentEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    menu.add(0, android.R.id.button1, 0, "📌 Bookmark")
                            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    try {
                        menu.removeItem(android.R.id.shareText);
                        menu.removeItem(android.R.id.selectAll);
                    } catch (Exception e) {
                    }
                    return true;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    if (item.getItemId() == android.R.id.button1) {
                        int pos = getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return false;

                        NoteBlock block = blocks.get(pos);
                        int start = contentEdit.getSelectionStart();
                        int end = contentEdit.getSelectionEnd();

                        if (start >= 0 && end > start && end <= contentEdit.getText().length()) {
                            String selectedText = contentEdit.getText().toString().substring(start, end);

                            if (listener instanceof NoteActivity) {
                                ((NoteActivity) listener).showBookmarkBottomSheet(
                                        selectedText,
                                        block.getId(),
                                        start,
                                        end
                                );
                            }
                        }

                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {
                }
            });

            contentEdit.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    EditText editText = (EditText) v;
                    int cursorPosition = editText.getSelectionStart();
                    String currentText = editText.getText().toString();

                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        String textBeforeCursor = currentText.substring(0, cursorPosition);
                        String textAfterCursor = currentText.substring(cursorPosition);

                        listener.onEnterPressed(pos, textBeforeCursor, textAfterCursor);
                        return true;
                    }
                    else if (keyCode == KeyEvent.KEYCODE_DEL) {
                        if (currentText.isEmpty()) {
                            listener.onBackspaceOnEmptyBlock(pos);
                            return true;
                        }
                        else if (cursorPosition == 0) {
                            listener.onBackspaceAtStart(pos, currentText);
                            return true;
                        }
                    }
                }
                return false;
            });

            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    block.setChecked(isChecked);
                    listener.onBlockChanged(block);
                }
            });

            contentEdit.addTextChangedListener(new TextWatcher() {
                private boolean isUpdatingText = false;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int after) {
                    if (isUpdatingText) return;

                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;

                    if (after == 1 && before == 0 && s.length() > start && s.charAt(start) == '\n' && !isProcessingEnter) {
                        isProcessingEnter = true;

                        String textWithoutNewline = s.toString().substring(0, start) + s.toString().substring(start + 1);
                        contentEdit.setText(textWithoutNewline);
                        contentEdit.setSelection(start);

                        String textBefore = textWithoutNewline.substring(0, start);
                        String textAfter = start < textWithoutNewline.length() ? textWithoutNewline.substring(start) : "";

                        NoteBlock block = blocks.get(pos);
                        block.setContent(textBefore);

                        listener.onEnterPressed(pos, textBefore, textAfter);
                        isProcessingEnter = false;
                        return;
                    }

                    NoteBlock block = blocks.get(pos);
                    block.setContent(s.toString());
                    listener.onBlockChanged(block);

                    // ✅ Reapply bookmarks
                    isUpdatingText = true;
                    reapplyBookmarksToView(contentEdit, block);
                    isUpdatingText = false;
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        void bind(NoteBlock block) {
            String content = block.getContent();
            if (content == null) content = "";

            List<Bookmark> bookmarks = blockBookmarksMap.get(block.getId());

            if (bookmarks != null && !bookmarks.isEmpty()) {
                // ✅ Use Editable
                android.text.Editable editable = android.text.Editable.Factory.getInstance().newEditable(content);

                for (Bookmark bookmark : bookmarks) {
                    int start = bookmark.getStartIndex();
                    int end = bookmark.getEndIndex();

                    if (start >= 0 && end <= content.length() && start < end) {
                        int color = Color.parseColor(bookmark.getColor());

                        if ("highlight".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        } else if ("underline".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            editable.setSpan(
                                    new UnderlineSpan(),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                contentEdit.setText(editable);
            } else {
                contentEdit.setText(content);
            }

            // ✅ Set cursor to end
            contentEdit.post(() -> {
                contentEdit.setSelection(contentEdit.getText().length());
            });


            int marginLeft = dpToPx(block.getIndentLevel() * 24);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);

            applyFontStyle(contentEdit, block.getFontStyle(), block.getFontColor());
        }

        private int dpToPx(int dp) {
            return (int) (dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ProgressBar progressBar;
        TextView imageSizeText;

        ImageViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.imageView);
            progressBar = view.findViewById(R.id.progressBar);
            imageSizeText = view.findViewById(R.id.imageSizeText);

            imageView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    listener.onImageClick(block.getImageId());
                }
            });

            // ✅ ADD: Long press to bookmark image
            imageView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showImageBookmarkOptions(v, pos);
                }
                return true;
            });
        }

        private void showImageBookmarkOptions(View view, int position) {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(view.getContext(), view);
            popup.getMenu().add("📌 Bookmark this image");
            popup.getMenu().add("🗑️ Delete image");

            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().toString().contains("Bookmark")) {
                    NoteBlock block = blocks.get(position);
                    // Bookmark the entire image (use imageId as text)
                    if (listener instanceof NoteActivity) {
                        ((NoteActivity) listener).showBookmarkBottomSheet(
                                "[Image]",
                                block.getId(),
                                0,
                                0
                        );
                    }
                } else if (item.getTitle().toString().contains("Delete")) {
                    listener.onBlockDeleted(position);
                }
                return true;
            });

            popup.show();
        }

        private void showDeleteImageDialog(View view, int position) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(view.getContext());
            builder.setTitle("Delete Image?");
            builder.setMessage("This will permanently delete this image.");
            builder.setPositiveButton("Delete", (dialog, which) -> {
                listener.onBlockDeleted(position);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        void bind(NoteBlock block) {
            if (block.getSizeKB() > 0) {
                imageSizeText.setVisibility(View.VISIBLE);
                imageSizeText.setText(block.getSizeKB() + " KB" + (block.isChunked() ? " (Large)" : ""));
            } else {
                imageSizeText.setVisibility(View.GONE);
            }

            if (block.isChunked()) {
                loadChunkedImage(block);
            } else {
                loadInlineImage(block);
            }
        }

        private void loadInlineImage(NoteBlock block) {
            String imageId = block.getImageId();
            if (imageId == null) return;

            progressBar.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);

            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("images").document(imageId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String base64Data = doc.getString("base64Data");
                            if (base64Data != null) {
                                displayBase64Image(base64Data);
                            }
                        }
                        progressBar.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                    });
        }

        private void loadChunkedImage(NoteBlock block) {
            String imageId = block.getImageId();
            if (imageId == null) return;

            progressBar.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);

            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("images").document(imageId)
                    .collection("chunks")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            List<QueryDocumentSnapshot> chunks = new ArrayList<>();
                            for (QueryDocumentSnapshot doc : querySnapshot) {
                                chunks.add(doc);
                            }

                            Collections.sort(chunks, (a, b) -> {
                                Long indexA = a.getLong("chunkIndex");
                                Long indexB = b.getLong("chunkIndex");
                                return indexA != null && indexB != null ?
                                        indexA.compareTo(indexB) : 0;
                            });

                            StringBuilder fullBase64 = new StringBuilder();
                            for (QueryDocumentSnapshot chunk : chunks) {
                                String data = chunk.getString("data");
                                if (data != null) {
                                    fullBase64.append(data);
                                }
                            }

                            displayBase64Image(fullBase64.toString());
                        }
                        progressBar.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                    });
        }

        private void displayBase64Image(String base64Data) {
            try {
                byte[] decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                imageView.setVisibility(View.VISIBLE);
            }
        }
    }

    class DividerViewHolder extends RecyclerView.ViewHolder {
        TextView dividerView;  // Changed from View to TextView for text-based dividers

        DividerViewHolder(View view) {
            super(view);
            dividerView = view.findViewById(R.id.dividerView);

            // Click divider to change style
            if (dividerView != null) {
                dividerView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showDividerStyleSheet(v, pos);
                    }
                });

                // Long press for more actions
                dividerView.setOnLongClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showDividerActionsSheet(v, pos);
                    }
                    return true;
                });
            }
        }

        void bind(NoteBlock block) {
            if (dividerView == null) return;

            String style = block.getDividerStyle();
            if (style == null || style.isEmpty()) {
                style = "solid";
            }

            // Apply divider style
            switch (style) {
                case "solid":
                    dividerView.setText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    dividerView.setTextColor(0xFF333333);
                    break;
                case "dashed":
                    dividerView.setText("╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍╍");
                    dividerView.setTextColor(0xFF333333);
                    break;
                case "dotted":
                    dividerView.setText("⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯");
                    dividerView.setTextColor(0xFF333333);
                    break;
                case "double":
                    dividerView.setText("═══════════════════════════════");
                    dividerView.setTextColor(0xFF333333);
                    break;
                case "arrows":
                    dividerView.setText("→→→→→→→→→→→ ✱ ←←←←←←←←←←←");
                    dividerView.setTextColor(0xFF666666);
                    break;
                case "stars":
                    dividerView.setText("✦✦✦✦✦✦✦✦✦✦✦✦ ❋ ✦✦✦✦✦✦✦✦✦✦✦✦");
                    dividerView.setTextColor(0xFF666666);
                    break;
                case "wave":
                    dividerView.setText("∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿");
                    dividerView.setTextColor(0xFF666666);
                    break;
                case "diamond":
                    dividerView.setText("◈◈◈◈◈◈◈◈◈◈◈◈◈◈ ◆ ◈◈◈◈◈◈◈◈◈◈◈◈◈◈");
                    dividerView.setTextColor(0xFF666666);
                    break;
                default:
                    dividerView.setText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    dividerView.setTextColor(0xFF333333);
                    break;
            }

            dividerView.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
            dividerView.setTextSize(14);
        }

        private void showDividerStyleSheet(View view, int position) {
            NoteBlock block = blocks.get(position);

            BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
            View sheetView = LayoutInflater.from(view.getContext())
                    .inflate(R.layout.divider_bottom_sheet, null);
            bottomSheet.setContentView(sheetView);

            // Get all style options
            LinearLayout dividerSolid = sheetView.findViewById(R.id.dividerSolid);
            LinearLayout dividerDashed = sheetView.findViewById(R.id.dividerDashed);
            LinearLayout dividerDotted = sheetView.findViewById(R.id.dividerDotted);
            LinearLayout dividerDouble = sheetView.findViewById(R.id.dividerDouble);
            LinearLayout dividerArrows = sheetView.findViewById(R.id.dividerArrows);
            LinearLayout dividerStars = sheetView.findViewById(R.id.dividerStars);
            LinearLayout dividerWave = sheetView.findViewById(R.id.dividerWave);
            LinearLayout dividerDiamond = sheetView.findViewById(R.id.dividerDiamond);

            // Set click listeners
            if (dividerSolid != null) {
                dividerSolid.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "solid");
                    bottomSheet.dismiss();
                });
            }

            if (dividerDashed != null) {
                dividerDashed.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "dashed");
                    bottomSheet.dismiss();
                });
            }

            if (dividerDotted != null) {
                dividerDotted.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "dotted");
                    bottomSheet.dismiss();
                });
            }

            if (dividerDouble != null) {
                dividerDouble.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "double");
                    bottomSheet.dismiss();
                });
            }

            if (dividerArrows != null) {
                dividerArrows.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "arrows");
                    bottomSheet.dismiss();
                });
            }

            if (dividerStars != null) {
                dividerStars.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "stars");
                    bottomSheet.dismiss();
                });
            }

            if (dividerWave != null) {
                dividerWave.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "wave");
                    bottomSheet.dismiss();
                });
            }

            if (dividerDiamond != null) {
                dividerDiamond.setOnClickListener(v -> {
                    updateDividerStyle(block, position, "diamond");
                    bottomSheet.dismiss();
                });
            }

            bottomSheet.show();
        }

        private void showDividerActionsSheet(View view, int position) {
            BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
            View sheetView = LayoutInflater.from(view.getContext())
                    .inflate(R.layout.divider_action_bottom_sheet, null);
            bottomSheet.setContentView(sheetView);

            LinearLayout moveUpBtn = sheetView.findViewById(R.id.moveUpBtn);
            LinearLayout moveDownBtn = sheetView.findViewById(R.id.moveDownBtn);
            LinearLayout duplicateBtn = sheetView.findViewById(R.id.duplicateBtn);
            LinearLayout deleteBtn = sheetView.findViewById(R.id.deleteBtn);

            if (moveUpBtn != null) {
                moveUpBtn.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    if (position > 0) {
                        moveBlock(position, position - 1);
                        listener.onBlockChanged(blocks.get(position - 1));
                        android.widget.Toast.makeText(view.getContext(),
                                "Moved up", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        android.widget.Toast.makeText(view.getContext(),
                                "Already at top", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (moveDownBtn != null) {
                moveDownBtn.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    if (position < blocks.size() - 1) {
                        moveBlock(position, position + 1);
                        listener.onBlockChanged(blocks.get(position + 1));
                        android.widget.Toast.makeText(view.getContext(),
                                "Moved down", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        android.widget.Toast.makeText(view.getContext(),
                                "Already at bottom", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (duplicateBtn != null) {
                duplicateBtn.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    duplicateDivider(position);
                });
            }

            if (deleteBtn != null) {
                deleteBtn.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    listener.onBlockDeleted(position);
                });
            }

            bottomSheet.show();
        }

        private void updateDividerStyle(NoteBlock block, int position, String style) {
            block.setDividerStyle(style);
            notifyItemChanged(position);
            listener.onBlockChanged(block);
        }

        private void duplicateDivider(int position) {
            NoteBlock originalBlock = blocks.get(position);

            // Create duplicate
            NoteBlock newBlock = new NoteBlock(
                    System.currentTimeMillis() + "",
                    NoteBlock.BlockType.DIVIDER
            );
            newBlock.setDividerStyle(originalBlock.getDividerStyle());
            newBlock.setPosition(position + 1);

            // Insert after current divider
            blocks.add(position + 1, newBlock);

            // Update positions
            for (int i = position + 1; i < blocks.size(); i++) {
                blocks.get(i).setPosition(i);
            }

            notifyItemInserted(position + 1);
            listener.onBlockChanged(newBlock);

            android.widget.Toast.makeText(itemView.getContext(),
                    "Divider duplicated", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    class SubpageViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;

        SubpageViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    listener.onSubpageClick(block.getSubpageId());
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showDeleteDialog(v, pos);
                }
                return true;
            });
        }

        private void showDeleteDialog(View view, int position) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(view.getContext());
            builder.setTitle("Delete Subpage?");
            builder.setMessage("This will delete the subpage and all its content.");
            builder.setPositiveButton("Delete", (dialog, which) -> {
                listener.onBlockDeleted(position);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        void bind(NoteBlock block) {
            titleText.setText(block.getContent());
        }
    }

// REPLACE ang LinkViewHolder class sa NoteBlockAdapter.java with this:

    class LinkViewHolder extends RecyclerView.ViewHolder {
        View cardView;
        TextView titleText;
        TextView urlText;
        TextView descriptionText;
        ImageView menuBtn;
        ImageView faviconView;

        LinkViewHolder(View view) {
            super(view);
            cardView = view.findViewById(R.id.linkCardView);
            titleText = view.findViewById(R.id.linkTitle);
            urlText = view.findViewById(R.id.linkUrl);
            descriptionText = view.findViewById(R.id.linkDescription);
            menuBtn = view.findViewById(R.id.linkMenuBtn);
            faviconView = view.findViewById(R.id.linkFavicon);

            // ✅ CHECK: Verify all views are found
            if (cardView == null) {
                android.util.Log.e("LinkViewHolder", "cardView is NULL!");
            }
            if (titleText == null) {
                android.util.Log.e("LinkViewHolder", "titleText is NULL!");
            }
            if (urlText == null) {
                android.util.Log.e("LinkViewHolder", "urlText is NULL!");
            }

            // Click to open URL
            if (cardView != null) {
                cardView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        NoteBlock block = blocks.get(pos);
                        if (block.getLinkUrl() != null) {
                            listener.onLinkClick(block.getLinkUrl());
                        }
                    }
                });
            }

            // Three dots menu
            if (menuBtn != null) {
                menuBtn.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showLinkActionsSheet(v, pos);
                    }
                });
            }
        }

        void bind(NoteBlock block) {
            // ✅ LOG: Debug what we're binding
            android.util.Log.d("LinkViewHolder", "Binding link block:");
            android.util.Log.d("LinkViewHolder", "  Title: " + block.getContent());
            android.util.Log.d("LinkViewHolder", "  URL: " + block.getLinkUrl());
            android.util.Log.d("LinkViewHolder", "  Description: " + block.getLinkDescription());
            android.util.Log.d("LinkViewHolder", "  BgColor: " + block.getLinkBackgroundColor());

            // Set title
            if (titleText != null) {
                String title = block.getContent();
                titleText.setText(title != null && !title.isEmpty() ? title : "Untitled Link");
            }

            // Set URL
            if (urlText != null) {
                String url = block.getLinkUrl();
                urlText.setText(url != null ? url : "No URL");
            }

            // Set description
            if (descriptionText != null) {
                String description = block.getLinkDescription();
                if (description != null && !description.isEmpty()) {
                    descriptionText.setText(description);
                    descriptionText.setVisibility(View.VISIBLE);
                } else {
                    descriptionText.setVisibility(View.GONE);
                }
            }

            // Set background color
            if (cardView != null) {
                String bgColor = block.getLinkBackgroundColor();
                if (bgColor != null && !bgColor.isEmpty()) {
                    try {
                        cardView.setBackgroundColor(Color.parseColor(bgColor));
                    } catch (Exception e) {
                        cardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
                        android.util.Log.e("LinkViewHolder", "Invalid color: " + bgColor);
                    }
                } else {
                    cardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
                }
            }
        }

        private void showLinkActionsSheet(View view, int position) {
            NoteBlock block = blocks.get(position);

            BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
            View sheetView = LayoutInflater.from(view.getContext())
                    .inflate(R.layout.link_actions_bottom_sheet, null);
            bottomSheet.setContentView(sheetView);

            LinearLayout colorOption = sheetView.findViewById(R.id.linkColorOption);
            LinearLayout captionOption = sheetView.findViewById(R.id.linkCaptionOption);
            LinearLayout deleteOption = sheetView.findViewById(R.id.linkDeleteOption);

            if (colorOption != null) {
                colorOption.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    showLinkColorSheet(view, block, position);
                });
            }

            if (captionOption != null) {
                captionOption.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    showLinkCaptionSheet(view, block, position);
                });
            }

            if (deleteOption != null) {
                deleteOption.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    listener.onBlockDeleted(position);
                });
            }

            bottomSheet.show();
        }

        private void showLinkColorSheet(View view, NoteBlock block, int position) {
            BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
            View sheetView = LayoutInflater.from(view.getContext())
                    .inflate(R.layout.link_color_bottom_sheet, null);
            bottomSheet.setContentView(sheetView);

            // Get all color options
            LinearLayout colorDefault = sheetView.findViewById(R.id.linkColorDefault);
            LinearLayout colorGray = sheetView.findViewById(R.id.linkColorGray);
            LinearLayout colorBrown = sheetView.findViewById(R.id.linkColorBrown);
            LinearLayout colorOrange = sheetView.findViewById(R.id.linkColorOrange);
            LinearLayout colorYellow = sheetView.findViewById(R.id.linkColorYellow);
            LinearLayout colorGreen = sheetView.findViewById(R.id.linkColorGreen);
            LinearLayout colorBlue = sheetView.findViewById(R.id.linkColorBlue);
            LinearLayout colorPurple = sheetView.findViewById(R.id.linkColorPurple);
            LinearLayout colorPink = sheetView.findViewById(R.id.linkColorPink);
            LinearLayout colorRed = sheetView.findViewById(R.id.linkColorRed);

            // Set listeners
            if (colorDefault != null) {
                colorDefault.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#FFFFFF");
                    bottomSheet.dismiss();
                });
            }

            if (colorGray != null) {
                colorGray.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#E0E0E0");
                    bottomSheet.dismiss();
                });
            }

            if (colorBrown != null) {
                colorBrown.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#D7CCC8");
                    bottomSheet.dismiss();
                });
            }

            if (colorOrange != null) {
                colorOrange.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#FFE0B2");
                    bottomSheet.dismiss();
                });
            }

            if (colorYellow != null) {
                colorYellow.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#FFF9C4");
                    bottomSheet.dismiss();
                });
            }

            if (colorGreen != null) {
                colorGreen.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#C8E6C9");
                    bottomSheet.dismiss();
                });
            }

            if (colorBlue != null) {
                colorBlue.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#BBDEFB");
                    bottomSheet.dismiss();
                });
            }

            if (colorPurple != null) {
                colorPurple.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#E1BEE7");
                    bottomSheet.dismiss();
                });
            }

            if (colorPink != null) {
                colorPink.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#F8BBD0");
                    bottomSheet.dismiss();
                });
            }

            if (colorRed != null) {
                colorRed.setOnClickListener(v -> {
                    updateLinkColor(block, position, "#FFCDD2");
                    bottomSheet.dismiss();
                });
            }

            bottomSheet.show();
        }

        private void updateLinkColor(NoteBlock block, int position, String color) {
            block.setLinkBackgroundColor(color);
            notifyItemChanged(position);
            listener.onBlockChanged(block);
        }

        private void showLinkCaptionSheet(View view, NoteBlock block, int position) {
            BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
            View sheetView = LayoutInflater.from(view.getContext())
                    .inflate(R.layout.link_caption_bottom_sheet, null);
            bottomSheet.setContentView(sheetView);

            com.google.android.material.textfield.TextInputEditText captionInput =
                    sheetView.findViewById(R.id.linkCaptionInput);
            TextView cancelBtn = sheetView.findViewById(R.id.cancelCaptionBtn);
            TextView saveBtn = sheetView.findViewById(R.id.saveCaptionBtn);

            // Pre-fill existing caption
            if (captionInput != null && block.getLinkDescription() != null) {
                captionInput.setText(block.getLinkDescription());
            }

            if (cancelBtn != null) {
                cancelBtn.setOnClickListener(v -> bottomSheet.dismiss());
            }

            if (saveBtn != null) {
                saveBtn.setOnClickListener(v -> {
                    if (captionInput != null) {
                        String caption = captionInput.getText().toString().trim();
                        block.setLinkDescription(caption);
                        notifyItemChanged(position);
                        listener.onBlockChanged(block);
                    }
                    bottomSheet.dismiss();
                });
            }

            bottomSheet.show();
        }
    }

    private void applyFontStyle(EditText editText, String fontStyle, String fontColor) {
        // Apply font style (bold, italic, etc.)
        if (fontStyle == null || fontStyle.isEmpty()) {
            editText.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            switch (fontStyle) {
                case "bold":
                    editText.setTypeface(null, android.graphics.Typeface.BOLD);
                    break;
                case "italic":
                    editText.setTypeface(null, android.graphics.Typeface.ITALIC);
                    break;
                case "boldItalic":
                    editText.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
                    break;
                default:
                    editText.setTypeface(null, android.graphics.Typeface.NORMAL);
                    break;
            }
        }

        // Apply font color
        if (fontColor != null && !fontColor.isEmpty()) {
            try {
                editText.setTextColor(android.graphics.Color.parseColor(fontColor));
            } catch (Exception e) {
                editText.setTextColor(android.graphics.Color.parseColor("#333333"));
            }
        } else {
            editText.setTextColor(android.graphics.Color.parseColor("#333333"));
        }
    }
    // Add this method sa NoteBlockAdapter class (before the ViewHolder classes)
    private void showBookmarkContextMenu(View anchorView, String selectedText,
                                         String blockId, int startIndex, int endIndex) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(anchorView.getContext(), anchorView);
        popup.getMenu().add("📌 Bookmark this");

        popup.setOnMenuItemClickListener(item -> {
            if (listener instanceof NoteActivity) {
                ((NoteActivity) listener).showBookmarkBottomSheet(selectedText, blockId, startIndex, endIndex);
            }
            return true;
        });

        popup.show();
    }

    public void updateBookmarks(Map<String, List<Bookmark>> bookmarksMap) {
        this.blockBookmarksMap = bookmarksMap;
    }
    private void applyBookmarkSpan(android.text.SpannableStringBuilder spannable,
                                   Bookmark bookmark, int maxLength) {
        int start = bookmark.getStartIndex();
        int end = bookmark.getEndIndex();

        if (start >= 0 && end <= maxLength && start < end) {
            int color = Color.parseColor(bookmark.getColor());

            if ("highlight".equals(bookmark.getStyle())) {
                spannable.setSpan(
                        new BackgroundColorSpan(color),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            } else if ("underline".equals(bookmark.getStyle())) {
                spannable.setSpan(
                        new BackgroundColorSpan(color),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                spannable.setSpan(
                        new UnderlineSpan(),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }
    private void reapplyBookmarksToView(EditText editText, NoteBlock block) {
        String content = block.getContent();
        if (content == null) content = "";

        List<Bookmark> bookmarks = blockBookmarksMap.get(block.getId());

        if (bookmarks != null && !bookmarks.isEmpty()) {
            // ✅ Save cursor position
            int cursorPos = editText.getSelectionStart();

            android.text.SpannableStringBuilder spannableBuilder =
                    new android.text.SpannableStringBuilder(content);

            for (Bookmark bookmark : bookmarks) {
                applyBookmarkSpan(spannableBuilder, bookmark, content.length());
            }

            editText.setText(spannableBuilder);

            // ✅ Restore cursor position
            if (cursorPos >= 0 && cursorPos <= spannableBuilder.length()) {
                editText.setSelection(cursorPos);
            } else {
                editText.setSelection(editText.getText().length());
            }
        }
    }

    // ✅ Custom Action Mode for text selection
    // Replace the BookmarkActionModeCallback class at the bottom of NoteBlockAdapter.java

    // Optional: Keep as helper if you want
    private void applyBookmarkToEditable(android.text.Editable editable,
                                         Bookmark bookmark, int maxLength) {
        int start = bookmark.getStartIndex();
        int end = bookmark.getEndIndex();

        if (start >= 0 && end <= maxLength && start < end) {
            int color = Color.parseColor(bookmark.getColor());

            if ("highlight".equals(bookmark.getStyle())) {
                editable.setSpan(
                        new BackgroundColorSpan(color),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            } else if ("underline".equals(bookmark.getStyle())) {
                editable.setSpan(
                        new BackgroundColorSpan(color),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                editable.setSpan(
                        new UnderlineSpan(),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }
}
