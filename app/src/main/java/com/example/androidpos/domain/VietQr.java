package com.example.androidpos.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class VietQr {
    private VietQr() {}

    public record Recipient(String bankBin, String account) {
        public Recipient {
            bankBin = bankBin.trim();
            account = account.trim().toUpperCase(Locale.ROOT);
            if (!bankBin.matches("[0-9]{6}")) {
                throw new IllegalArgumentException("Nhập mã BIN ngân hàng gồm 6 chữ số.");
            }
            if (!account.matches("[A-Z0-9]{1,19}")) {
                throw new IllegalArgumentException("Số tài khoản phải có từ 1 đến 19 chữ hoặc số.");
            }
        }
    }

    public static String reference(String orderCode) {
        String code = orderCode.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Mã đơn không hợp lệ.");
        }
        return "POS" + code.substring(0, Math.min(16, code.length()));
    }

    public static String payload(Recipient recipient, long amount, String orderCode) {
        if (amount <= 0 || amount > 9_999_999_999_999L) {
            throw new IllegalArgumentException("Số tiền nằm ngoài phạm vi tạo VietQR.");
        }
        String beneficiary = field("00", recipient.bankBin()) + field("01", recipient.account());
        String transfer = field("00", "A000000727") + field("01", beneficiary) + field("02", "QRIBFTTA");
        String data = field("00", "01") + field("01", "12") + field("38", transfer)
                + field("53", "704") + field("54", Long.toString(amount)) + field("58", "VN")
                + field("62", field("08", reference(orderCode))) + "6304";
        return data + String.format(Locale.ROOT, "%04X", crc16(data));
    }

    private static String field(String id, String value) {
        return id + String.format(Locale.ROOT, "%02d", value.length()) + value;
    }

    private static int crc16(String data) {
        int crc = 0xffff;
        for (byte value : data.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (value & 0xff) << 8;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 0x8000) == 0 ? crc << 1 : (crc << 1) ^ 0x1021) & 0xffff;
            }
        }
        return crc;
    }
}
