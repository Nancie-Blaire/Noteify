package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//IMAGES
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.activity.OnBackPressedCallback;
import android.graphics.Color;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import java.util.HashMap;
import java.util.Map;

//COLOR PICKER
import android.graphics.Color;
import org.json.JSONObject;
import org.json.JSONException;

public class NoteActivity extends AppCompatActivity implements NoteBlockAdapter.OnBlockChangeListener {

    private EditText noteTitle;
    private RecyclerView blocksRecycler;
    private NoteBlockAdapter adapter;
    private List<NoteBlock> blocks;
    private ImageView backBtn;
    private View keyboardToolbar;

    // Keyboard toolbar buttons
    private ImageButton headingsAndFont;
    private ImageButton addDividerBtn;
    private ImageButton addBulletBtn;
    private ImageButton addNumberedBtn;
    private ImageButton addCheckboxBtn;
    private ImageButton addLinkBtn;
    private ImageButton insertImageBtn;
    private ImageButton indentBtn;
    private ImageButton outdentBtn;
    private ImageButton addThemeBtn;
    private ImageButton addSubpageBtn;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String noteId;

    private ItemTouchHelper itemTouchHelper;
    private boolean isReordering = false;

    //images
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private Uri currentPhotoUri;

    private static final int MAX_IMAGE_WIDTH = 1024;
    private static final int MAX_IMAGE_HEIGHT = 1024;
    private static final int COMPRESSION_QUALITY = 80;
    private static final int MAX_INLINE_IMAGE_KB = 700;
    private static final int CHUNK_SIZE = 50000;

    private static final int REQUEST_SUBPAGE = 100;
    private static final int REQUEST_GALLERY = 101;
    private static final int REQUEST_CAMERA = 102;

    //themes
    private View colorPickerPanel;
    private RelativeLayout noteLayout;
    private String currentNoteColor = "#FAFAFA";
    private boolean isSelectingForBookmark = false;
    private int bookmarkStartIndex = -1;
    private int bookmarkEndIndex = -1;
    private String selectedBlockIdForBookmark = null;
    private String selectedTextForBookmark = null;

    private Map<String, List<Bookmark>> blockBookmarksMap = new HashMap<>();
    private int scrollToBlockPosition = -1;
    private int scrollToTextPosition = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ MODERN: Handle Android back button/gesture
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Auto-save before going back
                saveNoteTitle();
                saveAllBlocks();

                // Disable callback and trigger back action
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        setContentView(R.layout.activity_note);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        noteId = getIntent().getStringExtra("noteId");
        String subpageTitle = getIntent().getStringExtra("subpageTitle");

        // Initialize views
        noteTitle = findViewById(R.id.noteTitle);
        blocksRecycler = findViewById(R.id.blocksRecycler);
        backBtn = findViewById(R.id.checkBtn);
        keyboardToolbar = findViewById(R.id.keyboardToolbar);

        View emptySpace = findViewById(R.id.emptySpace);
        setupEmptySpaceClick(emptySpace);



        // Initialize keyboard toolbar buttons
        headingsAndFont = findViewById(R.id.headingsandfont);
        addDividerBtn = findViewById(R.id.addDividerOption);
        addBulletBtn = findViewById(R.id.addBulletListOption);
        addNumberedBtn = findViewById(R.id.addNumberedListOption);
        addCheckboxBtn = findViewById(R.id.addCheckboxOption);
        addLinkBtn = findViewById(R.id.addLinkOption);
        insertImageBtn = findViewById(R.id.insertImage);
        indentBtn = findViewById(R.id.indentOption);
        outdentBtn = findViewById(R.id.outdentOption);
        addThemeBtn = findViewById(R.id.addThemeOption);
        addSubpageBtn = findViewById(R.id.addSubpageOption);

        noteLayout = findViewById(R.id.noteLayout);
        colorPickerPanel = findViewById(R.id.colorPickerPanel);
        // Setup RecyclerView
        blocks = new ArrayList<>();
        adapter = new NoteBlockAdapter(blocks, this, noteId);
        blocksRecycler.setLayoutManager(new LinearLayoutManager(this));
        blocksRecycler.setAdapter(adapter);

        // Setup image pickers
        setupImagePickers();

        // Setup drag and drop
        setupDragAndDrop();
        setupColorPicker();

        if (noteId != null) {
            // Get scroll position from intent
            scrollToBlockPosition = getIntent().getIntExtra("scrollToBlockPosition", -1);
            scrollToTextPosition = getIntent().getIntExtra("scrollToTextPosition", -1);
            loadNote();
            loadNoteColor();
            loadBookmarksForNote();// ✅ Load saved color
        } else {
            createNewNote();
        }

        // Load note if exists
        if (noteId != null) {
            loadNote();
        } else {
            createNewNote();
        }

        // Set subpage title if provided
        if (subpageTitle != null && !subpageTitle.isEmpty()) {
            noteTitle.setText(subpageTitle);
        }

        // ✅ App back button saves and exits
        backBtn.setOnClickListener(v -> saveAndExit());

        // Setup keyboard toolbar
        setupKeyboardToolbar();

        // Show keyboard toolbar
        keyboardToolbar.setVisibility(View.VISIBLE);

