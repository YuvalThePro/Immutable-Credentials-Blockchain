package com.immutable.credentials.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.immutable.credentials.util.Logger;

/**
 * Cryptographic utilities for SHA-256 hashing and RSA signatures.
 * Provides methods for hash calculation, key generation, signing, and
 * verification.
 */
public class CryptoUtils {

    /**
     * Compute the SHA-256 hex digest of the given UTF-8 string.
     * This is the canonical hash method used throughout the codebase,
     * including password hashing for cloud authentication and block hashing.
     *
     * @param input the string to hash; must not be null
     * @return a 64-character lowercase hex string representing the SHA-256 digest
     * @throws RuntimeException if SHA-256 is unavailable on this JVM
     */
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1)
                    hexString.append('0');
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
     * @param data       the string to sign
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
     * @param data         the original data that was signed
     * @param signatureStr the Base64-encoded signature
     * @param publicKey    the public key for verification
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verifySignature(String data, String signature, PublicKey publicKey) {
        try {
            signature = signature.trim().replace("\n", "").replace("\r", "");

            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));

            return sig.verify(signatureBytes);
        } catch (Exception e) {
            Logger.error("Signature verification error: " + e.getMessage());
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
     * Reconstruct an RSA PublicKey from a Base64-encoded X.509 string.
     *
     * @param base64 the Base64-encoded public key
     * @return the reconstructed PublicKey
     * @throws IllegalArgumentException if the string is null/empty or cannot be
     *                                  parsed
     */
    public static PublicKey publicKeyFromBase64(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            throw new IllegalArgumentException("Public key string cannot be null or empty");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64.replaceAll("\\s+", ""));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse public key: " + e.getMessage(), e);
        }
    }

    /**
     * Reconstruct an RSA PrivateKey from a Base64-encoded PKCS#8 string.
     *
     * @param base64 the Base64-encoded private key
     * @return the reconstructed PrivateKey
     * @throws IllegalArgumentException if the string is null/empty or cannot be
     *                                  parsed
     */
    public static PrivateKey privateKeyFromBase64(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key string cannot be null or empty");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64.replaceAll("\\s+", ""));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse private key: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a key pair AND persist the private key to disk.
     * Returns the full KeyPair so the caller can extract the public key
     * (e.g. to register it in validators.properties).
     *
     * @param privateKeyPath the file path to save the private key
     * @return the generated KeyPair
     * @throws IOException if the private key cannot be saved
     */
    public static KeyPair generateAndSaveKeyPair(String privateKeyPath) throws IOException {
        KeyPair keyPair = generateKeyPair();
        Path path = Paths.get(privateKeyPath);
        Files.createDirectories(path.getParent());
        Files.write(path, keyToString(keyPair.getPrivate()).getBytes("UTF-8"));
        return keyPair;
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
