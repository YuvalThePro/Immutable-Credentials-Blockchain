package com.immutable.credentials.consensus;

import com.immutable.credentials.model.Block;
import com.immutable.credentials.util.Logger;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Proof-of-Authority (PoA) consensus mechanism.
 * Manages authorized validators and enforces consensus rules for block
 * acceptance.
 */
public class ProofOfAuthority {

    private List<Validator> authorizedValidators;
    private final Map<Integer, List<Block>> pendingBlocks;
    // Updated to track votes by block hash for accuracy
    private final Map<String, Map<String, Boolean>> votesByHash;

    /**
     * Initialize the PoA consensus engine with authorized validators.
     * * @param validators list of authorized Validator objects
     * 
     * @throws IllegalArgumentException if validators list is null or empty
     */
    public ProofOfAuthority(List<Validator> validators) {
        if (validators == null || validators.isEmpty()) {
            throw new IllegalArgumentException("Validators list cannot be null or empty");
        }
        this.authorizedValidators = new ArrayList<>(validators);
        this.pendingBlocks = new ConcurrentHashMap<>();
        this.votesByHash = new ConcurrentHashMap<>();
    }

    /**
     * Check if a validator is authorized to propose or vote on blocks.
     * * @param validatorPublicKey the public key of the validator to check
     * 
     * @return true if the validator is authorized, false otherwise
     * @throws IllegalArgumentException if validatorPublicKey is null
     */
    public boolean isAuthorizedValidator(PublicKey validatorPublicKey) throws IllegalArgumentException {
        if (validatorPublicKey == null)
            throw new IllegalArgumentException("Public key is Null");

        for (Validator validator : authorizedValidators) {
            if (validator.getPublicKey() != null && validator.getPublicKey().equals(validatorPublicKey)) {
                if (validator.isActive()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the list of all authorized validators.
     * * @return unmodifiable list of authorized Validator objects
     */
    public List<Validator> getAuthorizedValidators() {
        if (authorizedValidators == null || authorizedValidators.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(authorizedValidators);
    }

    /**
     * Retrieve a specific validator by their public key.
     * * @param publicKey the public key of the validator to retrieve
     * 
     * @return the Validator object if found, null otherwise
     * @throws IllegalArgumentException if publicKey is null
     */
    public Validator getValidatorByPublicKey(PublicKey publicKey) throws IllegalArgumentException {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key is Null");
        }
        for (Validator validator : authorizedValidators) {
            if (validator.getPublicKey() != null && validator.getPublicKey().equals(publicKey))
                return validator;
        }
        return null;
    }

    /**
     * Retrieve a specific validator by their validator ID.
     * * @param validatorId the ID of the validator to retrieve
     * 
     * @return the Validator object if found, null otherwise
     * @throws IllegalArgumentException if validatorId is null
     */
    public Validator getValidatorById(String validatorId) throws IllegalArgumentException {
        if (validatorId == null) {
            throw new IllegalArgumentException("Validator ID is null");
        }
        for (Validator validator : authorizedValidators) {
            if (validator.getValidatorId().equals(validatorId)) {
                return validator;
            }
        }
        return null;
    }

    /**
     * Register a proposed block from a validator.
     * Validates that the proposer is authorized before accepting the block.
     * * @param block the proposed block
     * 
     * @return true if the block was accepted for voting, false if rejected
     * @throws IllegalArgumentException if block is null
     */
    public boolean proposeBlock(Block block) throws IllegalArgumentException {
        if (block == null) {
            throw new IllegalArgumentException("Block is null");
        }

        String validatorId = block.getValidatorId();
        Validator validator = getValidatorById(validatorId);

        if (validator == null || !isAuthorizedValidator(validator.getPublicKey())) {
            return false;
        }

        if (!validateBlockSignature(block, validator.getPublicKey())) {
            return false;
        }

        int blockIndex = block.getIndex();
        String blockHash = block.getHash();

        pendingBlocks.computeIfAbsent(blockIndex, k -> new ArrayList<>()).add(block);

        // Proposer automatically votes 'true' for its own block
        votesByHash.computeIfAbsent(blockHash, k -> new HashMap<>()).put(validatorId, true);

        Logger.log("[POA] Block #" + blockIndex + " proposed and auto-voted by " + validatorId);
        return true;
    }

    /**
     * Validate that a block's signature matches the claimed validator.
     * * @param block the block to validate
     * 
     * @param validatorPublicKey the public key of the claimed validator
     * @return true if signature is valid, false otherwise
     */
    public boolean validateBlockSignature(Block block, PublicKey validatorPublicKey) {
        return block.verifySignature(validatorPublicKey);
    }

    /**
     * Record a validator's vote on a proposed block.
     * * @param blockIndex the index of the block being voted on
     * 
     * @param blockHash          the hash of the specific block candidate
     * @param validatorPublicKey the public key of the voting validator
     * @param approve            true to approve, false to reject
     * @return true if vote was recorded, false if validator not authorized
     */
    public boolean recordVote(int blockIndex, String blockHash, PublicKey validatorPublicKey, boolean approve)
            throws IllegalArgumentException {
        if (validatorPublicKey == null || blockHash == null)
            throw new IllegalArgumentException("Required parameters are null");

        Validator v = getValidatorByPublicKey(validatorPublicKey);
        if (v == null || !isAuthorizedValidator(validatorPublicKey))
            return false;

        votesByHash.computeIfAbsent(blockHash, k -> new HashMap<>()).put(v.getValidatorId(), approve);

        Logger.log("[POA] Vote recorded for Block #" + blockIndex + " (" + (approve ? "APPROVE" : "REJECT") + ")");
        return true;
    }

    /**
     * Check if consensus has been reached for a specific block.
     * Consensus requires majority approval (>50% of authorized validators).
     * * @param blockIndex the index of the block to check
     * 
     * @param blockHash the hash of the specific block candidate
     * @return true if consensus reached, false otherwise
     */
    public boolean hasConsensus(int blockIndex, String blockHash) throws IllegalArgumentException {
        if (blockHash == null)
            throw new IllegalArgumentException("BlockHash is null");

        int approvalCount = getVoteCount(blockIndex, blockHash);
        int requiredVotes = getRequiredVotes();

        return approvalCount >= requiredVotes;
    }

    /**
     * Get the block that achieved consensus for a given index.
     * * @param blockIndex the index of the block to retrieve
     * 
     * @return the Block that achieved consensus, or null if no consensus yet
     */
    public Block getConsensusBlock(int blockIndex) {
        List<Block> candidates = pendingBlocks.get(blockIndex);
        if (candidates == null)
            return null;

        for (Block block : candidates) {
            if (hasConsensus(blockIndex, block.getHash())) {
                return block;
            }
        }
        return null;
    }

    /**
     * Clear pending blocks and votes for a specific block index after consensus.
     * * @param blockIndex the index of the block to clear
     */
    public void clearPendingBlocks(int blockIndex) {
        List<Block> candidates = pendingBlocks.remove(blockIndex);
        if (candidates != null) {
            for (Block b : candidates) {
                votesByHash.remove(b.getHash());
            }
        }
    }

    /**
     * Validate that a block meets all PoA consensus rules.
     * * @param block the block to validate
     * 
     * @param previousBlock the previous block in the chain (null for genesis)
     * @return true if all consensus rules are satisfied, false otherwise
     */
    public boolean enforceConsensusRules(Block block, Block previousBlock) {
        if (block == null)
            throw new IllegalArgumentException("Block is null");

        Validator proposer = getValidatorById(block.getValidatorId());
        if (proposer == null || !isAuthorizedValidator(proposer.getPublicKey()))
            return false;

        if (!validateBlockSignature(block, proposer.getPublicKey()))
            return false;

        if (previousBlock == null)
            return block.getIndex() == 0;

        return block.getIndex() == previousBlock.getIndex() + 1 &&
                block.getPreviousHash() != null &&
                block.getPreviousHash().equals(previousBlock.getHash()) &&
                block.getTimestamp() >= previousBlock.getTimestamp();
    }

    /**
     * Determine if a block should be accepted into the blockchain.
     */
    public boolean shouldAcceptBlock(Block block, Block previousBlock) {
        if (block == null)
            throw new IllegalArgumentException("Block is null");
        if (!enforceConsensusRules(block, previousBlock))
            return false;
        if (previousBlock == null && block.getIndex() == 0)
            return true;

        Block consensus = getConsensusBlock(block.getIndex());
        return consensus != null && consensus.getHash().equals(block.getHash());
    }

    /**
     * Get vote count for a specific block candidate.
     */
    public int getVoteCount(int blockIndex, String blockHash) {
        if (blockHash == null)
            throw new IllegalArgumentException("BlockHash is null");
        Map<String, Boolean> blockVotes = votesByHash.get(blockHash);
        if (blockVotes == null)
            return 0;

        int approvalCount = 0;
        for (Boolean vote : blockVotes.values()) {
            if (vote != null && vote)
                approvalCount++;
        }
        return approvalCount;
    }

    /**
     * Calculate the minimum number of votes required for consensus.
     */
    public int getRequiredVotes() {
        return (authorizedValidators.size() / 2) + 1;
    }

    /**
     * Determine which validator should propose the next block using round-robin.
     */
    public Validator getCurrentProposer(int blockIndex) throws IllegalArgumentException {
        if (blockIndex < 0)
            throw new IllegalArgumentException("blockIndex must be >= 0");
        List<Validator> activeValidators = new ArrayList<>();
        for (Validator v : authorizedValidators) {
            if (v.isActive())
                activeValidators.add(v);
        }
        if (activeValidators.isEmpty())
            return null;
        return activeValidators.get(blockIndex % activeValidators.size());
    }

    /**
     * Replace the authorized validator list with a fresh copy from the database.
     */
    public synchronized void syncValidators(List<Validator> fresh) throws IllegalArgumentException {
        if (fresh == null || fresh.isEmpty())
            throw new IllegalArgumentException("List of validators cannot be null or empty.");

        for (Validator incoming : fresh) {
            for (Validator existing : authorizedValidators) {
                if (existing.getValidatorId().equals(incoming.getValidatorId()) && existing.isActive()) {
                    incoming.activate();
                    break;
                }
            }
        }
        authorizedValidators.clear();
        authorizedValidators.addAll(fresh);
    }
}