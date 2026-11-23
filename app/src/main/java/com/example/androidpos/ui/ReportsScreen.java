package com.example.androidpos.ui;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.androidpos.R;
import com.example.androidpos.data.local.Sale;
import com.example.androidpos.data.local.SaleItem;
import com.example.androidpos.data.repository.PosRepository;
import com.example.androidpos.domain.Periods;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

final class ReportsScreen {
    private final PosFragment owner;
    private String code = "";
    private LocalDate historyDay;
    private int historyStatus;
    private LocalDate reportDate = Periods.today();
    private Periods.Unit unit = Periods.Unit.DAY;

    ReportsScreen(PosFragment owner) {
        this.owner = owner;
    }

    void save(Bundle out) {
        out.putString("historyCode", code);
        out.putString("historyDay", historyDay == null ? null : historyDay.toString());
        out.putInt("historyStatus", historyStatus);
        out.putString("reportDate", reportDate.toString());
        out.putString("reportUnit", unit.name());
    }

    void restore(Bundle saved) {
        code = saved.getString("historyCode", "");
        String day = saved.getString("historyDay");
        historyDay = day == null ? null : LocalDate.parse(day);
        reportDate = LocalDate.parse(saved.getString("reportDate", Periods.today().toString()));
        historyStatus = saved.getInt("historyStatus");
        unit = Periods.Unit.valueOf(saved.getString("reportUnit", Periods.Unit.DAY.name()));
    }

