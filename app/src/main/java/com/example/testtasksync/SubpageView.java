package com.example.testtasksync;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SubpageView extends LinearLayout {

    private Subpage subpageData;
    private TextView titleText;
    private ImageView iconView;
    private ImageView arrowView;

    public SubpageView(Context context) {
        super(context);
        init();
    }

    public SubpageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // ✅ Inflate the existing item_subpage.xml layout
        LayoutInflater.from(getContext()).inflate(R.layout.item_subpage, this, true);

        // ✅ Find views from the inflated layout
        titleText = findViewById(R.id.subpageTitle);
        iconView = findViewById(R.id.subpageIcon);

        // ✅ Set click listener on the whole view
        setOnClickListener(v -> openSubpage());
    }

    public void setSubpageData(Subpage subpage) {
        this.subpageData = subpage;

        // ✅ Update title
        if (subpage.getTitle() != null && !subpage.getTitle().isEmpty()) {
            titleText.setText(subpage.getTitle());
        } else {
            titleText.setText("");
        }
    }

    public Subpage getSubpageData() {
        return subpageData;
    }

    private void openSubpage() {
        if (subpageData != null) {
            Intent intent = new Intent(getContext(), SubpageActivity.class);
            intent.putExtra("noteId", subpageData.getParentNoteId());
            intent.putExtra("subpageId", subpageData.getId());
            getContext().startActivity(intent);
        }
    }
    //go lia
}