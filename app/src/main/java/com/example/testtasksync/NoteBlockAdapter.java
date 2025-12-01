package com.example.testtasksync;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ProgressBar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class NoteBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<NoteBlock> blocks;
    private OnBlockChangeListener listener;
    private String noteId; // ✅ ADDED

    // View types
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
    }

    // ✅ FIXED: Constructor now accepts noteId

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

    // Move block up/down
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

        // Update positions
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).setPosition(i);
        }

        notifyItemMoved(fromPosition, toPosition);
    }

    // ViewHolder classes
    class TextViewHolder extends RecyclerView.ViewHolder {
        EditText contentEdit;

        TextViewHolder(View view) {
            super(view);
            contentEdit = view.findViewById(R.id.contentEdit);

            contentEdit.addTextChangedListener(new TextWatcher() {
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
            });
        }

        void bind(NoteBlock block) {
            contentEdit.setText(block.getContent());

            // Set dynamic hint based on position
            int pos = getAdapterPosition();
            if (pos == 0) {
                contentEdit.setHint("Enter here");
            } else if (pos > 0) {
                // Check if previous block is a subpage
                NoteBlock previousBlock = blocks.get(pos - 1);
                if (previousBlock.getType() == NoteBlock.BlockType.SUBPAGE) {
                    contentEdit.setHint("Continue here");
                } else {
                    contentEdit.setHint("Type something...");
                }
            }

            // Set indent
            int marginLeft = block.getIndentLevel() * 32;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) contentEdit.getLayoutParams();
            params.leftMargin = marginLeft;
            contentEdit.setLayoutParams(params);
        }
    }

    class HeadingViewHolder extends RecyclerView.ViewHolder {
        EditText contentEdit;

        HeadingViewHolder(View view) {
            super(view);
            contentEdit = view.findViewById(R.id.contentEdit);

            contentEdit.addTextChangedListener(new TextWatcher() {
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
            });
        }

        void bind(NoteBlock block) {
            contentEdit.setText(block.getContent());

            // Set heading size
            float textSize = 16f;
            switch (block.getType()) {
                case HEADING_1: textSize = 28f; break;
                case HEADING_2: textSize = 24f; break;
                case HEADING_3: textSize = 20f; break;
            }
            contentEdit.setTextSize(textSize);
        }
    }

    class BulletViewHolder extends RecyclerView.ViewHolder {
        TextView bulletIcon;
        EditText contentEdit;

        BulletViewHolder(View view) {
            super(view);
            bulletIcon = view.findViewById(R.id.bulletIcon);
            contentEdit = view.findViewById(R.id.contentEdit);

            contentEdit.addTextChangedListener(new TextWatcher() {
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
            });
        }

        void bind(NoteBlock block) {
            contentEdit.setText(block.getContent());

            // Set bullet style based on indent
            String bullet = "●";
            if (block.getIndentLevel() == 1) bullet = "○";
            else if (block.getIndentLevel() >= 2) bullet = "■";
            bulletIcon.setText(bullet);

            // Set indent
            int marginLeft = block.getIndentLevel() * 32;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);
        }
    }

    class NumberedViewHolder extends RecyclerView.ViewHolder {
        TextView numberText;
        EditText contentEdit;

        NumberedViewHolder(View view) {
            super(view);
            numberText = view.findViewById(R.id.numberText);
            contentEdit = view.findViewById(R.id.contentEdit);

            contentEdit.addTextChangedListener(new TextWatcher() {
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
            });
        }

        void bind(NoteBlock block) {
            contentEdit.setText(block.getContent());
            numberText.setText(block.getListNumber() + ".");

            // Set indent
            int marginLeft = block.getIndentLevel() * 32;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);
        }
    }

    class CheckboxViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkbox;
        EditText contentEdit;

        CheckboxViewHolder(View view) {
            super(view);
            checkbox = view.findViewById(R.id.checkbox);
            contentEdit = view.findViewById(R.id.contentEdit);

            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    block.setChecked(isChecked);
                    listener.onBlockChanged(block);
                }
            });

            contentEdit.addTextChangedListener(new TextWatcher() {
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
            });
        }

        void bind(NoteBlock block) {
            checkbox.setChecked(block.isChecked());
            contentEdit.setText(block.getContent());

            // Set indent
            int marginLeft = block.getIndentLevel() * 32;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = marginLeft;
            itemView.setLayoutParams(params);
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

            // Long press to delete
            imageView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showDeleteImageDialog(v, pos);
                }
                return true;
            });
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
            // Show size info
            if (block.getSizeKB() > 0) {
                imageSizeText.setVisibility(View.VISIBLE);
                imageSizeText.setText(block.getSizeKB() + " KB" + (block.isChunked() ? " (Large)" : ""));
            } else {
                imageSizeText.setVisibility(View.GONE);
            }

            // Check if image is chunked or inline
            if (block.isChunked()) {
                // Large image - load from chunks
                loadChunkedImage(block);
            } else {
                // Small image - load from base64Data in block
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

            // Get all chunks
            db.collection("users").document(user.getUid())
                    .collection("notes").document(noteId)
                    .collection("images").document(imageId)
                    .collection("chunks")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            // Sort chunks by index
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

                            // Combine chunks
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
        View dividerView;

        DividerViewHolder(View view) {
            super(view);
            dividerView = view.findViewById(R.id.dividerView);

            dividerView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onDividerClick(pos);
                }
            });
        }

        void bind(NoteBlock block) {
            // Apply divider style
            // This will be customized based on dividerStyle
        }
    }

    class SubpageViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;

        SubpageViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);

            // Regular click - open subpage
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    listener.onSubpageClick(block.getSubpageId());
                }
            });

            // Long press - show delete dialog
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

    class LinkViewHolder extends RecyclerView.ViewHolder {
        TextView linkText;

        LinkViewHolder(View view) {
            super(view);
            linkText = view.findViewById(R.id.linkText);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    NoteBlock block = blocks.get(pos);
                    listener.onLinkClick(block.getLinkUrl());
                }
            });
        }

        void bind(NoteBlock block) {
            linkText.setText(block.getContent());
        }
    }
}