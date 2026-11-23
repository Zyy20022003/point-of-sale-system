package com.example.androidpos.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidpos.R;
import com.example.androidpos.data.local.Category;
import com.example.androidpos.data.local.Product;
import com.example.androidpos.data.repository.PosRepository;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class ProductGrid {
    private ProductGrid() {}

    static final class Filters {
        String search = "";
        long category;
        int sort;

        void save(Bundle state, String prefix) {
            state.putString(prefix + "Search", search);
            state.putLong(prefix + "Category", category);
            state.putInt(prefix + "Sort", sort);
        }

        void restore(Bundle state, String prefix) {
            search = state.getString(prefix + "Search", "");
            category = state.getLong(prefix + "Category");
            sort = state.getInt(prefix + "Sort");
        }
    }

    static void add(LinearLayout parent, PosViewModel model, PosRepository.Snapshot snapshot,
                    Filters filters, boolean selling, int columns, Consumer<Product> click) {
        Context context = parent.getContext();
        EditText search = Ui.field(parent, "Tìm món", filters.search, android.text.InputType.TYPE_CLASS_TEXT);
        List<String> options = new ArrayList<>();
        options.add("Tất cả danh mục");
        int selected = 0;
        for (Category category : snapshot.categories()) {
            options.add(category.name);
            if (category.id == filters.category) {
                selected = options.size() - 1;
            }
        }
        LinearLayout controls = Ui.row(context);
        parent.addView(controls);
        Spinner category = Ui.spinner(controls, options, selected);
        category.setContentDescription("Danh mục món");
        category.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Spinner sort = Ui.spinner(controls, java.util.Arrays.asList("Theo tên", "Giá tăng", "Giá giảm"),
                filters.sort);
        sort.setContentDescription("Sắp xếp món");
        sort.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView empty = Ui.text(parent, "", 16);
        RecyclerView grid = new RecyclerView(context);
        grid.setLayoutManager(new GridLayoutManager(context, columns));
        Cards cards = new Cards(model, click);
        grid.setAdapter(cards);
        parent.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        Runnable update = () -> {
            filters.search = search.getText().toString();
            int index = category.getSelectedItemPosition() - 1;
            filters.category = index >= 0 ? snapshot.categories().get(index).id : 0;
            filters.sort = sort.getSelectedItemPosition();
            String term = filters.search.trim().toLowerCase(Ui.VI);
            List<Product> matching = new ArrayList<>();
            for (Product product : snapshot.products()) {
                if ((!selling || product.active) && (filters.category == 0 || product.categoryId == filters.category)
                        && product.name.toLowerCase(Ui.VI).contains(term)) {
                    matching.add(product);
                }
            }
            Collator collator = Collator.getInstance(Ui.VI);
            Comparator<Product> byName = (a, b) -> collator.compare(a.name, b.name);
            Comparator<Product> byPrice = Comparator.comparingLong(p -> p.price);
            if (filters.sort == 1) {
                matching.sort(byPrice.thenComparing(byName));
            } else if (filters.sort == 2) {
                matching.sort(byPrice.reversed().thenComparing(byName));
            } else {
                matching.sort(byName);
            }
            cards.submitList(matching);
            empty.setText(snapshot.products().isEmpty() ? "Thực đơn chưa có món." : "Không tìm thấy món.");
            empty.setVisibility(matching.isEmpty() ? View.VISIBLE : View.GONE);
        };
        Ui.whenTextChanges(search, value -> update.run());
        category.setOnItemSelectedListener(selection(update));
        sort.setOnItemSelectedListener(selection(update));
        update.run();
    }

    static AdapterView.OnItemSelectedListener selection(Runnable update) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
    }

    private static final class Cards extends ListAdapter<Product, Card> {
        private static final DiffUtil.ItemCallback<Product> DIFF = new DiffUtil.ItemCallback<Product>() {
            @Override public boolean areItemsTheSame(@NonNull Product a, @NonNull Product b) {
                return a.id == b.id;
            }

            @Override public boolean areContentsTheSame(@NonNull Product a, @NonNull Product b) {
                return a.name.equals(b.name) && a.price == b.price && a.active == b.active
                        && Objects.equals(a.imagePath, b.imagePath);
            }
        };

        private final PosViewModel model;
        private final Consumer<Product> click;

        Cards(PosViewModel model, Consumer<Product> click) {
            super(DIFF);
            this.model = model;
            this.click = click;
        }

        @NonNull @Override public Card onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Card(LayoutInflater.from(parent.getContext()).inflate(R.layout.product_card, parent, false));
        }

        @Override public void onViewRecycled(@NonNull Card card) {
            model.cancelImage(card.image);
            super.onViewRecycled(card);
        }

        @Override public void onBindViewHolder(@NonNull Card card, int position) {
            Product product = getItem(position);
            card.name.setText(product.name);
            String formatted = Ui.money(product.price);
            card.price.setText(product.active ? formatted
                    : card.price.getContext().getString(R.string.inactive_price, formatted));
            model.loadImage(product.imagePath, card.image);
            card.itemView.setFocusable(true);
            card.itemView.setOnClickListener(view -> click.accept(product));
        }
    }

    private static final class Card extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView price;
        final ImageView image;

        Card(View view) {
            super(view);
            name = view.findViewById(R.id.product_name);
            price = view.findViewById(R.id.product_price);
            image = view.findViewById(R.id.product_image);
        }
    }
}
