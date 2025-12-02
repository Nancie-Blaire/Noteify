package com.example.testtasksync;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SubpageActivity extends AppCompatActivity {

    private EditText subpageTitle;
    private RecyclerView subpageBlocksRecycler;
    private ImageView checkBtn;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    public String noteId; // ✅ CHANGE to public
    public String subpageId;

    private SubpageBlockAdapter blockAdapter;
    private List<SubpageBlock> blocks = new ArrayList<>();

    // Keyboard toolbar buttons
    private ImageView btnHeadingsFont, btnDivider, btnBullet, btnNumbered;
    private ImageView btnCheckbox, btnLink, btnImage, btnIndent, btnOutdent, btnTheme;
    private View emptySpace, colorPickerPanel;

    // Add after existing instance variables
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private Uri currentPhotoUri;

    private static final int MAX_IMAGE_WIDTH = 1024;
    private static final int MAX_IMAGE_HEIGHT = 1024;
    private static final int COMPRESSION_QUALITY = 80;
    private static final int MAX_INLINE_IMAGE_KB = 700;
    private static final int CHUNK_SIZE = 50000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subpage);

        // Initialize views
        subpageTitle = findViewById(R.id.subpageTitle);
        subpageBlocksRecycler = findViewById(R.id.subpageBlocksRecycler);
        checkBtn = findViewById(R.id.checkBtn);
        emptySpace = findViewById(R.id.subpageEmptySpace);
        colorPickerPanel = findViewById(R.id.subpageColorPickerPanel);

        // Keyboard toolbar buttons
        btnHeadingsFont = findViewById(R.id.subpageHeadingsAndFont);
        btnDivider = findViewById(R.id.subpageAddDivider);
        btnBullet = findViewById(R.id.subpageAddBullet);
        btnNumbered = findViewById(R.id.subpageAddNumbered);
        btnCheckbox = findViewById(R.id.subpageAddCheckbox);
        btnLink = findViewById(R.id.subpageAddLink);
        btnImage = findViewById(R.id.subpageInsertImage);
        btnIndent = findViewById(R.id.subpageIndent);
        btnOutdent = findViewById(R.id.subpageOutdent);
        btnTheme = findViewById(R.id.subpageAddTheme);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Get parent note ID and subpage ID
        noteId = getIntent().getStringExtra("noteId");
        subpageId = getIntent().getStringExtra("subpageId");
        String initialTitle = getIntent().getStringExtra("subpageTitle");

        if (noteId == null || subpageId == null) {
            Toast.makeText(this, "Error: Invalid subpage data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set initial title if provided
        if (initialTitle != null && !initialTitle.equals("Untitled Subpage")) {
            subpageTitle.setText(initialTitle);
        }

        // Setup RecyclerView
        setupRecyclerView();

        // Button listeners
        checkBtn.setOnClickListener(v -> saveAndExit());

        // Empty space click - add new block
        emptySpace.setOnClickListener(v -> addNewBlock("text", ""));

        // Keyboard toolbar listeners
        setupKeyboardToolbar();

        // Load existing subpage data
        loadSubpage();
        setupImagePickers();
    }

    private void setupRecyclerView() {
        // ✅ PASS noteId and subpageId AFTER the listener, not inside it
        blockAdapter = new SubpageBlockAdapter(this, blocks, new SubpageBlockAdapter.BlockListener() {

            @Override
            public void onLinkClick(String url) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                startActivity(browserIntent);
            }
            @Override
            public void onBlockChanged(SubpageBlock block) {
                saveBlock(block);
            }

            @Override
            public void onBlockDeleted(SubpageBlock block, int position) {
                deleteBlock(block, position);
            }

            @Override
            public void onEnterPressed(int position, String textBeforeCursor, String textAfterCursor) {
                handleEnterPressed(position, textBeforeCursor, textAfterCursor);
            }

            @Override
            public void onBackspaceOnEmptyBlock(int position) {
                handleBackspaceOnEmptyBlock(position);
            }

            @Override
            public void onIndentChanged(SubpageBlock block, boolean indent) {
                if (indent) {
                    block.setIndentLevel(Math.min(block.getIndentLevel() + 1, 5));
                } else {
                    block.setIndentLevel(Math.max(block.getIndentLevel() - 1, 0));
                }
                saveBlock(block);
                blockAdapter.notifyDataSetChanged();
            }
        }, noteId, subpageId); // ✅ CORRECT PLACEMENT - after the closing brace of BlockListener

        subpageBlocksRecycler.setLayoutManager(new LinearLayoutManager(this));
        subpageBlocksRecycler.setAdapter(blockAdapter);

         DragDropHelper dragDropHelper = new DragDropHelper(new DragDropHelper.DragListener() {
            @Override
            public void onItemMoved(int fromPosition, int toPosition) {
                if (fromPosition < toPosition) {
                    for (int i = fromPosition; i < toPosition; i++) {
                        SubpageBlock temp = blocks.get(i);
                        blocks.set(i, blocks.get(i + 1));
                        blocks.set(i + 1, temp);
                    }
                } else {
                    for (int i = fromPosition; i > toPosition; i--) {
                        SubpageBlock temp = blocks.get(i);
                        blocks.set(i, blocks.get(i - 1));
                        blocks.set(i - 1, temp);
                    }
                }
                blockAdapter.notifyItemMoved(fromPosition, toPosition);
            }

            @Override
            public void onDragFinished() {
                updateBlockOrder();
            }
        });

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(dragDropHelper);
        itemTouchHelper.attachToRecyclerView(subpageBlocksRecycler);
    }

    private void setupKeyboardToolbar() {
        btnDivider.setOnClickListener(v -> showDividerSelectionSheet());
        btnBullet.setOnClickListener(v -> addBlock("bullet"));
        btnNumbered.setOnClickListener(v -> addBlock("numbered"));
        btnCheckbox.setOnClickListener(v -> addBlock("checkbox"));

        btnIndent.setOnClickListener(v -> indentSelectedBlock(true));
        btnOutdent.setOnClickListener(v -> indentSelectedBlock(false));

        btnTheme.setOnClickListener(v -> {
            if (colorPickerPanel.getVisibility() == View.VISIBLE) {
                colorPickerPanel.setVisibility(View.GONE);
            } else {
                colorPickerPanel.setVisibility(View.VISIBLE);
                setupColorPicker();
            }
        });

        // TODO: Implement heading selector
        btnHeadingsFont.setOnClickListener(v -> {
            Toast.makeText(this, "Heading selector coming soon", Toast.LENGTH_SHORT).show();
        });

        btnLink.setOnClickListener(v -> addLinkBlock());

        btnImage.setOnClickListener(v -> showInsertMediaBottomSheet());
    }

    private void indentSelectedBlock(boolean indent) {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView.ViewHolder holder = subpageBlocksRecycler.findContainingViewHolder(focusedView);
        if (holder == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= blocks.size()) {
            return;
        }

        SubpageBlock block = blocks.get(position);

        if (!canIndent(block)) {
            Toast.makeText(this, "This block type cannot be indented", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentIndent = block.getIndentLevel();
        int maxIndent = getMaxIndentForType(block.getType());

        if (indent) {
            // Indent
            if (currentIndent < maxIndent) {
                block.setIndentLevel(currentIndent + 1);
                blockAdapter.notifyItemChanged(position);
                saveBlock(block);
                focusedView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                Toast.makeText(this, "Maximum indent level reached", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Outdent
            if (currentIndent > 0) {
                block.setIndentLevel(currentIndent - 1);
                blockAdapter.notifyItemChanged(position);
                saveBlock(block);
                focusedView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                Toast.makeText(this, "Already at minimum indent level", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean canIndent(SubpageBlock block) {
        String type = block.getType();
        switch (type) {
            case "text":
            case "bullet":
            case "numbered":
            case "checkbox":
                return true;
            case "heading1":
            case "heading2":
            case "heading3":
            case "divider":
            case "image":
            case "link":
            default:
                return false;
        }
    }

    private int getMaxIndentForType(String type) {
        switch (type) {
            case "bullet":
                return 2; // 0 = ●, 1 = ○, 2 = ■
            case "numbered":
                return 3; // 0 = 1., 1 = a., 2 = i., 3 = deeper numbers
            case "text":
            case "checkbox":
                return 5; // Allow deeper indenting
            default:
                return 0;
        }
    }

    private void setupColorPicker() {
        int[] colorIds = {
                R.id.subpageColorDefault, R.id.subpageColorRed, R.id.subpageColorPink,
                R.id.subpageColorPurple, R.id.subpageColorBlue, R.id.subpageColorCyan,
                R.id.subpageColorGreen, R.id.subpageColorYellow, R.id.subpageColorOrange,
                R.id.subpageColorBrown, R.id.subpageColorGrey
        };

        for (int id : colorIds) {
            View colorView = findViewById(id);
            colorView.setOnClickListener(v -> {
                String color = (String) v.getTag();
                findViewById(R.id.subpageLayout).setBackgroundColor(android.graphics.Color.parseColor(color));
                colorPickerPanel.setVisibility(View.GONE);

                // Save color preference
                saveSubpageColor(color);
            });
        }
    }

    private void loadSubpage() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Load subpage metadata (title)
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String title = doc.getString("title");
                        if (title != null && !title.isEmpty()) {
                            subpageTitle.setText(title);
                        }

                        // Load background color if saved
                        String bgColor = doc.getString("backgroundColor");
                        if (bgColor != null) {
                            findViewById(R.id.subpageLayout).setBackgroundColor(
                                    android.graphics.Color.parseColor(bgColor));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading subpage", Toast.LENGTH_SHORT).show()
                );

        // Load blocks
        loadBlocks();
    }

    private void loadBlocks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("blocks")
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    blocks.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        SubpageBlock block = doc.toObject(SubpageBlock.class);
                        if (block != null) {
                            block.setBlockId(doc.getId());
                            blocks.add(block);
                        }
                    }

                    // If no blocks exist, add a default one
                    if (blocks.isEmpty()) {
                        addNewBlock("text", "");
                    } else {
                        blockAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading blocks", Toast.LENGTH_SHORT).show()
                );
    }

    private void addNewBlock(String type, String content) {
        addNewBlockAt(blocks.size(), type, content);
    }

    private void addBlock(String type) {
        // Try to replace last empty text block first
        if (tryReplaceLastEmptyTextBlock(type)) {
            return; // Successfully replaced
        }

        // Otherwise, create new block
        addNewBlock(type, "");
    }

    private boolean tryReplaceLastEmptyTextBlock(String type) {
        if (blocks.isEmpty()) {
            return false;
        }

        SubpageBlock lastBlock = blocks.get(blocks.size() - 1);

        // Check if last block is an empty TEXT block
        if (lastBlock.getType().equals("text") &&
                (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return false;

            // Delete the old empty text block from Firestore
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(subpageId)
                    .collection("blocks").document(lastBlock.getBlockId())
                    .delete();

            // Remove from list
            int position = blocks.size() - 1;
            blocks.remove(position);

            // Create new block with the desired type
            SubpageBlock newBlock = new SubpageBlock();
            newBlock.setBlockId(java.util.UUID.randomUUID().toString());
            newBlock.setType(type);
            newBlock.setContent("");
            newBlock.setOrder(position);
            newBlock.setIndentLevel(0);
            newBlock.setChecked(false);

            // Add new block at same position
            blocks.add(position, newBlock);
            blockAdapter.notifyItemChanged(position);
            saveBlock(newBlock);

            // Scroll and focus
            subpageBlocksRecycler.smoothScrollToPosition(position);

            return true; // Successfully replaced
        }

        return false; // No empty text block to replace
    }

// Add this helper method to SubpageActivity.java

    private void focusBlockAt(int position) {
        subpageBlocksRecycler.post(() -> {
            RecyclerView.ViewHolder holder = subpageBlocksRecycler.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView != null) {
                EditText editText = holder.itemView.findViewById(R.id.contentEdit);
                if (editText != null) {
                    editText.requestFocus();
                    editText.setSelection(editText.getText().length()); // Cursor at end

                    // Show keyboard
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });
    }

// UPDATE handleEnterPressed method - add focus call at the end of each case

    // ✅ GOOGLE KEEP STYLE: handleEnterPressed
    private void handleEnterPressed(int position, String textBeforeCursor, String textAfterCursor) {
        if (position < 0 || position >= blocks.size()) return;

        SubpageBlock currentBlock = blocks.get(position);
        String currentType = currentBlock.getType();

        // ✅ CHECK: Is the text BEFORE cursor empty?
        boolean isTextBeforeEmpty = textBeforeCursor.trim().isEmpty();

        switch (currentType) {
            case "bullet":
            case "numbered":
            case "checkbox":
                if (isTextBeforeEmpty) {
                    // ✅ EMPTY + ENTER = Convert to TEXT (no new block)
                    currentBlock.setContent("");
                    saveBlock(currentBlock);
                    convertBlockToText(position);
                } else {
                    // ✅ HAS CONTENT = Create new same-type block
                    currentBlock.setContent(textBeforeCursor);
                    blockAdapter.notifyItemChanged(position);
                    saveBlock(currentBlock);

                    SubpageBlock newBlock = new SubpageBlock();
                    newBlock.setBlockId(java.util.UUID.randomUUID().toString());
                    newBlock.setType(currentType);
                    newBlock.setContent(textAfterCursor != null ? textAfterCursor : "");
                    newBlock.setOrder(position + 1);
                    newBlock.setIndentLevel(currentBlock.getIndentLevel());
                    newBlock.setChecked(false);

                    blocks.add(position + 1, newBlock);
                    blockAdapter.notifyItemInserted(position + 1);
                    saveBlock(newBlock);
                    updateBlockOrder();

                    // Focus new block
                    subpageBlocksRecycler.smoothScrollToPosition(position + 1);
                    subpageBlocksRecycler.postDelayed(() -> focusBlockAt(position + 1), 100);
                }
                break;

            default:
                // TEXT/HEADING: Always create new text block
                currentBlock.setContent(textBeforeCursor);
                blockAdapter.notifyItemChanged(position);
                saveBlock(currentBlock);

                SubpageBlock newBlock = new SubpageBlock();
                newBlock.setBlockId(java.util.UUID.randomUUID().toString());
                newBlock.setType("text");
                newBlock.setContent(textAfterCursor != null ? textAfterCursor : "");
                newBlock.setOrder(position + 1);
                newBlock.setIndentLevel(currentBlock.getIndentLevel());

                blocks.add(position + 1, newBlock);
                blockAdapter.notifyItemInserted(position + 1);
                saveBlock(newBlock);
                updateBlockOrder();

                subpageBlocksRecycler.smoothScrollToPosition(position + 1);
                subpageBlocksRecycler.postDelayed(() -> focusBlockAt(position + 1), 100);
                break;
        }
    }

    // ✅ UPDATED: handleBackspaceOnEmptyBlock - Outdent or Convert to Text
    private void handleBackspaceOnEmptyBlock(int position) {
        if (position < 0 || position >= blocks.size()) return;

        SubpageBlock block = blocks.get(position);
        String type = block.getType();

        // ✅ For bullet/numbered/checkbox at cursor position 0:
        // If has indent -> reduce indent
        // If no indent -> convert to text
        if (type.equals("bullet") || type.equals("numbered") || type.equals("checkbox")) {
            int currentIndent = block.getIndentLevel();

            if (currentIndent > 0) {
                // ✅ Reduce indent level
                block.setIndentLevel(currentIndent - 1);
                blockAdapter.notifyItemChanged(position);
                saveBlock(block);

                // Haptic feedback
                View view = subpageBlocksRecycler.getLayoutManager().findViewByPosition(position);
                if (view != null) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }
            } else {
                // ✅ No indent - convert to text (stay on same line)
                convertBlockToText(position);
            }
        }
    }
    private void convertBlockToText(int position) {
        if (position < 0 || position >= blocks.size()) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        SubpageBlock oldBlock = blocks.get(position);
        String oldContent = oldBlock.getContent();
        int oldIndent = oldBlock.getIndentLevel();

        // Delete old block from Firestore
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("blocks").document(oldBlock.getBlockId())
                .delete();

        // Remove from list
        blocks.remove(position);

        // Create new text block with same content
        SubpageBlock newBlock = new SubpageBlock();
        newBlock.setBlockId(java.util.UUID.randomUUID().toString());
        newBlock.setType("text");
        newBlock.setContent(oldContent);
        newBlock.setOrder(position);
        newBlock.setIndentLevel(oldIndent);

        // Insert new block
        blocks.add(position, newBlock);
        blockAdapter.notifyItemChanged(position);
        saveBlock(newBlock);
        updateBlockOrder();
    }

    private void addNewBlockAt(int position, String type, String content) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        SubpageBlock newBlock = new SubpageBlock();
        newBlock.setBlockId(UUID.randomUUID().toString());
        newBlock.setType(type);
        newBlock.setContent(content);
        newBlock.setOrder(position);
        newBlock.setIndentLevel(0);
        newBlock.setChecked(false);

        blocks.add(position, newBlock);
        blockAdapter.notifyItemInserted(position);

        // Update order for all blocks after this
        updateBlockOrder();

        // Save to Firestore
        saveBlock(newBlock);

        // Scroll to new block
        subpageBlocksRecycler.smoothScrollToPosition(position);
    }
    private void saveBlock(SubpageBlock block) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> blockData = new HashMap<>();
        blockData.put("type", block.getType());
        blockData.put("content", block.getContent());
        blockData.put("order", block.getOrder());
        blockData.put("indentLevel", block.getIndentLevel());
        blockData.put("checked", block.isChecked());
        blockData.put("imageUrl", block.getImageUrl());
        blockData.put("linkUrl", block.getLinkUrl());
        blockData.put("timestamp", System.currentTimeMillis());

        blockData.put("linkBackgroundColor", block.getLinkBackgroundColor());
        blockData.put("linkDescription", block.getLinkDescription());
        blockData.put("dividerStyle", block.getDividerStyle());
        // ✅ ADD IMAGE FIELDS
        blockData.put("imageId", block.getImageId());
        blockData.put("isChunked", block.isChunked());
        blockData.put("sizeKB", block.getSizeKB());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("blocks").document(block.getBlockId())
                .set(blockData);
    }

    private void deleteBlock(SubpageBlock block, int position) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Don't allow deleting the last block
        if (blocks.size() == 1) {
            Toast.makeText(this, "Cannot delete the last block", Toast.LENGTH_SHORT).show();
            return;
        }

        blocks.remove(position);
        blockAdapter.notifyItemRemoved(position);

        // Delete from Firestore
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("blocks").document(block.getBlockId())
                .delete();

        updateBlockOrder();
    }

    private void updateBlockOrder() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        for (int i = 0; i < blocks.size(); i++) {
            SubpageBlock block = blocks.get(i);
            block.setOrder(i);

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(subpageId)
                    .collection("blocks").document(block.getBlockId())
                    .update("order", i);
        }
    }

    private void saveSubpageColor(String color) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .update("backgroundColor", color);
    }

    private void saveAndExit() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        String title = subpageTitle.getText().toString().trim();
        String displayTitle = title.isEmpty() ? "Untitled Subpage" : title;

        // Save subpage metadata
        Map<String, Object> subpageData = new HashMap<>();
        subpageData.put("title", title);
        subpageData.put("parentNoteId", noteId);
        subpageData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .set(subpageData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    updateParentBlockTitle(user.getUid(), displayTitle);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error saving subpage", Toast.LENGTH_SHORT).show();
                });

        finish();
    }

    private void updateParentBlockTitle(String userId, String newTitle) {
        if (subpageId == null) return;

        db.collection("users").document(userId)
                .collection("notes").document(noteId)
                .collection("blocks")
                .whereEqualTo("subpageId", subpageId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        querySnapshot.getDocuments().get(0).getReference()
                                .update("content", newTitle);
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = subpageTitle.getText().toString().trim();
        String displayTitle = title.isEmpty() ? "Untitled Subpage" : title;

        Map<String, Object> subpageData = new HashMap<>();
        subpageData.put("title", title);
        subpageData.put("parentNoteId", noteId);
        subpageData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .set(subpageData, com.google.firebase.firestore.SetOptions.merge());

        updateParentBlockTitle(user.getUid(), displayTitle);
    }

//IMAGES

    private void setupImagePickers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageToFirebase(imageUri);
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (currentPhotoUri != null) {
                            uploadImageToFirebase(currentPhotoUri);
                        }
                    }
                }
        );

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
    private void showInsertMediaBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.insert_media_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        View openGallery = sheetView.findViewById(R.id.openGalleryOption);
        View takePicture = sheetView.findViewById(R.id.takePictureOption);

        openGallery.setOnClickListener(v -> {
            bottomSheet.dismiss();
            openGallery();
        });

        takePicture.setOnClickListener(v -> {
            bottomSheet.dismiss();
            checkCameraPermission();
        });

        bottomSheet.show();
    }
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }
    private void checkCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.CAMERA);
            } else {
                openCamera();
            }
        } else {
            openCamera();
        }
    }
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void uploadImageToFirebase(Uri imageUri) {
        if (subpageId == null) {
            Toast.makeText(this, "Please save the subpage first", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Processing image...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            progressDialog.dismiss();
            return;
        }

        new Thread(() -> {
            try {
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                        getContentResolver(), imageUri);

                int originalWidth = bitmap.getWidth();
                int originalHeight = bitmap.getHeight();
                float scale = Math.min(
                        MAX_IMAGE_WIDTH / (float) originalWidth,
                        MAX_IMAGE_HEIGHT / (float) originalHeight
                );

                if (scale < 1.0f) {
                    int newWidth = (int) (originalWidth * scale);
                    int newHeight = (int) (originalHeight * scale);
                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
                byte[] imageBytes = baos.toByteArray();

                String base64Image = android.util.Base64.encodeToString(
                        imageBytes, android.util.Base64.NO_WRAP);

                int originalSizeKB = imageBytes.length / 1024;
                int base64SizeKB = base64Image.length() / 1024;

                final android.graphics.Bitmap finalBitmap = bitmap;

                runOnUiThread(() -> {
                    progressDialog.dismiss();

                    if (base64SizeKB > MAX_INLINE_IMAGE_KB) {
                        Toast.makeText(this, "Saving large image (" + originalSizeKB + " KB) in chunks...",
                                Toast.LENGTH_SHORT).show();
                        uploadImageInChunks(base64Image, originalSizeKB);
                    } else {
                        Toast.makeText(this, "Saving image (" + originalSizeKB + " KB)...",
                                Toast.LENGTH_SHORT).show();
                        insertImageBlock(base64Image, false, originalSizeKB);
                    }
                });

            } catch (java.io.IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error processing image: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Image too large for device memory", Toast.LENGTH_LONG).show();
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void uploadImageInChunks(String base64Image, int sizeKB) {
        String imageId = System.currentTimeMillis() + "";
        int totalLength = base64Image.length();
        int chunkCount = (int) Math.ceil(totalLength / (double) CHUNK_SIZE);

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("imageId", imageId);
        metadata.put("isChunked", true);
        metadata.put("chunkCount", chunkCount);
        metadata.put("totalSize", totalLength);
        metadata.put("sizeKB", sizeKB);
        metadata.put("timestamp", System.currentTimeMillis());

        // ✅ CHANGED PATH: Added subpages/subpageId
        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(subpageId)
                .collection("images").document(imageId)
                .set(metadata)
                .addOnSuccessListener(aVoid -> {
                    for (int i = 0; i < chunkCount; i++) {
                        int start = i * CHUNK_SIZE;
                        int end = Math.min(start + CHUNK_SIZE, totalLength);
                        String chunk = base64Image.substring(start, end);

                        java.util.Map<String, Object> chunkData = new java.util.HashMap<>();
                        chunkData.put("data", chunk);
                        chunkData.put("chunkIndex", i);

                        // ✅ CHANGED PATH
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("subpages").document(subpageId)
                                .collection("images").document(imageId)
                                .collection("chunks").document(String.valueOf(i))
                                .set(chunkData);
                    }

                    createImageBlock(imageId, true, sizeKB);
                    Toast.makeText(this, "✅ Large image saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save large image", Toast.LENGTH_SHORT).show();
                });
    }

    private void insertImageBlock(String base64Image, boolean isChunked, int sizeKB) {
        String imageId = System.currentTimeMillis() + "";

        java.util.Map<String, Object> imageData = new java.util.HashMap<>();
        if (!isChunked) {
            imageData.put("base64Data", base64Image);
        }
        imageData.put("imageId", imageId);
        imageData.put("isChunked", isChunked);
        imageData.put("sizeKB", sizeKB);
        imageData.put("timestamp", System.currentTimeMillis());

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            // ✅ CHANGED PATH: Added subpages/subpageId
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(subpageId)
                    .collection("images").document(imageId)
                    .set(imageData)
                    .addOnSuccessListener(aVoid -> {
                        createImageBlock(imageId, isChunked, sizeKB);
                        Toast.makeText(this, "✅ Image saved", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void createImageBlock(String imageId, boolean isChunked, int sizeKB) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // ✅ Check if last block is empty text - if so, replace it with image
        boolean shouldReplaceLastBlock = false;
        int insertPosition = blocks.size();

        if (!blocks.isEmpty()) {
            SubpageBlock lastBlock = blocks.get(blocks.size() - 1);

            if (lastBlock.getType().equals("text") &&
                    (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

                shouldReplaceLastBlock = true;
                insertPosition = blocks.size() - 1;

                // Delete the empty text block from Firestore
                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .collection("subpages").document(subpageId)
                        .collection("blocks").document(lastBlock.getBlockId())
                        .delete();

                blocks.remove(blocks.size() - 1);
            }
        }

        // Create the image block
        SubpageBlock imageBlock = new SubpageBlock();
        imageBlock.setBlockId(UUID.randomUUID().toString());
        imageBlock.setType("image");
        imageBlock.setImageId(imageId);
        imageBlock.setChunked(isChunked);
        imageBlock.setSizeKB(sizeKB);
        imageBlock.setOrder(insertPosition); // ✅ Use insertPosition

        blocks.add(insertPosition, imageBlock); // ✅ Insert at position

        if (shouldReplaceLastBlock) {
            blockAdapter.notifyItemChanged(insertPosition);
        } else {
            blockAdapter.notifyItemInserted(insertPosition);
        }

        saveBlock(imageBlock);

        // ✅ AUTO ADD: Create new text block after image
        SubpageBlock textBlock = new SubpageBlock();
        textBlock.setBlockId(UUID.randomUUID().toString());
        textBlock.setType("text");
        textBlock.setContent("");
        textBlock.setOrder(blocks.size());

        blocks.add(textBlock);
        blockAdapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(textBlock);

        // Update order for all blocks
        updateBlockOrder();

        // ✅ Scroll to image
        final int scrollPosition = insertPosition;
        subpageBlocksRecycler.post(() -> {
            subpageBlocksRecycler.smoothScrollToPosition(scrollPosition);
        });
    }
    private void addLinkBlock() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.link_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        com.google.android.material.textfield.TextInputEditText linkUrlInput =
                sheetView.findViewById(R.id.linkUrlInput);
        TextView createLinkBtn = sheetView.findViewById(R.id.createLinkBtn);

        createLinkBtn.setOnClickListener(v -> {
            String url = linkUrlInput.getText().toString().trim();

            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add https:// if missing
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // Extract title from URL
            String title = extractTitle(url);

            // Check if last block is empty text - replace it
            boolean shouldReplaceLastBlock = false;
            int insertPosition = blocks.size();

            if (!blocks.isEmpty()) {
                SubpageBlock lastBlock = blocks.get(blocks.size() - 1);

                if (lastBlock.getType().equals("text") &&
                        (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

                    shouldReplaceLastBlock = true;
                    insertPosition = blocks.size() - 1;

                    // ✅ Delete empty text block - SUBPAGE PATH
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("subpages").document(subpageId) // ✅ SUBPAGE
                                .collection("blocks").document(lastBlock.getBlockId())
                                .delete();
                    }

                    blocks.remove(blocks.size() - 1);
                }
            }

            // ✅ Create SubpageBlock (not NoteBlock)
            SubpageBlock block = new SubpageBlock();
            block.setBlockId(java.util.UUID.randomUUID().toString());
            block.setType("link");
            block.setContent(title);
            block.setLinkUrl(url);
            block.setLinkBackgroundColor("#FFFFFF");
            block.setLinkDescription("");
            block.setOrder(insertPosition);

            blocks.add(insertPosition, block);

            if (shouldReplaceLastBlock) {
                blockAdapter.notifyItemChanged(insertPosition);
            } else {
                blockAdapter.notifyItemInserted(insertPosition);
            }

            saveBlock(block);

            // Add new text block after link
            SubpageBlock textBlock = new SubpageBlock();
            textBlock.setBlockId(java.util.UUID.randomUUID().toString());
            textBlock.setType("text");
            textBlock.setContent("");
            textBlock.setOrder(blocks.size());

            blocks.add(textBlock);
            blockAdapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(textBlock);

            updateBlockOrder();

            bottomSheet.dismiss();
            Toast.makeText(this, "Link added", Toast.LENGTH_SHORT).show();
        });

        bottomSheet.show();
    }

    // ✅ Helper method - same as NoteActivity
    private String extractTitle(String url) {
        try {
            String domain = url.replace("https://", "").replace("http://", "");
            int slashIndex = domain.indexOf("/");
            if (slashIndex > 0) {
                domain = domain.substring(0, slashIndex);
            }

            // Capitalize first letter
            if (!domain.isEmpty()) {
                domain = domain.substring(0, 1).toUpperCase() + domain.substring(1);
            }

            return domain;
        } catch (Exception e) {
            return "Link";
        }
    }

    private void showDividerSelectionSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.divider_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        LinearLayout dividerSolid = sheetView.findViewById(R.id.dividerSolid);
        LinearLayout dividerDashed = sheetView.findViewById(R.id.dividerDashed);
        LinearLayout dividerDotted = sheetView.findViewById(R.id.dividerDotted);
        LinearLayout dividerDouble = sheetView.findViewById(R.id.dividerDouble);
        LinearLayout dividerArrows = sheetView.findViewById(R.id.dividerArrows);
        LinearLayout dividerStars = sheetView.findViewById(R.id.dividerStars);
        LinearLayout dividerWave = sheetView.findViewById(R.id.dividerWave);
        LinearLayout dividerDiamond = sheetView.findViewById(R.id.dividerDiamond);

        if (dividerSolid != null) {
            dividerSolid.setOnClickListener(v -> {
                addDividerBlockWithStyle("solid");
                bottomSheet.dismiss();
            });
        }

        if (dividerDashed != null) {
            dividerDashed.setOnClickListener(v -> {
                addDividerBlockWithStyle("dashed");
                bottomSheet.dismiss();
            });
        }

        if (dividerDotted != null) {
            dividerDotted.setOnClickListener(v -> {
                addDividerBlockWithStyle("dotted");
                bottomSheet.dismiss();
            });
        }

        if (dividerDouble != null) {
            dividerDouble.setOnClickListener(v -> {
                addDividerBlockWithStyle("double");
                bottomSheet.dismiss();
            });
        }

        if (dividerArrows != null) {
            dividerArrows.setOnClickListener(v -> {
                addDividerBlockWithStyle("arrows");
                bottomSheet.dismiss();
            });
        }

        if (dividerStars != null) {
            dividerStars.setOnClickListener(v -> {
                addDividerBlockWithStyle("stars");
                bottomSheet.dismiss();
            });
        }

        if (dividerWave != null) {
            dividerWave.setOnClickListener(v -> {
                addDividerBlockWithStyle("wave");
                bottomSheet.dismiss();
            });
        }

        if (dividerDiamond != null) {
            dividerDiamond.setOnClickListener(v -> {
                addDividerBlockWithStyle("diamond");
                bottomSheet.dismiss();
            });
        }

        bottomSheet.show();
    }
    private void addDividerBlockWithStyle(String style) {
        boolean replacedEmptyBlock = tryReplaceLastEmptyTextBlock("divider");

        if (!replacedEmptyBlock) {
            SubpageBlock block = new SubpageBlock();
            block.setBlockId(java.util.UUID.randomUUID().toString());
            block.setType("divider");
            block.setContent(style); // ✅ Store style in content
            block.setOrder(blocks.size());

            blocks.add(block);
            blockAdapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(block);

            subpageBlocksRecycler.post(() -> {
                subpageBlocksRecycler.smoothScrollToPosition(blocks.size() - 1);
            });
        } else {
            // If replaced empty block, update its style
            SubpageBlock lastBlock = blocks.get(blocks.size() - 1);
            lastBlock.setContent(style);
            blockAdapter.notifyItemChanged(blocks.size() - 1);
            saveBlock(lastBlock);
        }
    }
}