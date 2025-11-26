package com.example.testtasksync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class AccountSwitchAdapter extends RecyclerView.Adapter<AccountSwitchAdapter.AccountViewHolder> {

    private Context context;
    private List<AccountManager.SavedAccount> accounts;
    private String currentAccountEmail;
    private OnAccountClickListener listener;

    public interface OnAccountClickListener {
        void onAccountClick(AccountManager.SavedAccount account);
    }

    public AccountSwitchAdapter(Context context, List<AccountManager.SavedAccount> accounts,
                                String currentAccountEmail, OnAccountClickListener listener) {
        this.context = context;
        this.accounts = accounts;
        this.currentAccountEmail = currentAccountEmail;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        AccountManager.SavedAccount account = accounts.get(position);

        // Set account name
        if (account.getDisplayName() != null && !account.getDisplayName().isEmpty()) {
            holder.tvAccountName.setText(account.getDisplayName());
        } else {
            // Use email username as fallback
            String email = account.getEmail();
            if (email.contains("@")) {
                holder.tvAccountName.setText(email.split("@")[0]);
            } else {
                holder.tvAccountName.setText("User");
            }
        }

        // Set email
        holder.tvAccountEmail.setText(account.getEmail());

        // Load profile picture
        if (account.getPhotoUrl() != null && !account.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(account.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_settings_account)
                    .error(R.drawable.ic_settings_account)
                    .into(holder.ivAccountAvatar);
        } else {
            holder.ivAccountAvatar.setImageResource(R.drawable.ic_settings_account);
        }

        // Show badge if this is the current account
        if (account.getEmail().equals(currentAccountEmail)) {
            holder.ivCurrentBadge.setVisibility(View.VISIBLE);
        } else {
            holder.ivCurrentBadge.setVisibility(View.GONE);
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountClick(account);
            }
        });
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAccountAvatar, ivCurrentBadge;
        TextView tvAccountName, tvAccountEmail;

        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAccountAvatar = itemView.findViewById(R.id.ivAccountAvatar);
            ivCurrentBadge = itemView.findViewById(R.id.ivCurrentBadge);
            tvAccountName = itemView.findViewById(R.id.tvAccountName);
            tvAccountEmail = itemView.findViewById(R.id.tvAccountEmail);
        }
    }
}