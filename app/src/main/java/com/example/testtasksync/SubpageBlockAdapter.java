package com.example.testtasksync;

import static com.example.testtasksync.NoteBlock.BlockType.HEADING_1;
import static com.example.testtasksync.NoteBlock.BlockType.HEADING_2;
import static com.example.testtasksync.NoteBlock.BlockType.HEADING_3;

import android.content.Context;
import android.graphics.Color;
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

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan; // Para sa BOLD/ITALIC
import android.text.style.RelativeSizeSpan; // Para sa Custom Font Size (optional)
import android.graphics.Typeface; // Para sa BOLD/ITALIC
public class SubpageBlockAdapter extends RecyclerView.Adapter<SubpageBlockAdapter.BlockViewHolder> {

    private Context context;
    private List<SubpageBlock> blocks;
    private BlockListener listener;
    private String noteId;
    private String subpageId;
    private Map<String, List<Bookmark>> blockBookmarksMap = new HashMap<>();

    public interface BlockListener {
        void onBlockChanged(SubpageBlock block);
        void onBlockDeleted(SubpageBlock block, int position);
        void onEnterPressed(int position, String textBeforeCursor, String textAfterCursor);
        void onBackspaceOnEmptyBlock(int position);
        void onIndentChanged(SubpageBlock block, boolean indent);
        void onLinkClick(String url);
        void onLinkToPageClick(String pageId, String pageType, String collection);
    }

    public SubpageBlockAdapter(Context context, List<SubpageBlock> blocks,
                               BlockListener listener, String noteId, String subpageId) {
        //                                        ☝️ ADD THESE TWO PARAMETERS!
        this.context = context;
        this.blocks = blocks;
        this.listener = listener;
        this.noteId = noteId; // ✅ NOW noteId comes from the parameter
        this.subpageId = subpageId; // ✅ NOW subpageId comes from the parameter
    }
    @Override
    public int getItemViewType(int position) {
        SubpageBlock block = blocks.get(position);
        String type = block.getType();

        switch (type) {
            case "divider":
                return 1;
            case "checkbox":
                return 2;
            case "bullet":
                return 3;
            case "numbered":
                return 4;
            case "heading1":
            case "heading2":
            case "heading3":
                return 5;
            case "image":
                return 6;
            case "link":
                return 7;
            case "link_to_page": // ✅ ADD THIS
                return 8;
            default:
                return 0; // text
        }
    }

    @NonNull
    @Override
    public BlockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;

        switch (viewType) {
            case 1: // Divider
                view = LayoutInflater.from(context).inflate(R.layout.item_block_divider, parent, false);
                break;
            case 2: // Checkbox
                view = LayoutInflater.from(context).inflate(R.layout.item_block_checkbox, parent, false);
                break;
            case 3: // Bullet
                view = LayoutInflater.from(context).inflate(R.layout.item_block_bullet, parent, false);
                break;
            case 4: // Numbered
                view = LayoutInflater.from(context).inflate(R.layout.item_block_numbered, parent, false);
                break;
            case 5: // Heading
                view = LayoutInflater.from(context).inflate(R.layout.item_block_heading, parent, false);
                break;
            case 6: // Image
                view = LayoutInflater.from(context).inflate(R.layout.item_block_image, parent, false);
                break;
            case 7: // Link
                view = LayoutInflater.from(context).inflate(R.layout.item_block_link, parent, false);
                break;
            case 8: // ✅ ADD THIS - Link to Page
                view = LayoutInflater.from(context).inflate(R.layout.item_block_link_to_page_subpage, parent, false);
                break;
            default: // Text
                view = LayoutInflater.from(context).inflate(R.layout.item_block_text, parent, false);
                break;
        }

        return new BlockViewHolder(view);
    }

    // ================================================================
