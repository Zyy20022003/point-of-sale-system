package com.example.androidpos.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "order_items",
        foreignKeys = {
                @ForeignKey(entity = Sale.class, parentColumns = "id", childColumns = "order_id"),
                @ForeignKey(entity = Product.class, parentColumns = "id", childColumns = "product_id",
                        onDelete = ForeignKey.SET_NULL)
        },
        indices = {@Index(value = {"order_id", "product_id"}, unique = true), @Index("product_id")})
public class SaleItem {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "order_id") public long orderId;
    @ColumnInfo(name = "product_id") public Long productId;
    @NonNull @ColumnInfo(name = "product_name_snapshot") public String productName = "";
    @ColumnInfo(name = "unit_price_snapshot") public long unitPrice;
    public int quantity;
    @ColumnInfo(name = "line_total") public long lineTotal;
}
