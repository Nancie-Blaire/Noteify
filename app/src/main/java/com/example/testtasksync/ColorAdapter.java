package com.example.testtasksync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ColorViewHolder> {
    private List<ColorOption> colorOptions;
    private OnColorSelectedListener listener;

    public interface OnColorSelectedListener {
        void onColorSelected(ColorOption colorOption);
    }

    public ColorAdapter(List<ColorOption> colorOptions, OnColorSelectedListener listener) {
        this.colorOptions = colorOptions;
        this.listener = listener;
    }

    @Override
    public ColorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color_option, parent, false);
        return new ColorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ColorViewHolder holder, int position) {
        ColorOption option = colorOptions.get(position);
        holder.bind(option);
    }

    @Override
    public int getItemCount() {
        return colorOptions.size();
    }

    class ColorViewHolder extends RecyclerView.ViewHolder {
        View colorPreview;
        TextView tvColorName;
        ImageView ivCheck;

        ColorViewHolder(View itemView) {
            super(itemView);
            colorPreview = itemView.findViewById(R.id.colorPreview);
            tvColorName = itemView.findViewById(R.id.tvColorName);
            ivCheck = itemView.findViewById(R.id.ivCheck);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Unselect all
                    for (ColorOption option : colorOptions) {
                        option.setSelected(false);
                    }
                    // Select current
                    colorOptions.get(position).setSelected(true);
                    notifyDataSetChanged();
                    listener.onColorSelected(colorOptions.get(position));
                }
            });
        }

        void bind(ColorOption option) {
            colorPreview.setBackgroundColor(option.getColor());
            tvColorName.setText(option.getName());
            ivCheck.setVisibility(option.isSelected() ? View.VISIBLE : View.GONE);
        }
    }
}
