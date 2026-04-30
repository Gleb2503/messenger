package com.example.messenger.media;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.messenger.R;
import com.example.messenger.data.api.RetrofitClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static final int PERMISSION_REQUEST_CODE = 1001;

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
        new AlertDialog.Builder(this)
                .setTitle("Меню")
                .setItems(new String[]{"Скачать", "Поделиться", "Удалить"}, (dialog, which) -> {
                    if (which == 0) downloadCurrentMedia();
                    else if (which == 1) shareCurrentMedia();
                })
                .show();
    }

    private void downloadCurrentMedia() {
        MediaItem item = mediaItems.get(currentPosition);


        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
                return;
            }
        }

        downloadMediaToGallery(item);
    }

    private void downloadMediaToGallery(MediaItem item) {
        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Начинаю загрузку...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String url = item.getUrl();
                if (url == null || url.isEmpty()) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Ошибка: URL пуст", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String imageUrl = url.startsWith("http") ? url : "https://storage.yandexcloud.net" + url;

                Log.d(TAG, "📥 Downloading from: " + imageUrl);


                HttpURLConnection connection = null;
                InputStream inputStream = null;

                try {
                    URL downloadUrl = new URL(imageUrl);
                    connection = (HttpURLConnection) downloadUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "📥 Response code: " + responseCode);

                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new Exception("HTTP error: " + responseCode);
                    }

                    inputStream = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (bitmap == null) {
                        throw new Exception("Failed to decode bitmap");
                    }


                    boolean saved = saveImageToGallery(bitmap, item.getFileName());

                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (saved) {
                            Toast.makeText(this, "✅ Изображение сохранено в галерею", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "❌ Ошибка сохранения", Toast.LENGTH_SHORT).show();
                        }
                    });

                } finally {
                    if (inputStream != null) {
                        try { inputStream.close(); } catch (Exception e) { }
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Download error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "❌ Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private boolean saveImageToGallery(Bitmap bitmap, String fileName) {
        try {
            // Для Android 10+ используем MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName != null ? fileName : "image_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Messenger");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                        outputStream.close();


                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);

                        Log.d(TAG, "✅ Image saved to: " + uri);
                        return true;
                    }
                }
            } else {
                // Для Android 9 и ниже
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File messengerDir = new File(picturesDir, "Messenger");

                if (!messengerDir.exists()) {
                    messengerDir.mkdirs();
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String finalFileName = fileName != null ? fileName : "image_" + timestamp + ".jpg";
                File imageFile = new File(messengerDir, finalFileName);

                FileOutputStream outputStream = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                outputStream.flush();
                outputStream.close();

                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(imageFile);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);

                Log.d(TAG, "✅ Image saved to: " + imageFile.getAbsolutePath());
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving image: " + e.getMessage(), e);
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadCurrentMedia();
            } else {
                Toast.makeText(this, "❌ Требуется разрешение для сохранения", Toast.LENGTH_SHORT).show();
            }
        }
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
            Log.d(TAG, "🖼️ bind() STARTED | pos=" + getAdapterPosition());

            try {
                String url = item.getUrl();
                if (url == null || url.isEmpty()) {
                    showImageError();
                    return;
                }

                String imageUrl = url.startsWith("http") ? url : "https://storage.yandexcloud.net" + url;

                loadingProgress.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                imageView.setImageDrawable(null);

                new Thread(() -> {
                    try {
                        HttpURLConnection connection = null;
                        InputStream inputStream = null;

                        try {
                            URL downloadUrl = new URL(imageUrl);
                            connection = (HttpURLConnection) downloadUrl.openConnection();
                            connection.setRequestMethod("GET");
                            connection.setConnectTimeout(30000);
                            connection.setReadTimeout(30000);
                            connection.connect();

                            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                inputStream = connection.getInputStream();
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                                if (bitmap != null) {
                                    itemView.post(() -> {
                                        loadingProgress.setVisibility(View.GONE);
                                        imageView.setImageBitmap(bitmap);
                                        imageView.setVisibility(View.VISIBLE);
                                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    });
                                } else {
                                    itemView.post(this::showImageError);
                                }
                            } else {
                                itemView.post(this::showImageError);
                            }
                        } finally {
                            if (inputStream != null) {
                                try { inputStream.close(); } catch (Exception e) { }
                            }
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Download failed: " + e.getMessage());
                        itemView.post(this::showImageError);
                    }
                }).start();

            } catch (Exception e) {
                Log.e(TAG, "💥 EXCEPTION: " + e.getMessage());
                showImageError();
            }
        }

        private void showImageError() {
            loadingProgress.setVisibility(View.GONE);
            imageView.setImageResource(android.R.drawable.ic_menu_report_image);
            imageView.setVisibility(View.VISIBLE);
        }
    }
}