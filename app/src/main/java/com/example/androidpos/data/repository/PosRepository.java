package com.example.androidpos.data.repository;

import com.example.androidpos.data.local.Category;
import com.example.androidpos.data.local.PosDao;
import com.example.androidpos.data.local.PosDatabase;
import com.example.androidpos.data.local.Product;
import com.example.androidpos.data.local.Sale;
import com.example.androidpos.data.local.SaleItem;
import com.example.androidpos.domain.Amounts;
import com.example.androidpos.domain.Periods;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class PosRepository {
    public record Cart(Sale sale, List<SaleItem> items) {}
    public record Snapshot(List<Category> categories, List<Product> products, Cart cart,
                           List<Sale> history, LocalDate reportDay, Report today) {}
    public record Report(BigInteger revenue, long quantity, List<Sale> sales,
                         Map<LocalDate, BigInteger> buckets) {}

    private final PosDatabase database;
    private final PosDao dao;

    public PosRepository(PosDatabase database) {
        this.database = database;
        dao = database.posDao();
    }

    public Snapshot snapshot() {
        return database.runInTransaction(() -> {
            Sale draft = dao.draft();
            Cart cart = draft == null ? new Cart(null, Collections.emptyList())
                    : new Cart(draft, dao.items(draft.id));
            LocalDate today = Periods.today();
            return new Snapshot(dao.categories(), dao.products(), cart, dao.history(), today,
                    report(today, Periods.Unit.DAY));
        });
    }

    public void seedSampleCatalog() {
        database.runInTransaction(() -> {
            List<Category> categories = dao.categories();
            List<Product> products = dao.products();
            seedCategory(categories, products, "[Mẫu] Đồ uống",
                    new String[] {"Cà phê sữa", "Trà đào", "Bạc xỉu"},
                    new long[] {25_000, 35_000, 30_000});
            seedCategory(categories, products, "[Mẫu] Đồ ăn",
                    new String[] {"Bánh mì trứng", "Bánh croissant", "Khoai tây chiên"},
                    new long[] {22_000, 38_000, 45_000});
        });
    }

    private void seedCategory(List<Category> categories, List<Product> products, String name,
                              String[] items, long[] prices) {
        long categoryId = categories.stream().filter(category -> category.name.equals(name))
                .mapToLong(category -> category.id).findFirst().orElse(0);
        if (categoryId == 0) {
            Category category = new Category();
            category.name = name;
            categoryId = dao.insertCategory(category);
        }
        for (int i = 0; i < items.length; i++) {
            String productName = "[Mẫu] " + items[i];
            long id = categoryId;
            boolean exists = products.stream()
                    .anyMatch(product -> product.categoryId == id && product.name.equals(productName));
            if (!exists) {
                saveProduct(0, categoryId, productName, prices[i], null);
            }
        }
    }

    public void saveCategory(long id, String name) {
        Category category = new Category();
        category.id = id;
        category.name = required(name, "Nhập tên danh mục.");
        if (id == 0) {
            dao.insertCategory(category);
        } else {
            dao.updateCategory(category);
        }
    }

    public void deleteCategory(long id) {
        if (dao.deleteEmptyCategory(id) == 0) {
            throw new IllegalStateException("Chỉ có thể xóa danh mục chưa có sản phẩm.");
        }
    }

    public void saveProduct(long id, long categoryId, String name, long price, String imagePath) {
        String productName = required(name, "Nhập tên món.");
        if (price < 0) {
            throw new IllegalArgumentException("Giá bán phải lớn hơn hoặc bằng 0.");
        }
        database.runInTransaction(() -> {
            Product product = id == 0 ? new Product() : requireProduct(id);
            long now = System.currentTimeMillis();
            product.categoryId = categoryId;
            product.name = productName;
            product.price = price;
            product.imagePath = imagePath;
            product.updatedAt = now;
            if (id == 0) {
                product.createdAt = now;
                dao.insertProduct(product);
            } else {
                dao.updateProduct(product);
            }
        });
    }

    public void setActive(long id, boolean active) {
        database.runInTransaction(() -> {
            Product product = requireProduct(id);
            product.active = active;
            product.updatedAt = System.currentTimeMillis();
            dao.updateProduct(product);
        });
    }

    public List<String> imagePaths() {
        return dao.imagePaths();
    }

    public void addToCart(long productId) {
        database.runInTransaction(() -> {
            Product product = requireProduct(productId);
            if (!product.active) {
                throw new IllegalStateException("Món này đã ngừng bán.");
            }

            Sale sale = dao.draft();
            if (sale == null) {
                sale = new Sale();
                sale.code = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
                sale.createdAt = System.currentTimeMillis();
                sale.id = dao.insertSale(sale);
            }
            SaleItem item = dao.item(sale.id, productId);
            if (item == null) {
                item = new SaleItem();
                item.orderId = sale.id;
                item.productId = product.id;
                item.productName = product.name;
                item.unitPrice = product.price;
            }
            setQuantity(item, Math.addExact(item.quantity, 1));
        });
    }

    public void changeQuantity(long orderId, long productId, int delta) {
        database.runInTransaction(() -> {
            requireDraft(orderId);
            SaleItem item = dao.item(orderId, productId);
            if (item == null) {
                throw new IllegalStateException("Món không còn trong giỏ hàng.");
            }
            int quantity = Math.addExact(item.quantity, delta);
            if (quantity < 1) {
                throw new IllegalArgumentException("Số lượng phải lớn hơn 0.");
            }
            setQuantity(item, quantity);
        });
    }

    public void removeItem(long orderId, long productId) {
        database.runInTransaction(() -> {
            requireDraft(orderId);
            SaleItem item = dao.item(orderId, productId);
            if (item != null) {
                dao.deleteItem(item.id);
                updateTotal(orderId);
            }
        });
    }

    public void cancel(long orderId) {
        if (dao.cancelDraft(orderId) != 1) {
            throw new IllegalStateException("Đơn đang tạo đã thay đổi.");
        }
    }

    public void pay(long orderId, long expectedTotal, boolean cashPayment, Long cash) {
        database.runInTransaction(() -> {
            Sale sale = requireDraft(orderId);
            if (sale.total != expectedTotal) {
                throw new IllegalStateException("Tổng tiền đã thay đổi. Hãy kiểm tra lại đơn.");
            }
            if (dao.items(orderId).isEmpty()) {
                throw new IllegalStateException("Thêm món trước khi thanh toán.");
            }
            if (cashPayment && cash == null) {
                throw new IllegalArgumentException("Nhập tiền khách đưa.");
            }
            Long change = cashPayment ? Amounts.change(sale.total, cash) : null;
            if (dao.payDraft(orderId, System.currentTimeMillis(), cashPayment ? "CASH" : "TRANSFER",
                    cashPayment ? cash : null, change) != 1) {
                throw new IllegalStateException("Đơn đang tạo đã thay đổi.");
            }
        });
    }

    public List<SaleItem> items(long orderId) {
        return dao.items(orderId);
    }

    public Report report(LocalDate date, Periods.Unit unit) {
        Periods.Range range = Periods.range(date, unit);
        return database.runInTransaction(() -> {
            List<Sale> sales = dao.paidSales(range.start(), range.end());
            BigInteger revenue = BigInteger.ZERO;
            Map<LocalDate, BigInteger> buckets = new TreeMap<>();
            for (Sale sale : sales) {
                BigInteger total = BigInteger.valueOf(sale.total);
                revenue = revenue.add(total);
                LocalDate day = Instant.ofEpochMilli(sale.paidAt).atZone(Periods.ZONE).toLocalDate();
                buckets.merge(unit == Periods.Unit.YEAR ? day.withDayOfMonth(1) : day,
                        total, BigInteger::add);
            }
            return new Report(revenue, dao.paidQuantity(range.start(), range.end()),
                    new ArrayList<>(sales), buckets);
        });
    }

    private Product requireProduct(long id) {
        Product product = dao.product(id);
        if (product == null) {
            throw new IllegalStateException("Không tìm thấy sản phẩm.");
        }
        return product;
    }

    private Sale requireDraft(long id) {
        Sale sale = dao.sale(id);
        if (sale == null || !"DRAFT".equals(sale.status)) {
            throw new IllegalStateException("Đơn đang tạo đã thay đổi.");
        }
        return sale;
    }

    private void setQuantity(SaleItem item, int quantity) {
        item.quantity = quantity;
        item.lineTotal = Amounts.line(item.unitPrice, quantity);
        if (item.id == 0) {
            dao.insertItem(item);
        } else {
            dao.updateItem(item);
        }
        updateTotal(item.orderId);
    }

    private void updateTotal(long orderId) {
        long total = 0;
        for (SaleItem item : dao.items(orderId)) {
            total = Amounts.add(total, item.lineTotal);
        }
        dao.updateDraftTotal(orderId, total);
    }

    private static String required(String value, String message) {
        String name = value.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return name;
    }
}
