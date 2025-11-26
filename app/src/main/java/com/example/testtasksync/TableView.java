package com.example.testtasksync;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableView extends FrameLayout {
    private LinearLayout tableContainer;
    private LinearLayout toolbarContainer;
    private ImageView moreIcon;
    private boolean isExpanded = false;
    private int rows = 3;
    private int columns = 3;

    // Store current active dots
    private ImageView currentLeftDots;
    private ImageView currentTopDots;
    private Table tableData;
    private OnTableChangeListener changeListener;




    public interface OnTableChangeListener {
        void onTableChanged(Table table);
    }

    public TableView(Context context) {
        super(context);
        init(context);
    }

    public TableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.TRANSPARENT);

        // Create table container
        tableContainer = new LinearLayout(context);
        tableContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams tableParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        tableParams.topMargin = dpToPx(0);
        tableContainer.setLayoutParams(tableParams);
        tableContainer.setBackgroundColor(Color.TRANSPARENT);

        // Create toolbar - Only shows when table has focus
        toolbarContainer = new LinearLayout(context);
        toolbarContainer.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dpToPx(36)
        );
        toolbarParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        toolbarContainer.setLayoutParams(toolbarParams);

        GradientDrawable toolbarBg = new GradientDrawable();
        toolbarBg.setColor(Color.WHITE);
        toolbarBg.setCornerRadius(dpToPx(-40));
        toolbarContainer.setBackground(toolbarBg);
        toolbarContainer.setElevation(dpToPx(4));
        toolbarContainer.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        toolbarContainer.setVisibility(View.GONE); // Initially hidden

        // Only More icon
        moreIcon = new ImageView(context);
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                dpToPx(24), dpToPx(24)
        );
        moreIcon.setLayoutParams(moreParams);
        moreIcon.setImageResource(R.drawable.three_dots);
        moreIcon.setOnClickListener(v -> showTableOptionsBottomSheet());

        toolbarContainer.addView(moreIcon);

        addView(tableContainer);
        addView(toolbarContainer);

        createTable();
    }

    private void createTable() {
        tableContainer.removeAllViews();

        // Create wrapper for table with dots OUTSIDE
        FrameLayout tableWrapper = new FrameLayout(getContext());
        tableWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));

        // The actual table grid
        LinearLayout tableGrid = new LinearLayout(getContext());
        tableGrid.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        gridParams.leftMargin = dpToPx(20); // Space for left dots
        gridParams.topMargin = dpToPx(0);  // Space for top dots
        tableGrid.setLayoutParams(gridParams);

        for (int i = 0; i < rows; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
            ));

            for (int j = 0; j < columns; j++) {
                View cell = createCell(i, j);
                row.addView(cell);
            }

            tableGrid.addView(row);
        }

        tableWrapper.addView(tableGrid);
        tableContainer.addView(tableWrapper);

        // Add dots to the wrapper
        addDotsToWrapper(tableWrapper);
    }

    private void addDotsToWrapper(FrameLayout wrapper) {
        // Left dots (for rows) - Black dots without circle background
        currentLeftDots = new ImageView(getContext());
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(
                dpToPx(24), dpToPx(24)
        );
        leftParams.gravity = Gravity.START | Gravity.TOP;
        leftParams.leftMargin = -dpToPx(4);
        leftParams.topMargin = dpToPx(4);
        currentLeftDots.setLayoutParams(leftParams);

        // No background, just black icon
        currentLeftDots.setImageResource(R.drawable.six_dots_vertical);
        currentLeftDots.setColorFilter(Color.BLACK); // Make dots black
        currentLeftDots.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        currentLeftDots.setElevation(dpToPx(2));
        currentLeftDots.setVisibility(View.GONE);
        currentLeftDots.setClickable(true);
        currentLeftDots.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showRowActionsBottomSheet(0);
        });

        // Top dots (for columns) - Black dots without circle background
        currentTopDots = new ImageView(getContext());
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                dpToPx(24), dpToPx(24)
        );
        topParams.gravity = Gravity.TOP | Gravity.START;
        topParams.leftMargin = dpToPx(30);
        topParams.topMargin = -dpToPx(-28);
        currentTopDots.setLayoutParams(topParams);

        // No background, just black icon
        currentTopDots.setImageResource(R.drawable.six_dots_horizontal);
        currentTopDots.setColorFilter(Color.BLACK); // Make dots black
        currentTopDots.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        currentTopDots.setElevation(dpToPx(2));
        currentTopDots.setVisibility(View.GONE);
        currentTopDots.setClickable(true);
        currentTopDots.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showColumnActionsBottomSheet(0);
        });

        wrapper.addView(currentLeftDots);
        wrapper.addView(currentTopDots);
    }

    private View createCell(int rowIndex, int colIndex) {
        FrameLayout cellContainer = new FrameLayout(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT
        );
        params.weight = 1;
        cellContainer.setLayoutParams(params);

        GradientDrawable cellBorder = new GradientDrawable();
        cellBorder.setColor(Color.TRANSPARENT);
        cellBorder.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
        cellContainer.setBackground(cellBorder);

        EditText editText = new EditText(getContext());
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setGravity(Gravity.CENTER);
        editText.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        editText.setTextSize(14);
        editText.setTextColor(Color.BLACK);
        editText.setMinHeight(dpToPx(36));
        editText.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));

        // ✅ CRITICAL: Auto-save on text change
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                // ✅ Save cell content to table data
                if (tableData != null) {
                    tableData.setCellContent(rowIndex, colIndex, s.toString());
                    notifyTableChanged(); // This triggers Firestore save
                }
            }
        });

        cellContainer.addView(editText);

        return cellContainer;
    }
    private void showDots() {
        if (currentLeftDots != null) {
            currentLeftDots.setVisibility(View.VISIBLE);
        }
        if (currentTopDots != null) {
            currentTopDots.setVisibility(View.VISIBLE);
        }
        if (toolbarContainer != null) {
            toolbarContainer.setVisibility(View.VISIBLE);
        }
    }

    private void hideDots() {
        if (currentLeftDots != null) {
            currentLeftDots.setVisibility(View.GONE);
        }
        if (currentTopDots != null) {
            currentTopDots.setVisibility(View.GONE);
        }
        if (toolbarContainer != null) {
            toolbarContainer.setVisibility(View.GONE);
        }
    }

    private void showRowActionsBottomSheet(int rowIndex) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.bottom_sheet_row_actions, null
        );

        View colorOption = view.findViewById(R.id.colorOption);
        View insertAbove = view.findViewById(R.id.insertAbove);
        View insertBelow = view.findViewById(R.id.insertBelow);
        View duplicate = view.findViewById(R.id.duplicate);
        View clearContents = view.findViewById(R.id.clearContents);
        View delete = view.findViewById(R.id.delete);

        colorOption.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showColorPickerBottomSheet(true, rowIndex);
        });

        insertAbove.setOnClickListener(v -> {
            insertRowAt(rowIndex);
            bottomSheet.dismiss();
        });

        insertBelow.setOnClickListener(v -> {
            insertRowAt(rowIndex + 1);
            bottomSheet.dismiss();
        });

        duplicate.setOnClickListener(v -> {
            duplicateRow(rowIndex);
            bottomSheet.dismiss();
        });

        clearContents.setOnClickListener(v -> {
            clearRowContents(rowIndex);
            bottomSheet.dismiss();
        });

        delete.setOnClickListener(v -> {
            deleteRow(rowIndex);
            bottomSheet.dismiss();
        });

        bottomSheet.setContentView(view);
        bottomSheet.show();
    }

    private void showColumnActionsBottomSheet(int colIndex) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.bottom_sheet_column_actions, null
        );

        View colorOption = view.findViewById(R.id.colorOption);
        View insertLeft = view.findViewById(R.id.insertLeft);
        View insertRight = view.findViewById(R.id.insertRight);
        View duplicate = view.findViewById(R.id.duplicate);
        View clearContents = view.findViewById(R.id.clearContents);
        View delete = view.findViewById(R.id.delete);

        colorOption.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showColorPickerBottomSheet(false, colIndex);
        });

        insertLeft.setOnClickListener(v -> {
            insertColumnAt(colIndex);
            bottomSheet.dismiss();
        });

        insertRight.setOnClickListener(v -> {
            insertColumnAt(colIndex + 1);
            bottomSheet.dismiss();
        });

        duplicate.setOnClickListener(v -> {
            duplicateColumn(colIndex);
            bottomSheet.dismiss();
        });

        clearContents.setOnClickListener(v -> {
            clearColumnContents(colIndex);
            bottomSheet.dismiss();
        });

        delete.setOnClickListener(v -> {
            deleteColumn(colIndex);
            bottomSheet.dismiss();
        });

        bottomSheet.setContentView(view);
        bottomSheet.show();
    }

    private void showTableOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.table_options_bottom_sheet, null
        );

        View duplicate = view.findViewById(R.id.duplicate);
        View delete = view.findViewById(R.id.delete);

        duplicate.setOnClickListener(v -> {
            duplicateTable();
            bottomSheet.dismiss();
        });

        delete.setOnClickListener(v -> {
            deleteTable();
            bottomSheet.dismiss();
        });

        bottomSheet.setContentView(view);
        bottomSheet.show();
    }

    private void showColorPickerBottomSheet(boolean isRow, int index) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.table_color_bottom_sheet, null
        );

        int[] backgroundColors = {
                Color.WHITE, // Default
                Color.parseColor("#E0E0E0"), // Gray
                Color.parseColor("#BCAAA4"), // Brown
                Color.parseColor("#FFCC80"), // Orange
                Color.parseColor("#FFF59D"), // Yellow
                Color.parseColor("#C5E1A5"), // Green
                Color.parseColor("#B3E5FC"), // Blue
                Color.parseColor("#E1BEE7"), // Purple
                Color.parseColor("#F8BBD0"), // Pink
                Color.parseColor("#FFCDD2")  // Red
        };

        View defaultBg = view.findViewById(R.id.defaultBackground);
        View grayBg = view.findViewById(R.id.grayBackground);
        View brownBg = view.findViewById(R.id.brownBackground);
        View orangeBg = view.findViewById(R.id.orangeBackground);
        View yellowBg = view.findViewById(R.id.yellowBackground);
        View greenBg = view.findViewById(R.id.greenBackground);
        View blueBg = view.findViewById(R.id.blueBackground);
        View purpleBg = view.findViewById(R.id.purpleBackground);
        View pinkBg = view.findViewById(R.id.pinkBackground);
        View redBg = view.findViewById(R.id.redBackground);

        View[] colorOptions = {defaultBg, grayBg, brownBg, orangeBg, yellowBg,
                greenBg, blueBg, purpleBg, pinkBg, redBg};

        for (int i = 0; i < colorOptions.length; i++) {
            int color = backgroundColors[i];
            colorOptions[i].setOnClickListener(v -> {
                if (isRow) {
                    setRowColor(index, color);
                } else {
                    setColumnColor(index, color);
                }
                bottomSheet.dismiss();
            });
        }

        bottomSheet.setContentView(view);
        bottomSheet.show();
    }

    // Row operations
    private void insertRowAt(int position) {
        rows++;
        createTable();
        notifyTableChanged();
    }

    private void deleteRow(int rowIndex) {
        if (rows > 1) {
            rows--;
            createTable();
            notifyTableChanged();
        }
    }

    private void duplicateRow(int rowIndex) {
        rows++;
        createTable();
        notifyTableChanged();
    }

    private void clearRowContents(int rowIndex) {
        LinearLayout tableGrid = (LinearLayout) ((FrameLayout) tableContainer.getChildAt(0)).getChildAt(0);
        if (rowIndex < tableGrid.getChildCount()) {
            LinearLayout row = (LinearLayout) tableGrid.getChildAt(rowIndex);
            for (int i = 0; i < row.getChildCount(); i++) {
                FrameLayout cell = (FrameLayout) row.getChildAt(i);
                EditText editText = (EditText) cell.getChildAt(0);
                editText.setText("");
            }
        }
    }

    private void setRowColor(int rowIndex, int color) {
        LinearLayout tableGrid = (LinearLayout) ((FrameLayout) tableContainer.getChildAt(0)).getChildAt(0);
        if (rowIndex < tableGrid.getChildCount()) {
            LinearLayout row = (LinearLayout) tableGrid.getChildAt(rowIndex);
            for (int i = 0; i < row.getChildCount(); i++) {
                FrameLayout cell = (FrameLayout) row.getChildAt(i);
                GradientDrawable cellBorder = new GradientDrawable();
                cellBorder.setColor(color);
                cellBorder.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
                cell.setBackground(cellBorder);

                // ✅ ADD: Save color
                if (tableData != null) {
                    tableData.getCellColors().put(rowIndex + "-" + i, color);
                }
            }
        }
        notifyTableChanged(); // ✅ ADD
    }
    // Column operations
    private void insertColumnAt(int position) {
        columns++;
        createTable();
        notifyTableChanged();
    }

    private void deleteColumn(int colIndex) {
        if (columns > 1) {
            columns--;
            createTable();
            notifyTableChanged();
        }
    }

    private void duplicateColumn(int colIndex) {
        columns++;
        createTable();
        notifyTableChanged();
    }

    private void clearColumnContents(int colIndex) {
        LinearLayout tableGrid = (LinearLayout) ((FrameLayout) tableContainer.getChildAt(0)).getChildAt(0);
        for (int i = 0; i < tableGrid.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) tableGrid.getChildAt(i);
            if (colIndex < row.getChildCount()) {
                FrameLayout cell = (FrameLayout) row.getChildAt(colIndex);
                EditText editText = (EditText) cell.getChildAt(0);
                editText.setText("");
            }
        }
    }

    private void setColumnColor(int colIndex, int color) {
        LinearLayout tableGrid = (LinearLayout) ((FrameLayout) tableContainer.getChildAt(0)).getChildAt(0);
        for (int i = 0; i < tableGrid.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) tableGrid.getChildAt(i);
            if (colIndex < row.getChildCount()) {
                FrameLayout cell = (FrameLayout) row.getChildAt(colIndex);
                GradientDrawable cellBorder = new GradientDrawable();
                cellBorder.setColor(color);
                cellBorder.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
                cell.setBackground(cellBorder);

                // ✅ ADD: Save color
                if (tableData != null) {
                    tableData.getCellColors().put(i + "-" + colIndex, color);
                }
            }
        }
        notifyTableChanged(); // ✅ ADD
    }
    // Table operations
    private void duplicateTable() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            TableView newTable = new TableView(getContext());
            newTable.rows = this.rows;
            newTable.columns = this.columns;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dpToPx(16), 0, dpToPx(16));
            newTable.setLayoutParams(params);

            int currentIndex = parent.indexOfChild(this);
            parent.addView(newTable, currentIndex + 1);
        }
    }

    private void deleteTable() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    public void setTableData(Table table) {
        this.tableData = table;
        if (table != null) {
            this.rows = table.getRowCount();
            this.columns = table.getColumnCount();
            createTable();

            // Load data after a short delay to ensure table is created
            postDelayed(() -> loadTableData(), 100);
        }
    }



    public Table getTableData() {
        if (tableData == null) {
            tableData = new Table(0, rows, columns);
        }
        saveCurrentTableData();
        return tableData;
    }
    public void setOnTableChangeListener(OnTableChangeListener listener) {
        this.changeListener = listener;
    }

    private void notifyTableChanged() {
        if (changeListener != null && tableData != null) {
            saveCurrentTableData();
            changeListener.onTableChanged(tableData);
        }
    }

    private void saveCurrentTableData() {
        if (tableData == null) return;

        tableData.setRowCount(rows);
        tableData.setColumnCount(columns);

        // ✅ Use Map instead of List<List<String>>
        Map<String, String> contents = new HashMap<>();
        Map<String, Integer> colors = new HashMap<>();

        try {
            if (tableContainer.getChildCount() == 0) {
                Log.w("TableView", "No children in tableContainer");
                return;
            }

            FrameLayout tableWrapper = (FrameLayout) tableContainer.getChildAt(0);
            if (tableWrapper.getChildCount() == 0) {
                Log.w("TableView", "No children in tableWrapper");
                return;
            }

            LinearLayout tableGrid = (LinearLayout) tableWrapper.getChildAt(0);

            for (int i = 0; i < tableGrid.getChildCount(); i++) {
                View rowView = tableGrid.getChildAt(i);
                if (!(rowView instanceof LinearLayout)) continue;

                LinearLayout row = (LinearLayout) rowView;

                for (int j = 0; j < row.getChildCount(); j++) {
                    View cellView = row.getChildAt(j);
                    if (!(cellView instanceof FrameLayout)) continue;

                    FrameLayout cell = (FrameLayout) cellView;
                    if (cell.getChildCount() == 0) continue;

                    View childView = cell.getChildAt(0);
                    if (!(childView instanceof EditText)) continue;

                    EditText editText = (EditText) childView;
                    String content = editText.getText().toString();

                    // ✅ Save with "row-col" key format
                    contents.put(i + "-" + j, content);
                }
            }

            tableData.setCellContents(contents);
            tableData.setCellColors(colors);

        } catch (Exception e) {
            Log.e("TableView", "Error saving table data", e);
            e.printStackTrace();
        }
    }

    private void loadTableData() {
        if (tableData == null || tableData.getCellContents() == null) return;

        try {
            LinearLayout tableGrid = (LinearLayout) ((FrameLayout) tableContainer.getChildAt(0)).getChildAt(0);
            Map<String, String> contents = tableData.getCellContents();
            Map<String, Integer> colors = tableData.getCellColors();

            for (int i = 0; i < tableGrid.getChildCount(); i++) {
                LinearLayout row = (LinearLayout) tableGrid.getChildAt(i);

                for (int j = 0; j < row.getChildCount(); j++) {
                    FrameLayout cell = (FrameLayout) row.getChildAt(j);
                    EditText editText = (EditText) cell.getChildAt(0);

                    // ✅ Load using "row-col" key format
                    String key = i + "-" + j;
                    String content = contents.getOrDefault(key, "");
                    editText.setText(content);

                    if (colors.containsKey(key)) {
                        Integer color = colors.get(key);
                        GradientDrawable cellBorder = new GradientDrawable();
                        cellBorder.setColor(color);
                        cellBorder.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
                        cell.setBackground(cellBorder);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("TableView", "Error loading table data", e);
            e.printStackTrace();
        }
    }


}