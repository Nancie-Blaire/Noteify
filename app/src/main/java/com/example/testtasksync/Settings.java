package com.example.testtasksync;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

public class Settings extends Fragment {

    private LinearLayout userProfileIcon, btnTheme, btnSecutiy, btnBin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);


        btnBin = view.findViewById(R.id.btnBin);
        userProfileIcon = view.findViewById(R.id.userProfileIcon);
        btnTheme = view.findViewById(R.id.btnTheme);
        btnSecutiy = view.findViewById(R.id.btnSecurity);

        // Bin action (example)
        if (btnBin != null) {
            btnBin.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), Bin.class);
                startActivity(intent);
            });
        }

        // Profile -> open Account normally (NO flags)
        if (userProfileIcon != null) {
            userProfileIcon.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), Account.class);
                startActivity(intent); 
            });
        }

        // Theme action
        if (btnTheme != null) {
            btnTheme.setOnClickListener(v -> {
                // toggle theme or open settings
            });
        }

        // Security action
        if (btnSecutiy != null) {
            btnSecutiy.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SecuritySettingsActivity.class);
                startActivity(intent);
            });
        }

        return view;
    }
}
