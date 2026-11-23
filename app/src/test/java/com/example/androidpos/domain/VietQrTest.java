package com.example.androidpos.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;

import org.junit.Test;

public final class VietQrTest {
    private final VietQr.Recipient recipient = new VietQr.Recipient("999999", "00123456789");

    @Test public void producesExpectedTransferFieldsChecksumAndScannableSymbol() throws Exception {
        String payload = VietQr.payload(recipient, 25_000, "abcdef12-3456-7890-abcd");
        assertEquals("00020101021238550010A000000727012500069999990111001234567890208QRIBFTTA"
                + "53037045405250005802VN62230819POSABCDEF12345678906304E006", payload);

        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 224, 224);
        int[] pixels = new int[224 * 224];
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 224; x++) {
                pixels[y * 224 + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(224, 224, pixels)));
        assertEquals(payload, new QRCodeReader().decode(bitmap).getText());
    }

    @Test public void rejectsMalformedRecipientsAndUnsupportedAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new VietQr.Recipient("123", "001234"));
        assertThrows(IllegalArgumentException.class, () -> new VietQr.Recipient("999999", "00 1234"));
        assertThrows(IllegalArgumentException.class, () -> VietQr.payload(recipient, 0, "ORDER"));
        assertThrows(IllegalArgumentException.class, () -> VietQr.payload(recipient, Long.MAX_VALUE, "ORDER"));
    }
}
