package com.example.androidpos.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;

public final class AmountsAndPeriodsTest {
    @Test public void calculatesLineTotalOrderTotalAndChange() {
        assertEquals(65_000L, Amounts.add(Amounts.line(20_000L, 2), Amounts.line(25_000L, 1)));
        assertEquals(35_000L, Amounts.change(65_000L, 100_000L));
        assertEquals(0L, Amounts.parse(" 0 "));
    }

    @Test public void rejectsInvalidAmountsAndQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Amounts.line(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> Amounts.line(1, 0));
        assertThrows(IllegalArgumentException.class, () -> Amounts.parse("9223372036854775808"));
        assertThrows(IllegalArgumentException.class, () -> Amounts.change(10, 9));
    }

    @Test public void detectsBothKindsOfOverflow() {
        assertThrows(ArithmeticException.class, () -> Amounts.line(Long.MAX_VALUE, 2));
        assertThrows(ArithmeticException.class, () -> Amounts.add(Long.MAX_VALUE, 1));
    }

    @Test public void derivesDayMonthAndYearBoundariesInVietnamTime() {
        LocalDate date = LocalDate.of(2024, 2, 29);
        assertRange(Periods.range(date, Periods.Unit.DAY), "2024-02-28T17:00:00Z", "2024-02-29T17:00:00Z");
        assertRange(Periods.range(date, Periods.Unit.MONTH), "2024-01-31T17:00:00Z", "2024-02-29T17:00:00Z");
        assertRange(Periods.range(date, Periods.Unit.YEAR), "2023-12-31T17:00:00Z", "2024-12-31T17:00:00Z");
    }

    private void assertRange(Periods.Range range, String from, String until) {
        assertEquals(Instant.parse(from).toEpochMilli(), range.start());
        assertEquals(Instant.parse(until).toEpochMilli(), range.end());
    }
}
