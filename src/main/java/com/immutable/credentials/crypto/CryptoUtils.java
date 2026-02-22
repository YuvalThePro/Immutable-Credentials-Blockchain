package com.immutable.credentials.crypto;

import java.security.*;
import java.util.Base64;

/**
 * Cryptographic utilities for SHA-256 hashing and RSA signatures.
 * Provides methods for hash calculation, key generation, signing, and verification.
 */
public class CryptoUtils {
    
    /**
     * Calculate SHA-256 hash of an input string.
     * 
     * @param input the string to hash
     * @return hex-encoded SHA-256 hash
     */
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            
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
     * Generate an RSA key pair with 2048-bit keys.
     * 
     * @return a new KeyPair with public and private keys
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
     * Sign data with a private key using RSA.
     * 
     * @param data the string to sign
     * @param privateKey the private key for signing
     * @return Base64-encoded signature
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
     * Verify a signature with a public key.
     * 
     * @param data the original data that was signed
     * @param signatureStr the Base64-encoded signature
     * @param publicKey the public key for verification
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verifySignature(String data, String signatureStr, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes("UTF-8"));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Convert a key to a Base64-encoded string.
     * 
     * @param key the key to convert
     * @return Base64-encoded string representation of the key
     */
    public static String keyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }


    /**
     * Validate an Israeli ID number using the checksum algorithm.
     * 
     * @param id the Israeli ID to validate
     * @return true if the ID is valid, false otherwise
     */
    public static boolean isValidIsraeliId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        
        id = id.trim();
        
        if (!id.matches("\\d{9}")) {
            return false;
        }
        
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(id.charAt(i));
            
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit / 10) + (digit % 10);
                }
            }
            sum += digit;
        }
        
        return sum % 10 == 0;
    }



}