// REPLACE THE onBindViewHolder METHOD IN SubpageBlockAdapter.java
// ================================================================

    @Override
    public void onBindViewHolder(@NonNull BlockViewHolder holder, int position) {
        SubpageBlock block = blocks.get(position);

        if (block.getType().equals("link_to_page")) {
            bindLinkToPageBlock(holder, block);
            return;
        }

        if (block.getType().equals("link")) {
            bindLinkBlock(holder, block);
            return;
        }

        if (block.getType().equals("image")) {
            bindImageBlock(holder, block);
            return;
        }

        // Apply indent (only for text-based blocks)
        if (holder.editText != null) {
            int indentPx = (int) (block.getIndentLevel() * 32 * context.getResources().getDisplayMetrics().density);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            params.leftMargin = indentPx;
            holder.itemView.setLayoutParams(params);
        }

        // Handle different block types
        if (holder.editText != null) {
            // Remove previous TextWatcher to avoid triggering on setText
            if (holder.textWatcher != null) {
                holder.editText.removeTextChangedListener(holder.textWatcher);
            }

            // ✅ APPLY BOOKMARKS to text content
            String content = block.getContent();
            if (content == null) content = "";

            List<Bookmark> bookmarks = blockBookmarksMap.get(block.getBlockId());

            if (bookmarks != null && !bookmarks.isEmpty()) {
                android.text.Editable editable = android.text.Editable.Factory.getInstance().newEditable(content);

                for (Bookmark bookmark : bookmarks) {
                    int start = bookmark.getStartIndex();
                    int end = bookmark.getEndIndex();

                    if (start >= 0 && end <= content.length() && start < end) {
                        int color = Color.parseColor(bookmark.getColor());

                        if ("highlight".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new android.text.style.BackgroundColorSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        } else if ("underline".equals(bookmark.getStyle())) {
                            editable.setSpan(
                                    new ColoredUnderlineSpan(color),
                                    start,
                                    end,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                holder.editText.setText(editable);
            } else {
                holder.editText.setText(content);
            }

            // ✅ SET INPUT TYPE (prevent multiline)
            holder.editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            holder.editText.setHorizontallyScrolling(false);
            holder.editText.setMaxLines(Integer.MAX_VALUE);

            // ✅ SET TEXT SIZE AND BOLD (for headings)
            if (block.getType().equals("heading1")) {
                holder.editText.setTextSize(28);
                holder.editText.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (block.getType().equals("heading2")) {
                holder.editText.setTextSize(24);
                holder.editText.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (block.getType().equals("heading3")) {
                holder.editText.setTextSize(20);
                holder.editText.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                holder.editText.setTextSize(16);
                applyFontStyle(holder.editText, block.getFontStyle());
            }

            applyFontColor(holder.editText, block.getFontColor());

            // ✅ ENABLE TEXT SELECTION for bookmarking
            holder.editText.setEnabled(true);
            holder.editText.setFocusable(true);
            holder.editText.setFocusableInTouchMode(true);
            holder.editText.setCursorVisible(true);
            holder.editText.setLongClickable(true);

            // ✅ SETUP CUSTOM SELECTION MENU for bookmarking
            holder.editText.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    SubpageBlock block = blocks.get(pos);
                    int start = holder.editText.getSelectionStart();
                    int end = holder.editText.getSelectionEnd();

                    // ✅ Check if selection is within an existing bookmark
                    if (context instanceof SubpageActivity) {
                        SubpageActivity activity = (SubpageActivity) context;
                        Bookmark existingBookmark = activity.getBookmarkAtSelection(block.getBlockId(), start, end);

                        if (existingBookmark != null) {
                            // ✅ Selection is within bookmark - show expand/update options
                            menu.clear();
                            menu.add(0, 1, 0, "Expand Bookmark")
                                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                            menu.add(0, 2, 0, "Update Color/Style")
                                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                            menu.add(0, 3, 0, "Delete Bookmark")
                                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                        } else {
                            // ✅ Normal selection - show bookmark option
                            menu.clear();
                            menu.add(0, 0, 0, "📌 Bookmark")
                                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                        }
                    }

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
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    SubpageBlock block = blocks.get(pos);
                    int start = holder.editText.getSelectionStart();
                    int end = holder.editText.getSelectionEnd();

                    if (context instanceof SubpageActivity) {
                        SubpageActivity activity = (SubpageActivity) context;

                        switch (item.getItemId()) {
                            case 0: // Bookmark (new)
                                if (start >= 0 && end > start && end <= holder.editText.getText().length()) {
                                    String selectedText = holder.editText.getText().toString().substring(start, end);
                                    activity.showBookmarkBottomSheet(
                                            selectedText,
                                            block.getBlockId(),
                                            start,
                                            end
                                    );
                                }
                                mode.finish();
                                return true;

                            case 1: // Expand Bookmark
                                Bookmark bookmarkToExpand = activity.getBookmarkAtSelection(block.getBlockId(), start, end);
                                if (bookmarkToExpand != null) {
                                    activity.expandBookmark(bookmarkToExpand, block.getBlockId(), start, end);
                                }
                                mode.finish();
                                return true;

                            case 2: // Update Color/Style
                                Bookmark bookmarkToUpdate = activity.getBookmarkAtSelection(block.getBlockId(), start, end);
                                if (bookmarkToUpdate != null) {
                                    activity.showUpdateBookmarkBottomSheet(bookmarkToUpdate, pos);
                                }
                                mode.finish();
                                return true;

                            case 3: // Delete Bookmark
                                Bookmark bookmarkToDelete = activity.getBookmarkAtSelection(block.getBlockId(), start, end);
                                if (bookmarkToDelete != null) {
                                    activity.showDeleteBookmarkConfirmation(bookmarkToDelete, pos);
                                }
                                mode.finish();
                                return true;
                        }
                    }

                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });

            // Add TextWatcher for auto-save
            holder.textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    block.setContent(s.toString());
                    listener.onBlockChanged(block);
                }
            };
            holder.editText.addTextChangedListener(holder.textWatcher);

            // Handle Enter and Backspace
            holder.editText.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = holder.getAdapterPosition();
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
                            String blockType = block.getType();
                            if (blockType.equals("bullet") || blockType.equals("numbered") || blockType.equals("checkbox")) {
                                listener.onBackspaceOnEmptyBlock(pos);
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
        }

        // Checkbox specific
        if (holder.checkBox != null) {
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(block.isChecked());
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                block.setChecked(isChecked);
                listener.onBlockChanged(block);
            });
        }

        // Bullet icon - change based on indent level
        if (holder.bulletIcon != null && block.getType().equals("bullet")) {
            int indentLevel = block.getIndentLevel();
            switch (indentLevel) {
                case 0: holder.bulletIcon.setText("●"); break;
                case 1: holder.bulletIcon.setText("○"); break;
                case 2: holder.bulletIcon.setText("■"); break;
                default: holder.bulletIcon.setText("●"); break;
            }
        }

        // Numbered list - change style based on indent level
        if (holder.numberText != null && block.getType().equals("numbered")) {
            updateNumbering(holder);
        }

        // Divider style
        if (block.getType().equals("divider")) {
            if (holder.dividerView != null) {
                String style = block.getContent();
                if (style == null || style.isEmpty()) {
                    style = "solid";
                }

                switch (style) {
                    case "solid": holder.dividerView.setText("────────────────────────────────"); break;
                    case "dashed": holder.dividerView.setText("╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌"); break;
                    case "dotted": holder.dividerView.setText("⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯"); break;
                    case "double": holder.dividerView.setText("════════════════════════════"); break;
                    case "arrows": holder.dividerView.setText("→→→→→→→→→→→ ✱ ←←←←←←←←←←←"); break;
                    case "stars": holder.dividerView.setText("✦✦✦✦✦✦✦✦✦✦✦✦ ⋆ ✦✦✦✦✦✦✦✦✦✦✦✦"); break;
                    case "wave": holder.dividerView.setText("∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿"); break;
                    case "diamond": holder.dividerView.setText("◈◈◈◈◈◈◈◈◈◈◈◈◈◈ ◆ ◈◈◈◈◈◈◈◈◈◈◈◈◈◈"); break;
                    default: holder.dividerView.setText("────────────────────────────────"); break;
                }

                holder.dividerView.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
                holder.dividerView.setTextSize(14);
            }
        }
    }

    // ================================================================
// ADD THESE HELPER METHODS TO SubpageBlockAdapter.java
// ================================================================

    // ✅ Apply font style (bold, italic, boldItalic)
    private void applyFontStyle(EditText editText, String fontStyle) {
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
    }

    // ✅ Apply font color
    private void applyFontColor(EditText editText, String fontColor) {
        if (fontColor != null && !fontColor.isEmpty()) {
            try {
                editText.setTextColor(android.graphics.Color.parseColor(fontColor));
            } catch (Exception e) {
                editText.setTextColor(android.graphics.Color.parseColor("#333333")); // Default black
            }
        } else {
            editText.setTextColor(android.graphics.Color.parseColor("#333333")); // Default black
        }
    }
    @Override
    public int getItemCount() {
        return blocks.size();
    }

    private void updateNumbering(BlockViewHolder holder) {
        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        SubpageBlock block = blocks.get(position);
        int indentLevel = block.getIndentLevel();

        // Count previous numbered items at same indent level
        int number = 1;
        for (int i = 0; i < position; i++) {
            SubpageBlock prevBlock = blocks.get(i);
            if (prevBlock.getType().equals("numbered") &&
                    prevBlock.getIndentLevel() == indentLevel) {
                number++;
            }
        }

        // Format based on indent level
        String formattedNumber;
        switch (indentLevel) {
            case 0:
                formattedNumber = number + ".";
                break;
            case 1:
                formattedNumber = toLowerCaseLetter(number) + ".";
                break;
            case 2:
                formattedNumber = toRomanNumeral(number) + ".";
                break;
            case 3:
                formattedNumber = "(" + number + ")";
                break;
            default:
                formattedNumber = number + ".";
                break;
        }

        holder.numberText.setText(formattedNumber);
    }

    private String toLowerCaseLetter(int number) {
        if (number <= 0) return "a";
        if (number > 26) {
            int first = (number - 1) / 26;
            int second = (number - 1) % 26;
            return "" + (char)('a' + first) + (char)('a' + second);
        }
        return String.valueOf((char)('a' + number - 1));
    }

    private String toRomanNumeral(int number) {
        if (number <= 0) return "i";
        if (number > 20) return String.valueOf(number);

        String[] romanNumerals = {
                "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x",
                "xi", "xii", "xiii", "xiv", "xv", "xvi", "xvii", "xviii", "xix", "xx"
        };

        if (number <= romanNumerals.length) {
            return romanNumerals[number - 1];
        }
        return String.valueOf(number);
    }

    class BlockViewHolder extends RecyclerView.ViewHolder {
        EditText editText;
        CheckBox checkBox;
        TextView bulletIcon;
        TextView numberText;
        TextView dividerView;
        TextWatcher textWatcher;

        // Image views
        ImageView imageView;
        ProgressBar progressBar;
        TextView imageSizeText;

        // ✅ ADD: Link views
        View linkCardView;
        TextView linkTitle;
        TextView linkUrl;
        TextView linkDescription;
        ImageView linkMenuBtn;

        public BlockViewHolder(@NonNull View itemView) {
            super(itemView);

            // Text-based blocks
            editText = itemView.findViewById(R.id.contentEdit);
            checkBox = itemView.findViewById(R.id.checkbox);
            bulletIcon = itemView.findViewById(R.id.bulletIcon);
            numberText = itemView.findViewById(R.id.numberText);
            dividerView = itemView.findViewById(R.id.dividerView);

            // Image blocks
            imageView = itemView.findViewById(R.id.imageView);
            progressBar = itemView.findViewById(R.id.progressBar);
            imageSizeText = itemView.findViewById(R.id.imageSizeText);

            // ✅ Link blocks
            linkCardView = itemView.findViewById(R.id.linkCardView);
            linkTitle = itemView.findViewById(R.id.linkTitle);
            linkUrl = itemView.findViewById(R.id.linkUrl);
            linkDescription = itemView.findViewById(R.id.linkDescription);
            linkMenuBtn = itemView.findViewById(R.id.linkMenuBtn);
            // ✅ Long press to delete image
            if (imageView != null) {
                imageView.setOnLongClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showDeleteImageDialog(v, pos);
                    }
                    return true;
                });
            }
            if (linkCardView != null) {
                linkCardView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        SubpageBlock block = blocks.get(pos);
                        if (block.getLinkUrl() != null) {
                            listener.onLinkClick(block.getLinkUrl());
                        }
                    }
                });
            }

            // ✅ Link menu button
            if (linkMenuBtn != null) {
                linkMenuBtn.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showLinkActionsSheet(v, pos);
                    }
                });
            }
            if (dividerView != null) {
                dividerView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showDividerStyleSheet(v, pos);
                    }
                });
            }
        }

        private void showDeleteImageDialog(View view, int position) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(view.getContext());
            builder.setTitle("Delete Image?");
            builder.setMessage("This will permanently delete this image.");
            builder.setPositiveButton("Delete", (dialog, which) -> {
                // ✅ Now we can access listener and blocks from outer class
                if (position < blocks.size()) {
                    SubpageBlock block = blocks.get(position);
                    listener.onBlockDeleted(block, position);
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }
    }
    private void bindImageBlock(BlockViewHolder holder, SubpageBlock block) {
        if (holder.imageView == null || holder.progressBar == null) return;

        // Show size info
        if (block.getSizeKB() > 0 && holder.imageSizeText != null) {
            holder.imageSizeText.setVisibility(View.VISIBLE);
            holder.imageSizeText.setText(block.getSizeKB() + " KB" +
                    (block.isChunked() ? " (Large)" : ""));
        } else if (holder.imageSizeText != null) {
            holder.imageSizeText.setVisibility(View.GONE);
        }

        // Load image
        if (block.isChunked()) {
            loadChunkedImage(holder, block);
        } else {
            loadInlineImage(holder, block);
        }
    }
    private void bindLinkBlock(BlockViewHolder holder, SubpageBlock block) {
        if (holder.linkTitle == null || holder.linkUrl == null) return;

        // Set title
        String title = block.getContent();
        holder.linkTitle.setText(title != null && !title.isEmpty() ? title : "Untitled Link");

        // Set URL
        String url = block.getLinkUrl();
        holder.linkUrl.setText(url != null ? url : "No URL");

        // Set description
        if (holder.linkDescription != null) {
            String description = block.getLinkDescription();
            if (description != null && !description.isEmpty()) {
                holder.linkDescription.setText(description);
                holder.linkDescription.setVisibility(View.VISIBLE);
            } else {
                holder.linkDescription.setVisibility(View.GONE);
            }
        }

        // Set background color
        if (holder.linkCardView != null) {
            String bgColor = block.getLinkBackgroundColor();
            if (bgColor != null && !bgColor.isEmpty()) {
                try {
                    holder.linkCardView.setBackgroundColor(Color.parseColor(bgColor));
                } catch (Exception e) {
                    holder.linkCardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
                }
            } else {
                holder.linkCardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }
        }
    }
    private void loadInlineImage(BlockViewHolder holder, SubpageBlock block) {
        String imageId = block.getImageId();
        if (imageId == null) return;

        holder.progressBar.setVisibility(View.VISIBLE);
        holder.imageView.setVisibility(View.GONE);

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (noteId == null || subpageId == null) return;

        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("images").document(imageId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String base64Data = doc.getString("base64Data");
                        if (base64Data != null) {
                            displayBase64Image(holder, base64Data);
                        }
                    }
                    holder.progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    holder.progressBar.setVisibility(View.GONE);
                    holder.imageView.setVisibility(View.VISIBLE);
                });
    }

    private void loadChunkedImage(BlockViewHolder holder, SubpageBlock block) {
        String imageId = block.getImageId();
        if (imageId == null) return;

        holder.progressBar.setVisibility(View.VISIBLE);
        holder.imageView.setVisibility(View.GONE);

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (noteId == null || subpageId == null) return;

        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("images").document(imageId)
                .collection("chunks")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        java.util.List<com.google.firebase.firestore.QueryDocumentSnapshot> chunks =
                                new java.util.ArrayList<>();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                            chunks.add(doc);
                        }

                        java.util.Collections.sort(chunks, (a, b) -> {
                            Long indexA = a.getLong("chunkIndex");
                            Long indexB = b.getLong("chunkIndex");
                            return indexA != null && indexB != null ? indexA.compareTo(indexB) : 0;
                        });

                        StringBuilder fullBase64 = new StringBuilder();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot chunk : chunks) {
                            String data = chunk.getString("data");
                            if (data != null) {
                                fullBase64.append(data);
                            }
                        }

                        displayBase64Image(holder, fullBase64.toString());
                    }
                    holder.progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    holder.progressBar.setVisibility(View.GONE);
                    holder.imageView.setVisibility(View.VISIBLE);
                });
    }

    private void displayBase64Image(BlockViewHolder holder, String base64Data) {
        try {
            byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    decodedBytes, 0, decodedBytes.length);

            if (bitmap != null && holder.imageView != null) {
                holder.imageView.setImageBitmap(bitmap);
                holder.imageView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (holder.imageView != null) {
                holder.imageView.setVisibility(View.VISIBLE);
            }
        }
    }
    // ================================================================
