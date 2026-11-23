package com.example.androidpos.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "orders",
        indices = {@Index(value = "order_code", unique = true), @Index({"status", "paid_at"}),
                @Index("created_at")})
public class Sale {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull @ColumnInfo(name = "order_code") public String code = "";
    @NonNull public String status = "DRAFT";
    @ColumnInfo(name = "payment_method") public String paymentMethod;
    @ColumnInfo(name = "total_amount") public long total;
    @ColumnInfo(name = "customer_paid") public Long customerPaid;
    @ColumnInfo(name = "change_amount") public Long change;
    @ColumnInfo(name = "created_at") public long createdAt;
    @ColumnInfo(name = "paid_at") public Long paidAt;
}