        ImageView viewBookmarksBtn = findViewById(R.id.viewBookmarksBtn);
        viewBookmarksBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, BookmarksActivity.class);
            intent.putExtra("noteId", noteId);
            startActivity(intent);
        });
    }

    private void setupKeyboardToolbar() {
        // Headings & Font
        headingsAndFont.setOnClickListener(v -> showHeadingOptions());

        // Divider
        addDividerBtn.setOnClickListener(v -> showDividerSelectionSheet());

        // Bullet List
        addBulletBtn.setOnClickListener(v -> addBulletBlock());

        // Numbered List
        addNumberedBtn.setOnClickListener(v -> addNumberedBlock());

        // Checkbox
        addCheckboxBtn.setOnClickListener(v -> addCheckboxBlock());

        // Link
        addLinkBtn.setOnClickListener(v -> addLinkBlock());

        // Image
        insertImageBtn.setOnClickListener(v -> addImageBlock());

        // Indent (to be implemented)
        indentBtn.setOnClickListener(v -> indentCurrentBlock());

        // Outdent (to be implemented)
        outdentBtn.setOnClickListener(v -> outdentCurrentBlock());

        // Theme
        addThemeBtn.setOnClickListener(v -> toggleColorPicker());
        // Subpage
        addSubpageBtn.setOnClickListener(v -> addSubpageBlock());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (noteId != null) {
            // ✅ Check if we need to scroll
            boolean needsScroll = (scrollToBlockPosition >= 0 && scrollToTextPosition >= 0);

            if (needsScroll) {
                // ✅ Load blocks first, THEN scroll
                loadBlocksAndScroll();
            } else {
                // ✅ Normal load
                loadBlocks();
            }
        }
    }

    private void loadBlocksAndScroll() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        android.util.Log.d("NoteActivity", "Loading blocks for scroll...");

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("blocks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    blocks.clear();

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        NoteBlock block = NoteBlock.fromMap(doc.getData());
                        blocks.add(block);
                    }

                    refreshSubpageTitlesAfterLoad();
                    adapter.notifyDataSetChanged();
                    renumberLists();

                    android.util.Log.d("NoteActivity", "Blocks loaded: " + blocks.size());

                    // ✅ NOW scroll after blocks are loaded
                    if (scrollToBlockPosition >= 0 && scrollToTextPosition >= 0) {
                        final int blockPos = scrollToBlockPosition;
                        final int textPos = scrollToTextPosition;

                        // ✅ Wait for RecyclerView to layout
                        blocksRecycler.postDelayed(() -> {
                            android.util.Log.d("NoteActivity", "Attempting scroll to block " + blockPos);
                            scrollToBookmark(blockPos, textPos);

                            // ✅ Reset scroll flags
                            scrollToBlockPosition = -1;
                            scrollToTextPosition = -1;
                        }, 500); // Give RecyclerView time to layout
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("NoteActivity", "Error loading blocks", e);
                });
    }

    private void loadBlocks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("blocks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    blocks.clear();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        NoteBlock block = NoteBlock.fromMap(doc.getData());
                        blocks.add(block);
                    }

                    refreshSubpageTitlesAfterLoad();
                    adapter.notifyDataSetChanged();
                    renumberLists();
                })
                .addOnFailureListener(e -> {
                    // Error loading blocks
                });
    }

    private void refreshSubpageTitlesAfterLoad() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        for (int i = 0; i < blocks.size(); i++) {
            NoteBlock block = blocks.get(i);

            if (block.getType() == NoteBlock.BlockType.SUBPAGE && block.getSubpageId() != null) {
                final int position = i;
                String subpageId = block.getSubpageId();

                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .collection("subpages").document(subpageId)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String title = doc.getString("title");
                                blocks.get(position).setContent(
                                        title != null && !title.isEmpty() ? title : "Untitled Subpage"
                                );
                                adapter.notifyItemChanged(position);
                            }
                        });
            }
        }
    }

    private void setupDragAndDrop() {
        DragDropHelper dragHelper = new DragDropHelper(new DragDropHelper.DragListener() {
            @Override
            public void onItemMoved(int fromPosition, int toPosition) {
                isReordering = true;
                adapter.moveBlock(fromPosition, toPosition);
            }

            @Override
            public void onDragFinished() {
                isReordering = false;
                saveBlockOrder();
                Toast.makeText(NoteActivity.this, "Block moved", Toast.LENGTH_SHORT).show();
            }
        });

        itemTouchHelper = new ItemTouchHelper(dragHelper);
        itemTouchHelper.attachToRecyclerView(blocksRecycler);
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    private void createNewNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        noteId = db.collection("users").document(user.getUid())
                .collection("notes").document().getId();

        adapter.setNoteId(noteId);

        Map<String, Object> newNote = new HashMap<>();
        newNote.put("title", "");
        newNote.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .set(newNote);

        addTextBlock();
    }

    private void loadNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String title = doc.getString("title");
                        if (title != null && !title.isEmpty()) {
                            noteTitle.setText(title);
                        }
                    } else {
                        createNoteDocument();
                    }
                });

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("blocks")
                .orderBy("position")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    blocks.clear();

                    if (querySnapshot.isEmpty()) {
                        addTextBlock();
                    } else {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            NoteBlock block = NoteBlock.fromMap(doc.getData());
                            blocks.add(block);
                        }
                        adapter.notifyDataSetChanged();
                        renumberLists();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading note", Toast.LENGTH_SHORT).show();
                });
    }

    private void createNoteDocument() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> newNote = new HashMap<>();
        newNote.put("title", noteTitle.getText().toString());
        newNote.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .set(newNote);
    }

    private void saveAndExit() {
        saveNoteTitle();
        saveAllBlocks();
        finish();
    }

    private void saveNoteTitle() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = noteTitle.getText().toString();

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .update("title", title, "timestamp", System.currentTimeMillis());
    }

    private void saveAllBlocks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        for (NoteBlock block : blocks) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("blocks").document(block.getId())
                    .set(block.toMap());
        }
    }

    private void saveBlockOrder() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        for (int i = 0; i < blocks.size(); i++) {
            NoteBlock block = blocks.get(i);
            block.setPosition(i);

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("blocks").document(block.getId())
                    .update("position", i);
        }

        renumberLists();
    }

    private void renumberLists() {
        int currentNumber = 1;
        int currentIndent = -1;
        NoteBlock.BlockType currentListType = null;

        for (int i = 0; i < blocks.size(); i++) {
            NoteBlock block = blocks.get(i);

            if (block.getType() == NoteBlock.BlockType.NUMBERED) {
                if (currentListType == NoteBlock.BlockType.NUMBERED &&
                        block.getIndentLevel() == currentIndent) {
                    currentNumber++;
                } else {
                    currentNumber = 1;
                    currentIndent = block.getIndentLevel();
                    currentListType = NoteBlock.BlockType.NUMBERED;
                }

                block.setListNumber(currentNumber);
                adapter.notifyItemChanged(i);
            } else if (block.getType() != NoteBlock.BlockType.NUMBERED) {
                currentListType = null;
                currentIndent = -1;
            }
        }
    }

    private void addTextBlock() {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.TEXT);
        block.setPosition(blocks.size());

        // ✅ Set default style - no special formatting
        block.setFontStyle(null);
        block.setFontColor("#333333");

        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);

        blocksRecycler.post(() -> {
            blocksRecycler.postDelayed(() -> {
                int position = blocks.size() - 1;
                if (position >= 0 && position < blocks.size()) {
                    adapter.notifyItemChanged(position);
                    focusBlock(position, 0);
                }
            }, 100);
        });
    }

    // ✅ UPDATED: addHeadingBlock - Replace empty text
    private void addHeadingBlock(NoteBlock.BlockType headingType) {
        boolean replacedEmptyBlock = tryReplaceLastEmptyTextBlock(headingType);

        if (!replacedEmptyBlock) {
            NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", headingType);
            block.setPosition(blocks.size());
            blocks.add(block);
            adapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(block);

            focusBlock(blocks.size() - 1, 0);
        }
    }
    private void addBulletBlock() {
        boolean replacedEmptyBlock = tryReplaceLastEmptyTextBlock(NoteBlock.BlockType.BULLET);

        if (!replacedEmptyBlock) {
            // No empty text to replace, create new bullet
            NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.BULLET);
            block.setPosition(blocks.size());
            blocks.add(block);
            adapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(block);

            focusBlock(blocks.size() - 1, 0);
        }
    }

    private void addNumberedBlock() {
        boolean replacedEmptyBlock = tryReplaceLastEmptyTextBlock(NoteBlock.BlockType.NUMBERED);

        if (!replacedEmptyBlock) {
            NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.NUMBERED);
            block.setPosition(blocks.size());
            block.setListNumber(1);
            blocks.add(block);
            adapter.notifyItemInserted(blocks.size() - 1);
            renumberLists();
            saveBlock(block);

            focusBlock(blocks.size() - 1, 0);
        }
    }

    // ✅ UPDATED: addCheckboxBlock - Replace empty text
    private void addCheckboxBlock() {
        boolean replacedEmptyBlock = tryReplaceLastEmptyTextBlock(NoteBlock.BlockType.CHECKBOX);

        if (!replacedEmptyBlock) {
            NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.CHECKBOX);
            block.setPosition(blocks.size());
            blocks.add(block);
            adapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(block);

            focusBlock(blocks.size() - 1, 0);
        }
    }

    private void showDividerSelectionSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.divider_bottom_sheet, null);
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

        // Set click listeners for each style
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

    // Update yung existing addDividerBlock() method to accept style parameter:
    private void addDividerBlockWithStyle(String style) {
        boolean replacedEmptyBlock = tryReplaceLastEmptyTextBlock(NoteBlock.BlockType.DIVIDER);

        if (!replacedEmptyBlock) {
            NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.DIVIDER);
            block.setPosition(blocks.size());
            block.setDividerStyle(style); // ✅ Use selected style
            blocks.add(block);
            adapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(block);

            blocksRecycler.post(() -> {
                blocksRecycler.smoothScrollToPosition(blocks.size() - 1);
            });
        } else {
            // If replaced empty block, update its style
            NoteBlock lastBlock = blocks.get(blocks.size() - 1);
            lastBlock.setDividerStyle(style);
            adapter.notifyItemChanged(blocks.size() - 1);
            saveBlock(lastBlock);
        }
    }

    //indent & outdent
    private void indentCurrentBlock() {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView.ViewHolder holder = blocksRecycler.findContainingViewHolder(focusedView);
        if (holder == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= blocks.size()) {
            return;
        }

        NoteBlock block = blocks.get(position);

        if (canIndent(block)) {
            int currentIndent = block.getIndentLevel();
            int maxIndent = getMaxIndentForType(block.getType());

            if (currentIndent < maxIndent) {
                block.setIndentLevel(currentIndent + 1);
                adapter.notifyItemChanged(position);
                saveBlock(block);
                renumberLists(); // ✅ Renumber after indent change
                focusedView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                Toast.makeText(this, "Maximum indent level reached", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "This block type cannot be indented", Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ NEW: Outdent functionality
    private void outdentCurrentBlock() {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView.ViewHolder holder = blocksRecycler.findContainingViewHolder(focusedView);
        if (holder == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= blocks.size()) {
            return;
        }

        NoteBlock block = blocks.get(position);

        if (canIndent(block)) {
            int currentIndent = block.getIndentLevel();

            if (currentIndent > 0) {
                block.setIndentLevel(currentIndent - 1);
                adapter.notifyItemChanged(position);
                saveBlock(block);
                renumberLists(); // ✅ Renumber after outdent change
                focusedView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                Toast.makeText(this, "Already at minimum indent level", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "This block type cannot be outdented", Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ NEW: Check if block type supports indenting
    private boolean canIndent(NoteBlock block) {
        switch (block.getType()) {
            case TEXT:
            case BULLET:
            case NUMBERED:
            case CHECKBOX:
                return true;
            case HEADING_1:
            case HEADING_2:
            case HEADING_3:
            case IMAGE:
            case DIVIDER:
            case SUBPAGE:
            case LINK:
            default:
                return false;
        }
    }

    // ✅ NEW: Get max indent level per block type
    private int getMaxIndentForType(NoteBlock.BlockType type) {
        switch (type) {
            case BULLET:
                return 2; // 0 = ●, 1 = ○, 2 = ■
            case NUMBERED:
                return 3; // 0 = 1., 1 = a., 2 = i., 3 = deeper numbers
            case TEXT:
            case CHECKBOX:
                return 5; // Allow deeper indenting
            default:
                return 0;
        }
    }


    //COLOR PICKER


    private void setupColorPicker() {
        findViewById(R.id.colorDefault).setOnClickListener(v -> changeNoteColor("#FAFAFA"));
        findViewById(R.id.colorRed).setOnClickListener(v -> changeNoteColor("#FFCDD2"));
        findViewById(R.id.colorPink).setOnClickListener(v -> changeNoteColor("#F8BBD0"));
        findViewById(R.id.colorPurple).setOnClickListener(v -> changeNoteColor("#E1BEE7"));
        findViewById(R.id.colorBlue).setOnClickListener(v -> changeNoteColor("#BBDEFB"));
        findViewById(R.id.colorCyan).setOnClickListener(v -> changeNoteColor("#B2EBF2"));
        findViewById(R.id.colorGreen).setOnClickListener(v -> changeNoteColor("#C8E6C9"));
        findViewById(R.id.colorYellow).setOnClickListener(v -> changeNoteColor("#FFF9C4"));
        findViewById(R.id.colorOrange).setOnClickListener(v -> changeNoteColor("#FFE0B2"));
        findViewById(R.id.colorBrown).setOnClickListener(v -> changeNoteColor("#D7CCC8"));
        findViewById(R.id.colorGrey).setOnClickListener(v -> changeNoteColor("#CFD8DC"));
    }

    private void toggleColorPicker() {
        if (colorPickerPanel.getVisibility() == View.VISIBLE) {
            colorPickerPanel.setVisibility(View.GONE);
        } else {
            colorPickerPanel.setVisibility(View.VISIBLE);
        }
    }

    private void changeNoteColor(String color) {
        noteLayout.setBackgroundColor(Color.parseColor(color));
        View topBar = findViewById(R.id.topBar);
        topBar.setBackgroundColor(Color.parseColor(color));
        currentNoteColor = color;
        colorPickerPanel.setVisibility(View.GONE);
        saveNoteColor(color);
    }

    private void saveNoteColor(String color) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .update("color", color)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Color saved", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadNoteColor() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String color = doc.getString("color");
                        if (color != null) {
                            currentNoteColor = color;
                            noteLayout.setBackgroundColor(Color.parseColor(color));
                            View topBar = findViewById(R.id.topBar);
                            topBar.setBackgroundColor(Color.parseColor(color));
                        }
                    }
                });
    }


    //IMAGESSSS
    private void addImageBlock() {
        showInsertMediaBottomSheet();
    }

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
        if (noteId == null) {
            Toast.makeText(this, "Please save the note first", Toast.LENGTH_SHORT).show();
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
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                int originalWidth = bitmap.getWidth();
                int originalHeight = bitmap.getHeight();
                float scale = Math.min(
                        MAX_IMAGE_WIDTH / (float) originalWidth,
                        MAX_IMAGE_HEIGHT / (float) originalHeight
                );

                if (scale < 1.0f) {
                    int newWidth = (int) (originalWidth * scale);
                    int newHeight = (int) (originalHeight * scale);
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
                byte[] imageBytes = baos.toByteArray();

                String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);

                int originalSizeKB = imageBytes.length / 1024;
                int base64SizeKB = base64Image.length() / 1024;

                final Bitmap finalBitmap = bitmap;

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

            } catch (IOException e) {
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

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("imageId", imageId);
        metadata.put("isChunked", true);
        metadata.put("chunkCount", chunkCount);
        metadata.put("totalSize", totalLength);
        metadata.put("sizeKB", sizeKB);
        metadata.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("images").document(imageId)
                .set(metadata)
                .addOnSuccessListener(aVoid -> {
                    for (int i = 0; i < chunkCount; i++) {
                        int start = i * CHUNK_SIZE;
                        int end = Math.min(start + CHUNK_SIZE, totalLength);
                        String chunk = base64Image.substring(start, end);

                        Map<String, Object> chunkData = new HashMap<>();
                        chunkData.put("data", chunk);
                        chunkData.put("chunkIndex", i);

                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
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

        Map<String, Object> imageData = new HashMap<>();
        if (!isChunked) {
            imageData.put("base64Data", base64Image);
        }
        imageData.put("imageId", imageId);
        imageData.put("isChunked", isChunked);
        imageData.put("sizeKB", sizeKB);
        imageData.put("timestamp", System.currentTimeMillis());

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
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
        // ✅ Check if last block is empty text - if so, replace it with image
        boolean shouldReplaceLastBlock = false;
        int insertPosition = blocks.size();

        if (!blocks.isEmpty()) {
            NoteBlock lastBlock = blocks.get(blocks.size() - 1);

            if (lastBlock.getType() == NoteBlock.BlockType.TEXT &&
                    (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

                shouldReplaceLastBlock = true;
                insertPosition = blocks.size() - 1;

                // Delete the empty text block from Firestore
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    db.collection("users").document(user.getUid())
                            .collection("notes").document(noteId)
                            .collection("blocks").document(lastBlock.getId())
                            .delete();
                }

                blocks.remove(blocks.size() - 1);
            }
        }

        // Create the image block
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.IMAGE);
        block.setPosition(insertPosition);
        block.setImageId(imageId);
        block.setChunked(isChunked);
        block.setSizeKB(sizeKB);

        blocks.add(insertPosition, block);

        if (shouldReplaceLastBlock) {
            adapter.notifyItemChanged(insertPosition);
        } else {
            adapter.notifyItemInserted(insertPosition);
        }

        saveBlock(block);

        // ✅ AUTO ADD: Create new text block after image so user can continue typing
        NoteBlock textBlock = new NoteBlock(System.currentTimeMillis() + "1", NoteBlock.BlockType.TEXT);
        textBlock.setPosition(blocks.size());
        blocks.add(textBlock);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(textBlock);

        // ✅ FIX: Create final variable for lambda
        final int scrollPosition = insertPosition;
        blocksRecycler.post(() -> {
            blocksRecycler.smoothScrollToPosition(scrollPosition);
        });
    }
    //subPage
    private void addSubpageBlock() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        boolean shouldReplaceLastBlock = false;
        int insertPosition = blocks.size();

        if (!blocks.isEmpty()) {
            NoteBlock lastBlock = blocks.get(blocks.size() - 1);

            if (lastBlock.getType() == NoteBlock.BlockType.TEXT &&
                    (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

                shouldReplaceLastBlock = true;
                insertPosition = blocks.size() - 1;

                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .collection("blocks").document(lastBlock.getId())
                        .delete();

                blocks.remove(blocks.size() - 1);
            }
        }

        String newSubpageId = db.collection("users").document(user.getUid())
                .collection("notes").document().getId();

        Map<String, Object> subpageData = new HashMap<>();
        subpageData.put("title", "");
        subpageData.put("content", "");
        subpageData.put("parentNoteId", noteId);
        subpageData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("subpages").document(newSubpageId)
                .set(subpageData);

        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.SUBPAGE);
        block.setPosition(insertPosition);
        block.setContent("Untitled Subpage");
        block.setSubpageId(newSubpageId);
        blocks.add(insertPosition, block);

        if (shouldReplaceLastBlock) {
            adapter.notifyItemChanged(insertPosition);
        } else {
            adapter.notifyItemInserted(insertPosition);
        }

        saveBlock(block);

        NoteBlock textBlock = new NoteBlock(System.currentTimeMillis() + "1", NoteBlock.BlockType.TEXT);
        textBlock.setPosition(blocks.size());
        blocks.add(textBlock);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(textBlock);

        updateBlockPositions();

        Intent intent = new Intent(this, SubpageActivity.class);
        intent.putExtra("noteId", noteId);
        intent.putExtra("subpageId", newSubpageId);
        intent.putExtra("subpageTitle", "Untitled Subpage");
        startActivityForResult(intent, REQUEST_SUBPAGE); // ✅ Use constant
    }

    private void updateBlockPositions() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        for (int i = 0; i < blocks.size(); i++) {
            NoteBlock block = blocks.get(i);
            block.setPosition(i);

            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("blocks").document(block.getId())
                    .update("position", i);
        }
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
                NoteBlock lastBlock = blocks.get(blocks.size() - 1);

                if (lastBlock.getType() == NoteBlock.BlockType.TEXT &&
                        (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

                    shouldReplaceLastBlock = true;
                    insertPosition = blocks.size() - 1;

                    // Delete empty text block
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("blocks").document(lastBlock.getId())
                                .delete();
                    }

                    blocks.remove(blocks.size() - 1);
                }
            }

            // Create link block
            NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.LINK);
            block.setPosition(insertPosition);
            block.setContent(title);
            block.setLinkUrl(url);
            block.setLinkBackgroundColor("#FFFFFF");
            block.setLinkDescription("");

            blocks.add(insertPosition, block);

            if (shouldReplaceLastBlock) {
                adapter.notifyItemChanged(insertPosition);
            } else {
                adapter.notifyItemInserted(insertPosition);
            }

            saveBlock(block);

            // Add new text block after link
            NoteBlock textBlock = new NoteBlock(System.currentTimeMillis() + "1", NoteBlock.BlockType.TEXT);
            textBlock.setPosition(blocks.size());
            blocks.add(textBlock);
            adapter.notifyItemInserted(blocks.size() - 1);
            saveBlock(textBlock);

            updateBlockPositions();

            bottomSheet.dismiss();
            Toast.makeText(this, "Link added", Toast.LENGTH_SHORT).show();
        });

        bottomSheet.show();
    }

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

    private void saveBlock(NoteBlock block) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("blocks").document(block.getId())
                .set(block.toMap());
    }

    @Override
    public void onBlockChanged(NoteBlock block) {
        if (!isReordering) {
            saveBlock(block);
        }
    }

    @Override
    public void onBlockDeleted(int position) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        NoteBlock block = blocks.get(position);

        if (block.getType() == NoteBlock.BlockType.SUBPAGE && block.getSubpageId() != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("subpages").document(block.getSubpageId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Subpage deleted", Toast.LENGTH_SHORT).show();
                    });
        }

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("blocks").document(block.getId())
                .delete();

        blocks.remove(position);
        adapter.notifyItemRemoved(position);
        renumberLists();
    }

    @Override
    public void onBlockTypeChanged(int position, NoteBlock.BlockType newType) {
        if (newType == NoteBlock.BlockType.TEXT) {
            // Use convertBlockToText for smooth conversion
            convertBlockToText(position);
        } else {
            NoteBlock block = blocks.get(position);
            block.setType(newType);
            adapter.notifyItemChanged(position);
            saveBlock(block);
            renumberLists();
        }
    }
    @Override
    public void onImageClick(String imageId) {
        Toast.makeText(this, "Image clicked", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSubpageClick(String subpageId) {
        NoteBlock subpageBlock = null;
        for (int i = 0; i < blocks.size(); i++) {
            NoteBlock block = blocks.get(i);
            if (block.getSubpageId() != null && block.getSubpageId().equals(subpageId)) {
                subpageBlock = block;
                break;
            }
        }

        Intent intent = new Intent(this, SubpageActivity.class);
        intent.putExtra("noteId", noteId);
        intent.putExtra("subpageId", subpageId);
        if (subpageBlock != null) {
            intent.putExtra("subpageTitle", subpageBlock.getContent());
        }
        startActivityForResult(intent, REQUEST_SUBPAGE);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SUBPAGE) {
            if (noteId != null) {
                loadBlocks();
            }
        }
    }

    @Override
    public void onLinkClick(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        startActivity(browserIntent);
    }

    @Override
    public void onDividerClick(int position) {
        // This is handled in the adapter now
        // But you can add additional logic here if needed
        NoteBlock block = blocks.get(position);
        Toast.makeText(this, "Tap to change style, long press for options",
                Toast.LENGTH_SHORT).show();
    }

    // ✅ OPTIONAL: Add helper method to handle divider style changes
    public void updateDividerStyle(int position, String newStyle) {
        if (position >= 0 && position < blocks.size()) {
            NoteBlock block = blocks.get(position);
            if (block.getType() == NoteBlock.BlockType.DIVIDER) {
                block.setDividerStyle(newStyle);
                adapter.notifyItemChanged(position);
                saveBlock(block);
            }
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        saveNoteTitle();
        saveAllBlocks();
    }

    @Override
    public void onEnterPressed(int position, String textBeforeCursor, String textAfterCursor) {
        if (position < 0 || position >= blocks.size()) return;

        NoteBlock currentBlock = blocks.get(position);
        NoteBlock.BlockType currentType = currentBlock.getType();

        // ✅ CHECK: Is the text BEFORE cursor empty?
        boolean isTextBeforeEmpty = textBeforeCursor.trim().isEmpty();

        switch (currentType) {
            case BULLET:
            case NUMBERED:
            case CHECKBOX:
                if (isTextBeforeEmpty) {
                    // ✅ EMPTY + ENTER = Convert to TEXT (no new block)
                    currentBlock.setContent("");
                    saveBlock(currentBlock);
                    convertBlockToText(position);
                    focusBlock(position, 0);
                } else {
                    // ✅ HAS CONTENT = Create new same-type block
                    currentBlock.setContent(textBeforeCursor);
                    saveBlock(currentBlock);

                    NoteBlock newBlock;
                    int insertPosition = position + 1;

                    if (currentType == NoteBlock.BlockType.NUMBERED) {
                        newBlock = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.NUMBERED);
                        newBlock.setIndentLevel(currentBlock.getIndentLevel());
                        newBlock.setContent(textAfterCursor != null ? textAfterCursor : "");
                        newBlock.setPosition(insertPosition);
                        insertBlockAt(newBlock, insertPosition);
                        renumberLists();
                    } else if (currentType == NoteBlock.BlockType.CHECKBOX) {
                        newBlock = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.CHECKBOX);
                        newBlock.setIndentLevel(currentBlock.getIndentLevel());
                        newBlock.setChecked(false);
                        newBlock.setContent(textAfterCursor != null ? textAfterCursor : "");
                        newBlock.setPosition(insertPosition);
                        insertBlockAt(newBlock, insertPosition);
                    } else { // BULLET
                        newBlock = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.BULLET);
                        newBlock.setIndentLevel(currentBlock.getIndentLevel());
                        newBlock.setContent(textAfterCursor != null ? textAfterCursor : "");
                        newBlock.setPosition(insertPosition);
                        insertBlockAt(newBlock, insertPosition);
                    }

                    focusBlock(insertPosition, 0);
                }
                break;

            case TEXT:
            case HEADING_1:
            case HEADING_2:
            case HEADING_3:
            default:
                // TEXT/HEADING: Always create new text block
                currentBlock.setContent(textBeforeCursor);
                saveBlock(currentBlock);

                NoteBlock newBlock = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.TEXT);
                newBlock.setIndentLevel(currentBlock.getIndentLevel());
                newBlock.setContent(textAfterCursor != null ? textAfterCursor : "");
                newBlock.setPosition(position + 1);
                insertBlockAt(newBlock, position + 1);
                focusBlock(position + 1, 0);
                break;
        }
    }

    // ===============================================
