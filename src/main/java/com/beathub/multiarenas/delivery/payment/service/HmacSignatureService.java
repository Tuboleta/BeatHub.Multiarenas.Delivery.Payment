package com.beathub.multiarenas.delivery.payment.service;

import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class HmacSignatureService {

    /**
     * Algoritmo de validación de firma HMAC SHA-256 según especificación Credibanco numeral 10.4.1
     */
    public String generateHMacSHA256(String key, String data) {
        try {
            Mac hMacSHA256 = Mac.getInstance("HmacSHA256");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
            hMacSHA256.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] res = hMacSHA256.doFinal(dataBytes);
            return new String(Hex.encodeHex(res)).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando HMAC-SHA256", e);
        }
    }

    public boolean verifySignature(String key, String data, String expectedSignature) {
        if (expectedSignature == null || key == null) return false;
        String calculated = generateHMacSHA256(key, data);
        return calculated.equalsIgnoreCase(expectedSignature);
    }
}
