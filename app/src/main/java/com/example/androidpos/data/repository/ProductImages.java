package com.example.androidpos.data.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProductImages {
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final int THUMB_SIZE = 320;
    private final Context context;
    private final File directory;

    public ProductImages(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "product-images");
    }

    public String importImage(Uri uri) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Không tạo được thư mục ảnh.");
        }
        File temporary = File.createTempFile("import-", ".tmp", directory);
        try {
            String key = copyAndHash(uri, temporary);
            File original = new File(directory, key);
            File preview = thumbnail(key);
            if (!preview.isFile()) {
                createThumbnail(temporary, preview);
            }
            if (!original.isFile() && !temporary.renameTo(original)) {
                throw new IOException("Không lưu được ảnh.");
            }
            return key;
        } finally {
            temporary.delete();
        }
    }

    public boolean exists(String key) {
        return key != null && key.matches("[0-9a-f]{64}")
                && new File(directory, key).isFile() && thumbnail(key).isFile();
    }

    public Bitmap load(String key) {
        return exists(key) ? BitmapFactory.decodeFile(thumbnail(key).getPath()) : null;
    }

    public void prune(List<String> referencedPaths) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        Set<String> keep = new HashSet<>(referencedPaths);
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("import-") && name.endsWith(".tmp")) {
                file.delete();
            } else if (name.matches("[0-9a-f]{64}(\\.jpg)?") && !keep.contains(name.substring(0, 64))) {
                file.delete();
            }
        }
    }

    private String copyAndHash(Uri uri, File temporary) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) {
                throw new IOException("Không đọc được ảnh.");
            }
            byte[] buffer = new byte[16 * 1024];
            long length = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                length += count;
                if (length > MAX_BYTES) {
                    throw new IOException("Ảnh vượt quá 25 MB.");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        StringBuilder key = new StringBuilder(64);
        for (byte value : digest.digest()) {
            key.append(Character.forDigit((value & 0xff) >>> 4, 16));
            key.append(Character.forDigit(value & 15, 16));
        }
        return key.toString();
    }

    private File thumbnail(String key) {
        return new File(directory, key + ".jpg");
    }

    private void createThumbnail(File original, File target) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(original.getPath(), options);
        int maxDimension = Math.max(options.outWidth, options.outHeight);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("Định dạng ảnh không đọc được.");
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (maxDimension / options.inSampleSize > THUMB_SIZE * 2) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(original.getPath(), options);
        if (bitmap == null) {
            throw new IOException("Không thể giải mã ảnh.");
        }

        Matrix transform = orientation(original);
        float scale = Math.min(1f, (float) THUMB_SIZE / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        transform.postScale(scale, scale);
        Bitmap preview = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                transform, true);
        File temporary = File.createTempFile("import-", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!preview.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw new IOException("Không tạo được ảnh thu nhỏ.");
                }
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("Không lưu được ảnh thu nhỏ.");
            }
        } finally {
            temporary.delete();
            if (preview != bitmap) {
                preview.recycle();
            }
            bitmap.recycle();
        }
    }

    private Matrix orientation(File image) {
        Matrix matrix = new Matrix();
        int value;
        try {
            value = new ExifInterface(image.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            return matrix;
        }
        switch (value) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(270);
                matrix.postScale(-1, 1);
                break;
            default: break;
        }
        return matrix;
    }
}