// HELPER: Focus a specific block
// ===============================================
    private void focusBlock(int position, int cursorPosition) {
        blocksRecycler.post(() -> {
            RecyclerView.ViewHolder holder = blocksRecycler.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView != null) {
                EditText editText = holder.itemView.findViewById(R.id.contentEdit);
                if (editText != null) {
                    // ✅ CRITICAL: Ensure EditText is enabled and focusable
                    editText.setEnabled(true);
                    editText.setFocusable(true);
                    editText.setFocusableInTouchMode(true);
                    editText.setCursorVisible(true);

                    editText.requestFocus();

                    // Set cursor position
                    if (cursorPosition >= 0 && cursorPosition <= editText.getText().length()) {
                        editText.setSelection(cursorPosition);
                    } else {
                        editText.setSelection(editText.getText().length());
                    }

                    // Show keyboard
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });
    }
    @Override
    public void onBackspaceOnEmptyBlock(int position) {
        if (position < 0 || position >= blocks.size()) return;

        NoteBlock block = blocks.get(position);
        NoteBlock.BlockType type = block.getType();

        // ✅ For bullet/numbered/checkbox at cursor position 0:
        // If has indent -> reduce indent
        // If no indent -> convert to text
        if (type == NoteBlock.BlockType.BULLET ||
                type == NoteBlock.BlockType.NUMBERED ||
                type == NoteBlock.BlockType.CHECKBOX) {

            int currentIndent = block.getIndentLevel();

            if (currentIndent > 0) {
                // ✅ Reduce indent level
                block.setIndentLevel(currentIndent - 1);
                adapter.notifyItemChanged(position);
                saveBlock(block);

                if (type == NoteBlock.BlockType.NUMBERED) {
                    renumberLists();
                }

                // Haptic feedback
                View view = getCurrentFocus();
                if (view != null) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }

                focusBlock(position, 0);
            } else {
                // ✅ No indent - convert to text (stay on same line)
                convertBlockToText(position);
                focusBlock(position, 0);
            }
        } else if (type == NoteBlock.BlockType.TEXT) {
            // For text blocks at position 0, just ignore or merge with previous
            if (position > 0) {
                deleteBlockAndFocusPrevious(position);
            }
        }
    }

    private void deleteBlockAndFocusPrevious(int position) {
        if (position <= 0 || position >= blocks.size()) return;

        NoteBlock block = blocks.get(position);
        NoteBlock.BlockType blockType = block.getType();

        // Delete from Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("blocks").document(block.getId())
                    .delete();
        }

        // Remove from list
        blocks.remove(position);
        adapter.notifyItemRemoved(position);

        // Update positions
        updateBlockPositions();

        // Renumber lists if needed
        if (blockType == NoteBlock.BlockType.NUMBERED) {
            renumberLists();
        }

        // Focus previous block
        final int previousPosition = position - 1;
        blocksRecycler.post(() -> {
            blocksRecycler.postDelayed(() -> {
                RecyclerView.ViewHolder holder = blocksRecycler.findViewHolderForAdapterPosition(previousPosition);
                if (holder != null) {
                    View view = holder.itemView;
                    EditText editText = view.findViewById(R.id.contentEdit);
                    if (editText != null) {
                        editText.requestFocus();
                        editText.setSelection(editText.getText().length());

                        // Show keyboard
                        android.view.inputmethod.InputMethodManager imm =
                                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                }
            }, 100);
        });
    }

    // Helper: Insert block at specific position
    private void insertBlockAt(NoteBlock block, int position) {
        blocks.add(position, block);

        // Update positions for all blocks after insertion
        for (int i = position; i < blocks.size(); i++) {
            blocks.get(i).setPosition(i);
        }

        adapter.notifyItemInserted(position);
        saveBlock(block);
        updateBlockPositions();
    }
    private void convertBlockToText(int position) {
        if (position < 0 || position >= blocks.size()) return;

        NoteBlock oldBlock = blocks.get(position);
        NoteBlock.BlockType oldType = oldBlock.getType();
        String oldContent = oldBlock.getContent();
        int oldIndent = oldBlock.getIndentLevel();

        // Delete old block from Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("blocks").document(oldBlock.getId())
                    .delete();
        }

        // Remove old block from list
        blocks.remove(position);

        // Create NEW text block with same content
        NoteBlock newBlock = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.TEXT);
        newBlock.setContent(oldContent != null ? oldContent : "");
        newBlock.setIndentLevel(oldIndent);
        newBlock.setPosition(position);

        // Insert new text block
        blocks.add(position, newBlock);
        adapter.notifyItemChanged(position);
        saveBlock(newBlock);

        // Update positions
        updateBlockPositions();

        // If it was a numbered list, renumber
        if (oldType == NoteBlock.BlockType.NUMBERED) {
            renumberLists();
        }

        Toast.makeText(this, "Converted to text", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackspaceAtStart(int position, String currentText) {
        if (position <= 0 || position >= blocks.size()) return;

        NoteBlock currentBlock = blocks.get(position);
        NoteBlock previousBlock = blocks.get(position - 1);

        // Don't merge with IMAGE, DIVIDER, SUBPAGE
        switch (previousBlock.getType()) {
            case IMAGE:
            case DIVIDER:
            case SUBPAGE:
                convertBlockToText(position);
                return;
        }

        // Merge content
        String mergedContent = previousBlock.getContent() + currentText;
        previousBlock.setContent(mergedContent);
        saveBlock(previousBlock);

        // Delete current block
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("blocks").document(currentBlock.getId())
                    .delete();
        }

        blocks.remove(position);
        adapter.notifyItemRemoved(position);
        updateBlockPositions();

        // Focus previous
        final int previousPosition = position - 1;
        final int cursorPosition = previousBlock.getContent().length() - currentText.length();

        blocksRecycler.post(() -> {
            adapter.notifyItemChanged(previousPosition);

            blocksRecycler.postDelayed(() -> {
                RecyclerView.ViewHolder holder = blocksRecycler.findViewHolderForAdapterPosition(previousPosition);
                if (holder != null) {
                    View view = holder.itemView;
                    EditText editText = view.findViewById(R.id.contentEdit);
                    if (editText != null) {
                        editText.requestFocus();
                        editText.setSelection(Math.max(0, cursorPosition));
                    }
                }
            }, 50);
        });
    }

    private void setupEmptySpaceClick(View emptySpace) {
        if (emptySpace == null) return;

        emptySpace.setOnClickListener(v -> {
            // Check if last block is empty TEXT
            if (!blocks.isEmpty()) {
                NoteBlock lastBlock = blocks.get(blocks.size() - 1);

                // If last block is empty TEXT, just focus it
                if (lastBlock.getType() == NoteBlock.BlockType.TEXT &&
                        (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

                    focusBlock(blocks.size() - 1, 0);
                    return;
                }
            }

            // Otherwise, create new TEXT block
            addTextBlockFromEmptySpace();
        });
    }

    // ===============================================
// ✅ NEW METHOD: Add text block when clicking empty space
// ===============================================
    private void addTextBlockFromEmptySpace() {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.TEXT);
        block.setPosition(blocks.size());
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);

        // Auto-focus the new block
        blocksRecycler.post(() -> {
            blocksRecycler.smoothScrollToPosition(blocks.size() - 1);
            focusBlock(blocks.size() - 1, 0);
        });
    }

    private boolean tryReplaceLastEmptyTextBlock(NoteBlock.BlockType newType) {
        if (blocks.isEmpty()) {
            return false;
        }

        NoteBlock lastBlock = blocks.get(blocks.size() - 1);

        // Check if last block is an empty TEXT block
        if (lastBlock.getType() == NoteBlock.BlockType.TEXT &&
                (lastBlock.getContent() == null || lastBlock.getContent().trim().isEmpty())) {

            // Delete the old empty text block from Firestore
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .collection("blocks").document(lastBlock.getId())
                        .delete();
            }

            // Remove from list
            int position = blocks.size() - 1;
            blocks.remove(position);

            // Create new block with the desired type
            NoteBlock newBlock = new NoteBlock(System.currentTimeMillis() + "", newType);
            newBlock.setPosition(position);

            // Set default values based on type
            switch (newType) {
                case NUMBERED:
                    newBlock.setListNumber(1);
                    break;
                case DIVIDER:
                    newBlock.setDividerStyle("solid");
                    break;
                case CHECKBOX:
                    newBlock.setChecked(false);
                    break;
            }

            // Add new block at same position
            blocks.add(position, newBlock);
            adapter.notifyItemChanged(position);
            saveBlock(newBlock);

            // Renumber lists if needed
            if (newType == NoteBlock.BlockType.NUMBERED) {
                renumberLists();
            }

            // Focus the replaced block
            focusBlock(position, 0);

            return true; // Successfully replaced
        }

        return false; // No empty text block to replace
    }

    // ✅ REPLACE ang showHeadingOptions() method sa NoteActivity.java with this:

    private void showHeadingOptions() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.headings_fonts_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Get all option views
        LinearLayout heading1Option = sheetView.findViewById(R.id.heading1Option);
        LinearLayout heading2Option = sheetView.findViewById(R.id.heading2Option);
        LinearLayout heading3Option = sheetView.findViewById(R.id.heading3Option);
        LinearLayout boldOption = sheetView.findViewById(R.id.boldOption);
        LinearLayout italicOption = sheetView.findViewById(R.id.italicOption);
        LinearLayout boldItalicOption = sheetView.findViewById(R.id.boldItalicOption);
        LinearLayout normalOption = sheetView.findViewById(R.id.normalOption);

        // ✅ Font color options
        LinearLayout fontColorDefault = sheetView.findViewById(R.id.fontColorDefault);
        LinearLayout fontColorRed = sheetView.findViewById(R.id.fontColorRed);
        LinearLayout fontColorOrange = sheetView.findViewById(R.id.fontColorOrange);
        LinearLayout fontColorYellow = sheetView.findViewById(R.id.fontColorYellow);
        LinearLayout fontColorGreen = sheetView.findViewById(R.id.fontColorGreen);
        LinearLayout fontColorBlue = sheetView.findViewById(R.id.fontColorBlue);
        LinearLayout fontColorPurple = sheetView.findViewById(R.id.fontColorPurple);
        LinearLayout fontColorPink = sheetView.findViewById(R.id.fontColorPink);
        LinearLayout fontColorBrown = sheetView.findViewById(R.id.fontColorBrown);
        LinearLayout fontColorGray = sheetView.findViewById(R.id.fontColorGray);

        // ✅ HEADING OPTIONS
        if (heading1Option != null) {
            heading1Option.setOnClickListener(v -> {
                addHeadingBlock(NoteBlock.BlockType.HEADING_1);
                bottomSheet.dismiss();
            });
        }

        if (heading2Option != null) {
            heading2Option.setOnClickListener(v -> {
                addHeadingBlock(NoteBlock.BlockType.HEADING_2);
                bottomSheet.dismiss();
            });
        }

        if (heading3Option != null) {
            heading3Option.setOnClickListener(v -> {
                addHeadingBlock(NoteBlock.BlockType.HEADING_3);
                bottomSheet.dismiss();
            });
        }

        // ✅ FONT STYLE OPTIONS
        if (boldOption != null) {
            boldOption.setOnClickListener(v -> {
                applyFontStyle("bold");
                bottomSheet.dismiss();
            });
        }

        if (italicOption != null) {
            italicOption.setOnClickListener(v -> {
                applyFontStyle("italic");
                bottomSheet.dismiss();
            });
        }

        if (boldItalicOption != null) {
            boldItalicOption.setOnClickListener(v -> {
                applyFontStyle("boldItalic");
                bottomSheet.dismiss();
            });
        }

        if (normalOption != null) {
            normalOption.setOnClickListener(v -> {
                convertToNormalText();
                bottomSheet.dismiss();
            });
        }

        // ✅ FONT COLOR OPTIONS
        if (fontColorDefault != null) {
            fontColorDefault.setOnClickListener(v -> {
                applyFontColor("#333333");
                bottomSheet.dismiss();
            });
        }

        if (fontColorRed != null) {
            fontColorRed.setOnClickListener(v -> {
                applyFontColor("#E53935");
                bottomSheet.dismiss();
            });
        }

        if (fontColorOrange != null) {
            fontColorOrange.setOnClickListener(v -> {
                applyFontColor("#FB8C00");
                bottomSheet.dismiss();
            });
        }

        if (fontColorYellow != null) {
            fontColorYellow.setOnClickListener(v -> {
                applyFontColor("#FDD835");
                bottomSheet.dismiss();
            });
        }

        if (fontColorGreen != null) {
            fontColorGreen.setOnClickListener(v -> {
                applyFontColor("#43A047");
                bottomSheet.dismiss();
            });
        }

        if (fontColorBlue != null) {
            fontColorBlue.setOnClickListener(v -> {
                applyFontColor("#1E88E5");
                bottomSheet.dismiss();
            });
        }

        if (fontColorPurple != null) {
            fontColorPurple.setOnClickListener(v -> {
                applyFontColor("#8E24AA");
                bottomSheet.dismiss();
            });
        }

        if (fontColorPink != null) {
            fontColorPink.setOnClickListener(v -> {
                applyFontColor("#D81B60");
                bottomSheet.dismiss();
            });
        }

        if (fontColorBrown != null) {
            fontColorBrown.setOnClickListener(v -> {
                applyFontColor("#6D4C41");
                bottomSheet.dismiss();
            });
        }

        if (fontColorGray != null) {
            fontColorGray.setOnClickListener(v -> {
                applyFontColor("#757575");
                bottomSheet.dismiss();
            });
        }

        bottomSheet.show();
    }

    // ✅ NEW METHOD: Apply font color to current block
    private void applyFontColor(String color) {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView.ViewHolder holder = blocksRecycler.findContainingViewHolder(focusedView);
        if (holder == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= blocks.size()) {
            return;
        }

        NoteBlock block = blocks.get(position);

        // Set the font color
        block.setFontColor(color);

        adapter.notifyItemChanged(position);
        saveBlock(block);

        // Show color name
        String colorName = getColorName(color);
        Toast.makeText(this, "Color applied: " + colorName, Toast.LENGTH_SHORT).show();

        // Refocus the block
        focusBlock(position, block.getContent().length());
    }

    // ✅ NEW METHOD: Get human-readable color name
    private String getColorName(String hexColor) {
        switch (hexColor) {
            case "#333333": return "Default";
            case "#E53935": return "Red";
            case "#FB8C00": return "Orange";
            case "#FDD835": return "Yellow";
            case "#43A047": return "Green";
            case "#1E88E5": return "Blue";
            case "#8E24AA": return "Purple";
            case "#D81B60": return "Pink";
            case "#6D4C41": return "Brown";
            case "#757575": return "Gray";
            default: return "Custom";
        }
    }
    private void applyFontStyle(String style) {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        RecyclerView.ViewHolder holder = blocksRecycler.findContainingViewHolder(focusedView);
        if (holder == null) {
            Toast.makeText(this, "No block selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= blocks.size()) {
            return;
        }

        NoteBlock block = blocks.get(position);

        // ✅ SIMPLE: Just set the style directly
        block.setFontStyle(style);

        adapter.notifyItemChanged(position);
        saveBlock(block);

        Toast.makeText(this, "Style applied: " + style, Toast.LENGTH_SHORT).show();

        // Refocus the block
        focusBlock(position, block.getContent().length());
    }

    // ✅ UPDATE convertToNormalText to use fontStyle field:
    private void convertToNormalText() {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            CustomToast.show(this, "No block selected", R.drawable.logo_noteify);
            return;
        }

        RecyclerView.ViewHolder holder = blocksRecycler.findContainingViewHolder(focusedView);
        if (holder == null) {
            CustomToast.show(this, "No block selected", R.drawable.logo_noteify);
            return;
        }

        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= blocks.size()) {
            return;
        }

        NoteBlock block = blocks.get(position);

        // Convert heading to text if needed
        if (block.getType() == NoteBlock.BlockType.HEADING_1 ||
                block.getType() == NoteBlock.BlockType.HEADING_2 ||
                block.getType() == NoteBlock.BlockType.HEADING_3) {
            block.setType(NoteBlock.BlockType.TEXT);
        }

        // Clear font style and reset color
        block.setFontStyle(null);
        block.setFontColor("#333333");

        adapter.notifyItemChanged(position);
        saveBlock(block);

        CustomToast.show(this, "Converted to normal text", R.drawable.logo_noteify);

        // ✅ USE focusBlock with content length
        focusBlock(position, block.getContent() != null ? block.getContent().length() : 0);
    }

    // ====================================================
