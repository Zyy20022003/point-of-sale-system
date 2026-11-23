package com.example.androidpos.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.androidpos.R;
import com.example.androidpos.data.local.Category;
import com.example.androidpos.data.local.Product;
import com.example.androidpos.data.local.Sale;
import com.example.androidpos.data.local.SaleItem;
import com.example.androidpos.data.repository.PosRepository;
import com.example.androidpos.domain.Amounts;
import com.example.androidpos.domain.Periods;
import com.example.androidpos.ui.dashboard.MainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;

public final class PosFragment extends Fragment {
    private static final int PAYMENT = -1;
    private final ProductGrid.Filters menuFilters = new ProductGrid.Filters();
    private final ProductGrid.Filters sellingFilters = new ProductGrid.Filters();
    private final ReportsScreen reports = new ReportsScreen(this);
    private LinearLayout page;
    private PosViewModel model;
    private PosRepository.Snapshot snapshot;
    private LinearLayout cartPanel;
    private NestedScrollView cartScroll;
    private MaterialButton cartToggle;
    private int screen = R.id.nav_home;
    private boolean cartVisible;
    private boolean paying;
    private OnBackPressedCallback back;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            screen = saved.getInt("screen", R.id.nav_home);
            cartVisible = saved.getBoolean("cartVisible");
            menuFilters.restore(saved, "menu");
            sellingFilters.restore(saved, "selling");
            reports.restore(saved);
        }
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        return inflater.inflate(R.layout.fragment_pos, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, Bundle saved) {
        page = (LinearLayout) view;
        model = new ViewModelProvider(requireActivity()).get(PosViewModel.class);
        Ui.text(page, "Đang tải dữ liệu…", 16);
        back = new OnBackPressedCallback(screen != R.id.nav_home) {
                    @Override public void handleOnBackPressed() {
                        if (screen == PAYMENT) {
                            show(R.id.nav_order);
                        } else {
                            navigate(R.id.nav_home);
                        }
                    }
                };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
        model.state().observe(getViewLifecycleOwner(), state -> {
            boolean reuseCatalog = screen == R.id.nav_order && (cartPanel != null || cartToggle != null)
                    && snapshot != null && sameCatalog(snapshot, state);
            snapshot = state;
            if (reuseCatalog) {
                updateCart();
            } else if (screen != PAYMENT || !paying) {
                render();
            }
        });
        model.errors().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                model.consumeError();
                paying = false;
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                if (snapshot == null) {
                    page.removeAllViews();
                    Ui.text(page, error, 16);
                    Ui.button(page, "Thử lại", model::refresh);
                } else if (screen == PAYMENT) {
                    render();
                }
            }
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("screen", screen);
        out.putBoolean("cartVisible", cartVisible);
        menuFilters.save(out, "menu");
        sellingFilters.save(out, "selling");
        reports.save(out);
    }

    @Override public void onResume() {
        super.onResume();
        refreshDateIfNeeded();
    }

    @Override public void onDestroyView() {
        page = null;
        snapshot = null;
        cartPanel = null;
        cartScroll = null;
        cartToggle = null;
        super.onDestroyView();
    }

    public void show(int item) {
        screen = item;
        if (back != null) {
            back.setEnabled(screen != R.id.nav_home);
        }
        if (page != null) {
            hideKeyboard();
            render();
            refreshDateIfNeeded();
        }
    }

    private void hideKeyboard() {
        View focus = page.findFocus();
        if (focus != null) {
            InputMethodManager keyboard = requireContext().getSystemService(InputMethodManager.class);
            keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private void refreshDateIfNeeded() {
        if (snapshot != null && !snapshot.reportDay().equals(Periods.today())) {
            model.refresh();
        }
    }

    PosViewModel model() { return model; }

    private void navigate(int item) {
        ((MainActivity) requireActivity()).navigate(item);
    }

    private void render() {
        if (page == null || snapshot == null) {
            return;
        }
        page.removeAllViews();
        cartPanel = null;
        cartScroll = null;
        cartToggle = null;
        if (screen == R.id.nav_products) {
            products();
        } else if (screen == R.id.nav_order) {
            order();
        } else if (screen == PAYMENT) {
            payment();
        } else if (screen == R.id.nav_history) {
            reports.history(page, snapshot.history());
        } else if (screen == R.id.nav_statistics) {
            reports.statistics(page);
        } else {
            dashboard();
        }
    }

    private boolean sameCatalog(PosRepository.Snapshot first, PosRepository.Snapshot second) {
        if (first.categories().size() != second.categories().size()
                || first.products().size() != second.products().size()) {
            return false;
        }
        for (int i = 0; i < first.categories().size(); i++) {
            Category a = first.categories().get(i);
            Category b = second.categories().get(i);
            if (a.id != b.id || !a.name.equals(b.name)) {
                return false;
            }
        }
        for (int i = 0; i < first.products().size(); i++) {
            Product a = first.products().get(i);
            Product b = second.products().get(i);
            if (a.id != b.id || a.categoryId != b.categoryId || a.active != b.active
                    || a.price != b.price || !a.name.equals(b.name) || !Objects.equals(a.imagePath, b.imagePath)) {
                return false;
            }
        }
        return true;
    }

    private void dashboard() {
        LinearLayout column = Ui.scroll(page);
        Ui.heading(column, "Thu ngân POS");
        PosRepository.Report today = snapshot.today();
        Ui.text(column, "Doanh thu hôm nay · " + Ui.DATE.format(snapshot.reportDay()), 16);
        Ui.text(column, Ui.money(today.revenue()), 34);
        Ui.text(column, today.sales().size() + " đơn đã thanh toán  ·  " + today.quantity() + " món đã bán", 17);
        boolean hasDraft = snapshot.cart().sale() != null;
        Ui.button(column, hasDraft ? "Tiếp tục đơn đang tạo" : "Tạo đơn mới", () -> {
            cartVisible = hasDraft;
            navigate(R.id.nav_order);
        });
        Ui.button(column, "Xem thống kê", () -> navigate(R.id.nav_statistics));
        Ui.button(column, "Cấu hình VietQR", () -> new TransferQr(requireContext()).configure());
        Ui.button(column, "Tạo dữ liệu mẫu", () -> confirm("Thêm thực đơn mẫu?",
                "Ứng dụng thêm 6 món và 2 danh mục có nhãn [Mẫu]. Các món dùng được trong giỏ hàng thử.",
                () -> model.write(model.repository::seedSampleCatalog, () -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Thực đơn mẫu đã sẵn sàng.",
                                Toast.LENGTH_SHORT).show();
                    }
                })));
    }

    private void products() {
        Ui.heading(page, "Thực đơn");
        LinearLayout actions = Ui.row(requireContext());
        page.addView(actions);
        Ui.button(actions, "Thêm món", () -> {
            if (snapshot.categories().isEmpty()) {
                categoryEditor(null);
            } else {
                ProductEditor.create(null, snapshot.categories()).show(getChildFragmentManager(), "product");
            }
        }).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Ui.button(actions, "Danh mục", this::categories)
                .setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        ProductGrid.add(page, model, snapshot, menuFilters, false,
                Math.max(1, getResources().getConfiguration().screenWidthDp / 175),
                product -> ProductEditor.create(product, snapshot.categories())
                        .show(getChildFragmentManager(), "product"));
    }

    private void categories() {
        List<Category> categoryList = snapshot.categories();
        String[] names = new String[categoryList.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = categoryList.get(i).name;
        }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Danh mục")
                .setItems(names, (dialog, index) -> categoryEditor(categoryList.get(index)))
                .setPositiveButton("Thêm danh mục", (dialog, which) -> categoryEditor(null))
                .setNegativeButton("Đóng", null).show();
    }

    private void categoryEditor(Category category) {
        LinearLayout fields = Ui.column(requireContext());
        int padding = Ui.dp(requireContext(), 20);
        fields.setPadding(padding, padding, padding, 0);
        EditText name = Ui.field(fields, "Tên danh mục", category == null ? "" : category.name,
                InputType.TYPE_CLASS_TEXT);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(category == null ? "Thêm danh mục" : "Sửa danh mục")
                .setView(fields).setNegativeButton("Đóng", null).setPositiveButton("Lưu", null);
        if (category != null) {
            builder.setNeutralButton("Xóa", (dialog, which) -> confirm("Xóa danh mục này?",
                    "Danh mục phải trống.", () -> model.write(() -> model.repository.deleteCategory(category.id))));
        }
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                name.setError("Nhập tên danh mục.");
                return;
            }
            dialog.dismiss();
            model.write(() -> model.repository.saveCategory(category == null ? 0 : category.id, value));
        });
    }

    private void order() {
        Ui.heading(page, "Bán hàng");
        int width = getResources().getConfiguration().screenWidthDp;
        if (width >= 700) {
            LinearLayout lanes = Ui.row(requireContext());
            page.addView(lanes, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout catalog = Ui.column(requireContext());
            lanes.addView(catalog, new LinearLayout.LayoutParams(0, -1, 3));
            cartPanel = Ui.column(requireContext());
            cartPanel.setPadding(Ui.dp(requireContext(), 16), 0, 0, 0);
            lanes.addView(cartPanel, new LinearLayout.LayoutParams(0, -1, 2));
            sellingGrid(catalog, Math.max(2, width / 300));
        } else {
            cartToggle = Ui.button(page, "", () -> {
                cartVisible = !cartVisible;
                hideKeyboard();
                render();
            });
            if (cartVisible) {
                cartPanel = Ui.column(requireContext());
                page.addView(cartPanel, new LinearLayout.LayoutParams(-1, 0, 1));
            } else {
                sellingGrid(page, Math.max(1, width / 165));
            }
        }
        updateCart();
    }

    private void sellingGrid(LinearLayout parent, int columns) {
        ProductGrid.add(parent, model, snapshot, sellingFilters, true, columns,
                product -> model.write(() -> model.repository.addToCart(product.id)));
    }

    private void updateCart() {
        PosRepository.Cart cart = snapshot.cart();
        if (cartToggle != null) {
            cartToggle.setText(cartVisible ? "Quay lại chọn món" : "Giỏ hàng (" + cart.items().size() + ")");
        }
        if (cartPanel == null) {
            return;
        }
        int scrollPosition = cartScroll == null ? 0 : cartScroll.getScrollY();
        cartPanel.removeAllViews();
        Ui.heading(cartPanel, "Giỏ hàng");
        LinearLayout lines = Ui.scroll(cartPanel);
        cartScroll = (NestedScrollView) lines.getParent();
        if (scrollPosition > 0) {
            NestedScrollView updated = cartScroll;
            OneShotPreDrawListener.add(updated, () -> updated.scrollTo(0, scrollPosition));
        }
        if (cart.items().isEmpty()) {
            Ui.text(lines, "Chưa có món trong giỏ.", 16);
        }
        if (cart.sale() == null) {
            return;
        }
        long orderId = cart.sale().id;
        for (SaleItem line : cart.items()) {
            Ui.text(lines, line.productName + " × " + line.quantity, 17);
            Ui.text(lines, Ui.money(line.unitPrice) + " / món  ·  " + Ui.money(line.lineTotal), 15);
            LinearLayout controls = Ui.row(requireContext());
            lines.addView(controls);
            MaterialButton minus = Ui.button(controls, "−", () ->
                    model.write(() -> model.repository.changeQuantity(orderId, line.productId, -1)));
            minus.setContentDescription("Giảm số lượng " + line.productName);
            minus.setEnabled(line.quantity > 1);
            Ui.button(controls, "+", () ->
                    model.write(() -> model.repository.changeQuantity(orderId, line.productId, 1)))
                    .setContentDescription("Tăng số lượng " + line.productName);
            Ui.button(controls, "Bỏ món", () ->
                    model.write(() -> model.repository.removeItem(orderId, line.productId)));
        }
        Ui.text(cartPanel, "Tổng cộng  " + Ui.money(cart.sale().total), 21);
        Ui.button(cartPanel, "Thanh toán", () -> show(PAYMENT)).setEnabled(!cart.items().isEmpty());
        Ui.button(cartPanel, "Hủy đơn", () -> confirm("Hủy đơn đang tạo?",
                "Đơn sẽ được ghi vào lịch sử với trạng thái đã hủy.",
                () -> model.write(() -> model.repository.cancel(orderId))));
    }

    private void payment() {
        PosRepository.Cart cart = snapshot.cart();
        if (cart.sale() == null || cart.items().isEmpty()) {
            show(R.id.nav_order);
            return;
        }
        Sale sale = cart.sale();
        LinearLayout content = Ui.scroll(page);
        Ui.heading(content, "Thanh toán");
        Ui.text(content, "Tổng cần thu  " + Ui.money(sale.total), 26);
        for (SaleItem line : cart.items()) {
            Ui.text(content, line.productName + " × " + line.quantity + "  ·  " + Ui.money(line.lineTotal), 16);
        }
        EditText cash = Ui.field(content, "Tiền khách đưa (VNĐ)", "", InputType.TYPE_CLASS_NUMBER);
        TextView change = Ui.text(content, "Tiền thừa  —", 18);
        MaterialButton cashButton = Ui.button(content, "Xác nhận tiền mặt", () ->
                confirm("Ghi nhận thanh toán tiền mặt?", "Tổng cần thu " + Ui.money(sale.total),
                        () -> pay(sale, true, Amounts.parse(cash.getText().toString()))));
        cashButton.setEnabled(false);
        Ui.whenTextChanges(cash, input -> {
            try {
                change.setText(getString(R.string.cash_change,
                        Ui.money(Amounts.change(sale.total, Amounts.parse(input)))));
                cashButton.setEnabled(!paying);
            } catch (IllegalArgumentException error) {
                change.setText(input.isEmpty() ? "Tiền thừa  —" : error.getMessage());
                cashButton.setEnabled(false);
            }
        });
        Ui.button(content, "Hiện mã VietQR", () -> new TransferQr(requireContext()).show(sale))
                .setEnabled(sale.total > 0);
        Ui.button(content, "Xác nhận chuyển khoản", () -> confirm("Đã nhận được chuyển khoản?",
                "Hãy kiểm tra đã nhận đủ " + Ui.money(sale.total) + " trước khi xác nhận.",
                () -> pay(sale, false, null)));
        Ui.button(content, "Quay lại giỏ hàng", () -> show(R.id.nav_order));
    }

    private void pay(Sale sale, boolean cash, Long received) {
        if (paying) {
            return;
        }
        paying = true;
        model.write(() -> model.repository.pay(sale.id, sale.total, cash, received), () -> {
            paying = false;
            if (isAdded() && page != null) {
                Toast.makeText(requireContext(), "Đã ghi nhận thanh toán.", Toast.LENGTH_SHORT).show();
                if (screen == PAYMENT) {
                    show(R.id.nav_order);
                }
            }
        });
    }

    private void confirm(String title, String message, Runnable action) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message)
                .setNegativeButton("Quay lại", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> action.run()).show();
    }
}
