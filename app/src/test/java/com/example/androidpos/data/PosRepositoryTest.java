package com.example.androidpos.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.androidpos.data.local.PosDatabase;
import com.example.androidpos.data.local.Sale;
import com.example.androidpos.data.local.SaleItem;
import com.example.androidpos.data.repository.PosRepository;
import com.example.androidpos.domain.Periods;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.math.BigInteger;
import java.time.LocalDate;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public final class PosRepositoryTest {
    private Context context;
    private PosDatabase database;
    private PosRepository repository;
    private long category;

    @Before public void openDatabase() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, PosDatabase.class).allowMainThreadQueries().build();
        repository = new PosRepository(database);
        repository.saveCategory(0, "Đồ uống");
        category = repository.snapshot().categories().get(0).id;
    }

    @After public void closeDatabase() {
        database.close();
    }

    @Test public void productStorageEnforcesForeignKeyAndPreservesOptionalImage() {
        assertThrows(SQLiteConstraintException.class, () -> repository.saveProduct(0, -1, "Sai nhóm", 5, null));
        long coffee = product("Cà phê", 20_000);
        assertNull(database.posDao().product(coffee).imagePath);
        repository.saveProduct(coffee, category, "Cà phê sữa", 25_000, "photo-key");
        assertEquals("photo-key", database.posDao().product(coffee).imagePath);
        assertEquals(25_000, database.posDao().product(coffee).price);
        repository.saveProduct(coffee, category, "Cà phê sữa", 25_000, null);
        assertNull(database.posDao().product(coffee).imagePath);
        repository.setActive(coffee, false);
        assertFalse(database.posDao().product(coffee).active);
        assertThrows(IllegalStateException.class, () -> repository.addToCart(coffee));
        assertThrows(IllegalStateException.class, () -> repository.deleteCategory(category));
    }

    @Test public void orderCodeIsUniqueAndEmptyCategoryCanBeRemoved() {
        Sale first = new Sale();
        first.code = "TEST";
        database.posDao().insertSale(first);
        Sale second = new Sale();
        second.code = first.code;
        assertThrows(SQLiteConstraintException.class, () -> database.posDao().insertSale(second));
        repository.deleteCategory(category);
        assertEquals(0, repository.snapshot().categories().size());
    }

    @Test public void cartUsesOneDraftAndRollsBackOverflow() {
        long coffee = product("Cà phê", Long.MAX_VALUE);
        long tea = product("Trà", 1);
        repository.addToCart(coffee);
        long order = repository.snapshot().cart().sale().id;
        assertThrows(ArithmeticException.class, () -> repository.addToCart(tea));
        assertThrows(ArithmeticException.class, () -> repository.addToCart(coffee));
        assertEquals(1, repository.snapshot().history().size());
        assertEquals(Long.MAX_VALUE, database.posDao().sale(order).total);
        assertEquals(1, repository.items(order).size());
        assertEquals(1, repository.items(order).get(0).quantity);
    }

    @Test public void cashCheckoutIsAtomicAndCannotBeRepeated() {
        long coffee = product("Cà phê", 20_000);
        repository.addToCart(coffee);
        long order = repository.snapshot().cart().sale().id;
        repository.addToCart(coffee);
        assertThrows(IllegalStateException.class, () -> repository.pay(order, 20_000, true, 100_000L));
        assertThrows(IllegalArgumentException.class, () -> repository.pay(order, 40_000, true, 39_999L));
        assertEquals("DRAFT", database.posDao().sale(order).status);
        repository.pay(order, 40_000, true, 50_000L);
        assertThrows(IllegalStateException.class, () -> repository.pay(order, 40_000, false, null));
        assertEquals("PAID", database.posDao().sale(order).status);
        assertEquals(Long.valueOf(10_000), database.posDao().sale(order).change);
        assertEquals(BigInteger.valueOf(40_000), repository.snapshot().today().revenue());
        repository.saveProduct(coffee, category, "Cà phê sữa", 30_000, null);
        SaleItem item = repository.items(order).get(0);
        assertEquals("Cà phê", item.productName);
        assertEquals(20_000, item.unitPrice);
        assertEquals(40_000, database.posDao().sale(order).total);
    }

    @Test public void reportsCountOnlyPaidOrdersInsideAllThreePeriods() {
        LocalDate day = LocalDate.of(2024, 2, 29);
        Periods.Range dayRange = Periods.range(day, Periods.Unit.DAY);
        Periods.Range month = Periods.range(day, Periods.Unit.MONTH);
        Periods.Range year = Periods.range(day, Periods.Unit.YEAR);
        historical("starts", "PAID", dayRange.start(), 500, 2);
        historical("next", "PAID", dayRange.end(), 800, 1);
        historical("previous", "PAID", month.start() - 1, 200, 3);
        historical("next-year", "PAID", year.end(), 950, 1);
        historical("draft", "DRAFT", dayRange.start(), 700, 9);
        historical("cancelled", "CANCELLED", dayRange.start(), 600, 9);
        PosRepository.Report daily = repository.report(day, Periods.Unit.DAY);
        assertEquals(BigInteger.valueOf(500), daily.revenue());
        assertEquals(2, daily.quantity());
        assertEquals(1, daily.sales().size());
        PosRepository.Report monthly = repository.report(day, Periods.Unit.MONTH);
        assertEquals(BigInteger.valueOf(500), monthly.revenue());
        PosRepository.Report yearly = repository.report(day, Periods.Unit.YEAR);
        assertEquals(BigInteger.valueOf(1_500), yearly.revenue());
        assertEquals(6, yearly.quantity());
        assertEquals(3, yearly.buckets().size());
    }

    @Test public void preservesDraftAndPaidHistoryAfterOpeningDatabaseAgain() {
        String name = "pos-reopen-test.db";
        context.deleteDatabase(name);
        database.close();
        database = open(name);
        repository = new PosRepository(database);
        repository.saveCategory(0, "Đồ uống");
        category = repository.snapshot().categories().get(0).id;
        long coffee = product("Cà phê", 20_000);
        repository.addToCart(coffee);
        long order = repository.snapshot().cart().sale().id;
        database.close();
        database = open(name);
        repository = new PosRepository(database);
        assertEquals(order, repository.snapshot().cart().sale().id);
        repository.pay(order, 20_000, false, null);
        assertNull(database.posDao().sale(order).customerPaid);
        database.close();
        database = open(name);
        repository = new PosRepository(database);
        assertNull(repository.snapshot().cart().sale());
        assertEquals("TRANSFER", database.posDao().sale(order).paymentMethod);
        assertEquals("Cà phê", repository.items(order).get(0).productName);
        database.close();
        context.deleteDatabase(name);
    }

    @Test public void updatesAndRemovesCartItemsAndRecordsCancellation() {
        long tea = product("Trà", 5_000);
        long coffee = product("Cà phê", 20_000);
        repository.addToCart(tea);
        repository.addToCart(coffee);
        long order = repository.snapshot().cart().sale().id;
        assertThrows(IllegalArgumentException.class, () -> repository.changeQuantity(order, tea, -1));
        repository.changeQuantity(order, tea, 1);
        repository.removeItem(order, coffee);
        assertEquals(10_000, repository.snapshot().cart().sale().total);
        repository.cancel(order);
        assertNull(repository.snapshot().cart().sale());
        assertEquals("CANCELLED", database.posDao().sale(order).status);
        assertEquals(0, repository.snapshot().today().sales().size());
    }

    @Test public void seedsSampleCatalogWithoutChangingExistingProductsOrRevenue() {
        long realProduct = product("Cà phê", 20_000);
        repository.seedSampleCatalog();
        repository.seedSampleCatalog();

        PosRepository.Snapshot snapshot = repository.snapshot();
        assertEquals(3, snapshot.categories().size());
        assertEquals(7, snapshot.products().size());
        assertEquals(6, snapshot.products().stream().filter(product -> product.name.startsWith("[Mẫu]")).count());
        assertEquals(20_000, database.posDao().product(realProduct).price);
        assertEquals(BigInteger.ZERO, snapshot.today().revenue());
        assertEquals(0, snapshot.history().size());
    }

    private PosDatabase open(String name) {
        return Room.databaseBuilder(context, PosDatabase.class, name).allowMainThreadQueries().build();
    }

    private long product(String name, long price) {
        repository.saveProduct(0, category, name, price, null);
        return repository.snapshot().products().stream()
                .filter(product -> product.name.equals(name)).findFirst().get().id;
    }

    private void historical(String code, String status, long paidAt, long total, int quantity) {
        Sale sale = new Sale();
        sale.code = code;
        sale.status = status;
        sale.total = total;
        sale.paidAt = paidAt;
        long id = database.posDao().insertSale(sale);
        SaleItem item = new SaleItem();
        item.orderId = id;
        item.productName = "Món";
        item.quantity = quantity;
        database.posDao().insertItem(item);
    }
}
