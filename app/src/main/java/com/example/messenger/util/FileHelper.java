package com.example.messenger.util;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileHelper {


    public static File uriToFile(Context context, Uri uri) {
        try {
            String fileName = getFileName(context, uri);
            File tempFile = File.createTempFile("upload_", "_" + fileName, context.getCacheDir());

            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(tempFile)) {

                if (is != null) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
            }
            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }


    private static String getFileName(Context context, Uri uri) {
        String name = "image.jpg"; // default

        if (uri.getScheme().equals("content")) {
            try (var cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex("_display_name");
                    if (idx >= 0) {
                        name = cursor.getString(idx);
                    }
                }
            }
        } else if (uri.getScheme().equals("file")) {
            name = new File(uri.getPath()).getName();
        }
        return name;
    }


    public static String getMimeType(String fileName) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }
}