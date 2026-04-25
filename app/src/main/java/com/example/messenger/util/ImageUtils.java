package com.example.messenger.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageUtils {


    private static final int MAX_WIDTH = 1080;
    private static final int MAX_HEIGHT = 1080;
    private static final int COMPRESS_QUALITY = 85; // 0-100

    public static File compressImage(Context context, Uri imageUri) throws IOException {

        Bitmap bitmap = decodeSampledBitmapFromUri(context, imageUri, MAX_WIDTH, MAX_HEIGHT);


        bitmap = rotateBitmapIfNeeded(context, imageUri, bitmap);


        File tempFile = File.createTempFile("compressed_", ".jpg", context.getCacheDir());

        try (OutputStream os = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, os);
            os.flush();
        }

        if (!bitmap.isRecycled()) bitmap.recycle();

        return tempFile;
    }


    private static Bitmap decodeSampledBitmapFromUri(Context context, Uri uri,
                                                     int reqWidth, int reqHeight) throws IOException {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, options);
        }


        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // Экономия памяти

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, options);
        }
    }


    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }


    private static Bitmap rotateBitmapIfNeeded(Context context, Uri uri, Bitmap bitmap) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return bitmap;

            ExifInterface exif = new ExifInterface(inputStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            inputStream.close();

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                default: return bitmap;
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            return bitmap;
        }
    }


    public static String getRealPathFromUri(Context context, Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        try (var cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(idx);
            }
        } catch (Exception e) {
            try {
                File temp = File.createTempFile("temp_", ".jpg", context.getCacheDir());
                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(temp)) {
                    if (is != null) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                return temp.getAbsolutePath();
            } catch (IOException ex) {
                return null;
            }
        }
        return null;
    }
}