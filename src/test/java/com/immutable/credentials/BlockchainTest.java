package com.immutable.credentials;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;

/**
 * Unit tests for Blockchain core functionality.
 * Tests block creation, chain validation, hash linking, and genesis block.
 */
public class BlockchainTest {

    private Blockchain blockchain;
    private KeyPair validatorKeyPair;
    private HashMap<String, PublicKey> validatorKeys;

    @Before
    public void setUp() {
        blockchain = new Blockchain();
        blockchain.initializeGenesis();
        validatorKeyPair = CryptoUtils.generateKeyPair();
        
        validatorKeys = new HashMap<>();
        validatorKeys.put("SYSTEM", validatorKeyPair.getPublic());
        validatorKeys.put("VALIDATOR_001", validatorKeyPair.getPublic());
    }

    // ========== Genesis Block Tests ==========

    @Test
    public void testGenesisBlockCreation() {
        Assert.assertNotNull("Blockchain should not be null", blockchain);
        Assert.assertEquals("Initial size should be 1", 1, blockchain.size());
        
        Block genesis = blockchain.getBlock(0);
        Assert.assertNotNull("Genesis block should not be null", genesis);
        Assert.assertEquals("Genesis index should be 0", 0, genesis.getIndex());
        Assert.assertEquals("Genesis previous hash should be '0'", "0", genesis.getPreviousHash());
        Assert.assertEquals("Genesis validator should be SYSTEM", "SYSTEM", genesis.getValidatorId());
    }

    @Test
    public void testGenesisBlockHasNoCredentials() {
        Block genesis = blockchain.getBlock(0);
        Assert.assertTrue("Genesis block should have no credentials", genesis.getCredentials().isEmpty());
    }

    // ========== Add Block Tests ==========

    @Test
    public void testAddBlockIncreasesSize() {
        int initialSize = blockchain.size();
        
        Credential credential = new Credential(
            "John Doe",
            new Date(),
            "Computer Science",
            "University A",
            "STU001",
            "CRED001"
        );
        
        Block latestBlock = blockchain.getLatestBlock();
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block newBlock = new Block(
            latestBlock.getIndex() + 1,
            latestBlock.getHash(),
            creds,
            "VALIDATOR_001"
        );
        
        blockchain.addBlock(newBlock);
        
        Assert.assertEquals("Size should increase by 1", initialSize + 1, blockchain.size());
    }

    @Test
    public void testAddMultipleBlocks() {
        for (int i = 0; i < 5; i++) {
            Credential credential = new Credential(
                "Student " + i,
                new Date(),
                "Degree " + i,
                "University",
                "STU00" + i,
                "CRED00" + i
            );
            
            Block latestBlock = blockchain.getLatestBlock();
            ArrayList<Credential> creds = new ArrayList<>();
            creds.add(credential);
            Block newBlock = new Block(
                latestBlock.getIndex() + 1,
                latestBlock.getHash(),
                creds,
                "VALIDATOR_001"
            );
            
            blockchain.addBlock(newBlock);
        }
        
        Assert.assertEquals("Should have 6 blocks total", 6, blockchain.size());
    }

    // ========== Get Block Tests ==========

    @Test
    public void testGetBlockValidIndex() {
        Block genesis = blockchain.getBlock(0);
        Assert.assertNotNull("Should retrieve genesis block", genesis);
        Assert.assertEquals("Should be genesis block", 0, genesis.getIndex());
    }

    @Test
    public void testGetBlockInvalidIndex() {
        Block result = blockchain.getBlock(999);
        Assert.assertNull("Should return null for invalid index", result);
    }

    @Test
    public void testGetBlockNegativeIndex() {
        Block result = blockchain.getBlock(-1);
        Assert.assertNull("Should return null for negative index", result);
    }

    @Test
    public void testGetLatestBlock() {
        Block latest = blockchain.getLatestBlock();
        Assert.assertNotNull("Latest block should not be null", latest);
        Assert.assertEquals("Latest block should be genesis", 0, latest.getIndex());
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block newBlock = new Block(1, latest.getHash(), creds, "VALIDATOR_001");
        blockchain.addBlock(newBlock);
        
        Block newLatest = blockchain.getLatestBlock();
        Assert.assertEquals("Latest block should be index 1", 1, newLatest.getIndex());
    }

    // ========== Search Tests ==========

    @Test
    public void testSearchByStudentIdFound() {
        Credential credential = new Credential(
            "John Doe",
            new Date(),
            "Computer Science",
            "University A",
            "STU001",
            "CRED001"
        );
        
        Block latestBlock = blockchain.getLatestBlock();
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block newBlock = new Block(
            latestBlock.getIndex() + 1,
            latestBlock.getHash(),
            creds,
            "VALIDATOR_001"
        );
        
        blockchain.addBlock(newBlock);
        
        ArrayList<Block> results = blockchain.searchByStudentId("STU001");
        Assert.assertEquals("Should find 1 block", 1, results.size());
        Assert.assertEquals("Should match student ID", "STU001", results.get(0).getCredentials().get(0).getStudentId());
    }