// LINK BLOCK ACTIONS (Outside BlockViewHolder class)
// ================================================================

    private void showLinkActionsSheet(View view, int position) {
        if (position < 0 || position >= blocks.size()) return;

        SubpageBlock block = blocks.get(position);

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
                listener.onBlockDeleted(block, position);
            });
        }

        bottomSheet.show();
    }

    private void showLinkColorSheet(View view, SubpageBlock block, int position) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
        View sheetView = LayoutInflater.from(view.getContext())
                .inflate(R.layout.link_color_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

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

    private void updateLinkColor(SubpageBlock block, int position, String color) {
        block.setLinkBackgroundColor(color);
        notifyItemChanged(position);
        listener.onBlockChanged(block);
    }

    private void showLinkCaptionSheet(View view, SubpageBlock block, int position) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(view.getContext());
        View sheetView = LayoutInflater.from(view.getContext())
                .inflate(R.layout.link_caption_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        com.google.android.material.textfield.TextInputEditText captionInput =
                sheetView.findViewById(R.id.linkCaptionInput);
        TextView cancelBtn = sheetView.findViewById(R.id.cancelCaptionBtn);
        TextView saveBtn = sheetView.findViewById(R.id.saveCaptionBtn);

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

    private void showDividerStyleSheet(View view, int position) {
        SubpageBlock block = blocks.get(position);

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

    private void updateDividerStyle(SubpageBlock block, int position, String style) {
        block.setContent(style); // ✅ Store style in content field
        notifyItemChanged(position);
        listener.onBlockChanged(block);
    }
    public void updateBookmarks(Map<String, List<Bookmark>> bookmarksMap) {
        this.blockBookmarksMap = bookmarksMap;
    }

// ✅ ADD ColoredUnderlineSpan class at the end of SubpageBlockAdapter.java (before the closing brace)

    class ColoredUnderlineSpan extends android.text.style.ReplacementSpan {
        private int underlineColor;
        private float strokeWidth = 4f; // Thickness of underline

        public ColoredUnderlineSpan(int color) {
            this.underlineColor = color;
        }

        @Override
        public int getSize(android.graphics.Paint paint, CharSequence text,
                           int start, int end, android.graphics.Paint.FontMetricsInt fm) {
            return (int) paint.measureText(text, start, end);
        }

        @Override
        public void draw(android.graphics.Canvas canvas, CharSequence text,
                         int start, int end, float x, int top, int y,
                         int bottom, android.graphics.Paint paint) {
            // Draw the text with original color
            canvas.drawText(text, start, end, x, y, paint);

            // Draw colored underline below text
            android.graphics.Paint underlinePaint = new android.graphics.Paint();
            underlinePaint.setColor(underlineColor);
            underlinePaint.setStrokeWidth(strokeWidth);
            underlinePaint.setStyle(android.graphics.Paint.Style.STROKE);

            // Calculate underline position
            float textWidth = paint.measureText(text, start, end);
            float underlineY = y + paint.descent() + 2; // Slight offset below text

            canvas.drawLine(x, underlineY, x + textWidth, underlineY, underlinePaint);
        }
    }
    private void bindLinkToPageBlock(BlockViewHolder holder, SubpageBlock block) {
        // Find views
        ImageView pageIcon = holder.itemView.findViewById(R.id.pageIcon);
        TextView pageTitle = holder.itemView.findViewById(R.id.pageTitle);
        TextView pageType = holder.itemView.findViewById(R.id.pageType);

        if (pageTitle == null || pageType == null || pageIcon == null) {
            android.util.Log.e("SubpageBlockAdapter", "Link to page views not found!");
            return;
        }

        // Set title
        String title = block.getContent();
        pageTitle.setText(title != null && !title.isEmpty() ? title : "Untitled");

        // Set type badge
        String type = block.getLinkedPageType();
        pageType.setText(type != null ? type : "page");

        // Set icon based on type
        if (type != null) {
            switch (type) {
                case "note":
                    pageIcon.setImageResource(R.drawable.ic_fab_notes);
                    pageIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#8daaa6")));
                    break;
                case "todo":
                    pageIcon.setImageResource(R.drawable.ic_fab_todo);
                    pageIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#FFF3E0")));
                    break;
                case "weekly":
                    pageIcon.setImageResource(R.drawable.ic_calendar);
                    pageIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#F3E5F5")));
                    break;
                default:
                    pageIcon.setImageResource(R.drawable.ic_fab_notes);
                    pageIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#E0E0E0")));
                    break;
            }
        }

        // Click listener to open linked page
        holder.itemView.setOnClickListener(v -> {
            String pageId = block.getLinkedPageId();
            String pageTypeStr = block.getLinkedPageType();
            String collection = block.getLinkedPageCollection();

            if (pageId != null && pageTypeStr != null && collection != null) {
                listener.onLinkToPageClick(pageId, pageTypeStr, collection);
            } else {
                android.widget.Toast.makeText(context, "Invalid link",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // Long press to delete
        holder.itemView.setOnLongClickListener(v -> {
            showLinkToPageOptions(v, holder.getAdapterPosition());
            return true;
        });
    }

    // 6️⃣ ADD: Show options for link to page (add after bindLinkToPageBlock)
    private void showLinkToPageOptions(View view, int position) {
        if (position < 0 || position >= blocks.size()) return;

        android.widget.PopupMenu popup = new android.widget.PopupMenu(view.getContext(), view);
        popup.getMenu().add("🗑️ Remove link");

        popup.setOnMenuItemClickListener(item -> {
            if (position < blocks.size()) {
                SubpageBlock block = blocks.get(position);
                listener.onBlockDeleted(block, position);
            }
            return true;
        });

        popup.show();
    }
}