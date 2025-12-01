package com.example.testtasksync;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BinAdapter extends RecyclerView.Adapter<BinAdapter.BinViewHolder> {

    private List<NotePreview> items;
    private Set<String> selectedIds;
    private OnSelectionChangeListener selectionListener;
    private SimpleDateFormat dateFormat;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }

    public BinAdapter(List<NotePreview> items, OnSelectionChangeListener listener) {
        this.items = items;
        this.selectedIds = new HashSet<>();
        this.selectionListener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public BinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bin, parent, false);
        return new BinViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BinViewHolder holder, int position) {
        NotePreview note = items.get(position);
        String noteId = note.getId();

        // Set title
        holder.title.setText(note.getTitle());

        // Set content preview
        String content = note.getContent();
        if (content != null && !content.isEmpty()) {
            holder.content.setText(content);
            holder.content.setVisibility(View.VISIBLE);
        } else {
            holder.content.setVisibility(View.GONE);
        }

        // Set deletion date
        String deletedText = "Deleted " + dateFormat.format(new Date(note.getDeletedAt()));
        holder.deletedDate.setText(deletedText);

        // Calculate days until permanent deletion
        long daysSinceDeletion = (System.currentTimeMillis() - note.getDeletedAt()) / (1000 * 60 * 60 * 24);
        long daysRemaining = 30 - daysSinceDeletion;

        if (daysRemaining > 0) {
            holder.daysRemaining.setText(daysRemaining + " days remaining");
            holder.daysRemaining.setTextColor(Color.parseColor("#757575"));
        } else {
            holder.daysRemaining.setText("Deleting soon");
            holder.daysRemaining.setTextColor(Color.parseColor("#D32F2F"));
        }

        // Handle selection state
        boolean isSelected = selectedIds.contains(noteId);
        holder.checkbox.setChecked(isSelected);

        // Highlight selected items
        if (isSelected) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            holder.cardView.setStrokeColor(Color.parseColor("#2196F3"));
            holder.cardView.setStrokeWidth(4);
        } else {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#BBDEFB"));
            holder.cardView.setStrokeColor(Color.parseColor("#E0E0E0"));
            holder.cardView.setStrokeWidth(2);
        }

        // Handle click - toggle selection
        View.OnClickListener toggleSelection = v -> {
            if (isSelected) {
                selectedIds.remove(noteId);
            } else {
                selectedIds.add(noteId);
            }
            notifyItemChanged(position);
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(selectedIds.size());
            }
        };

        holder.cardView.setOnClickListener(toggleSelection);
        holder.checkbox.setOnClickListener(toggleSelection);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public List<NotePreview> getSelectedItems() {
        List<NotePreview> selected = new ArrayList<>();
        for (NotePreview note : items) {
            if (selectedIds.contains(note.getId())) {
                selected.add(note);
            }
        }
        return selected;
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }

    static class BinViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        CheckBox checkbox;
        TextView title;
        TextView content;
        TextView deletedDate;
        TextView daysRemaining;

        BinViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            checkbox = itemView.findViewById(R.id.checkbox);
            title = itemView.findViewById(R.id.binItemTitle);
            content = itemView.findViewById(R.id.binItemContent);
            deletedDate = itemView.findViewById(R.id.deletedDate);
            daysRemaining = itemView.findViewById(R.id.daysRemaining);
        }
    }
}