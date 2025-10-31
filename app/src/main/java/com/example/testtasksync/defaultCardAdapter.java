package com.example.testtasksync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class defaultCardAdapter extends RecyclerView.Adapter<defaultCardAdapter.WelcomeViewHolder> {
    private boolean isGridLayout;

    public defaultCardAdapter(boolean isGridLayout) {
        this.isGridLayout = isGridLayout;
    }

    @NonNull
    @Override
    public WelcomeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isGridLayout ?
                R.layout.fav_default_notes :
                R.layout.recent_default_note;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new WelcomeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WelcomeViewHolder holder, int position) {
        // Nothing to bind for static welcome card
    }

    @Override
    public int getItemCount() {
        return 1; // Only one welcome card
    }

    static class WelcomeViewHolder extends RecyclerView.ViewHolder {
        public WelcomeViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}