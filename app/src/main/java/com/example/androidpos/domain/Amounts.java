package com.example.androidpos.domain;

public final class Amounts {
    private Amounts() {}

    public static long line(long price, int quantity) {
        if (price < 0 || quantity <= 0) {
            throw new IllegalArgumentException("Giá hoặc số lượng không hợp lệ.");
        }
        return Math.multiplyExact(price, quantity);
    }

    public static long add(long total, long amount) {
        return Math.addExact(total, amount);
    }

    public static long change(long total, long cash) {
        if (total < 0 || cash < total) {
            throw new IllegalArgumentException("Tiền khách đưa chưa đủ.");
        }
        return cash - total;
    }

    public static long parse(String value) {
        try {
            long amount = Long.parseLong(value.trim());
            if (amount >= 0) {
                return amount;
            }
        } catch (NumberFormatException ignored) {
            // Report invalid input and values outside the supported range together.
        }
        throw new IllegalArgumentException("Nhập số tiền nguyên không âm, không có dấu phân cách.");
    }
}
