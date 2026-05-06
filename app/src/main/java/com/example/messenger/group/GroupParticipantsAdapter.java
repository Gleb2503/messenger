package com.example.messenger.group;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.messenger.R;
import com.example.messenger.contacts.ContactItem;

import java.util.ArrayList;
import java.util.List;

public class GroupParticipantsAdapter extends RecyclerView.Adapter<GroupParticipantsAdapter.ViewHolder> {

    private final List<ContactItem> selectedContacts;
    private List<ContactItem> allContacts;
    private final OnContactSelectedListener listener;
    private final boolean showCheckBox;

    public interface OnContactSelectedListener {
        void onContactSelected(ContactItem contact);
    }

    public GroupParticipantsAdapter(List<ContactItem> selectedContacts, OnContactSelectedListener listener) {
        this(selectedContacts, listener, true);
    }

    public GroupParticipantsAdapter(List<ContactItem> selectedContacts, OnContactSelectedListener listener, boolean showCheckBox) {
        this.selectedContacts = selectedContacts != null ? selectedContacts : new ArrayList<>();
        this.allContacts = new ArrayList<>();
        this.listener = listener;
        this.showCheckBox = showCheckBox;
    }

    public void setAllContacts(List<ContactItem> allContacts) {
        this.allContacts = allContacts != null ? allContacts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_selectable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < allContacts.size()) {
            holder.bind(allContacts.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return allContacts != null ? allContacts.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final ImageView avatarImage;
        private final CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.contactName);
            avatarImage = itemView.findViewById(R.id.contactAvatar);
            checkBox = itemView.findViewById(R.id.checkBox);
        }

        public void bind(ContactItem contact) {
            if (contact == null) return;

            nameText.setText(contact.getDisplayName());

            String avatarUrl = contact.getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                avatarImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_avatar_placeholder)
                        .error(R.drawable.bg_avatar_placeholder)
                        .into(avatarImage);
            } else {
                avatarImage.setVisibility(View.GONE);
            }


            if (checkBox != null) {
                if (showCheckBox) {
                    checkBox.setVisibility(View.VISIBLE);
                    boolean isSelected = selectedContacts.contains(contact);
                    checkBox.setChecked(isSelected);
                } else {
                    checkBox.setVisibility(View.GONE);
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null && contact.getUserId() != null) {
                    listener.onContactSelected(contact);
                }
            });
        }
    }
}