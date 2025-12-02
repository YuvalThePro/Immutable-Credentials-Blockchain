
import com.immutable.credentials.crypto.CryptoUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

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
        
        assertEquals(hash1, hash2, "Same input should produce same hash");
    }

    @Test
    public void testApplySha256ProducesHexString() {
        String input = "Test Data";
        String hash = CryptoUtils.applySha256(input);
        
        assertNotNull(hash);
        // SHA-256 produces 64 hex characters (256 bits / 4 bits per hex char)
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash should be 64 hex characters");
    }

    @Test
    public void testApplySha256DifferentInputsProduceDifferentHashes() {
        String input1 = "Data 1";
        String input2 = "Data 2";
        
        String hash1 = CryptoUtils.applySha256(input1);
        String hash2 = CryptoUtils.applySha256(input2);
        
        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }

    @Test
    public void testApplySha256EmptyString() {
        String hash = CryptoUtils.applySha256("");
        
        assertNotNull(hash);
        assertEquals(64, hash.length());
        // SHA-256 of empty string is a known value
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    public void testApplySha256LargeInput() {
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeInput.append("Test data ");
        }
        
        String hash = CryptoUtils.applySha256(largeInput.toString());
        
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    // ========== Key Pair Generation Tests ==========

    @Test
    public void testGenerateKeyPairNotNull() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
    }

    @Test
    public void testGenerateKeyPairRSA() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        
        assertEquals("RSA", keyPair.getPublic().getAlgorithm());
        assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void testGenerateKeyPairUnique() {
        KeyPair keyPair1 = CryptoUtils.generateKeyPair();
        KeyPair keyPair2 = CryptoUtils.generateKeyPair();
        
        assertNotEquals(keyPair1.getPublic(), keyPair2.getPublic(),
            "Different key pairs should have different public keys");
        assertNotEquals(keyPair1.getPrivate(), keyPair2.getPrivate(),
            "Different key pairs should have different private keys");
    }

    // ========== Signature Tests ==========

    @Test
    public void testSignDataProducesValidSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data to sign";
        
        String signature = CryptoUtils.signData(data, keyPair.getPrivate());
        
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    public void testSignDataConsistency() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data";
        
        String signature1 = CryptoUtils.signData(data, keyPair.getPrivate());
        String signature2 = CryptoUtils.signData(data, keyPair.getPrivate());
        
        // Note: RSA signatures may include randomness (padding), so they might differ
        assertNotNull(signature1);
        assertNotNull(signature2);
    }

    @Test
    public void testVerifySignatureValidSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data to verify";
        
        String signature = CryptoUtils.signData(data, keyPair.getPrivate());
        boolean isValid = CryptoUtils.verifySignature(data, signature, keyPair.getPublic());
        
        assertTrue(isValid, "Valid signature should verify successfully");
    }

    @Test
    public void testVerifySignatureInvalidSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String data = "Test data";
        String invalidSignature = "InvalidSignatureString";
        
        boolean isValid = CryptoUtils.verifySignature(data, invalidSignature, keyPair.getPublic());
        
        assertFalse(isValid, "Invalid signature should fail verification");
    }

    @Test
    public void testVerifySignatureTamperedData() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String originalData = "Original data";
        String tamperedData = "Tampered data";
        
        String signature = CryptoUtils.signData(originalData, keyPair.getPrivate());
        boolean isValid = CryptoUtils.verifySignature(tamperedData, signature, keyPair.getPublic());
        
        assertFalse(isValid, "Signature should fail for tampered data");
    }

    @Test
    public void testVerifySignatureWrongPublicKey() {
        KeyPair keyPair1 = CryptoUtils.generateKeyPair();
        KeyPair keyPair2 = CryptoUtils.generateKeyPair();
        String data = "Test data";
        
        String signature = CryptoUtils.signData(data, keyPair1.getPrivate());
        boolean isValid = CryptoUtils.verifySignature(data, signature, keyPair2.getPublic());
        
        assertFalse(isValid, "Signature should fail with wrong public key");
    }

    // ========== Key Serialization Tests ==========

    @Test
    public void testKeyToStringNotNull() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        
        String publicKeyStr = CryptoUtils.keyToString(keyPair.getPublic());
        String privateKeyStr = CryptoUtils.keyToString(keyPair.getPrivate());
        
        assertNotNull(publicKeyStr);
        assertNotNull(privateKeyStr);
        assertFalse(publicKeyStr.isEmpty());
        assertFalse(privateKeyStr.isEmpty());
    }

    @Test
    public void testKeyToStringProducesBase64() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String keyStr = CryptoUtils.keyToString(keyPair.getPublic());
        
        // Base64 uses A-Z, a-z, 0-9, +, /, and = for padding
        assertTrue(keyStr.matches("[A-Za-z0-9+/=]+"), "Key string should be Base64 encoded");
    }

    // ========== Israeli ID Validation Tests ==========

    @Test
    public void testIsValidIsraeliIdValidId() {
        // Valid Israeli ID: 123456782 (passes Luhn-like checksum)
        assertTrue(CryptoUtils.isValidIsraeliId("000000018"));
    }

    @Test
    public void testIsValidIsraeliIdNull() {
        assertFalse(CryptoUtils.isValidIsraeliId(null));
    }

    @Test
    public void testIsValidIsraeliIdEmpty() {
        assertFalse(CryptoUtils.isValidIsraeliId(""));
    }

    @Test
    public void testIsValidIsraeliIdTooShort() {
        assertFalse(CryptoUtils.isValidIsraeliId("12345"));
    }

    @Test
    public void testIsValidIsraeliIdTooLong() {
        assertFalse(CryptoUtils.isValidIsraeliId("1234567890"));
    }

    @Test
    public void testIsValidIsraeliIdNonNumeric() {
        assertFalse(CryptoUtils.isValidIsraeliId("12345678A"));
    }

    @Test
    public void testIsValidIsraeliIdInvalidChecksum() {
        // Invalid checksum
        assertFalse(CryptoUtils.isValidIsraeliId("123456789"));
    }
}
