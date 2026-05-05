package com.example.messenger.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.messenger.R;

public class ContactsAdapter extends ListAdapter<ContactItem, ContactsAdapter.ContactViewHolder> {
    private OnContactClickListener clickListener;

    public interface OnContactClickListener {
        void onContactClick(ContactItem contact);
    }

    protected ContactsAdapter() {
        super(new DiffUtil.ItemCallback<ContactItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull ContactItem oldItem, @NonNull ContactItem newItem) {
                return oldItem.getUserId().equals(newItem.getUserId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull ContactItem oldItem, @NonNull ContactItem newItem) {
                return oldItem.getUserId().equals(newItem.getUserId()) &&
                        oldItem.getDisplayName().equals(newItem.getDisplayName()) &&
                        oldItem.isOnline() == newItem.isOnline();
            }
        });
    }

    public void setOnContactClickListener(OnContactClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(getItem(position), position);
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView sectionLetter, contactName, contactUsername, contactStatus, avatarLetter;
        private final ImageView contactAvatar;
        private final View statusDot;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            sectionLetter = itemView.findViewById(R.id.sectionLetter);
            contactName = itemView.findViewById(R.id.contactName);
            contactUsername = itemView.findViewById(R.id.contactUsername);
            contactStatus = itemView.findViewById(R.id.contactStatus);
            avatarLetter = itemView.findViewById(R.id.avatarLetter);
            contactAvatar = itemView.findViewById(R.id.contactAvatar);
            statusDot = itemView.findViewById(R.id.statusDot);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onContactClick(getItem(pos));
                }
            });
        }

        public void bind(ContactItem contact, int position) {
            boolean isFirstInGroup = true;
            if (position > 0) {
                ContactItem prev = getItem(position - 1);
                isFirstInGroup = !prev.getSectionLetter().equals(contact.getSectionLetter());
            }
            sectionLetter.setVisibility(isFirstInGroup ? View.VISIBLE : View.GONE);
            sectionLetter.setText(contact.getSectionLetter());

            contactName.setText(contact.getDisplayName());
            String username = contact.getUsername();
            if (!username.startsWith("@") && !username.isEmpty()) {
                username = "@" + username;
            }
            contactUsername.setText(username);
            contactStatus.setText(contact.getStatusText());
            statusDot.setBackgroundColor(contact.getStatusColor());

            String avatarUrl = contact.getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                contactAvatar.setVisibility(View.VISIBLE);
                avatarLetter.setVisibility(View.GONE);
                Glide.with(itemView.getContext())
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_avatar_placeholder)
                        .error(R.drawable.bg_avatar_placeholder)
                        .into(contactAvatar);
            } else {
                contactAvatar.setVisibility(View.GONE);
                avatarLetter.setVisibility(View.VISIBLE);
                String letter = contact.getDisplayName().isEmpty() ? "?" : contact.getDisplayName().substring(0, 1).toUpperCase();
                avatarLetter.setText(letter);
            }
        }
    }
}