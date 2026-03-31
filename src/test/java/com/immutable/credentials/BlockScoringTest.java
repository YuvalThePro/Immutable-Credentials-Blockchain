package com.immutable.credentials;

import com.immutable.credentials.consensus.BlockScoring;
import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the BlockScoring engine.
 *
 * Scoring model:
 * IN_TURN_SCORE = 2 (expected round-robin validator)
 * OUT_OF_TURN_SCORE = 1 (any other authorized validator)
 * VOTE_BONUS = 1 per verified voter attestation
 */
public class BlockScoringTest {

    private static final String VAL_A = "VAL_A";
    private static final String VAL_B = "VAL_B";
    private static final String VAL_C = "VAL_C";

    private KeyPair keyA;
    private KeyPair keyB;
    private KeyPair keyC;

    private List<Validator> activeValidators;
    private Map<String, PublicKey> pubKeyMap;

    private Blockchain blockchain;

    @Before
    public void setUp() {
        keyA = CryptoUtils.generateKeyPair();
        keyB = CryptoUtils.generateKeyPair();
        keyC = CryptoUtils.generateKeyPair();

        Validator vA = new Validator(VAL_A, "Validator A", keyA.getPublic(), keyA.getPrivate(), "Inst A");
        Validator vB = new Validator(VAL_B, "Validator B", keyB.getPublic(), keyB.getPrivate(), "Inst B");
        Validator vC = new Validator(VAL_C, "Validator C", keyC.getPublic(), keyC.getPrivate(), "Inst C");
        vA.activate();
        vB.activate();
        vC.activate();

        activeValidators = new ArrayList<>();
        activeValidators.add(vA);
        activeValidators.add(vB);
        activeValidators.add(vC);

        pubKeyMap = new HashMap<>();
        pubKeyMap.put(VAL_A, keyA.getPublic());
        pubKeyMap.put(VAL_B, keyB.getPublic());
        pubKeyMap.put(VAL_C, keyC.getPublic());

        blockchain = new Blockchain();
        blockchain.initializeGenesis();
    }

    // ========== computeBlockScore ==========

    @Test
    public void testGenesisScoresZero() {
        Block genesis = blockchain.getBlock(0);
        int score = BlockScoring.computeBlockScore(genesis, VAL_A, pubKeyMap);
        Assert.assertEquals("Genesis block should score 0", 0, score);
    }

