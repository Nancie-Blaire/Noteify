package com.example.testtasksync;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SubpageAdapter extends RecyclerView.Adapter<SubpageAdapter.SubpageViewHolder> {

    private Context context;
    private List<Subpage> subpageList;
    private String noteId;

    public SubpageAdapter(Context context, String noteId) {
        this.context = context;
        this.noteId = noteId;
        this.subpageList = new ArrayList<>();
    }

    public void setSubpages(List<Subpage> subpages) {
        this.subpageList = subpages;
        notifyDataSetChanged();
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    @NonNull
    @Override
    public SubpageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_subpage, parent, false);
        return new SubpageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubpageViewHolder holder, int position) {
        Subpage subpage = subpageList.get(position);

        // Get title and content
        String title = subpage.getTitle();
        String content = subpage.getContent();

        // If title is empty but content exists, show "New page"
        if ((title == null || title.trim().isEmpty()) &&
                content != null && !content.trim().isEmpty()) {
            holder.subpageTitle.setText("New page");
        } else if (title != null && !title.trim().isEmpty()) {
            holder.subpageTitle.setText(title);
        } else {
            // Both empty - show "Untitled"
            holder.subpageTitle.setText("Untitled");
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, SubpageActivity.class);
            intent.putExtra("noteId", noteId);
            intent.putExtra("subpageId", subpage.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return subpageList.size();
    }

    static class SubpageViewHolder extends RecyclerView.ViewHolder {
        ImageView subpageIcon;
        TextView subpageTitle;

        public SubpageViewHolder(@NonNull View itemView) {
            super(itemView);
            subpageIcon = itemView.findViewById(R.id.subpageIcon);
            subpageTitle = itemView.findViewById(R.id.subpageTitle);
        }
    }
    //go lia
}