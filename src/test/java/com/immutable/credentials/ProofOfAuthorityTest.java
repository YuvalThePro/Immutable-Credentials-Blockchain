// package com.immutable.credentials;

// import java.security.KeyPair;
// import java.util.ArrayList;
// import java.util.Date;
// import java.util.List;

// import org.junit.Assert;
// import org.junit.Before;
// import org.junit.Test;

// import com.immutable.credentials.consensus.ProofOfAuthority;
// import com.immutable.credentials.consensus.Validator;
// import com.immutable.credentials.crypto.CryptoUtils;
// import com.immutable.credentials.model.Block;
// import com.immutable.credentials.model.Credential;

// /**
// * Unit tests for ProofOfAuthority consensus mechanism - Sprint 2.
// * Tests validator authorization, block validation, voting, and consensus
// rules.
// */
// public class ProofOfAuthorityTest {

// private ProofOfAuthority poa;
// private List<Validator> validators;
// private Validator validator1;
// private Validator validator2;
// private KeyPair keyPair1;
// private KeyPair keyPair2;

// @Before
// public void setUp() {
// // Create validators
// keyPair1 = CryptoUtils.generateKeyPair();
// keyPair2 = CryptoUtils.generateKeyPair();

// validator1 = new Validator(
// "VALIDATOR_001",
// "University A",
// keyPair1.getPublic(),
// keyPair1.getPrivate(),
// "Institution A"
// );
// validator1.activate();

// validator2 = new Validator(
// "VALIDATOR_002",
// "University B",
// keyPair2.getPublic(),
// keyPair2.getPrivate(),
// "Institution B"
// );
// validator2.activate();

// validators = new ArrayList<>();
// validators.add(validator1);
// validators.add(validator2);

// poa = new ProofOfAuthority(validators);
// }

// // ========== Validator Authorization Tests ==========

// @Test
// public void testIsAuthorizedValidatorTrue() {
// Assert.assertTrue(poa.isAuthorizedValidator(keyPair1.getPublic()));
// Assert.assertTrue(poa.isAuthorizedValidator(keyPair2.getPublic()));
// }

// @Test
// public void testIsAuthorizedValidatorFalse() {
// KeyPair unauthorizedKey = CryptoUtils.generateKeyPair();
// Assert.assertFalse(poa.isAuthorizedValidator(unauthorizedKey.getPublic()));
// }

// @Test
// public void testIsAuthorizedValidatorNull() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.isAuthorizedValidator(null);
// });
// }

// @Test
// public void testIsAuthorizedValidatorInactive() {
// validator1.deactivate();
// Assert.assertFalse(poa.isAuthorizedValidator(keyPair1.getPublic()));
// }

// // ========== Get Validator Tests ==========

// @Test
// public void testGetValidatorByPublicKey() {
// Validator found = poa.getValidatorByPublicKey(keyPair1.getPublic());
// Assert.assertNotNull(found);
// Assert.assertEquals("VALIDATOR_001", found.getValidatorId());
// }

// @Test
// public void testGetValidatorByPublicKeyNotFound() {
// KeyPair unknownKey = CryptoUtils.generateKeyPair();
// Validator found = poa.getValidatorByPublicKey(unknownKey.getPublic());
// Assert.assertNull(found);
// }

// @Test
// public void testGetValidatorByPublicKeyNull() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.getValidatorByPublicKey(null);
// });
// }

// @Test
// public void testGetValidatorById() {
// Validator found = poa.getValidatorById("VALIDATOR_001");
// Assert.assertNotNull(found);
// Assert.assertEquals("VALIDATOR_001", found.getValidatorId());
// }

// @Test
// public void testGetValidatorByIdNotFound() {
// Validator found = poa.getValidatorById("NONEXISTENT");
// Assert.assertNull(found);
// }

// @Test
// public void testGetValidatorByIdNull() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.getValidatorById(null);
// });
// }

// @Test
// public void testGetAuthorizedValidators() {
// List<Validator> authValidators = poa.getAuthorizedValidators();
// Assert.assertNotNull(authValidators);
// Assert.assertEquals(2, authValidators.size());
// }

// // ========== Block Signature Validation Tests ==========

// @Test
// public void testValidateBlockSignatureValid() {
// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, "prevHash", creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(block, signature);

// Assert.assertTrue(poa.validateBlockSignature(signedBlock,
// keyPair1.getPublic()));
// }

// @Test
// public void testValidateBlockSignatureInvalid() {
// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, "prevHash", creds, "VALIDATOR_001",
// "INVALID_SIG");

// Assert.assertFalse(poa.validateBlockSignature(block, keyPair1.getPublic()));
// }

// @Test
// public void testValidateBlockSignatureWrongKey() {
// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, "prevHash", creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(1, "prevHash", creds, "VALIDATOR_001",
// signature);

// Assert.assertFalse(poa.validateBlockSignature(signedBlock,
// keyPair2.getPublic()));
// }

// // ========== Block Proposal Tests ==========

// @Test
// public void testProposeBlockValid() {
// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, "prevHash", creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(block, signature);

// Assert.assertTrue(poa.proposeBlock(signedBlock));
// }

// @Test
// public void testProposeBlockNull() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.proposeBlock(null);
// });
// }

// @Test
// public void testProposeBlockUnauthorizedValidator() {
// KeyPair unauthorizedKey = CryptoUtils.generateKeyPair();
// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, "prevHash", creds, "UNAUTHORIZED");
// String signature = CryptoUtils.signData(block.getHash(),
// unauthorizedKey.getPrivate());
// Block signedBlock = new Block(block, signature);
// Assert.assertFalse(poa.proposeBlock(signedBlock));
// }

// // ========== Voting Tests ==========

