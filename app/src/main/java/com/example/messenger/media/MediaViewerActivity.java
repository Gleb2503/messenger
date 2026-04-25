package com.example.messenger.media;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.messenger.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MediaViewerActivity extends AppCompatActivity {

    private static final String TAG = "MediaViewerActivity";
    private static final String EXTRA_MEDIA_ITEMS = "media_items";
    private static final String EXTRA_POSITION = "position";
    private static final String EXTRA_CHAT_ID = "chat_id";

    private ViewPager2 viewPager;
    private TextView positionIndicator;
    private View fileInfoCard;
    private TextView fileNameText, fileSizeText, fileDateText;
    private ImageView downloadButton, shareButton, backButton, menuButton;
    private ProgressBar progressBar;

    private List<MediaItem> mediaItems = new ArrayList<>();
    private int currentPosition = 0;
    private long chatId;

    public static void start(Context context, List<MediaItem> items, int position, long chatId) {
        Intent intent = new Intent(context, MediaViewerActivity.class);
        intent.putParcelableArrayListExtra(EXTRA_MEDIA_ITEMS, new ArrayList<>(items));
        intent.putExtra(EXTRA_POSITION, position);
        intent.putExtra(EXTRA_CHAT_ID, chatId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        mediaItems = getIntent().getParcelableArrayListExtra(EXTRA_MEDIA_ITEMS);
        if (mediaItems == null) {
            Log.e(TAG, "❌ mediaItems is NULL from intent!");
            mediaItems = new ArrayList<>();
        }

        currentPosition = getIntent().getIntExtra(EXTRA_POSITION, 0);
        chatId = getIntent().getLongExtra(EXTRA_CHAT_ID, -1);

        if (mediaItems.isEmpty()) {
            Toast.makeText(this, "Нет медиа для просмотра", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupViewPager();
        setupClickListeners();

    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        positionIndicator = findViewById(R.id.positionIndicator);
        fileInfoCard = findViewById(R.id.fileInfoCard);
        fileNameText = findViewById(R.id.fileNameText);
        fileSizeText = findViewById(R.id.fileSizeText);
        fileDateText = findViewById(R.id.fileDateText);
        downloadButton = findViewById(R.id.downloadButton);
        shareButton = findViewById(R.id.shareButton);
        backButton = findViewById(R.id.backButton);
        menuButton = findViewById(R.id.menuButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupViewPager() {
        MediaPagerAdapter adapter = new MediaPagerAdapter(mediaItems);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updatePositionIndicator();
                updateFileInfo(mediaItems.get(position));
            }
        });

        updatePositionIndicator();
        updateFileInfo(mediaItems.get(currentPosition));
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        menuButton.setOnClickListener(v -> showMediaMenu());
        downloadButton.setOnClickListener(v -> downloadCurrentMedia());
        shareButton.setOnClickListener(v -> shareCurrentMedia());

        fileInfoCard.setOnClickListener(v -> {
            if (fileInfoCard.getVisibility() == View.VISIBLE) {
                fileInfoCard.setVisibility(View.GONE);
            } else {
                fileInfoCard.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updatePositionIndicator() {
        positionIndicator.setText((currentPosition + 1) + "/" + mediaItems.size());
    }

    private void updateFileInfo(MediaItem item) {
        fileNameText.setText(item.getFileName() != null ? item.getFileName() : "image.jpg");
        fileSizeText.setText(formatFileSize(item.getFileSize()));
        fileDateText.setText(formatDate(item.getCreatedAt()));
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            String datePart = isoDate.split("T")[0];
            String[] parts = datePart.split("-");
            if (parts.length == 3) {
                return parts[2] + "." + parts[1] + "." + parts[0];
            }
            return datePart;
        } catch (Exception e) {
            return isoDate;
        }
    }

    private void showMediaMenu() {
        Toast.makeText(this, "Меню медиа", Toast.LENGTH_SHORT).show();
    }

    private void downloadCurrentMedia() {
        MediaItem item = mediaItems.get(currentPosition);
        Toast.makeText(this, "Скачивание: " + item.getFileName(), Toast.LENGTH_SHORT).show();
    }

    private void shareCurrentMedia() {
        MediaItem item = mediaItems.get(currentPosition);
        Toast.makeText(this, "Поделиться: " + item.getFileName(), Toast.LENGTH_SHORT).show();
    }


    private static class MediaPagerAdapter extends RecyclerView.Adapter<MediaViewHolder> {
        private final List<MediaItem> items;

        public MediaPagerAdapter(List<MediaItem> items) {
            this.items = items;
        }

        @Override
        public MediaViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_media_page, parent, false);
            return new MediaViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MediaViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }



    private static class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final ProgressBar loadingProgress;
        private static final String TAG = "MediaViewHolder";

        public MediaViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.mediaImageView);
            loadingProgress = itemView.findViewById(R.id.loadingProgress);
        }

        public void bind(MediaItem item) {
            Log.d(TAG, "🖼️ bind() STARTED | pos=" + getAdapterPosition() +
                    ", file='" + item.getFileName() + "'");

            try {
                String url = item.getUrl();
                Log.d(TAG, "   └─ URL from item: '" + url + "'");

                if (url == null || url.isEmpty()) {
                    Log.e(TAG, "❌ URL is null or empty!");
                    showImageError();
                    return;
                }


                final String imageUrl = url.startsWith("http")
                        ? url
                        : "https://storage.yandexcloud.net" + url;

                Log.d(TAG, "   └─ Final imageUrl: " + imageUrl);

                Log.d(TAG, "🔄 Showing loading indicator");
                loadingProgress.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                imageView.setImageDrawable(null);


                Log.d(TAG, "🌐 Starting direct OkHttp download...");

                new Thread(() -> {
                    try {
                        String token = com.example.messenger.data.api.RetrofitClient.getToken();
                        Log.d(TAG, "🔑 Token: " + (token != null ? token.substring(0, 20) + "..." : "NULL"));


                        okhttp3.Request request = new okhttp3.Request.Builder()
                                .url(imageUrl)
                                .addHeader("Authorization", "Bearer " + (token != null ? token : ""))
                                .build();

                        Log.d(TAG, "📤 Sending request to: " + imageUrl);


                        okhttp3.Response response = new okhttp3.OkHttpClient.Builder()
                                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                                .newCall(request)
                                .execute();

                        Log.d(TAG, "📥 Response code: " + response.code());

                        if (response.isSuccessful() && response.body() != null) {

                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(
                                    response.body().byteStream());

                            if (bitmap != null) {
                                Log.d(TAG, "✅ Bitmap decoded: " + bitmap.getWidth() + "x" + bitmap.getHeight());


                                itemView.post(() -> {
                                    loadingProgress.setVisibility(View.GONE);
                                    imageView.setImageBitmap(bitmap);
                                    imageView.setVisibility(View.VISIBLE);
                                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    Log.d(TAG, "🖼️ Bitmap set to ImageView");
                                });
                            } else {
                                Log.e(TAG, "❌ Failed to decode bitmap");
                                itemView.post(this::showImageError);
                            }
                        } else {
                            Log.e(TAG, "❌ HTTP error: " + response.code() + " - " + response.message());
                            itemView.post(this::showImageError);
                        }

                        response.close();

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Download failed: " + e.getMessage(), e);
                        itemView.post(this::showImageError);
                    }
                }).start();

                Log.d(TAG, "✅ bind() COMPLETED (async download started)");

            } catch (Exception e) {
                Log.e(TAG, "💥 EXCEPTION in bind(): " + e.getMessage(), e);
                showImageError();
            }
        }

        private void showImageError() {
            Log.d(TAG, "🖼️ showImageError() called");
            loadingProgress.setVisibility(View.GONE);
            imageView.setImageResource(android.R.drawable.ic_menu_report_image);
            imageView.setVisibility(View.VISIBLE);
        }
    }
}