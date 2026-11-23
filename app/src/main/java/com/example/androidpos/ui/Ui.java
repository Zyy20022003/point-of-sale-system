package com.example.androidpos.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.example.androidpos.domain.Periods;

final class Ui {
    static final Locale VI = Locale.forLanguageTag("vi-VN");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", VI);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", VI)
            .withZone(Periods.ZONE);

    private Ui() {}

    static int dp(Context context, int amount) {
        return Math.round(amount * context.getResources().getDisplayMetrics().density);
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBaselineAligned(false);
        return layout;
    }

    static LinearLayout scroll(LinearLayout parent) {
        NestedScrollView scroll = new NestedScrollView(parent.getContext());
        scroll.setFillViewport(true);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout column = column(parent.getContext());
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        return column;
    }

    static TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(parent.getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, dp(parent.getContext(), 6), 0, dp(parent.getContext(), 6));
        parent.addView(view);
        return view;
    }

    static TextView heading(LinearLayout parent, String value) {
        TextView view = text(parent, value, 24);
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    static MaterialButton button(LinearLayout parent, String label, Runnable action) {
        MaterialButton view = new MaterialButton(parent.getContext());
        view.setText(label);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        parent.addView(view);
        return view;
    }

    static EditText field(LinearLayout parent, String label, String value, int inputType) {
        TextInputLayout container = new TextInputLayout(parent.getContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        container.setHint(label);
        TextInputEditText text = new TextInputEditText(container.getContext());
        text.setSingleLine();
        text.setInputType(inputType);
        text.setText(value);
        container.addView(text, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(container, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    static void whenTextChanges(EditText field, Consumer<String> action) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                action.accept(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    static Spinner spinner(LinearLayout parent, List<String> options, int selected) {
        Spinner spinner = new Spinner(parent.getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(parent.getContext(),
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setMinimumHeight(dp(parent.getContext(), 48));
        spinner.setAdapter(adapter);
        if (!options.isEmpty()) {
            spinner.setSelection(Math.min(selected, options.size() - 1));
        }
        parent.addView(spinner);
        return spinner;
    }

    static String money(Number value) {
        return NumberFormat.getIntegerInstance(VI).format(value) + " ₫";
    }

    static String stamp(long time) {
        return STAMP.format(Instant.ofEpochMilli(time));
    }

    static String period(LocalDate date, Periods.Unit unit) {
        switch (unit) {
            case YEAR: return Integer.toString(date.getYear());
            case MONTH: return String.format(VI, "%02d/%d", date.getMonthValue(), date.getYear());
            default: return DATE.format(date);
        }
    }

    static String status(String value) {
        switch (value) {
            case "PAID": return "Đã thanh toán";
            case "CANCELLED": return "Đã hủy";
            default: return "Đang tạo";
        }
    }

    static String payment(String method) {
        if ("CASH".equals(method)) {
            return "Tiền mặt";
        }
        if ("TRANSFER".equals(method)) {
            return "Chuyển khoản";
        }
        return "Chưa thanh toán";
    }
}