    @Test
    public void testSearchByStudentIdNotFound() {
        ArrayList<Block> results = blockchain.searchByStudentId("NONEXISTENT");
        Assert.assertTrue("Should return empty list", results.isEmpty());
    }

    @Test
    public void testSearchByStudentIdNull() {
        ArrayList<Block> results = blockchain.searchByStudentId(null);
        Assert.assertTrue("Should return empty list for null", results.isEmpty());
    }

    @Test
    public void testSearchByStudentIdEmpty() {
        ArrayList<Block> results = blockchain.searchByStudentId("");
        Assert.assertTrue("Should return empty list for empty string", results.isEmpty());
    }

    @Test
    public void testSearchByStudentIdMultipleResults() {
        for (int i = 0; i < 3; i++) {
            Credential credential = new Credential(
                "Same Student",
                new Date(),
                "Degree " + i,
                "University",
                "STU001",
                "CRED00" + i
            );
            
            Block latestBlock = blockchain.getLatestBlock();
            ArrayList<Credential> creds = new ArrayList<>();
            creds.add(credential);
            Block newBlock = new Block(
                latestBlock.getIndex() + 1,
                latestBlock.getHash(),
                creds,
                "VALIDATOR_001"
            );
            
            blockchain.addBlock(newBlock);
        }
        
        ArrayList<Block> results = blockchain.searchByStudentId("STU001");
        Assert.assertEquals("Should find 3 blocks", 3, results.size());
    }

    // ========== Get Chain Tests ==========

    @Test
    public void testGetChain() {
        ArrayList<Block> chain = blockchain.getChain();
        Assert.assertNotNull("Chain should not be null", chain);
        Assert.assertEquals("Chain size should match blockchain size", blockchain.size(), chain.size());
    }

    @Test
    public void testGetChainReturnsCopy() {
        ArrayList<Block> chain = blockchain.getChain();
        int originalSize = chain.size();
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        chain.add(new Block(999, "hash", creds, "VALIDATOR_001"));
        
        Assert.assertEquals("Original blockchain should not be modified", originalSize, blockchain.size());
    }

    // ========== Chain Validation Tests ==========

    @Test
    public void testValidateChainValid() {
        Block genesis = blockchain.getLatestBlock();
        String signature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, signature);
        
        Blockchain validChain = new Blockchain(new ArrayList<>());
        validChain.addBlock(signedGenesis);
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block block = new Block(1, signedGenesis.getHash(), creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);
        
        validChain.addBlock(signedBlock);
        
