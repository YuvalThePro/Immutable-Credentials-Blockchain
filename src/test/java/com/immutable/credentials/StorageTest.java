package com.immutable.credentials;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;
import com.immutable.credentials.storage.BlockchainStorage;
import com.immutable.credentials.storage.CredentialIndex;
import com.immutable.credentials.util.JsonSerializer;

/**
 * Unit tests for Sprint 3: Storage & Persistence functionality.
 * Tests JSON serialization, blockchain storage, indexing, and configuration
 * loading.
 */
public class StorageTest {

    private Blockchain blockchain;
    private BlockchainStorage storage;
    private CredentialIndex index;
    private KeyPair validatorKeyPair;
    private HashMap<String, PublicKey> validatorKeys;
    private String testStorageFile;
    private String testBackupFile;

    @Before
    public void setUp() {
        blockchain = new Blockchain();
        storage = new BlockchainStorage();
        index = new CredentialIndex();
        validatorKeyPair = CryptoUtils.generateKeyPair();

        validatorKeys = new HashMap<>();
        validatorKeys.put("SYSTEM", validatorKeyPair.getPublic());
        validatorKeys.put("VALIDATOR_001", validatorKeyPair.getPublic());

        // Use test-specific file paths
        testStorageFile = "test_blockchain.jsonl";
        testBackupFile = "test_blockchain_backup.jsonl";
    }

    @After
    public void tearDown() {
        // Clean up test files
        deleteFileIfExists(testStorageFile);
        deleteFileIfExists(testBackupFile);
    }

    private void deleteFileIfExists(String filename) {
        try {
            Path path = Paths.get(filename);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Helper method to create a test credential with auto-generated ID.
     */
    private Credential createTestCredential(String studentId, String studentName, String degree, String institution) {
        return new Credential(studentName, new Date(), degree, institution, studentId,
                studentId + "_" + degree.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis());
    }

    // ========== JSON Serialization Tests ==========

    @Test
    public void testBlockToJson() {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        Block block = new Block(1, "0", credentials, "VALIDATOR_001");
        String json = JsonSerializer.blockToJson(block);

        Assert.assertNotNull("JSON should not be null", json);
        Assert.assertTrue("JSON should contain index", json.contains("\"index\":1"));
        Assert.assertTrue("JSON should contain validatorId", json.contains("\"validatorId\":\"VALIDATOR_001\""));
        Assert.assertTrue("JSON should contain credentials array", json.contains("\"credentials\":["));
    }

    @Test
    public void testJsonToBlock() {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        Block original = new Block(1, "0", credentials, "VALIDATOR_001");
        String json = JsonSerializer.blockToJson(original);
        Block deserialized = JsonSerializer.jsonToBlock(json);

        Assert.assertNotNull("Deserialized block should not be null", deserialized);
        Assert.assertEquals("Index should match", original.getIndex(), deserialized.getIndex());
        Assert.assertEquals("Hash should match", original.getHash(), deserialized.getHash());
        Assert.assertEquals("Previous hash should match", original.getPreviousHash(), deserialized.getPreviousHash());
        Assert.assertEquals("Validator ID should match", original.getValidatorId(), deserialized.getValidatorId());
        Assert.assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
        Assert.assertEquals("Credentials count should match", original.getCredentials().size(),
                deserialized.getCredentials().size());
    }

    @Test
    public void testChainToJson() {
        ArrayList<Credential> credentials1 = new ArrayList<>();
        credentials1.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        ArrayList<Credential> credentials2 = new ArrayList<>();
        credentials2.add(createTestCredential("student2", "Jane Smith", "Master of Mathematics", "University B"));

        Block block1 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials1,
                "VALIDATOR_001");
        Block block2 = new Block(blockchain.size() + 1, blockchain.getLatestBlock().getHash(), credentials2,
                "VALIDATOR_001");
        blockchain.addBlock(block1);
        blockchain.addBlock(block2);

        String json = JsonSerializer.chainToJson(blockchain.getChain());

        Assert.assertNotNull("JSON should not be null", json);
        Assert.assertTrue("JSON should be a JSON array", json.trim().startsWith("["));
        Assert.assertTrue("JSON should end with array bracket", json.trim().endsWith("]"));
    }

    @Test
    public void testJsonToChain() {
        ArrayList<Credential> credentials1 = new ArrayList<>();
        credentials1.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        ArrayList<Credential> credentials2 = new ArrayList<>();
        credentials2.add(createTestCredential("student2", "Jane Smith", "Master of Mathematics", "University B"));

        Block block1 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials1,
                "VALIDATOR_001");
        blockchain.addBlock(block1);
        Block block2 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials2,
                "VALIDATOR_001");
        blockchain.addBlock(block2);

        String json = JsonSerializer.chainToJson(blockchain.getChain());
        ArrayList<Block> deserializedChain = JsonSerializer.jsonToChain(json);

