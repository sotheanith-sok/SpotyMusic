package utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Utils {
    public static String hash(String source, String algorithm) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance(algorithm);

        } catch (NoSuchAlgorithmException e) {
            System.err.println("[Utils][hash] Requested hashing algorithm not supported: " + algorithm + " using MD5 instead");
            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e1) {
                return null;
            }
        }

        return Base64.getUrlEncoder().encodeToString(md.digest(source.getBytes()));
    }
}
