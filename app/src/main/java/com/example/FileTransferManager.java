package com.example;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * AirLink File Transfer Manager
 * Manages Storage Access Framework (SAF) file streaming, SHA-256 integrity verification,
 * and user-approved local storage writes.
 */
public class FileTransferManager {

    public interface TransferListener {
        void onProgress(String fileName, int percent, long bytesTransferred, long totalBytes);
        void onComplete(String fileName, String sha256Checksum, File savedFile);
        void onError(String fileName, String errorMessage);
    }

    private static FileTransferManager instance;
    private final Context context;

    public static synchronized FileTransferManager getInstance(Context context) {
        if (instance == null) {
            instance = new FileTransferManager(context.getApplicationContext());
        }
        return instance;
    }

    private FileTransferManager(Context context) {
        this.context = context;
    }

    /**
     * Resolves file name from a content Uri
     */
    public String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "airlink_file_" + System.currentTimeMillis();
    }

    /**
     * Resolves file size from a content Uri
     */
    public long getFileSize(Uri uri) {
        long size = 0;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}
        return size;
    }

    /**
     * Calculates SHA-256 checksum of a file
     */
    public static String calculateSha256(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "error_calculating_hash";
        }
    }

    /**
     * Writes incoming input stream to the app downloads directory safely
     */
    public File saveIncomingFile(String fileName, InputStream inputStream, TransferListener listener) {
        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) {
            downloadDir = new File(context.getFilesDir(), "downloads");
        }
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        File targetFile = new File(downloadDir, fileName);
        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[16384];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (listener != null) {
                    listener.onProgress(fileName, -1, totalRead, -1);
                }
            }
            outputStream.flush();

            String checksum = calculateSha256(targetFile);
            if (listener != null) {
                listener.onComplete(fileName, checksum, targetFile);
            }
            return targetFile;
        } catch (Exception e) {
            if (listener != null) {
                listener.onError(fileName, e.getMessage());
            }
            return null;
        }
    }

    /**
     * Returns received files as JSON for the WebView table
     */
    public String getReceivedFilesJson() {
        JSONArray arr = new JSONArray();
        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists()) {
            File[] files = downloadDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("name", f.getName());
                        obj.put("size", formatSize(f.length()));
                        obj.put("date", "Recent");
                        obj.put("path", f.getAbsolutePath());
                        arr.put(obj);
                    } catch (Exception ignored) {}
                }
            }
        }
        return arr.toString();
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
