package com.android.tvremoteime;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Created by kingt on 2018/1/8.
 */

public class QRCodeGen {
    public static Bitmap generateBitmap(String content, int width, int height) {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, String> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        try {
            BitMatrix encode = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            // zxing encode 自带 quiet zone（白边），导致二维码图形居中偏右、看起来小；
            // 扫描黑点实际边界裁剪掉白边，使二维码图形占满画布（与标题左对齐、视觉更大）
            int minX = width, minY = height, maxX = -1, maxY = -1;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (encode.get(j, i)) {
                        if (j < minX) minX = j;
                        if (j > maxX) maxX = j;
                        if (i < minY) minY = i;
                        if (i > maxY) maxY = i;
                    }
                }
            }
            if (minX <= maxX && minY <= maxY) {
                int pad = 4; // 保留 4px 白边，防止图形紧贴边界导致扫码失败
                int left = Math.max(0, minX - pad);
                int top = Math.max(0, minY - pad);
                int cropW = Math.min(width - left, maxX - minX + 1 + pad * 2);
                int cropH = Math.min(height - top, maxY - minY + 1 + pad * 2);
                Bitmap full = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                int[] pixels = new int[width * height];
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        pixels[i * width + j] = encode.get(j, i) ? 0x00000000 : 0xffffffff;
                    }
                }
                full.setPixels(pixels, 0, width, 0, 0, width, height);
                Bitmap cropped = Bitmap.createBitmap(full, left, top, cropW, cropH);
                return Bitmap.createScaledBitmap(cropped, width, height, true);
            }
            int[] pixels = new int[width * height];
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (encode.get(j, i)) {
                        pixels[i * width + j] = 0x00000000;
                    } else {
                        pixels[i * width + j] = 0xffffffff;
                    }
                }
            }
            return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565);
        } catch (WriterException e) {
            e.printStackTrace();
        }
        return null;
    }
}