    @Test
    public void testInTurnBlockNoAttestations() {
        // Index 1, round-robin -> VAL_A (1 % 3 == 1, index 0 = VAL_A)
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_A, keyA);
        // Expected proposer for index 1 is activeValidators.get(1 % 3) = VAL_B
        // But we signed with VAL_A, so it is OUT_OF_TURN
        String expectedProposer = activeValidators.get(1 % 3).getValidatorId(); // VAL_B
        int score = BlockScoring.computeBlockScore(block, expectedProposer, pubKeyMap);
        Assert.assertEquals("VAL_A proposing at index 1 (VAL_B's turn) = OUT_OF_TURN",
                BlockScoring.OUT_OF_TURN_SCORE, score);
    }

    @Test
    public void testInTurnBlockCorrectProposer() {
        // Index 1, round-robin -> activeValidators.get(1 % 3) = VAL_B
        String expectedProposer = activeValidators.get(1 % activeValidators.size()).getValidatorId();
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), expectedProposer,
                expectedProposer.equals(VAL_A) ? keyA : expectedProposer.equals(VAL_B) ? keyB : keyC);
        int score = BlockScoring.computeBlockScore(block, expectedProposer, pubKeyMap);
        Assert.assertEquals("In-turn proposer with no attestations scores 2",
                BlockScoring.IN_TURN_SCORE, score);
    }

    @Test
    public void testOutOfTurnBlockNoAttestations() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_C, keyC);
        // VAL_B is expected at index 1
        int score = BlockScoring.computeBlockScore(block, VAL_B, pubKeyMap);
        Assert.assertEquals("Out-of-turn block scores 1", BlockScoring.OUT_OF_TURN_SCORE, score);
    }

    @Test
    public void testInTurnBlockWithOneAttestation() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_B, keyB);
        String attestSig = CryptoUtils.signData(block.getHash(), keyA.getPrivate());
        block.addVoterAttestation(VAL_A, attestSig);

        int score = BlockScoring.computeBlockScore(block, VAL_B, pubKeyMap);
        Assert.assertEquals("IN_TURN + 1 valid attestation = 3",
                BlockScoring.IN_TURN_SCORE + BlockScoring.VOTE_BONUS, score);
    }

    @Test
    public void testBlockWithThreeValidAttestations() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_A, keyA);
        block.addVoterAttestation(VAL_A, CryptoUtils.signData(block.getHash(), keyA.getPrivate()));
        block.addVoterAttestation(VAL_B, CryptoUtils.signData(block.getHash(), keyB.getPrivate()));
        block.addVoterAttestation(VAL_C, CryptoUtils.signData(block.getHash(), keyC.getPrivate()));

        // VAL_A is out-of-turn at index 1 (VAL_B expected)
        int score = BlockScoring.computeBlockScore(block, VAL_B, pubKeyMap);
        Assert.assertEquals("OUT_OF_TURN + 3 attestations = 4",
                BlockScoring.OUT_OF_TURN_SCORE + 3 * BlockScoring.VOTE_BONUS, score);
    }

    @Test
    public void testInvalidAttestationSignatureNotCounted() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_B, keyB);
        // Sign with the wrong key (attacker key)
        KeyPair attackerKey = CryptoUtils.generateKeyPair();
        block.addVoterAttestation(VAL_A, CryptoUtils.signData(block.getHash(), attackerKey.getPrivate()));

        int score = BlockScoring.computeBlockScore(block, VAL_B, pubKeyMap);
        Assert.assertEquals("Invalid attestation signature should not add bonus",
                BlockScoring.IN_TURN_SCORE, score);
    }

    @Test
    public void testUnknownVoterAttestationNotCounted() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_B, keyB);
        KeyPair unknownKey = CryptoUtils.generateKeyPair();
        block.addVoterAttestation("UNKNOWN_VAL", CryptoUtils.signData(block.getHash(), unknownKey.getPrivate()));

        int score = BlockScoring.computeBlockScore(block, VAL_B, pubKeyMap);
        Assert.assertEquals("Unknown voter attestation should not add bonus",
                BlockScoring.IN_TURN_SCORE, score);
    }

    // ========== computeChainScore ==========

    @Test
    public void testChainScoreGenesisOnly() {
        int score = BlockScoring.computeChainScore(blockchain.getChain(), activeValidators, pubKeyMap);
        Assert.assertEquals("Genesis-only chain scores 0", 0, score);
    }

    @Test
    public void testChainScoreMultipleBlocks() {
        Block genesis = blockchain.getLatestBlock();

        // Block 1: in-turn proposer = VAL_B (index 1 % 3 = 1)
        Block b1 = makeSignedBlock(1, genesis.getHash(), VAL_B, keyB);
        b1.addVoterAttestation(VAL_A, CryptoUtils.signData(b1.getHash(), keyA.getPrivate()));
        blockchain.addBlock(b1);

        // Block 2: in-turn proposer = VAL_C (index 2 % 3 = 2)
        Block b2 = makeSignedBlock(2, b1.getHash(), VAL_A, keyA); // out-of-turn
        blockchain.addBlock(b2);

        // Expected: b1 = 2 (in-turn) + 1 (attestation) = 3; b2 = 1 (out-of-turn) = 1 =>
        // total = 4
        int score = BlockScoring.computeChainScore(blockchain.getChain(), activeValidators, pubKeyMap);
        Assert.assertEquals("Chain score should be 4", 4, score);
    }

    @Test
    public void testHigherScoreChainWinsOverLongerLowerScoreChain() {
        Block genesis = blockchain.getLatestBlock();

        // Legitimate chain: 2 blocks, both in-turn with 2 attestations each
        Block legit1 = makeSignedBlock(1, genesis.getHash(), VAL_B, keyB); // in-turn at idx 1
        legit1.addVoterAttestation(VAL_A, CryptoUtils.signData(legit1.getHash(), keyA.getPrivate()));
        legit1.addVoterAttestation(VAL_C, CryptoUtils.signData(legit1.getHash(), keyC.getPrivate()));

        List<Block> legitimateChain = new ArrayList<>();
        legitimateChain.add(genesis);
        legitimateChain.add(legit1);
        // legit score = 0 (genesis) + 2 (in-turn) + 2 (attestations) = 4

        // Attacker chain: 3 blocks, all out-of-turn, no attestations (compromised
        // single key).
        // Index 1 expects VAL_B, index 2 expects VAL_C — VAL_A is out-of-turn for both.
        // Index 3 would be VAL_A's turn (3 % 3 == 0), so we stop at index 2 to keep
        // scores unambiguous.
        Block atk1 = makeSignedBlock(1, genesis.getHash(), VAL_A, keyA); // out-of-turn
        Block atk2 = makeSignedBlock(2, atk1.getHash(), VAL_A, keyA); // out-of-turn

        List<Block> attackerChain = new ArrayList<>();
        attackerChain.add(genesis);
        attackerChain.add(atk1);
        attackerChain.add(atk2);
        // attacker score = 0 + 1 + 1 = 2

        int legitScore = BlockScoring.computeChainScore(legitimateChain, activeValidators, pubKeyMap);
        int attackerScore = BlockScoring.computeChainScore(attackerChain, activeValidators, pubKeyMap);

        Assert.assertTrue("Legitimate chain (score " + legitScore + ") should outscore attacker chain (score "
                + attackerScore + ") even though attacker chain is longer",
                legitScore > attackerScore);
    }

    @Test
    public void testEmptyChainScoresZero() {
        int score = BlockScoring.computeChainScore(new ArrayList<>(), activeValidators, pubKeyMap);
        Assert.assertEquals("Empty chain scores 0", 0, score);
    }

    @Test
    public void testNullValidatorsScoresZero() {
        int score = BlockScoring.computeChainScore(blockchain.getChain(), null, pubKeyMap);
        Assert.assertEquals("Null validators returns 0", 0, score);
    }

    // ========== verifyAttestation ==========

    @Test
    public void testVerifyAttestationValid() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_A, keyA);
        String sig = CryptoUtils.signData(block.getHash(), keyB.getPrivate());
        Assert.assertTrue("Valid attestation should verify",
                BlockScoring.verifyAttestation(block.getHash(), sig, keyB.getPublic()));
    }

    @Test
    public void testVerifyAttestationWrongKey() {
        Block block = makeSignedBlock(1, blockchain.getLatestBlock().getHash(), VAL_A, keyA);
        String sig = CryptoUtils.signData(block.getHash(), keyA.getPrivate());
        Assert.assertFalse("Attestation verified with wrong key should fail",
                BlockScoring.verifyAttestation(block.getHash(), sig, keyB.getPublic()));
    }

    @Test
    public void testVerifyAttestationNullInputs() {
        Assert.assertFalse("Null blockHash should return false",
                BlockScoring.verifyAttestation(null, "sig", keyA.getPublic()));
        Assert.assertFalse("Null signature should return false",
                BlockScoring.verifyAttestation("hash", null, keyA.getPublic()));
        Assert.assertFalse("Null publicKey should return false",
                BlockScoring.verifyAttestation("hash", "sig", null));
    }

    // ========== Helpers ==========

    private Block makeSignedBlock(int index, String previousHash, String validatorId, KeyPair keyPair) {
        Block unsigned = new Block(index, previousHash, new ArrayList<>(), validatorId);
        String sig = CryptoUtils.signData(unsigned.getHash(), keyPair.getPrivate());
        return new Block(unsigned, sig);
    }
}
