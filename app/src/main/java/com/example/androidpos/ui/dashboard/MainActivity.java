package com.example.androidpos.ui.dashboard;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.androidpos.R;
import com.example.androidpos.ui.PosFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.start_screen);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safeArea = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        BottomNavigationView navigation = findViewById(R.id.navigation);
        PosFragment existing = (PosFragment) getSupportFragmentManager().findFragmentById(R.id.screen);
        PosFragment page = existing == null ? new PosFragment() : existing;
        if (existing == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.screen, page).commitNow();
        }
        navigation.setOnItemSelectedListener(item -> {
            page.show(item.getItemId());
            return true;
        });
        navigation.setOnItemReselectedListener(item -> page.show(item.getItemId()));
    }

    public void navigate(int item) {
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setSelectedItemId(item);
    }
}
