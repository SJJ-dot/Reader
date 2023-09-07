package com.sjianjun.reader.utils;


import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

public class AesUtil {
    public static void main(String[] args) throws Exception {

        // Java
//        String decryptedData = decrypt(data, key, transformation, iv);
//
//        System.out.println("Java:");
//        System.out.println("Decrypted data: " + decryptedData.trim().replace("###$$$", ""));

    }

    public static byte[] encrypt(String data, String key, String transformation, String iv) throws Exception {
        return Base64.encode(encrypt(data.getBytes(StandardCharsets.UTF_8), key.getBytes(), transformation, iv.getBytes()), Base64.NO_WRAP);
    }

    public static byte[] encrypt(byte[] data, byte[] key, String transformation, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    public static String decrypt(String data, String key, String transformation, String iv) throws Exception {
        return new String(decrypt(Base64.decode(data, Base64.NO_WRAP), key.getBytes(), transformation, iv.getBytes()), StandardCharsets.UTF_8);
    }

    public static byte[] decrypt(byte[] data, byte[] key, String transformation, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }
}
