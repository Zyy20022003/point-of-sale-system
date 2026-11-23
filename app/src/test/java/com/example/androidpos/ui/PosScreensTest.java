package com.example.androidpos.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidpos.R;
import com.example.androidpos.data.repository.ProductImages;
import com.example.androidpos.ui.dashboard.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class PosScreensTest {
    @Test
    @Config(qualifiers = "sw360dp-w360dp-h740dp")
    public void phoneCanAddProductOpenCartAndViewPaymentHistoryAndStatistics() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ViewGroup root = activity.findViewById(R.id.screen);
            seedProduct(activity);

            findText(root, "Tạo đơn mới").performClick();
            chooseFirstProduct(root);
            await(() -> findText(root, "Giỏ hàng (1)") != null);
            findText(root, "Giỏ hàng (1)").performClick();
            assertNotNull(findText(root, "Cà phê × 1"));
            findText(root, "+").performClick();
            await(() -> findText(root, "Cà phê × 2") != null);
            assertNotNull(findText(root, "Tổng cộng  " + Ui.money(40_000)));
            findText(root, "−").performClick();
            await(() -> findText(root, "Cà phê × 1") != null);
            findText(root, "Thanh toán").performClick();
            assertNotNull(findText(root, "Xác nhận tiền mặt"));
            findText(root, "Hiện mã VietQR").performClick();
            AlertDialog configuration = editor();
            List<EditText> recipient = new ArrayList<>();
            collectFields((ViewGroup) configuration.getWindow().getDecorView(), recipient);
            recipient.get(0).setText("999999");
            recipient.get(1).setText("00123456789");
            configuration.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            AlertDialog qr = editor();
            ViewGroup qrContent = (ViewGroup) qr.getWindow().getDecorView();
            assertNotNull(findText(qrContent, "BIN 999999 · TK 00123456789"));
            assertNotNull(findText(qrContent, "Số tiền  " + Ui.money(20_000)));
            qr.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

            findText(root, "Xác nhận chuyển khoản").performClick();
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(() -> findText(root, "Bán hàng") != null);
            PosViewModel model = new ViewModelProvider(activity).get(PosViewModel.class);
            assertEquals("PAID", model.state().getValue().history().get(0).status);

            activity.navigate(R.id.nav_history);
            assertNotNull(findText(root, "Lịch sử đơn hàng"));
            ListView history = findWidget(root, ListView.class);
            assertEquals(1, history.getAdapter().getCount());
            assertTrue(history.getAdapter().getItem(0).toString().contains("Đã thanh toán · Chuyển khoản"));
            history.performItemClick(null, 0, history.getAdapter().getItemId(0));
            await(() -> ShadowDialog.getLatestDialog() != dialog
                    && ShadowDialog.getLatestDialog().isShowing());
            AlertDialog details = editor();
            ViewGroup detailContent = (ViewGroup) details.getWindow().getDecorView();
            assertNotNull(findText(detailContent, model.state().getValue().history().get(0).code));
            assertNotNull(findText(detailContent, "Tổng cộng  " + Ui.money(20_000)));
            details.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            activity.navigate(R.id.nav_statistics);
            await(() -> findText(root, "Đơn đã thanh toán") != null);
        }
    }

    @Test
    @Config(qualifiers = "sw720dp-w900dp-h900dp")
    public void tabletDisplaysCatalogAndCartTogether() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ViewGroup root = activity.findViewById(R.id.screen);
            seedProduct(activity);
            activity.navigate(R.id.nav_order);
            assertNotNull(findText(root, "Giỏ hàng"));
            chooseFirstProduct(root);
            await(() -> findText(root, "Cà phê × 1") != null);
            assertNotNull(findGrid(root));
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "sw360dp-w360dp-h740dp")
    public void productEditorAddsReplacesAndRemovesPhoto() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ViewGroup root = activity.findViewById(R.id.screen);
            PosViewModel model = new ViewModelProvider(activity).get(PosViewModel.class);
            model.write(() -> model.repository.saveCategory(0, "Đồ uống"));
            await(() -> model.state().getValue() != null && !model.state().getValue().categories().isEmpty());

            activity.navigate(R.id.nav_products);
            findText(root, "Thêm món").performClick();
            AlertDialog editor = editor();
            ViewGroup form = (ViewGroup) editor.getWindow().getDecorView();
            List<EditText> fields = new ArrayList<>();
            collectFields(form, fields);
            fields.get(0).setText("Cà phê");
            fields.get(1).setText("20000");
            selectPhoto(activity, form, Color.RED);
            editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(() -> model.state().getValue().products().size() == 1 && !editor.isShowing());
            String firstPath = model.state().getValue().products().get(0).imagePath;
            assertNotNull(firstPath);

            chooseFirstProduct(root);
            AlertDialog replacement = editor();
            selectPhoto(activity, (ViewGroup) replacement.getWindow().getDecorView(), Color.BLUE);
            replacement.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(() -> !replacement.isShowing());
            String secondPath = model.state().getValue().products().get(0).imagePath;
            assertNotNull(secondPath);
            assertNotEquals(firstPath, secondPath);

            chooseFirstProduct(root);
            AlertDialog removal = editor();
            findText((ViewGroup) removal.getWindow().getDecorView(), "Xóa ảnh đại diện").performClick();
            removal.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(() -> model.state().getValue().products().get(0).imagePath == null && !removal.isShowing());

            chooseFirstProduct(root);
            editor().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            AlertDialog confirmation = editor();
            assertTrue(model.state().getValue().products().get(0).active);
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(() -> !model.state().getValue().products().get(0).active);
            activity.navigate(R.id.nav_order);
            assertEquals(0, findGrid(root).getAdapter().getItemCount());
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = 34, qualifiers = "sw360dp-w360dp-h740dp")
    public void scrollingCatalogLoadsThumbnailAndShowsPlaceholderForMissingPhoto() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            PosViewModel model = new ViewModelProvider(activity).get(PosViewModel.class);
            ProductImages images = new ProductImages(activity);
            File valid = photoFile(activity, Color.BLUE);
            File missing = photoFile(activity, Color.RED);
            model.query(() -> new String[] {images.importImage(Uri.fromFile(valid)),
                    images.importImage(Uri.fromFile(missing))}, paths -> model.write(() -> {
                model.repository.saveCategory(0, "Đồ uống");
                long categoryId = model.repository.snapshot().categories().get(0).id;
                for (int i = 0; i < 20; i++) {
                    model.repository.saveProduct(0, categoryId, String.format(Locale.ROOT,
                            "Món kiểm thử %02d", i), 20_000, i == 18 ? paths[0] : (i == 19 ? paths[1] : null));
                }
                File original = new File(activity.getFilesDir(), "product-images/" + paths[1]);
                if (!original.delete()) {
                    throw new IllegalStateException("Could not simulate a missing original image");
                }
            }));
            await(() -> model.state().getValue() != null && model.state().getValue().products().stream()
                    .filter(product -> product.name.startsWith("Món kiểm thử")).count() == 20);

            activity.navigate(R.id.nav_products);
            ViewGroup root = activity.findViewById(R.id.screen);
            RecyclerView grid = findGrid(root);
            assertNotNull(grid);
            EditText search = findWidget(root, EditText.class);
            assertNotNull(search);
            search.setText("Món kiểm thử");
            await(() -> grid.getAdapter().getItemCount() == 20);
            // Let the test control layout passes while the main looper delivers the image callback.
            ((ViewGroup) grid.getParent()).removeView(grid);
            layoutGrid(grid);
            assertNotNull(grid.findViewHolderForAdapterPosition(0));

            grid.scrollToPosition(19);
            layoutGrid(grid);
            RecyclerView.ViewHolder visiblePhoto = grid.findViewHolderForAdapterPosition(18);
            assertNotNull(visiblePhoto);
            assertNotNull(findText((ViewGroup) visiblePhoto.itemView, "Món kiểm thử 18"));
            assertNotNull(grid.findViewHolderForAdapterPosition(19));
            await(() -> {
                RecyclerView.ViewHolder card = grid.findViewHolderForAdapterPosition(18);
                if (card == null) {
                    return false;
                }
                ImageView image = card.itemView.findViewById(R.id.product_image);
                return image.getScaleType() == ImageView.ScaleType.CENTER_CROP;
            });
            RecyclerView.ViewHolder missingCard = grid.findViewHolderForAdapterPosition(19);
            assertNotNull(missingCard);
            ImageView missingImage = missingCard.itemView.findViewById(R.id.product_image);
            assertEquals(ImageView.ScaleType.CENTER_INSIDE, missingImage.getScaleType());
            assertNotNull(missingImage.getDrawable());
        }
    }

    private void layoutGrid(RecyclerView grid) {
        grid.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 360, 300);
    }

    private AlertDialog editor() {
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private void selectPhoto(MainActivity activity, ViewGroup form, int color) throws IOException {
        File file = photoFile(activity, color);
        findText(form, "Chọn ảnh").performClick();
        ShadowActivity.IntentForResult request = shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(request);
        activity.getActivityResultRegistry().dispatchResult(request.requestCode, Activity.RESULT_OK,
                new Intent().setData(Uri.fromFile(file)));
    }

    private File photoFile(MainActivity activity, int color) throws IOException {
        File file = File.createTempFile("picker-", ".png", activity.getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    private void collectFields(ViewGroup group, List<EditText> result) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof EditText) {
                result.add((EditText) child);
            } else if (child instanceof ViewGroup) {
                collectFields((ViewGroup) child, result);
            }
        }
    }

    private void seedProduct(MainActivity activity) throws Exception {
        PosViewModel model = new ViewModelProvider(activity).get(PosViewModel.class);
        model.write(() -> {
            model.repository.saveCategory(0, "Đồ uống");
            long category = model.repository.snapshot().categories().get(0).id;
            model.repository.saveProduct(0, category, "Cà phê", 20_000, null);
        });
        await(() -> model.state().getValue() != null && model.state().getValue().products().size() == 1);
    }

    private void chooseFirstProduct(ViewGroup root) {
        RecyclerView grid = findGrid(root);
        assertNotNull(grid);
        RecyclerView.Adapter<?> adapter = grid.getAdapter();
        bindAndClick(grid, adapter);
    }

    private <Holder extends RecyclerView.ViewHolder> void bindAndClick(RecyclerView grid,
                                                                       RecyclerView.Adapter<Holder> adapter) {
        assertEquals(1, adapter.getItemCount());
        Holder holder = adapter.createViewHolder(grid, adapter.getItemViewType(0));
        adapter.bindViewHolder(holder, 0);
        ViewGroup card = (ViewGroup) holder.itemView;
        assertNotNull(findText(card, "Cà phê"));
        assertNotNull(card.findViewById(R.id.product_image));
        assertTrue(card.performClick());
    }

    private RecyclerView findGrid(ViewGroup group) {
        return findWidget(group, RecyclerView.class);
    }

    private <T extends View> T findWidget(ViewGroup group, Class<T> type) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof ViewGroup) {
                T found = findWidget((ViewGroup) child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private TextView findText(ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && text.contentEquals(((TextView) child).getText())) {
                return (TextView) child;
            }
            if (child instanceof ViewGroup) {
                TextView found = findText((ViewGroup) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue("The expected UI state did not appear", condition.getAsBoolean());
    }
}
