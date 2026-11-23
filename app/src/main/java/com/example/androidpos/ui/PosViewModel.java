package com.example.androidpos.ui;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.androidpos.R;
import com.example.androidpos.data.local.PosDatabase;
import com.example.androidpos.data.repository.PosRepository;
import com.example.androidpos.data.repository.ProductImages;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class PosViewModel extends AndroidViewModel {
    private static final class ImageRequest {
        volatile boolean cancelled;
    }

    private static final ExecutorService databaseWorker = Executors.newSingleThreadExecutor();

    final PosRepository repository;
    private final ProductImages images;
    private final MutableLiveData<PosRepository.Snapshot> state = new MutableLiveData<>();
    private final MutableLiveData<String> errors = new MutableLiveData<>();
    private final ExecutorService thumbnails = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };

    public PosViewModel(@NonNull Application application) {
        super(application);
        repository = new PosRepository(PosDatabase.get(application));
        images = new ProductImages(application);
        query(() -> {
            images.prune(repository.imagePaths());
            return repository.snapshot();
        }, state::setValue);
    }

    LiveData<PosRepository.Snapshot> state() { return state; }
    LiveData<String> errors() { return errors; }

    void consumeError() { errors.setValue(null); }

    void refresh() {
        query(repository::snapshot, state::setValue);
    }

    void write(Runnable operation) {
        write(operation, () -> {});
    }

    void write(Runnable operation, Runnable success) {
        submit(() -> {
            operation.run();
            return repository.snapshot();
        }, value -> {
            state.setValue(value);
            success.run();
        }, null);
    }

    void saveProduct(long id, long categoryId, String name, long price, String oldPath, Uri newImage,
                     Runnable success, Consumer<String> failure) {
        submit(() -> {
            try {
                String path = newImage == null ? oldPath : images.importImage(newImage);
                repository.saveProduct(id, categoryId, name, price, path);
            } finally {
                images.prune(repository.imagePaths());
                bitmapCache.evictAll();
            }
            return repository.snapshot();
        }, value -> {
            state.setValue(value);
            success.run();
        }, failure);
    }

    <T> void query(Callable<T> operation, Consumer<T> result) {
        submit(operation, result, null);
    }

    <T> void query(Callable<T> operation, Consumer<T> result, Consumer<String> failure) {
        submit(operation, result, failure);
    }

    private <T> void submit(Callable<T> operation, Consumer<T> result, Consumer<String> failure) {
        databaseWorker.execute(() -> {
            try {
                T value = operation.call();
                main.post(() -> result.accept(value));
            } catch (Exception error) {
                String message;
                if (error instanceof ArithmeticException) {
                    message = "Số tiền hoặc số lượng vượt phạm vi cho phép.";
                } else if (error instanceof IllegalArgumentException || error instanceof IllegalStateException
                        || error instanceof IOException) {
                    message = error.getMessage();
                } else {
                    Log.e("AndroidPOS", "Local operation failed", error);
                    message = "Không thể hoàn thành thao tác. Vui lòng thử lại.";
                }
                String displayed = message == null ? "Thao tác thất bại." : message;
                main.post(() -> {
                    if (failure == null) {
                        errors.setValue(displayed);
                    } else {
                        failure.accept(displayed);
                    }
                });
            }
        });
    }

    void loadImage(String path, ImageView image) {
        cancelImage(image);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImageResource(R.drawable.ic_product);
        if (path == null) {
            return;
        }
        ImageRequest request = new ImageRequest();
        image.setTag(request);
        WeakReference<ImageView> reference = new WeakReference<>(image);
        thumbnails.execute(() -> {
            if (request.cancelled || reference.get() == null || !images.exists(path)) {
                return;
            }
            Bitmap cached = bitmapCache.get(path);
            Bitmap bitmap = cached == null ? images.load(path) : cached;
            if (bitmap != null) {
                bitmapCache.put(path, bitmap);
                main.post(() -> {
                    ImageView target = reference.get();
                    if (target != null && !request.cancelled) {
                        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        target.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    void cancelImage(ImageView image) {
        if (image.getTag() instanceof ImageRequest) {
            ((ImageRequest) image.getTag()).cancelled = true;
            image.setTag(null);
        }
    }

    @Override
    protected void onCleared() {
        thumbnails.shutdownNow();
        super.onCleared();
    }
}