        Assert.assertTrue("Valid chain should validate", validChain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainNullMap() {
        Assert.assertFalse("Should fail with null validator map", blockchain.validateChain(null));
    }

    @Test
    public void testValidateChainInvalidIndex() {
        Block genesis = blockchain.getLatestBlock();
        String signature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, signature);
        
        Blockchain invalidChain = new Blockchain(new ArrayList<>());
        invalidChain.addBlock(signedGenesis);
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block block = new Block(5, signedGenesis.getHash(), creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);
        
        invalidChain.addBlock(signedBlock);
        
        Assert.assertFalse("Should fail with invalid index", invalidChain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainInvalidPreviousHash() {
        Block genesis = blockchain.getLatestBlock();
        String signature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, signature);
        
        Blockchain invalidChain = new Blockchain(new ArrayList<>());
        invalidChain.addBlock(signedGenesis);
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block block = new Block(1, "WRONG_HASH", creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);
        
        invalidChain.addBlock(signedBlock);
        
        Assert.assertFalse("Should fail with invalid previous hash", invalidChain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainMissingSignature() {
        Block genesis = blockchain.getLatestBlock();
        String signature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, signature);
        
        Blockchain invalidChain = new Blockchain(new ArrayList<>());
        invalidChain.addBlock(signedGenesis);
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block block = new Block(1, signedGenesis.getHash(), creds, "VALIDATOR_001");
        invalidChain.addBlock(block);
        
        Assert.assertFalse("Should fail with missing signature", invalidChain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainUnauthorizedValidator() {
        Block genesis = blockchain.getLatestBlock();
        String signature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, signature);
        
        Blockchain invalidChain = new Blockchain(new ArrayList<>());
        invalidChain.addBlock(signedGenesis);
        
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block block = new Block(1, signedGenesis.getHash(), creds, "UNAUTHORIZED");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);
        
        invalidChain.addBlock(signedBlock);
        
        Assert.assertFalse("Should fail with unauthorized validator", invalidChain.validateChain(validatorKeys));
    }

    // ========== Voter Attestation Validation Tests ==========

    @Test
    public void testValidateChainWithValidAttestations() {
        Block genesis = blockchain.getLatestBlock();
        String genesisSignature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, genesisSignature);

        Blockchain chain = new Blockchain(new ArrayList<>());
        chain.addBlock(signedGenesis);

        ArrayList<Credential> creds = new ArrayList<>();
        Block block = new Block(1, signedGenesis.getHash(), creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);

        // Attach a valid attestation from VALIDATOR_001
        String attestationSig = CryptoUtils.signData(signedBlock.getHash(), validatorKeyPair.getPrivate());
        signedBlock.addVoterAttestation("VALIDATOR_001", attestationSig);

        chain.addBlock(signedBlock);

        Assert.assertTrue("Chain with valid attestations should pass validation",
                chain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainWithForgedAttestation() {
        Block genesis = blockchain.getLatestBlock();
        String genesisSignature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, genesisSignature);

        Blockchain chain = new Blockchain(new ArrayList<>());
        chain.addBlock(signedGenesis);

        ArrayList<Credential> creds = new ArrayList<>();
        Block block = new Block(1, signedGenesis.getHash(), creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);

        // Forge an attestation using a different key pair
        KeyPair attackerKeyPair = CryptoUtils.generateKeyPair();
        String forgedSig = CryptoUtils.signData(signedBlock.getHash(), attackerKeyPair.getPrivate());
        signedBlock.addVoterAttestation("VALIDATOR_001", forgedSig); // wrong key for this voter

        chain.addBlock(signedBlock);

        Assert.assertFalse("Chain with forged attestation should fail validation",
                chain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainWithUnknownVoterAttestation() {
        Block genesis = blockchain.getLatestBlock();
        String genesisSignature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, genesisSignature);

        Blockchain chain = new Blockchain(new ArrayList<>());
        chain.addBlock(signedGenesis);

        ArrayList<Credential> creds = new ArrayList<>();
        Block block = new Block(1, signedGenesis.getHash(), creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);

        // Attestation from a validator not in the key map
        KeyPair unknownKeyPair = CryptoUtils.generateKeyPair();
        String unknownSig = CryptoUtils.signData(signedBlock.getHash(), unknownKeyPair.getPrivate());
        signedBlock.addVoterAttestation("UNKNOWN_VALIDATOR", unknownSig);

        chain.addBlock(signedBlock);

        Assert.assertFalse("Chain with unknown voter attestation should fail validation",
                chain.validateChain(validatorKeys));
    }

    @Test
    public void testValidateChainEmptyAttestationsIsValid() {
        Block genesis = blockchain.getLatestBlock();
        String genesisSignature = CryptoUtils.signData(genesis.getHash(), validatorKeyPair.getPrivate());
        Block signedGenesis = new Block(genesis, genesisSignature);

        Blockchain chain = new Blockchain(new ArrayList<>());
        chain.addBlock(signedGenesis);

        ArrayList<Credential> creds = new ArrayList<>();
        Block block = new Block(1, signedGenesis.getHash(), creds, "VALIDATOR_001");
        String blockSignature = CryptoUtils.signData(block.getHash(), validatorKeyPair.getPrivate());
        Block signedBlock = new Block(block, blockSignature);
        // No attestations added

        chain.addBlock(signedBlock);

        Assert.assertTrue("Block with no attestations should still be valid (backward compat)",
                chain.validateChain(validatorKeys));
    }

    // ========== Credential ID Tests ==========

    @Test
    public void testCredentialIdExistsTrue() {
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        Block latestBlock = blockchain.getLatestBlock();
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block newBlock = new Block(
            latestBlock.getIndex() + 1,
            latestBlock.getHash(),
            creds,
            "VALIDATOR_001"
        );
        
        blockchain.addBlock(newBlock);
        
        Assert.assertTrue("Credential ID should exist", blockchain.credentialIdExists("CRED001"));
    }

    @Test
    public void testCredentialIdExistsFalse() {
        Assert.assertFalse("Non-existent credential ID should not exist", blockchain.credentialIdExists("NONEXISTENT"));
    }

    @Test
    public void testCredentialIdExistsNull() {
        Assert.assertFalse("Null credential ID should return false", blockchain.credentialIdExists(null));
    }

    @Test
    public void testCredentialIdExistsGenesis() {
        Assert.assertFalse("Genesis has no credentials", blockchain.credentialIdExists("GENESIS-CRED-000"));
    }

    // ========== Copy Constructor Tests ==========

    @Test
    public void testCopyConstructor() {
        Credential credential = new Credential(
            "Student",
            new Date(),
            "Degree",
            "University",
            "STU001",
            "CRED001"
        );
        
        Block latestBlock = blockchain.getLatestBlock();
        ArrayList<Credential> creds = new ArrayList<>();
        creds.add(credential);
        Block newBlock = new Block(
            latestBlock.getIndex() + 1,
            latestBlock.getHash(),
            creds,
            "VALIDATOR_001"
        );
        
        blockchain.addBlock(newBlock);
        
        Blockchain copy = new Blockchain(blockchain.getChain());
        
        Assert.assertEquals("Copy should have same size", blockchain.size(), copy.size());
        Assert.assertEquals("Genesis should match", blockchain.getBlock(0).getHash(), copy.getBlock(0).getHash());
    }

    @Test
    public void testCopyConstructorWithNull() {
        Blockchain empty = new Blockchain(null);
        Assert.assertEquals("Should create empty blockchain", 0, empty.size());
    }
}


