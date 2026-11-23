package com.example.androidpos.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.androidpos.data.local.Sale;
import com.example.androidpos.domain.VietQr;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

final class TransferQr {
    private final Context context;
    private final SharedPreferences preferences;

    TransferQr(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("transfer_recipient", Context.MODE_PRIVATE);
    }

    void configure() {
        configure(() -> {});
    }

    private void configure(Runnable onSaved) {
        LinearLayout fields = Ui.column(context);
        int padding = Ui.dp(context, 20);
        fields.setPadding(padding, padding, padding, 0);
        EditText bank = Ui.field(fields, "Mã BIN ngân hàng (6 số)",
                preferences.getString("bin", ""), InputType.TYPE_CLASS_NUMBER);
        EditText account = Ui.field(fields, "Số tài khoản nhận",
                preferences.getString("account", ""), InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Tài khoản nhận VietQR").setView(fields)
                .setPositiveButton("Lưu", null).setNegativeButton("Đóng", null)
                .setNeutralButton("Xóa cấu hình", (d, which) -> preferences.edit().clear().apply()).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                VietQr.Recipient recipient = new VietQr.Recipient(bank.getText().toString(),
                        account.getText().toString());
                preferences.edit().putString("bin", recipient.bankBin())
                        .putString("account", recipient.account()).apply();
                dialog.dismiss();
                onSaved.run();
            } catch (IllegalArgumentException invalid) {
                if (!bank.getText().toString().trim().matches("[0-9]{6}")) {
                    bank.setError(invalid.getMessage());
                } else {
                    account.setError(invalid.getMessage());
                }
            }
        });
    }

    void show(Sale sale) {
        String bank = preferences.getString("bin", "");
        String account = preferences.getString("account", "");
        if (bank.isEmpty() || account.isEmpty()) {
            configure(() -> show(sale));
            return;
        }
        try {
            VietQr.Recipient recipient = new VietQr.Recipient(bank, account);
            String payload = VietQr.payload(recipient, sale.total, sale.code);
            LinearLayout content = Ui.column(context);
            int padding = Ui.dp(context, 16);
            content.setPadding(padding, 0, padding, 0);
            Ui.text(content, "Số tiền  " + Ui.money(sale.total), 20);
            Ui.text(content, "BIN " + bank + " · TK " + account, 16);
            Ui.text(content, "Nội dung  " + VietQr.reference(sale.code), 16);
            ImageView image = new ImageView(context);
            image.setContentDescription("Mã VietQR để quét bằng ứng dụng ngân hàng");
            image.setImageBitmap(bitmap(payload));
            int size = Ui.dp(context, 224);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            content.addView(image, params);
            ScrollView scroll = new ScrollView(context);
            scroll.addView(content);
            new MaterialAlertDialogBuilder(context).setTitle("Chuyển khoản qua VietQR")
                    .setView(scroll).setPositiveButton("Đóng", null).show();
        } catch (IllegalArgumentException | WriterException error) {
            Toast.makeText(context, error.getMessage() == null ? "Không tạo được mã VietQR."
                    : error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap bitmap(String payload) throws WriterException {
        int size = 512;
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }
}
