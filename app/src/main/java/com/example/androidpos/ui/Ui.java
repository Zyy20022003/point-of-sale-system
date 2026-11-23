package com.example.androidpos.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
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
        TextView view = new MaterialTextView(parent.getContext());
        view.setText(value);
        view.setTextColor(color(view, com.google.android.material.R.attr.colorOnSurface));
        view.setTextSize(size);
        view.setPadding(0, dp(parent.getContext(), 6), 0, dp(parent.getContext(), 6));
        parent.addView(view);
        return view;
    }

    static TextView heading(LinearLayout parent, String value) {
        TextView view = emphasis(parent, value, 27);
        view.setLetterSpacing(-0.015f);
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    static TextView emphasis(LinearLayout parent, String value, int size) {
        TextView view = text(parent, value, size);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    static TextView muted(LinearLayout parent, String value, int size) {
        TextView view = text(parent, value, size);
        view.setTextColor(color(view, com.google.android.material.R.attr.colorOnSurfaceVariant));
        return view;
    }

    static int color(View view, int role) {
        return MaterialColors.getColor(view, role);
    }

    static LinearLayout card(LinearLayout parent, int surface, boolean border) {
        Context context = parent.getContext();
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 22));
        card.setCardElevation(0);
        card.setCardBackgroundColor(surface);
        card.setStrokeColor(color(parent, com.google.android.material.R.attr.colorOutlineVariant));
        card.setStrokeWidth(border ? dp(context, 1) : 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(context, 12);
        parent.addView(card, params);
        LinearLayout content = column(context);
        int inset = dp(context, 16);
        content.setPadding(inset, inset, inset, inset);
        card.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -2));
        return content;
    }

    static LinearLayout card(LinearLayout parent) {
        return card(parent, color(parent, com.google.android.material.R.attr.colorSurfaceContainerLow), true);
    }

    static MaterialButton button(LinearLayout parent, String label, Runnable action) {
        return button(parent, label, action, com.google.android.material.R.attr.materialButtonStyle);
    }

    static MaterialButton outlined(LinearLayout parent, String label, Runnable action) {
        return button(parent, label, action, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    }

    static MaterialButton quiet(LinearLayout parent, String label, Runnable action) {
        MaterialButton view = button(parent, label, action);
        view.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        int ink = color(view, androidx.appcompat.R.attr.colorPrimary);
        view.setTextColor(ink);
        view.setIconTint(ColorStateList.valueOf(ink));
        view.setStateListAnimator(null);
        return view;
    }

    private static MaterialButton button(LinearLayout parent, String label, Runnable action, int style) {
        MaterialButton view = new MaterialButton(parent.getContext(), null, style);
        view.setText(label);
        view.setAllCaps(false);
        view.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        view.setMinHeight(dp(parent.getContext(), 48));
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
