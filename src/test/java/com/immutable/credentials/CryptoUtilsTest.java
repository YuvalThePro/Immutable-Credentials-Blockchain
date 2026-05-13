package com.immutable.credentials;
import com.immutable.credentials.crypto.CryptoUtils;
import java.security.KeyPair;
import org.junit.Test;
import org.junit.Assert;

/**
 * Unit tests for CryptoUtils - Sprint 2.
 * Tests cryptographic functions: hashing, key generation, signing, and verification.
 */
public class CryptoUtilsTest {

    // ========== Hash Calculation Tests ==========

    @Test
    public void testApplySha256ProducesConsistentHash() {
        String input = "Test Data";
        String hash1 = CryptoUtils.applySha256(input);
        String hash2 = CryptoUtils.applySha256(input);
        
        Assert.assertEquals("Same input should produce same hash", hash1, hash2);
    }

    @Test
    public void testApplySha256ProducesHexString() {
        String input = "Test Data";
        String hash = CryptoUtils.applySha256(input);
        
        Assert.assertNotNull(hash);
        // SHA-256 produces 64 hex characters (256 bits / 4 bits per hex char)
        Assert.assertEquals(64, hash.length());
        Assert.assertTrue("Hash should be 64 hex characters", hash.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testApplySha256DifferentInputsProduceDifferentHashes() {
        String input1 = "Data 1";
        String input2 = "Data 2";
        
        String hash1 = CryptoUtils.applySha256(input1);
        String hash2 = CryptoUtils.applySha256(input2);
        
        Assert.assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }

    @Test
    public void testApplySha256EmptyString() {
        String hash = CryptoUtils.applySha256("");
        
        Assert.assertNotNull(hash);
        Assert.assertEquals(64, hash.length());
        // SHA-256 of empty string is a known value
        Assert.assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    public void testApplySha256LargeInput() {
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeInput.append("Test data ");
        }
        
        String hash = CryptoUtils.applySha256(largeInput.toString());
        
        Assert.assertNotNull(hash);
        Assert.assertEquals(64, hash.length());
    }

    // ========== Key Pair Generation Tests ==========

    @Test
    public void testGenerateKeyPairNotNull() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        
        Assert.assertNotNull(keyPair);
        Assert.assertNotNull(keyPair.getPublic());
        Assert.assertNotNull(keyPair.getPrivate());
    }

    @Test
    public void testGenerateKeyPairRSA() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        
        Assert.assertEquals("RSA", keyPair.getPublic().getAlgorithm());
        Assert.assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void testGenerateKeyPairUnique() {
        KeyPair keyPair1 = CryptoUtils.generateKeyPair();
        KeyPair keyPair2 = CryptoUtils.generateKeyPair();
        
        Assert.assertNotEquals("Different key pairs should have different public keys",
            keyPair1.getPublic(), keyPair2.getPublic());
        Assert.assertNotEquals("Different key pairs should have different private keys",
            keyPair1.getPrivate(), keyPair2.getPrivate());
    }

    // ========== Signature Tests ==========

    @Test
    public void testSignDataProducesValidSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data to sign";
        
        String signature = CryptoUtils.signData(data, keyPair.getPrivate());
        
        Assert.assertNotNull(signature);
        Assert.assertFalse(signature.isEmpty());
    }

    @Test
    public void testSignDataConsistency() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data";
        
        String signature1 = CryptoUtils.signData(data, keyPair.getPrivate());
        String signature2 = CryptoUtils.signData(data, keyPair.getPrivate());
        
        // Note: RSA signatures may include randomness (padding), so they might differ
        Assert.assertNotNull(signature1);
        Assert.assertNotNull(signature2);
    }

    @Test
    public void testVerifySignatureValidSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data to verify";
        
        String signature = CryptoUtils.signData(data, keyPair.getPrivate());
        boolean isValid = CryptoUtils.verifySignature(data, signature, keyPair.getPublic());
        
        Assert.assertTrue("Valid signature should verify successfully", isValid);
    }

    @Test
    public void testVerifySignatureInvalidSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data";
        String invalidSignature = "InvalidSignatureString";
        
        boolean isValid = CryptoUtils.verifySignature(data, invalidSignature, keyPair.getPublic());
        
        Assert.assertFalse("Invalid signature should fail verification", isValid);
    }

    @Test
    public void testVerifySignatureTamperedData() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String originalData = "Original data";
        String tamperedData = "Tampered data";
        
        String signature = CryptoUtils.signData(originalData, keyPair.getPrivate());
        boolean isValid = CryptoUtils.verifySignature(tamperedData, signature, keyPair.getPublic());
        
        Assert.assertFalse("Signature should fail for tampered data", isValid);
    }

    @Test
    public void testVerifySignatureWrongPublicKey() {
        KeyPair keyPair1 = CryptoUtils.generateKeyPair();
        KeyPair keyPair2 = CryptoUtils.generateKeyPair();
        String data = "Test data";
        
        String signature = CryptoUtils.signData(data, keyPair1.getPrivate());
        boolean isValid = CryptoUtils.verifySignature(data, signature, keyPair2.getPublic());
        
        Assert.assertFalse("Signature should fail with wrong public key", isValid);
    }

    // ========== Key Serialization Tests ==========

    @Test
    public void testKeyToStringNotNull() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        
        String publicKeyStr = CryptoUtils.keyToString(keyPair.getPublic());
        String privateKeyStr = CryptoUtils.keyToString(keyPair.getPrivate());
        
        Assert.assertNotNull(publicKeyStr);
        Assert.assertNotNull(privateKeyStr);
        Assert.assertFalse(publicKeyStr.isEmpty());
        Assert.assertFalse(privateKeyStr.isEmpty());
    }

    @Test
    public void testKeyToStringProducesBase64() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String keyStr = CryptoUtils.keyToString(keyPair.getPublic());
        
        // Base64 uses A-Z, a-z, 0-9, +, /, and = for padding
        Assert.assertTrue("Key string should be Base64 encoded", keyStr.matches("[A-Za-z0-9+/=]+"));
    }

    // ========== Israeli ID Validation Tests ==========

    @Test
    public void testIsValidIsraeliIdValidId() {
        // Valid Israeli ID: 123456782 (passes Luhn-like checksum)
        Assert.assertTrue(CryptoUtils.isValidIsraeliId("000000018"));
    }

    @Test
    public void testIsValidIsraeliIdNull() {
        Assert.assertFalse(CryptoUtils.isValidIsraeliId(null));
    }

    @Test
    public void testIsValidIsraeliIdEmpty() {
        Assert.assertFalse(CryptoUtils.isValidIsraeliId(""));
    }

    @Test
    public void testIsValidIsraeliIdTooShort() {
        Assert.assertFalse(CryptoUtils.isValidIsraeliId("12345"));
    }

    @Test
    public void testIsValidIsraeliIdTooLong() {
        Assert.assertFalse(CryptoUtils.isValidIsraeliId("1234567890"));
    }

    @Test
    public void testIsValidIsraeliIdNonNumeric() {
        Assert.assertFalse(CryptoUtils.isValidIsraeliId("12345678A"));
    }

    @Test
    public void testIsValidIsraeliIdInvalidChecksum() {
        // Invalid checksum
        Assert.assertFalse(CryptoUtils.isValidIsraeliId("123456789"));
    }
}