    void history(LinearLayout page, List<Sale> history) {
        Ui.heading(page, "Lịch sử đơn hàng");
        EditText search = Ui.field(page, "Tìm theo mã đơn", code, InputType.TYPE_CLASS_TEXT);
        Spinner status = Ui.spinner(page, Arrays.asList("Mọi trạng thái", "Đang tạo", "Đã thanh toán",
                "Đã hủy"), historyStatus);
        status.setContentDescription("Lọc trạng thái đơn");
        LinearLayout filters = Ui.row(page.getContext());
        page.addView(filters);
        MaterialButton dateButton = Ui.button(filters, dateLabel(), () -> {});
        dateButton.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView empty = Ui.text(page, "Không tìm thấy đơn.", 16);
        ListView list = new ListView(page.getContext());
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Ui.dp(page.getContext(), 8));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        List<Sale> matches = new ArrayList<>();
        Runnable update = () -> {
            code = search.getText().toString();
            historyStatus = Math.max(0, status.getSelectedItemPosition());
            dateButton.setText(dateLabel());
            String desired = new String[] {"", "DRAFT", "PAID", "CANCELLED"}[historyStatus];
            String term = code.trim().toLowerCase(Ui.VI);
            matches.clear();
            List<String> rows = new ArrayList<>();
            for (Sale sale : history) {
                LocalDate created = Instant.ofEpochMilli(sale.createdAt).atZone(Periods.ZONE).toLocalDate();
                if ((desired.isEmpty() || desired.equals(sale.status))
                        && (historyDay == null || historyDay.equals(created))
                        && sale.code.toLowerCase(Ui.VI).contains(term)) {
                    matches.add(sale);
                    rows.add(label(sale) + "\n" + Ui.status(sale.status) + " · " + Ui.payment(sale.paymentMethod));
                }
            }
            list.setAdapter(new ArrayAdapter<>(page.getContext(), R.layout.history_row, rows));
            empty.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
        };
        dateButton.setOnClickListener(v -> pickDate(historyDay == null ? Periods.today() : historyDay, date -> {
            historyDay = date;
            update.run();
        }));
        Ui.button(filters, "Bỏ lọc ngày", () -> {
            historyDay = null;
            update.run();
        });
        list.setOnItemClickListener((parent, view, index, id) -> details(page, matches.get(index)));
        Ui.whenTextChanges(search, value -> update.run());
        status.setOnItemSelectedListener(ProductGrid.selection(update));
        update.run();
    }

    void statistics(LinearLayout page) {
        Ui.heading(page, "Thống kê doanh thu");
        Spinner period = Ui.spinner(page, Arrays.asList("Theo ngày", "Theo tháng", "Theo năm"), unit.ordinal());
        period.setContentDescription("Kỳ thống kê");
        MaterialButton date = Ui.button(page, "", () -> {});
        LinearLayout content = Ui.scroll(page);
        Runnable load = () -> loadStatistics(content, date, period);
        period.setOnItemSelectedListener(ProductGrid.selection(load));
        date.setOnClickListener(v -> pickDate(reportDate, chosen -> {
            reportDate = chosen;
            load.run();
        }));
        load.run();
    }

    private void loadStatistics(LinearLayout content, MaterialButton date, Spinner period) {
        unit = Periods.Unit.values()[Math.max(0, period.getSelectedItemPosition())];
        date.setText(owner.getString(R.string.select_period, Ui.period(reportDate, unit)));
        LocalDate selected = reportDate;
        Periods.Unit selectedUnit = unit;
        content.removeAllViews();
        Ui.text(content, "Đang tải thống kê…", 16);
        owner.model().query(() -> owner.model().repository.report(selected, selectedUnit), report -> {
            if (content.isAttachedToWindow() && selected.equals(reportDate) && selectedUnit == unit) {
                showReport(content, report, selected, selectedUnit);
            }
        }, error -> {
            if (content.isAttachedToWindow() && selected.equals(reportDate) && selectedUnit == unit) {
                content.removeAllViews();
                Ui.text(content, error, 16);
                Ui.button(content, "Thử lại", () -> loadStatistics(content, date, period));
            }
        });
    }

    private void showReport(LinearLayout content, PosRepository.Report report, LocalDate date,
                            Periods.Unit unit) {
        content.removeAllViews();
        LinearLayout totals = Ui.card(content);
        Ui.muted(totals, "Doanh thu · " + Ui.period(date, unit), 14);
        Ui.emphasis(totals, Ui.money(report.revenue()), 32);
        Ui.muted(totals, report.sales().size() + " đơn đã thanh toán  ·  " + report.quantity() + " món đã bán", 15);
        if (report.sales().isEmpty()) {
            Ui.text(content, "Chưa có đơn đã thanh toán trong kỳ này.", 16);
            return;
        }
        if (unit == Periods.Unit.DAY) {
            Ui.heading(content, "Đơn đã thanh toán");
            for (Sale sale : report.sales()) {
                Ui.outlined(content, label(sale), () -> details(content, sale));
            }
            return;
        }

        Ui.heading(content, unit == Periods.Unit.MONTH ? "Doanh thu từng ngày" : "Doanh thu từng tháng");
        LinearLayout chart = Ui.card(content);
        BigInteger maximum = BigInteger.ONE;
        for (BigInteger amount : report.buckets().values()) {
            maximum = maximum.max(amount);
        }
        LocalDate start = unit == Periods.Unit.MONTH ? date.withDayOfMonth(1) : date.withDayOfYear(1);
        int count = unit == Periods.Unit.MONTH ? start.lengthOfMonth() : 12;
        for (int i = 0; i < count; i++) {
            LocalDate bucket = unit == Periods.Unit.MONTH ? start.plusDays(i) : start.plusMonths(i);
            BigInteger amount = report.buckets().get(bucket);
            amount = amount == null ? BigInteger.ZERO : amount;
            String legend = Ui.period(bucket, unit == Periods.Unit.MONTH ? Periods.Unit.DAY : Periods.Unit.MONTH)
                    + "  ·  " + Ui.money(amount);
            Ui.text(chart, legend, 15);
            LinearProgressIndicator bar = new LinearProgressIndicator(chart.getContext());
            bar.setTrackThickness(Ui.dp(chart.getContext(), 8));
            bar.setTrackCornerRadius(Ui.dp(chart.getContext(), 4));
            bar.setMax(100);
            bar.setProgress(amount.multiply(BigInteger.valueOf(100)).divide(maximum).intValue());
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2);
            barParams.bottomMargin = Ui.dp(chart.getContext(), 6);
            chart.addView(bar, barParams);
        }
    }

    private void details(LinearLayout origin, Sale sale) {
        owner.model().query(() -> owner.model().repository.items(sale.id), items -> {
            if (!owner.isAdded() || !origin.isAttachedToWindow()) {
                return;
            }
            android.widget.ScrollView scroll = new android.widget.ScrollView(owner.requireContext());
            LinearLayout content = Ui.column(owner.requireContext());
            int padding = Ui.dp(owner.requireContext(), 20);
            content.setPadding(padding, 0, padding, 0);
            scroll.addView(content);
            Ui.text(content, sale.code, 16);
            Ui.text(content, "Tạo lúc " + Ui.stamp(sale.createdAt), 15);
            Ui.text(content, Ui.status(sale.status) + " · " + Ui.payment(sale.paymentMethod), 16);
            if (sale.paidAt != null) {
                Ui.text(content, "Thanh toán lúc " + Ui.stamp(sale.paidAt), 15);
            }
            if (sale.customerPaid != null && sale.change != null) {
                Ui.text(content, "Khách đưa " + Ui.money(sale.customerPaid)
                        + " · Tiền thừa " + Ui.money(sale.change), 16);
            }
            for (SaleItem item : items) {
                Ui.text(content, item.productName + " × " + item.quantity + "\n"
                        + Ui.money(item.unitPrice) + " / món · " + Ui.money(item.lineTotal), 16);
            }
            Ui.text(content, "Tổng cộng  " + Ui.money(sale.total), 21);
            new MaterialAlertDialogBuilder(owner.requireContext()).setTitle("Chi tiết đơn hàng")
                    .setView(scroll).setPositiveButton("Đóng", null).show();
        });
    }

    private String dateLabel() {
        return historyDay == null ? "Mọi ngày" : Ui.DATE.format(historyDay);
    }

    private void pickDate(LocalDate date, Consumer<LocalDate> selected) {
        new DatePickerDialog(owner.requireContext(), (picker, year, month, day) ->
                selected.accept(LocalDate.of(year, month + 1, day)), date.getYear(),
                date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private String label(Sale sale) {
        return sale.code.substring(0, Math.min(8, sale.code.length())) + " · " + Ui.money(sale.total)
                + " · " + Ui.stamp(sale.paidAt == null ? sale.createdAt : sale.paidAt);
    }
}
