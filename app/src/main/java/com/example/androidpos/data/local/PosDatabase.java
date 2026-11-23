package com.example.androidpos.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Category.class, Product.class, Sale.class, SaleItem.class}, version = 1)
public abstract class PosDatabase extends RoomDatabase {
    private static PosDatabase instance;

    public abstract PosDao posDao();

    public static synchronized PosDatabase get(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(), PosDatabase.class, "pos.db")
                    .build();
        }
        return instance;
    }
}
