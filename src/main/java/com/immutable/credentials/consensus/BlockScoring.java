package com.immutable.credentials.consensus;

import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;

/**
 * Computes per-block and per-chain scores used to select the canonical chain.
 *
 * Scoring model:
 *   IN_TURN_SCORE  = 2  (block proposed by the expected round-robin validator)
 *   OUT_OF_TURN_SCORE = 1  (valid block but not the expected proposer)
 *   VOTE_BONUS     = 1  per verified voter attestation recorded in the block
 *
 * Chain selection: the chain with the highest total score wins.
 * On a tie, the defending (current) chain is kept (defender advantage).
 *
 * Security property: an attacker holding one compromised key can only produce
 * OUT_OF_TURN blocks (score 1) with zero attestations, while a legitimate chain
 * accumulates IN_TURN blocks (score 2) plus one bonus per honest voter.
 */
public class BlockScoring {

    public static final int IN_TURN_SCORE = 2;
    public static final int OUT_OF_TURN_SCORE = 1;
    public static final int VOTE_BONUS = 1;

    private BlockScoring() {}

    /**
     * Compute the score of a single block.
     *
     * @param block               the block to score
     * @param expectedProposerId  the validator ID that round-robin assigns to this index
     * @param validatorPublicKeys map of validatorId -> PublicKey for attestation verification
     * @return the block score (0 for genesis block at index 0)
     */
    public static int computeBlockScore(Block block, String expectedProposerId,
                                        Map<String, PublicKey> validatorPublicKeys) {
        if (block.getIndex() == 0) {
            return 0; // genesis is unscored
        }

        int base = block.getValidatorId().equals(expectedProposerId)
                ? IN_TURN_SCORE
                : OUT_OF_TURN_SCORE;

        int bonus = 0;
        for (Map.Entry<String, String> entry : block.getVoterAttestations().entrySet()) {
            PublicKey voterKey = validatorPublicKeys.get(entry.getKey());
            if (voterKey != null && CryptoUtils.verifySignature(block.getHash(), entry.getValue(), voterKey)) {
                bonus += VOTE_BONUS;
            }
        }

        return base + bonus;
    }

    /**
     * Compute the total score of a chain.
     *
     * @param chain               ordered list of blocks (index 0 = genesis)
     * @param activeValidators    ordered list of active validators used for round-robin
     * @param validatorPublicKeys map of validatorId -> PublicKey for attestation verification
     * @return total chain score (sum of individual block scores)
     */
    public static int computeChainScore(List<Block> chain,
                                        List<Validator> activeValidators,
                                        Map<String, PublicKey> validatorPublicKeys) {
        if (chain == null || chain.isEmpty() || activeValidators == null || activeValidators.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Block block : chain) {
            int idx = block.getIndex();
            if (idx == 0) continue; // genesis contributes 0
            String expectedProposerId = activeValidators.get(idx % activeValidators.size()).getValidatorId();
            total += computeBlockScore(block, expectedProposerId, validatorPublicKeys);
        }
        return total;
    }

    /**
     * Verify a single voter attestation against the block hash.
     *
     * @param blockHash      the hash of the block that was attested
     * @param signature      Base64-encoded RSA signature of the block hash
     * @param voterPublicKey the public key of the claimed voter
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verifyAttestation(String blockHash, String signature, PublicKey voterPublicKey) {
        if (blockHash == null || signature == null || voterPublicKey == null) {
            return false;
        }
        return CryptoUtils.verifySignature(blockHash, signature, voterPublicKey);
    }
}