// @Test
// public void testRecordVoteValid() {
// String blockHash = "testHash";
// boolean result = poa.recordVote(1, blockHash, keyPair1.getPublic(), true);
// Assert.assertTrue(result);
// }

// @Test
// public void testRecordVoteNullPublicKey() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.recordVote(1, "hash", null, true);
// });
// }

// @Test
// public void testRecordVoteNullBlockHash() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.recordVote(1, null, keyPair1.getPublic(), true);
// });
// }

// @Test
// public void testRecordVoteUnauthorizedValidator() {
// KeyPair unauthorizedKey = CryptoUtils.generateKeyPair();
// boolean result = poa.recordVote(1, "hash", unauthorizedKey.getPublic(),
// true);
// Assert.assertFalse(result);
// }

// @Test
// public void testGetVoteCount() {
// String blockHash = "testHash";
// poa.recordVote(1, blockHash, keyPair1.getPublic(), true);
// poa.recordVote(1, blockHash, keyPair2.getPublic(), true);

// int count = poa.getVoteCount(1, blockHash);
// Assert.assertEquals(2, count);
// }

// @Test
// public void testGetVoteCountWithRejections() {
// String blockHash = "testHash";
// poa.recordVote(1, blockHash, keyPair1.getPublic(), true);
// poa.recordVote(1, blockHash, keyPair2.getPublic(), false);

// int count = poa.getVoteCount(1, blockHash);
// Assert.assertEquals(1, count); // Only approvals count
// }

// // ========== Consensus Tests ==========

// @Test
// public void testHasConsensusTrue() {
// String blockHash = "testHash";
// poa.recordVote(1, blockHash, keyPair1.getPublic(), true);
// poa.recordVote(1, blockHash, keyPair2.getPublic(), true);

// Assert.assertTrue(poa.hasConsensus(1, blockHash));
// }

// @Test
// public void testGetRequiredVotes() {
// int required = poa.getRequiredVotes();
// Assert.assertEquals(1, required); // (2 + 1) / 2 = 1 (majority)
// }

// // ========== Consensus Rules Tests ==========

// @Test
// public void testEnforceConsensusRulesValidBlock() {
// Credential prevCredential = new Credential(
// "Prev Student",
// new Date(),
// "Degree",
// "University",
// "STU000",
// "CRED000"
// );
// ArrayList<Credential> prevCreds = new ArrayList<>();
// prevCreds.add(prevCredential);
// Block previousBlock = new Block(0, "0", prevCreds, "VALIDATOR_001");

// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, previousBlock.getHash(), creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(block, signature);

// Assert.assertTrue(poa.enforceConsensusRules(signedBlock, previousBlock));
// }

// @Test
// public void testEnforceConsensusRulesNullBlock() {
// Assert.assertThrows(IllegalArgumentException.class, () -> {
// poa.enforceConsensusRules(null, null);
// });
// }

// @Test
// public void testEnforceConsensusRulesGenesisBlock() {
// Credential credential = new Credential(
// "Genesis",
// new Date(),
// "Degree",
// "University",
// "STU000",
// "CRED000"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block genesis = new Block(0, "0", creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(genesis.getHash(),
// keyPair1.getPrivate());
// Block signedGenesis = new Block(genesis, signature);

// Assert.assertTrue(poa.enforceConsensusRules(signedGenesis, null));
// }

// @Test
// public void testEnforceConsensusRulesWrongIndex() {
// Credential prevCredential = new Credential(
// "Prev Student",
// new Date(),
// "Degree",
// "University",
// "STU000",
// "CRED000"
// );
// ArrayList<Credential> prevCreds = new ArrayList<>();
// prevCreds.add(prevCredential);
// Block previousBlock = new Block(0, "0", prevCreds, "VALIDATOR_001");

// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// // Wrong index (should be 1, not 5)
// Block block = new Block(5, previousBlock.getHash(), creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(5, previousBlock.getHash(), creds,
// "VALIDATOR_001", signature);

// Assert.assertFalse(poa.enforceConsensusRules(signedBlock, previousBlock));
// }

// @Test
// public void testEnforceConsensusRulesWrongPreviousHash() {
// Credential prevCredential = new Credential(
// "Prev Student",
// new Date(),
// "Degree",
// "University",
// "STU000",
// "CRED000"
// );
// ArrayList<Credential> prevCreds = new ArrayList<>();
// prevCreds.add(prevCredential);
// Block previousBlock = new Block(0, "0", prevCreds, "VALIDATOR_001");

// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// // Wrong previous hash
// Block block = new Block(1, "WRONG_HASH", creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(1, "WRONG_HASH", creds, "VALIDATOR_001",
// signature);

// Assert.assertFalse(poa.enforceConsensusRules(signedBlock, previousBlock));
// }

// // ========== Utility Tests ==========

// @Test
// public void testGetPendingBlocks() {
// List<Block> pending = poa.getPendingBlocks(1);
// Assert.assertNotNull(pending);
// }

// @Test
// public void testClearPendingBlocks() {
// Credential credential = new Credential(
// "Student",
// new Date(),
// "Degree",
// "University",
// "STU001",
// "CRED001"
// );

// ArrayList<Credential> creds = new ArrayList<>();
// creds.add(credential);
// Block block = new Block(1, "prevHash", creds, "VALIDATOR_001");
// String signature = CryptoUtils.signData(block.getHash(),
// keyPair1.getPrivate());
// Block signedBlock = new Block(1, "prevHash", creds, "VALIDATOR_001",
// signature);

// poa.proposeBlock(signedBlock);
// poa.clearPendingBlocks(1);

// List<Block> pending = poa.getPendingBlocks(1);
// Assert.assertEquals(0, pending.size());
// }
// }
