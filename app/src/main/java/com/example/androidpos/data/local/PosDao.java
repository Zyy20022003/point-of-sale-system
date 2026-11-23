package com.example.androidpos.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PosDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE, id")
    List<Category> categories();

    @Insert
    long insertCategory(Category category);

    @Update
    void updateCategory(Category category);

    @Query("DELETE FROM categories WHERE id = :id AND NOT EXISTS "
            + "(SELECT 1 FROM products WHERE category_id = :id)")
    int deleteEmptyCategory(long id);

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE, id")
    List<Product> products();

    @Query("SELECT * FROM products WHERE id = :id")
    Product product(long id);

    @Insert
    long insertProduct(Product product);

    @Update
    void updateProduct(Product product);

    @Query("SELECT DISTINCT image_path FROM products WHERE image_path IS NOT NULL")
    List<String> imagePaths();

    @Query("SELECT * FROM orders WHERE status = 'DRAFT' ORDER BY id LIMIT 1")
    Sale draft();

    @Query("SELECT * FROM orders WHERE id = :id")
    Sale sale(long id);

    @Query("SELECT * FROM orders ORDER BY created_at DESC, id DESC")
    List<Sale> history();

    @Query("SELECT * FROM orders WHERE status = 'PAID' AND paid_at >= :start AND paid_at < :end "
            + "ORDER BY paid_at DESC, id DESC")
    List<Sale> paidSales(long start, long end);

    @Insert
    long insertSale(Sale sale);

    @Query("UPDATE orders SET total_amount = :total WHERE id = :id AND status = 'DRAFT'")
    int updateDraftTotal(long id, long total);

    @Query("UPDATE orders SET status = 'CANCELLED' WHERE id = :id AND status = 'DRAFT'")
    int cancelDraft(long id);

    @Query("UPDATE orders SET status = 'PAID', paid_at = :now, payment_method = :method, "
            + "customer_paid = :cash, change_amount = :change WHERE id = :id AND status = 'DRAFT'")
    int payDraft(long id, long now, String method, Long cash, Long change);

    @Query("SELECT * FROM order_items WHERE order_id = :orderId ORDER BY id")
    List<SaleItem> items(long orderId);

    @Query("SELECT * FROM order_items WHERE order_id = :orderId AND product_id = :productId")
    SaleItem item(long orderId, long productId);

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM order_items INNER JOIN orders "
            + "ON order_items.order_id = orders.id WHERE status = 'PAID' "
            + "AND paid_at >= :start AND paid_at < :end")
    long paidQuantity(long start, long end);

    @Insert
    long insertItem(SaleItem item);

    @Update
    void updateItem(SaleItem item);

    @Query("DELETE FROM order_items WHERE id = :id")
    void deleteItem(long id);
}
