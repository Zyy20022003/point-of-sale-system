package com.example.androidpos.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "products",
        foreignKeys = @ForeignKey(entity = Category.class, parentColumns = "id", childColumns = "category_id"),
        indices = {@Index("category_id"), @Index("name"), @Index("image_path")})
public class Product {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "category_id") public long categoryId;
    @NonNull public String name = "";
    public long price;
    @ColumnInfo(name = "is_active") public boolean active = true;
    @ColumnInfo(name = "image_path") public String imagePath;
    @ColumnInfo(name = "created_at") public long createdAt;
    @ColumnInfo(name = "updated_at") public long updatedAt;
}
