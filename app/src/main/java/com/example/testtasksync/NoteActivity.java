package com.example.testtasksync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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

//COLOR PICKER
import android.graphics.Color;

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
            loadNote();
            loadNoteColor(); // ✅ Load saved color
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

        // Theme
        addThemeBtn.setOnClickListener(v -> toggleColorPicker());
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

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }


    private void createNewNote() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        noteId = db.collection("users").document(user.getUid())
                .collection("notes").document().getId();

        // ✅ UPDATE: Set noteId in adapter after creating it
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
        startActivityForResult(intent, REQUEST_SUBPAGE); // ✅ Use constant
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Only reload when coming back from a subpage
        if (requestCode == REQUEST_SUBPAGE) {
            if (noteId != null) {
                loadBlocks();
            }
        }
        // Gallery and Camera are handled by ActivityResultLauncher - no need to handle here
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