package com.example.androidpos.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.example.androidpos.data.repository.ProductImages;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ProductImagesTest {
    @Test public void storesThumbnailsOfflineReusesDuplicatesAndPrunesOnlyUnused() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        ProductImages images = new ProductImages(context);
        File original = image(context, Color.RED);
        File replacement = image(context, Color.BLUE);
        String red = images.importImage(Uri.fromFile(original));
        String blue = images.importImage(Uri.fromFile(replacement));
        assertNotEquals(red, blue);
        assertEquals(red, images.importImage(Uri.fromFile(original)));
        assertTrue(original.delete());
        assertTrue(replacement.delete());

        ProductImages reopened = new ProductImages(context);
        Bitmap thumb = reopened.load(red);
        assertNotNull(thumb);
        assertEquals(320, thumb.getWidth());
        assertEquals(160, thumb.getHeight());
        reopened.prune(Arrays.asList(red, blue));
        assertNotNull(reopened.load(red));
        reopened.prune(Collections.singletonList(blue));
        assertNull(reopened.load(red));
        assertNotNull(reopened.load(blue));
        assertNull(reopened.load(null));
        File stored = new File(context.getFilesDir(), "product-images/" + blue);
        assertTrue(stored.delete());
        assertNull(reopened.load(blue));
        reopened.prune(Collections.emptyList());
        assertEquals(0, new File(context.getFilesDir(), "product-images").list().length);
    }

    @Test public void rejectsInvalidImage() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File invalid = File.createTempFile("not-image", ".png", context.getCacheDir());
        ProductImages images = new ProductImages(context);
        assertThrows(IOException.class, () -> images.importImage(Uri.fromFile(invalid)));
        images.prune(Collections.emptyList());
        assertTrue(invalid.delete());
    }

    private File image(Context context, int color) throws IOException {
        File file = File.createTempFile("selected", ".png", context.getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }
        return file;
    }
}
