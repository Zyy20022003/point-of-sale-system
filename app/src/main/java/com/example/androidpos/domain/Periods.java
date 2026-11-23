package com.example.androidpos.domain;

import java.time.LocalDate;
import java.time.ZoneId;

public final class Periods {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public enum Unit { DAY, MONTH, YEAR }

    public record Range(long start, long end) {}

    private Periods() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static Range range(LocalDate date, Unit unit) {
        LocalDate start;
        LocalDate end;
        switch (unit) {
            case YEAR:
                start = date.withDayOfYear(1);
                end = start.plusYears(1);
                break;
            case MONTH:
                start = date.withDayOfMonth(1);
                end = start.plusMonths(1);
                break;
            default:
                start = date;
                end = start.plusDays(1);
        }
        return new Range(start.atStartOfDay(ZONE).toInstant().toEpochMilli(),
                end.atStartOfDay(ZONE).toInstant().toEpochMilli());
    }
}