// BOOKMARK FEATURE
// ====================================================

    public void showBookmarkBottomSheet(String selectedText, String blockId, int startIndex, int endIndex) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bookmark_bottom_sheet, null);
        bottomSheet.setContentView(sheetView);

        // Color options
        View colorViolet = sheetView.findViewById(R.id.colorViolet);
        View colorYellow = sheetView.findViewById(R.id.colorYellow);
        View colorPink = sheetView.findViewById(R.id.colorPink);
        View colorGreen = sheetView.findViewById(R.id.colorGreen);
        View colorBlue = sheetView.findViewById(R.id.colorBlue);
        View colorOrange = sheetView.findViewById(R.id.colorOrange);
        View colorRed = sheetView.findViewById(R.id.colorRed);
        View colorCyan = sheetView.findViewById(R.id.colorCyan);

        // Set tags for each color view
        colorViolet.setTag("#E1BEE7");
        colorYellow.setTag("#FFF9C4");
        colorPink.setTag("#F8BBD0");
        colorGreen.setTag("#C8E6C9");
        colorBlue.setTag("#BBDEFB");
        colorOrange.setTag("#FFE0B2");
        colorRed.setTag("#FFCDD2");
        colorCyan.setTag("#B2EBF2");

        // Style options
        TextView styleHighlight = sheetView.findViewById(R.id.styleHighlight);
        TextView styleUnderline = sheetView.findViewById(R.id.styleUnderline);

        com.google.android.material.textfield.TextInputEditText noteInput =
                sheetView.findViewById(R.id.bookmarkNoteInput);
        TextView cancelBtn = sheetView.findViewById(R.id.cancelBtn);
        TextView okBtn = sheetView.findViewById(R.id.okBtn);

        final String[] selectedColor = {"#FFF9C4"}; // Default yellow
        final String[] selectedStyle = {"highlight"}; // Default highlight

        // Set initial selection (yellow, highlight)
        setColorScale(colorViolet, colorYellow, colorPink, colorGreen, colorBlue,
                colorOrange, colorRed, colorCyan, selectedColor[0]);
        styleHighlight.setBackgroundResource(R.drawable.style_selected);
        styleHighlight.setTextColor(android.graphics.Color.parseColor("#ff9376"));

        // Color selection listeners
        View.OnClickListener colorListener = v -> {
            resetColorSelection(colorViolet, colorYellow, colorPink, colorGreen,
                    colorBlue, colorOrange, colorRed, colorCyan);
            v.setScaleX(1.2f);
            v.setScaleY(1.2f);
            selectedColor[0] = (String) v.getTag();
        };

        colorViolet.setOnClickListener(colorListener);
        colorYellow.setOnClickListener(colorListener);
        colorPink.setOnClickListener(colorListener);
        colorGreen.setOnClickListener(colorListener);
        colorBlue.setOnClickListener(colorListener);
        colorOrange.setOnClickListener(colorListener);
        colorRed.setOnClickListener(colorListener);
        colorCyan.setOnClickListener(colorListener);

        // Style selection
        styleHighlight.setOnClickListener(v -> {
            selectedStyle[0] = "highlight";
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#666666"));
        });

        styleUnderline.setOnClickListener(v -> {
            selectedStyle[0] = "underline";
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#666666"));
        });

        cancelBtn.setOnClickListener(v -> bottomSheet.dismiss());

        okBtn.setOnClickListener(v -> {
            String note = noteInput.getText().toString().trim();
            saveBookmark(selectedText, note, selectedColor[0], selectedStyle[0],
                    blockId, startIndex, endIndex);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    private void setColorScale(View violet, View yellow, View pink, View green,
                               View blue, View orange, View red, View cyan, String currentColor) {
        resetColorSelection(violet, yellow, pink, green, blue, orange, red, cyan);

        View selectedView = null;
        switch (currentColor) {
            case "#FFCDD2": selectedView = red; break;
            case "#F8BBD0": selectedView = pink; break;
            case "#E1BEE7": selectedView = violet; break;
            case "#BBDEFB": selectedView = blue; break;
            case "#B2EBF2": selectedView = cyan; break;
            case "#C8E6C9": selectedView = green; break;
            case "#FFF9C4": selectedView = yellow; break;
            case "#FFE0B2": selectedView = orange; break;
        }

        if (selectedView != null) {
            selectedView.setScaleX(1.2f);
            selectedView.setScaleY(1.2f);
        }
    }

    private void resetColorSelection(View... views) {
        for (View v : views) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
        }
    }

    private void saveBookmark(String text, String note, String color, String style,
                              String blockId, int startIndex, int endIndex) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String bookmarkId = db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document().getId();

        Bookmark bookmark = new Bookmark(text, note, color, style, startIndex, endIndex);
        bookmark.setId(bookmarkId);
        bookmark.setBlockId(blockId);

        Map<String, Object> bookmarkData = new HashMap<>();
        bookmarkData.put("text", text);
        bookmarkData.put("note", note);
        bookmarkData.put("color", color);
        bookmarkData.put("style", style);
        bookmarkData.put("startIndex", startIndex);
        bookmarkData.put("endIndex", endIndex);
        bookmarkData.put("blockId", blockId);
        bookmarkData.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmarkId)
                .set(bookmarkData)
                .addOnSuccessListener(aVoid -> {
                    // ✅ IMMEDIATELY update local map
                    if (!blockBookmarksMap.containsKey(blockId)) {
                        blockBookmarksMap.put(blockId, new ArrayList<>());
                    }
                    blockBookmarksMap.get(blockId).add(bookmark);

                    // ✅ Update adapter with new bookmarks
                    adapter.updateBookmarks(blockBookmarksMap);

                    // ✅ Find and refresh only the affected block
                    for (int i = 0; i < blocks.size(); i++) {
                        if (blocks.get(i).getId().equals(blockId)) {
                            adapter.notifyItemChanged(i);
                            break;
                        }
                    }

                    Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error saving bookmark", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadBookmarksForNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || noteId == null) return;

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }

                    if (value != null) {
                        // Clear existing bookmarks
                        blockBookmarksMap.clear();

                        // Group bookmarks by blockId
                        for (QueryDocumentSnapshot doc : value) {
                            Bookmark bookmark = doc.toObject(Bookmark.class);
                            bookmark.setId(doc.getId());

                            String blockId = bookmark.getBlockId();
                            if (blockId != null) {
                                if (!blockBookmarksMap.containsKey(blockId)) {
                                    blockBookmarksMap.put(blockId, new ArrayList<>());
                                }
                                blockBookmarksMap.get(blockId).add(bookmark);
                            }
                        }

                        // Refresh adapter to show bookmarks
                        adapter.updateBookmarks(blockBookmarksMap);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void forceEnableTextSelection() {
        blocksRecycler.post(() -> {
            // Loop through all visible ViewHolders
            for (int i = 0; i < blocksRecycler.getChildCount(); i++) {
                View child = blocksRecycler.getChildAt(i);
                RecyclerView.ViewHolder holder = blocksRecycler.getChildViewHolder(child);

                // Find the EditText in the ViewHolder
                EditText editText = child.findViewById(R.id.contentEdit);
                if (editText != null) {
                    // Force enable selection
                    editText.setEnabled(true);
                    editText.setFocusable(true);
                    editText.setFocusableInTouchMode(true);
                    editText.setLongClickable(true);
                    editText.setClickable(true);
                    editText.setCursorVisible(true);

                    // ✅ CRITICAL: Request layout to refresh state
                    editText.requestLayout();
                }
            }
        });
    }

    private void scrollToBookmark(int blockPosition, int textPosition) {
        if (blockPosition < 0 || blockPosition >= blocks.size()) {
            android.util.Log.e("NoteActivity", "Invalid block position: " + blockPosition);
            return;
        }

        android.util.Log.d("NoteActivity", "Scrolling to block " + blockPosition + ", text pos " + textPosition);

        // ✅ Step 1: Scroll RecyclerView to position
        blocksRecycler.post(() -> {
            try {
                blocksRecycler.smoothScrollToPosition(blockPosition);

                // ✅ Step 2: Wait for scroll, then focus and set selection
                blocksRecycler.postDelayed(() -> {
                    RecyclerView.ViewHolder holder = blocksRecycler.findViewHolderForAdapterPosition(blockPosition);

                    if (holder != null) {
                        View itemView = holder.itemView;
                        EditText editText = itemView.findViewById(R.id.contentEdit);

                        if (editText != null) {
                            android.util.Log.d("NoteActivity", "Found EditText, requesting focus");

                            // Request focus
                            editText.requestFocus();

                            // Set selection (cursor position)
                            int safePosition = Math.min(textPosition, editText.getText().length());
                            safePosition = Math.max(0, safePosition);
                            editText.setSelection(safePosition);

                            // Show keyboard
                            android.view.inputmethod.InputMethodManager imm =
                                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                            }

                            // Highlight the bookmark briefly
                            editText.postDelayed(() -> {
                                adapter.notifyItemChanged(blockPosition);
                            }, 200);

                            android.util.Log.d("NoteActivity", "Focus and selection applied");
                        } else {
                            android.util.Log.e("NoteActivity", "EditText not found in holder");
                        }
                    } else {
                        android.util.Log.e("NoteActivity", "ViewHolder not found, retrying...");

                        // Retry once if holder not found
                        blocksRecycler.postDelayed(() -> {
                            RecyclerView.ViewHolder retryHolder = blocksRecycler.findViewHolderForAdapterPosition(blockPosition);
                            if (retryHolder != null) {
                                View itemView = retryHolder.itemView;
                                EditText editText = itemView.findViewById(R.id.contentEdit);
                                if (editText != null) {
                                    editText.requestFocus();
                                    int safePosition = Math.min(textPosition, editText.getText().length());
                                    editText.setSelection(Math.max(0, safePosition));
                                }
                            }
                        }, 500);
                    }
                }, 400); // Delay to ensure scroll completes

            } catch (Exception e) {
                android.util.Log.e("NoteActivity", "Error scrolling to bookmark", e);
                e.printStackTrace();
            }
        });
    }
    public Bookmark getBookmarkAtSelection(String blockId, int start, int end) {
        List<Bookmark> bookmarks = blockBookmarksMap.get(blockId);
        if (bookmarks == null) return null;

        for (Bookmark bookmark : bookmarks) {
            int bStart = bookmark.getStartIndex();
            int bEnd = bookmark.getEndIndex();

            // Check if selection overlaps with bookmark
            if ((start >= bStart && start < bEnd) ||
                    (end > bStart && end <= bEnd) ||
                    (start <= bStart && end >= bEnd)) {
                return bookmark;
            }
        }
        return null;
    }

    // ✅ ADD: Helper method to restore focus and cursor
    private void restoreFocusToBlock(int position, int cursorPosition) {
        blocksRecycler.postDelayed(() -> {
            RecyclerView.ViewHolder holder = blocksRecycler.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView != null) {
                EditText editText = holder.itemView.findViewById(R.id.contentEdit);
                if (editText != null) {
                    editText.requestFocus();

                    // Set cursor position safely
                    int safePosition = Math.min(cursorPosition, editText.getText().length());
                    safePosition = Math.max(0, safePosition);
                    editText.setSelection(safePosition);

                    // Show keyboard
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        }, 150); // Increased delay to ensure adapter refresh is complete
    }

    // ✅ UPDATE: expandBookmark method
    public void expandBookmark(Bookmark bookmark, String blockId, int newStart, int newEnd) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Get the block
        NoteBlock block = null;
        int blockPosition = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                block = blocks.get(i);
                blockPosition = i;
                break;
            }
        }

        if (block == null) return;

        String content = block.getContent();
        if (content == null) return;

        int expandedStart = Math.min(bookmark.getStartIndex(), newStart);
        int expandedEnd = Math.max(bookmark.getEndIndex(), newEnd);

        if (expandedStart < 0 || expandedEnd > content.length() || expandedStart >= expandedEnd) {
            Toast.makeText(this, "Invalid selection range", Toast.LENGTH_SHORT).show();
            return;
        }

        // Trim whitespace
        while (expandedStart < expandedEnd && expandedStart < content.length()
                && Character.isWhitespace(content.charAt(expandedStart))) {
            expandedStart++;
        }
        while (expandedEnd > expandedStart && expandedEnd > 0
                && Character.isWhitespace(content.charAt(expandedEnd - 1))) {
            expandedEnd--;
        }

        String expandedText = content.substring(expandedStart, expandedEnd);

        if (expandedText.trim().isEmpty()) {
            Toast.makeText(this, "Cannot bookmark empty text", Toast.LENGTH_SHORT).show();
            return;
        }

        final int finalStart = expandedStart;
        final int finalEnd = expandedEnd;
        final String finalText = expandedText;
        final int finalBlockPosition = blockPosition;

        Map<String, Object> updates = new HashMap<>();
        updates.put("startIndex", finalStart);
        updates.put("endIndex", finalEnd);
        updates.put("text", finalText);

        db.collection("users").document(user.getUid())
                .collection("notes").document(noteId)
                .collection("bookmarks").document(bookmark.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    bookmark.setStartIndex(finalStart);
                    bookmark.setEndIndex(finalEnd);
                    bookmark.setText(finalText);

                    adapter.notifyItemChanged(finalBlockPosition);

                    // ✅ FIX: Restore focus to the expanded text
                    restoreFocusToBlock(finalBlockPosition, finalEnd);

                    Toast.makeText(this, "Bookmark expanded", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error expanding bookmark", Toast.LENGTH_SHORT).show();
                });
    }
    public void showUpdateBookmarkBottomSheet(Bookmark bookmark, int blockPosition) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bookmark_bottom_sheet_update, null);
        bottomSheet.setContentView(sheetView);

        // Color options
        View colorViolet = sheetView.findViewById(R.id.colorViolet);
        View colorYellow = sheetView.findViewById(R.id.colorYellow);
        View colorPink = sheetView.findViewById(R.id.colorPink);
        View colorGreen = sheetView.findViewById(R.id.colorGreen);
        View colorBlue = sheetView.findViewById(R.id.colorBlue);
        View colorOrange = sheetView.findViewById(R.id.colorOrange);
        View colorRed = sheetView.findViewById(R.id.colorRed);
        View colorCyan = sheetView.findViewById(R.id.colorCyan);

        colorViolet.setTag("#E1BEE7");
        colorYellow.setTag("#FFF9C4");
        colorPink.setTag("#F8BBD0");
        colorGreen.setTag("#C8E6C9");
        colorBlue.setTag("#BBDEFB");
        colorOrange.setTag("#FFE0B2");
        colorRed.setTag("#FFCDD2");
        colorCyan.setTag("#B2EBF2");

        TextView styleHighlight = sheetView.findViewById(R.id.styleHighlight);
        TextView styleUnderline = sheetView.findViewById(R.id.styleUnderline);
        com.google.android.material.textfield.TextInputEditText noteInput = sheetView.findViewById(R.id.bookmarkNoteInput);
        TextView updateBtn = sheetView.findViewById(R.id.updateBtn);
        TextView deleteBtn = sheetView.findViewById(R.id.deleteBtn);

        final String[] selectedColor = {bookmark.getColor()};
        final String[] selectedStyle = {bookmark.getStyle()};

        // Pre-fill note
        noteInput.setText(bookmark.getNote());

        // Set initial selections
        setColorScale(colorViolet, colorYellow, colorPink, colorGreen, colorBlue, colorOrange, colorRed, colorCyan, selectedColor[0]);

        if ("highlight".equals(selectedStyle[0])) {
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#666666"));
        } else {
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#666666"));
        }

        // Color selection listeners
        View.OnClickListener colorListener = v -> {
            resetColorSelection(colorViolet, colorYellow, colorPink, colorGreen, colorBlue, colorOrange, colorRed, colorCyan);
            v.setScaleX(1.2f);
            v.setScaleY(1.2f);
            selectedColor[0] = (String) v.getTag();
        };

        colorViolet.setOnClickListener(colorListener);
        colorYellow.setOnClickListener(colorListener);
        colorPink.setOnClickListener(colorListener);
        colorGreen.setOnClickListener(colorListener);
        colorBlue.setOnClickListener(colorListener);
        colorOrange.setOnClickListener(colorListener);
        colorRed.setOnClickListener(colorListener);
        colorCyan.setOnClickListener(colorListener);

        // Style selection
        styleHighlight.setOnClickListener(v -> {
            selectedStyle[0] = "highlight";
            styleHighlight.setBackgroundResource(R.drawable.style_selected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleUnderline.setBackgroundResource(R.drawable.style_unselected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#666666"));
        });

        styleUnderline.setOnClickListener(v -> {
            selectedStyle[0] = "underline";
            styleUnderline.setBackgroundResource(R.drawable.style_selected);
            styleUnderline.setTextColor(android.graphics.Color.parseColor("#ff9376"));
            styleHighlight.setBackgroundResource(R.drawable.style_unselected);
            styleHighlight.setTextColor(android.graphics.Color.parseColor("#666666"));
        });

        updateBtn.setOnClickListener(v -> {
            String newNote = noteInput.getText().toString().trim();

            // Update color, style, and note in Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("color", selectedColor[0]);
            updates.put("style", selectedStyle[0]);
            updates.put("note", newNote);

            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid())
                        .collection("notes").document(noteId)
                        .collection("bookmarks").document(bookmark.getId())
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            // ✅ Update the local bookmark object immediately
                            bookmark.setColor(selectedColor[0]);
                            bookmark.setStyle(selectedStyle[0]);
                            bookmark.setNote(newNote);

                            adapter.notifyItemChanged(blockPosition);

                            // ✅ FIX: Restore focus after update
                            restoreFocusToBlock(blockPosition, bookmark.getEndIndex());

                            Toast.makeText(this, "Bookmark updated", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Error updating bookmark", Toast.LENGTH_SHORT).show();
                        });
            }

            bottomSheet.dismiss();
        });

        deleteBtn.setOnClickListener(v -> {
            showDeleteBookmarkConfirmation(bookmark, blockPosition);
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }
    // ✅ UPDATE: showDeleteBookmarkConfirmation method
    public void showDeleteBookmarkConfirmation(Bookmark bookmark, int blockPosition) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Bookmark")
                .setMessage("Are you sure you want to delete this bookmark?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        db.collection("users").document(user.getUid())
                                .collection("notes").document(noteId)
                                .collection("bookmarks").document(bookmark.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    adapter.notifyItemChanged(blockPosition);

                                    // ✅ FIX: Restore focus after delete
                                    restoreFocusToBlock(blockPosition, bookmark.getStartIndex());

                                    Toast.makeText(this, "Bookmark deleted", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

}