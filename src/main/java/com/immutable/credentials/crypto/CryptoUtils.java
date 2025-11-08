package com.immutable.credentials.crypto;

import java.security.*;
import java.util.Base64;

/**
 * Simple cryptographic utilities using SHA-256 and RSA
 */
public class CryptoUtils {
    
    /**
     * Calculate SHA-256 hash of input string
     * @param input String to hash
     * @return Hex string of hash
     */
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            
            // Convert to hex string
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Generate RSA key pair (2048 bits)
     * @return KeyPair with public and private keys
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Sign data with private key using RSA
     * @param data String to sign
     * @param privateKey Private key
     * @return Base64 encoded signature
     */
    public static String signData(String data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes("UTF-8"));
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Verify signature with public key
     * @param data Original data
     * @param signatureStr Base64 signature
     * @param publicKey Public key
     * @return true if valid
     */
    public static boolean verifySignature(String data, String signatureStr, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA"); // This algorithm is used because RSA signatures require hashing the data first so the size isnt too large for RSA to handle
            signature.initVerify(publicKey);
            signature.update(data.getBytes("UTF-8"));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Convert public key to Base64 string
     */
    public static String keyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}