package com.example.androidpos.ui;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.androidpos.data.local.Category;
import com.example.androidpos.data.local.Product;
import com.example.androidpos.domain.Amounts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public final class ProductEditor extends DialogFragment {
    private Uri selectedImage;
    private String storedImage;
    private EditText name;
    private EditText price;
    private Spinner categories;
    private ImageView preview;
    private TextView imageStatus;
    private EditorState editorState;
    private PosViewModel model;

    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedImage = uri;
                    updatePreview();
                }
            });

    public static final class EditorState extends ViewModel {
        private boolean saving;
        private final MutableLiveData<SaveResult> result = new MutableLiveData<>();
    }

    private record SaveResult(String error) {}

    static ProductEditor create(Product product, java.util.List<Category> categories) {
        ProductEditor editor = new ProductEditor();
        Bundle args = new Bundle();
        args.putLong("id", product == null ? 0 : product.id);
        args.putLong("category", product == null ? 0 : product.categoryId);
        args.putString("name", product == null ? "" : product.name);
        args.putString("price", product == null ? "" : Long.toString(product.price));
        args.putString("image", product == null ? null : product.imagePath);
        args.putBoolean("active", product != null && product.active);
        ArrayList<String> names = new ArrayList<>();
        long[] ids = new long[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            names.add(categories.get(i).name);
            ids[i] = categories.get(i).id;
        }
        args.putStringArrayList("categories", names);
        args.putLongArray("ids", ids);
        editor.setArguments(args);
        return editor;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        Bundle args = requireArguments();
        Bundle values = saved == null ? args : saved;
        model = new ViewModelProvider(requireActivity()).get(PosViewModel.class);
        editorState = new ViewModelProvider(this).get(EditorState.class);
        storedImage = values.getString("image");
        if (saved != null) {
            selectedImage = androidx.core.os.BundleCompat.getParcelable(saved, "selected", Uri.class);
        }
        long id = args.getLong("id");
        long[] ids = args.getLongArray("ids");
        int categoryIndex = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == values.getLong("category")) {
                categoryIndex = i;
                break;
            }
        }

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout fields = Ui.column(requireContext());
        int padding = Ui.dp(requireContext(), 20);
        fields.setPadding(padding, padding, padding, 0);
        scroll.addView(fields);
        name = Ui.field(fields, "Tên món", values.getString("name"), InputType.TYPE_CLASS_TEXT);
        price = Ui.field(fields, "Giá bán (VNĐ)", values.getString("price"), InputType.TYPE_CLASS_NUMBER);
        categories = Ui.spinner(fields, args.getStringArrayList("categories"), categoryIndex);
        categories.setContentDescription("Danh mục");
        preview = new ImageView(requireContext());
        preview.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        fields.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(requireContext(), 110)));
        imageStatus = Ui.text(fields, "", 14);
        updatePreview();
        Ui.button(fields, "Chọn ảnh", () -> picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        Ui.button(fields, "Xóa ảnh đại diện", () -> {
            selectedImage = null;
            storedImage = null;
            updatePreview();
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(id == 0 ? "Thêm món" : "Chỉnh sửa món")
                .setView(scroll)
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Lưu", null);
        boolean active = args.getBoolean("active");
        if (id != 0) {
            builder.setNeutralButton(active ? "Ngừng bán" : "Mở bán", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(event -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> save(dialog, ids, id));
            if (id != 0) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(active ? "Ngừng bán món này?" : "Mở bán lại món này?")
                                .setMessage(active ? "Món sẽ ẩn khỏi danh sách chọn món khi bán hàng."
                                        : "Món sẽ xuất hiện lại trong danh sách chọn món.")
                                .setNegativeButton("Quay lại", null)
                                .setPositiveButton("Xác nhận", (confirmation, which) -> {
                                    dismiss();
                                    model.write(() -> model.repository.setActive(id, !active));
                                }).show());
            }
            setSaving(dialog, editorState.saving);
        });
        editorState.result.observe(this, result -> {
            if (result != null) {
                editorState.result.setValue(null);
                if (result.error() == null) {
                    dismissAllowingStateLoss();
                } else {
                    setSaving(dialog, false);
                    imageStatus.setText(result.error());
                }
            }
        });
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (name != null) {
            long[] ids = requireArguments().getLongArray("ids");
            out.putLong("category", ids[categories.getSelectedItemPosition()]);
            out.putString("name", name.getText().toString());
            out.putString("price", price.getText().toString());
            out.putString("image", storedImage);
            out.putParcelable("selected", selectedImage);
        }
    }

    private void updatePreview() {
        if (preview == null) {
            return;
        }
        model.loadImage(selectedImage == null ? storedImage : null, preview);
        imageStatus.setText(selectedImage == null
                ? (storedImage == null ? "Ảnh mặc định" : "Ảnh hiện tại")
                : "Đã chọn ảnh mới. Ảnh sẽ được lưu khi bấm Lưu.");
    }

    private void save(AlertDialog dialog, long[] ids, long id) {
        if (editorState.saving) {
            return;
        }
        String productName = name.getText().toString().trim();
        if (productName.isEmpty()) {
            name.setError("Nhập tên món.");
            return;
        }
        long amount;
        try {
            amount = Amounts.parse(price.getText().toString());
        } catch (IllegalArgumentException error) {
            price.setError(error.getMessage());
            return;
        }
        EditorState pending = editorState;
        setSaving(dialog, true);
        model.saveProduct(id, ids[categories.getSelectedItemPosition()], productName, amount,
                storedImage, selectedImage, () -> pending.result.setValue(new SaveResult(null)),
                error -> pending.result.setValue(new SaveResult(error)));
    }

    private void setSaving(AlertDialog dialog, boolean busy) {
        editorState.saving = busy;
        setCancelable(!busy);
        for (int button : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            if (dialog.getButton(button) != null) {
                dialog.getButton(button).setEnabled(!busy);
            }
        }
    }
}