        Assert.assertNotNull("Deserialized chain should not be null", deserializedChain);
        Assert.assertEquals("Chain size should match", blockchain.size(), deserializedChain.size());

        for (int i = 0; i < blockchain.size(); i++) {
            Block original = blockchain.getBlock(i);
            Block deserialized = deserializedChain.get(i);
            Assert.assertEquals("Block " + i + " hash should match", original.getHash(), deserialized.getHash());
        }
    }

    @Test
    public void testSerializationPreservesHash() {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        Block original = new Block(1, "0", credentials, "VALIDATOR_001");
        String json = JsonSerializer.blockToJson(original);
        Block deserialized = JsonSerializer.jsonToBlock(json);

        Assert.assertTrue("Deserialized block should have valid hash", deserialized.isHashValid());
        Assert.assertEquals("Hash should be preserved exactly", original.getHash(), deserialized.getHash());
    }

    // ========== Blockchain Storage Tests ==========

    @Test
    public void testSaveChain() throws Exception {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        Block block = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials, "VALIDATOR_001");
        blockchain.addBlock(block);

        storage.saveChain(blockchain, testStorageFile);

        File file = new File("data", testStorageFile);
        Assert.assertTrue("Storage file should exist", file.exists());
        Assert.assertTrue("Storage file should not be empty", file.length() > 0);
    }

    @Test
    public void testLoadChain() throws Exception {
        ArrayList<Credential> credentials1 = new ArrayList<>();
        credentials1.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        ArrayList<Credential> credentials2 = new ArrayList<>();
        credentials2.add(createTestCredential("student2", "Jane Smith", "Master of Mathematics", "University B"));

        Block block1 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials1,
                "VALIDATOR_001");
        blockchain.addBlock(block1);
        Block block2 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials2,
                "VALIDATOR_001");
        blockchain.addBlock(block2);

        storage.saveChain(blockchain, testStorageFile);

        Blockchain loadedBlockchain = storage.loadChain(testStorageFile);

        Assert.assertNotNull("Loaded blockchain should not be null", loadedBlockchain);
        Assert.assertEquals("Chain size should match", blockchain.size(), loadedBlockchain.size());

        for (int i = 0; i < blockchain.size(); i++) {
            Assert.assertEquals("Block " + i + " hash should match",
                    blockchain.getBlock(i).getHash(),
                    loadedBlockchain.getBlock(i).getHash());
        }
    }

    @Test
    public void testPersistenceAcrossRestarts() throws Exception {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        Block block = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials, "VALIDATOR_001");
        blockchain.addBlock(block);
        String originalHash = blockchain.getBlock(1).getHash();

        // Simulate restart: save, destroy, and reload
        storage.saveChain(blockchain, testStorageFile);
        blockchain = null;

        Blockchain reloadedBlockchain = storage.loadChain(testStorageFile);

        Assert.assertNotNull("Reloaded blockchain should not be null", reloadedBlockchain);
        Assert.assertEquals("Chain size should be preserved", 2, reloadedBlockchain.size());
        Assert.assertEquals("Block hash should be preserved", originalHash, reloadedBlockchain.getBlock(1).getHash());
    }

    @Test
    public void testBackupCreation() throws Exception {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        Block block = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials, "VALIDATOR_001");
        blockchain.addBlock(block);

        storage.saveChain(blockchain, testStorageFile);
        storage.createBackup(testStorageFile, testBackupFile);

        File backupFile = new File("data", testBackupFile);
        Assert.assertTrue("Backup file should exist", backupFile.exists());

        // Verify backup is identical to original
        Blockchain originalChain = storage.loadChain(testStorageFile);
        Blockchain backupChain = storage.loadChain(testBackupFile);

        Assert.assertEquals("Backup chain size should match", originalChain.size(), backupChain.size());
        for (int i = 0; i < originalChain.size(); i++) {
            Assert.assertEquals("Backup block " + i + " hash should match",
                    originalChain.getBlock(i).getHash(),
                    backupChain.getBlock(i).getHash());
        }
    }

    // ========== Credential Index Tests ==========

    @Test
    public void testAddCredentialToIndex() {
        ArrayList<Credential> credentials = new ArrayList<>();
        Credential cred = createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A");
        credentials.add(cred);

        index.addCredentials(credentials);

        List<Credential> found = index.getCredentialsByStudentId("student1");
        Assert.assertNotNull("Found credentials should not be null", found);
        Assert.assertEquals("Should find 1 credential", 1, found.size());
        Assert.assertEquals("Student name should match", "John Doe", found.get(0).getStudentName());
    }

    @Test
    public void testIndexByStudentId() {
        ArrayList<Credential> credentials1 = new ArrayList<>();
        credentials1.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));
        credentials1.add(createTestCredential("student1", "John Doe", "Minor in Mathematics", "University A"));

        ArrayList<Credential> credentials2 = new ArrayList<>();
        credentials2.add(createTestCredential("student2", "Jane Smith", "Master of Physics", "University B"));

        index.addCredentials(credentials1);
        index.addCredentials(credentials2);

        List<Credential> student1Creds = index.getCredentialsByStudentId("student1");
        Assert.assertEquals("Student1 should have 2 credentials", 2, student1Creds.size());

        List<Credential> student2Creds = index.getCredentialsByStudentId("student2");
        Assert.assertEquals("Student2 should have 1 credential", 1, student2Creds.size());
    }

    @Test
    public void testIndexByCredentialId() {
        Credential cred = createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A");
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(cred);

        index.addCredentials(credentials);

        Credential found = index.getCredentialById(cred.getCredentialId());
        Assert.assertNotNull("Credential should be found by ID", found);
        Assert.assertEquals("Credential ID should match", cred.getCredentialId(), found.getCredentialId());
    }

    @Test
    public void testRebuildIndex() {
        ArrayList<Credential> credentials1 = new ArrayList<>();
        credentials1.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        ArrayList<Credential> credentials2 = new ArrayList<>();
        credentials2.add(createTestCredential("student2", "Jane Smith", "Master of Mathematics", "University B"));

        Block block1 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials1,
                "VALIDATOR_001");
        blockchain.addBlock(block1);
        Block block2 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials2,
                "VALIDATOR_001");
        blockchain.addBlock(block2);

        index.rebuildIndex(blockchain);

        Assert.assertEquals("Index should have 2 students", 2, index.getStudentCount());
        Assert.assertEquals("Index should have 2 credentials", 2, index.getCredentialCount());

        List<Credential> found = index.getCredentialsByStudentId("student1");
        Assert.assertEquals("Should find student1's credential", 1, found.size());
    }

    @Test
    public void testIndexAccuracy() {
        ArrayList<Credential> credentials1 = new ArrayList<>();
        credentials1.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));
        credentials1.add(createTestCredential("student1", "John Doe", "Minor in Mathematics", "University A"));

        ArrayList<Credential> credentials2 = new ArrayList<>();
        credentials2.add(createTestCredential("student2", "Jane Smith", "Master of Physics", "University B"));

        Block block1 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials1,
                "VALIDATOR_001");
        blockchain.addBlock(block1);
        Block block2 = new Block(blockchain.size(), blockchain.getLatestBlock().getHash(), credentials2,
                "VALIDATOR_001");
        blockchain.addBlock(block2);

        index.rebuildIndex(blockchain);

        // Verify all credentials are indexed
        List<Credential> student1Creds = index.getCredentialsByStudentId("student1");
        Assert.assertEquals("Student1 should have 2 credentials", 2, student1Creds.size());

        // Verify index counts
        Assert.assertEquals("Should have 2 unique students", 2, index.getStudentCount());
        Assert.assertEquals("Should have 3 total credentials", 3, index.getCredentialCount());
    }

    @Test
    public void testIndexClear() {
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A"));

        index.addCredentials(credentials);
        Assert.assertEquals("Index should have 1 credential", 1, index.getCredentialCount());

        index.clear();
        Assert.assertEquals("Index should be empty after clear", 0, index.getCredentialCount());
        Assert.assertEquals("Student count should be 0 after clear", 0, index.getStudentCount());
    }

    @Test
    public void testHasCredential() {
        Credential cred = createTestCredential("student1", "John Doe", "Bachelor of Computer Science", "University A");
        ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(cred);

        index.addCredentials(credentials);

        Assert.assertTrue("Should have the credential", index.hasCredential(cred.getCredentialId()));
        Assert.assertFalse("Should not have non-existent credential", index.hasCredential("non-existent-id"));
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testLoadNonExistentFile() {
        try {
            storage.loadChain("non_existent_file.jsonl");
            Assert.fail("Should throw exception for non-existent file");
        } catch (Exception e) {
            // Expected
            Assert.assertTrue("Exception message should mention file issue",
                    e.getMessage().toLowerCase().contains("file") ||
                            e.getMessage().toLowerCase().contains("not found"));
        }
    }

    @Test
    public void testSaveToInvalidPath() {
        try {
            storage.saveChain(blockchain, "/invalid/path/that/does/not/exist/file.jsonl");
            Assert.fail("Should throw exception for invalid path");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testIndexWithNullBlockchain() {
        try {
            index.rebuildIndex(null);
            Assert.fail("Should throw exception for null blockchain");
        } catch (IllegalArgumentException e) {
            // Expected
            Assert.assertTrue("Exception message should mention blockchain",
                    e.getMessage().toLowerCase().contains("blockchain"));
        }
    }

    @Test
    public void testAddNullCredentials() {
        try {
            index.addCredentials(null);
            Assert.fail("Should throw exception for null credentials");
        } catch (IllegalArgumentException e) {
            // Expected
            Assert.assertTrue("Exception message should mention credentials",
                    e.getMessage().toLowerCase().contains("credential"));
        }
    }
}
