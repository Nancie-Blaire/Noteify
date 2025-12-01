package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // Setup RecyclerView
        blocks = new ArrayList<>();
        adapter = new NoteBlockAdapter(blocks, this);
        blocksRecycler.setLayoutManager(new LinearLayoutManager(this));
        blocksRecycler.setAdapter(adapter);

        // Setup drag and drop
        setupDragAndDrop();

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

        // Setup listeners
        backBtn.setOnClickListener(v -> saveAndExit());
        setupKeyboardToolbar();

        // Show keyboard toolbar
        keyboardToolbar.setVisibility(View.VISIBLE);
    }

    private void setupKeyboardToolbar() {
        // Headings & Font
        headingsAndFont.setOnClickListener(v -> showHeadingOptions());

        // Divider
        addDividerBtn.setOnClickListener(v -> addDividerBlock());

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
        indentBtn.setOnClickListener(v ->
                Toast.makeText(this, "Indent - to be implemented", Toast.LENGTH_SHORT).show());

        // Outdent (to be implemented)
        outdentBtn.setOnClickListener(v ->
                Toast.makeText(this, "Outdent - to be implemented", Toast.LENGTH_SHORT).show());

        // Theme (to be implemented)
        addThemeBtn.setOnClickListener(v ->
                Toast.makeText(this, "Theme - to be implemented", Toast.LENGTH_SHORT).show());

        // Subpage
        addSubpageBtn.setOnClickListener(v -> addSubpageBlock());
    }

    private void showHeadingOptions() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Heading");

        String[] options = {
                "Normal Text",
                "Heading 1",
                "Heading 2",
                "Heading 3"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: addTextBlock(); break;
                case 1: addHeadingBlock(NoteBlock.BlockType.HEADING_1); break;
                case 2: addHeadingBlock(NoteBlock.BlockType.HEADING_2); break;
                case 3: addHeadingBlock(NoteBlock.BlockType.HEADING_3); break;
            }
        });

        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (noteId != null) {
            loadBlocks();
        }
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

    private void createNewNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        noteId = db.collection("users").document(user.getUid())
                .collection("notes").document().getId();

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
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);
    }

    private void addHeadingBlock(NoteBlock.BlockType headingType) {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", headingType);
        block.setPosition(blocks.size());
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);
    }

    private void addBulletBlock() {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.BULLET);
        block.setPosition(blocks.size());
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);
    }

    private void addNumberedBlock() {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.NUMBERED);
        block.setPosition(blocks.size());
        block.setListNumber(1);
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        renumberLists();
        saveBlock(block);
    }

    private void addCheckboxBlock() {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.CHECKBOX);
        block.setPosition(blocks.size());
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);
    }

    private void addDividerBlock() {
        NoteBlock block = new NoteBlock(System.currentTimeMillis() + "", NoteBlock.BlockType.DIVIDER);
        block.setPosition(blocks.size());
        block.setDividerStyle("solid");
        blocks.add(block);
        adapter.notifyItemInserted(blocks.size() - 1);
        saveBlock(block);
    }

    private void addImageBlock() {
        Toast.makeText(this, "Image picker - to be implemented", Toast.LENGTH_SHORT).show();
    }

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
        startActivityForResult(intent, 100);
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
        Toast.makeText(this, "Link dialog - to be implemented", Toast.LENGTH_SHORT).show();
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
        NoteBlock block = blocks.get(position);
        block.setType(newType);
        adapter.notifyItemChanged(position);
        saveBlock(block);
        renumberLists();
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
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
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
        Toast.makeText(this, "Divider options", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNoteTitle();
        saveAllBlocks();
    }
}