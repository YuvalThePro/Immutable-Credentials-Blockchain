package com.immutable.credentials.consensus;

import com.immutable.credentials.model.Block;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implements Proof-of-Authority (PoA) consensus mechanism.
 * Manages authorized validators and enforces consensus rules for block
 * acceptance.
 */
public class ProofOfAuthority {

    private final List<Validator> authorizedValidators;
    private final Map<Integer, List<Block>> pendingBlocks;
    private final Map<Integer, Map<String, Boolean>> votes;

    /**
     * Initialize the PoA consensus engine with authorized validators.
     * 
     * @param validators list of authorized Validator objects
     * @throws IllegalArgumentException if validators list is null or empty
     */
    public ProofOfAuthority(List<Validator> validators) {
        if (validators == null || validators.isEmpty()) {
            throw new IllegalArgumentException("Validators list cannot be null or empty");
        }
        this.authorizedValidators = validators;
        this.pendingBlocks = new java.util.HashMap<>();
        this.votes = new java.util.HashMap<>();
    }

    /**
     * Check if a validator is authorized to propose or vote on blocks.
     * 
     * @param validatorPublicKey the public key of the validator to check
     * @return true if the validator is authorized, false otherwise
     * @throws IllegalArgumentException if validatorPublicKey is null
     */
    public boolean isAuthorizedValidator(PublicKey validatorPublicKey) throws IllegalArgumentException {
        if (validatorPublicKey == null)
            throw new IllegalArgumentException("Public key is Null");

        if (authorizedValidators == null || authorizedValidators.isEmpty())
            return false;

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
     * 
     * @return unmodifiable list of authorized Validator objects
     */
    public List<Validator> getAuthorizedValidators() {
        if (authorizedValidators == null || authorizedValidators.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(authorizedValidators);

    }

    /**
     * Retrieve a specific validator by their public key.
     * 
     * @param publicKey the public key of the validator to retrieve
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
     * 
     * @param validatorId the ID of the validator to retrieve
     * @return the Validator object if found, null otherwise
     * @throws IllegalArgumentException if validatorId is null
     */
    public Validator getValidatorById(String validatorId) throws IllegalArgumentException {
        if (validatorId == null) {
            throw new IllegalArgumentException("Validator ID is null");
        }

        if (authorizedValidators == null || authorizedValidators.isEmpty()) {
            return null;
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
     * 
     * @param block the proposed block
     * @return true if the block was accepted for voting, false if rejected
     * @throws IllegalArgumentException if block is null
     */
    public boolean proposeBlock(Block block) throws IllegalArgumentException {

        if (block == null) {
            throw new IllegalArgumentException("Block is null");
        }

        String validatorId = block.getValidatorId();

        Validator validator = getValidatorById(validatorId);

        if (validator == null) {
            return false;
        }

        PublicKey validatorPublicKey = validator.getPublicKey();

        if (!isAuthorizedValidator(validatorPublicKey)) {
            return false;
        }

        if (!validateBlockSignature(block, validatorPublicKey)) {
            return false;
        }

        int blockIndex = block.getIndex();

        if (!pendingBlocks.containsKey(blockIndex)) {
            pendingBlocks.put(blockIndex, new java.util.ArrayList<>());
        }
        pendingBlocks.get(blockIndex).add(block);

        if (!votes.containsKey(blockIndex)) {
            votes.put(blockIndex, new java.util.HashMap<>());
        }

        votes.get(blockIndex).put(validatorId, true);

        return true;
    }

    /**
     * Validate that a block's signature matches the claimed validator.
     * 
     * @param block              the block to validate
     * @param validatorPublicKey the public key of the claimed validator
     * @return true if signature is valid, false otherwise
     * @throws IllegalArgumentException if block or validatorPublicKey is null
     */
    public boolean validateBlockSignature(Block block, PublicKey validatorPublicKey) {
        return block.verifySignature(validatorPublicKey);
    }

    /**
     * Record a validator's vote on a proposed block.
     * 
     * @param blockIndex         the index of the block being voted on
     * @param blockHash          the hash of the specific block candidate
     * @param validatorPublicKey the public key of the voting validator
     * @param approve            true to approve, false to reject
     * @return true if vote was recorded, false if validator not authorized
     * @throws IllegalArgumentException if validatorPublicKey or blockHash is null
     */
    public boolean recordVote(int blockIndex, String blockHash, PublicKey validatorPublicKey, boolean approve)
            throws IllegalArgumentException {
        if (validatorPublicKey == null)
            throw new IllegalArgumentException("ValidatorPublicKey is Null");

        if (blockHash == null)
            throw new IllegalArgumentException("BlockHash is Null");
        if (blockIndex < 0)
            return false;

        Validator v = getValidatorByPublicKey(validatorPublicKey);

        if (v == null)
            return false;

        if (!isAuthorizedValidator(validatorPublicKey))
            return false;

        if (!votes.containsKey(blockIndex)) {
            votes.put(blockIndex, new java.util.HashMap<>());
        }

        votes.get(blockIndex).put(v.getValidatorId(), approve);

        return true;
    }

    /**
     * Check if consensus has been reached for a specific block.
     * Consensus requires majority approval (>50% of authorized validators).
     * 
     * @param blockIndex the index of the block to check
     * @param blockHash  the hash of the specific block candidate
     * @return true if consensus reached, false otherwise
     * @throws IllegalArgumentException if blockHash is null
     */
    public boolean hasConsensus(int blockIndex, String blockHash) throws IllegalArgumentException {
        int approvalCount = getVoteCount(blockIndex, blockHash);
        int requiredVotes = getRequiredVotes();

        return approvalCount > requiredVotes;
    }

    /**
     * Get the block that achieved consensus for a given index.
     * 
     * @param blockIndex the index of the block to retrieve
     * @return the Block that achieved consensus, or null if no consensus yet
     */
    public Block getConsensusBlock(int blockIndex) {
        if (blockIndex < 0) {
            return null;
        }

        if (pendingBlocks == null || !pendingBlocks.containsKey(blockIndex) || pendingBlocks.get(blockIndex) == null) {
            return null;
        }

        for (Block block : pendingBlocks.get(blockIndex)) {
            if (block == null)
                continue;
            String blockHash = block.getHash();
            if (blockHash == null)
                continue;
            if (hasConsensus(blockIndex, blockHash)) {
                return block;
            }
        }
        return null;
    }

    /**
     * Clear pending blocks and votes for a specific block index after consensus.
     * 
     * @param blockIndex the index of the block to clear
     */
    public void clearPendingBlocks(int blockIndex) {
        if (blockIndex < 0) {
            return;
        }

        if (pendingBlocks == null && votes == null) {
            return;
        }

        boolean hasPending = (pendingBlocks != null && pendingBlocks.containsKey(blockIndex));
        boolean hasVotes = (votes != null && votes.containsKey(blockIndex));
        if (!hasPending && !hasVotes) {
            return;
        }

        if (pendingBlocks != null) {
            pendingBlocks.remove(blockIndex);
        }
        if (votes != null) {
            votes.remove(blockIndex);
        }
    }

    /**
     * Validate that a block meets all PoA consensus rules.
     * 
     * @param block         the block to validate
     * @param previousBlock the previous block in the chain (null for genesis)
     * @return true if all consensus rules are satisfied, false otherwise
     * @throws IllegalArgumentException if block is null
     */
    public boolean enforceConsensusRules(Block block, Block previousBlock) {
        if (block == null) {
            throw new IllegalArgumentException("Block is null");
        }

        Validator proposer = getValidatorById(block.getValidatorId());
        if (proposer == null) {
            return false;
        }

        PublicKey proposerKey = proposer.getPublicKey();
        if (!isAuthorizedValidator(proposerKey)) {
            return false;
        }

        if (!validateBlockSignature(block, proposerKey)) {
            return false;
        }

        if (previousBlock == null) {
            return block.getIndex() == 0;
        }

        if (block.getIndex() != previousBlock.getIndex() + 1) {
            return false;
        }

        if (block.getPreviousHash() == null || !block.getPreviousHash().equals(previousBlock.getHash())) {
            return false;
        }

        if (block.getTimestamp() < previousBlock.getTimestamp()) {
            return false;
        }

        return true;
    }

    /**
     * Determine if a block should be accepted into the blockchain.
     * 
     * @param block         the block to evaluate
     * @param previousBlock the previous block in the chain (null for genesis)
     * @return true if the block should be accepted, false otherwise
     * @throws IllegalArgumentException if block is null
     */
    public boolean shouldAcceptBlock(Block block, Block previousBlock) {
        if (block == null) {
            throw new IllegalArgumentException("Block is null");
        }

        if (!enforceConsensusRules(block, previousBlock)) {
            return false;
        }

        if (previousBlock == null && block.getIndex() == 0) {
            return true;
        }

        Block consensus = getConsensusBlock(block.getIndex());
        if (consensus == null) {
            return false;
        }

        return consensus.getHash() != null && consensus.getHash().equals(block.getHash());
    }

    /**
     * Get all pending blocks for a specific block index.
     * 
     * @param blockIndex the index to query
     * @return unmodifiable list of pending blocks for the index, empty list if none
     */
    public List<Block> getPendingBlocks(int blockIndex) {

        if (pendingBlocks == null || !pendingBlocks.containsKey(blockIndex)) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(pendingBlocks.get(blockIndex));
    }

    /**
     * Get vote count for a specific block candidate.
     * 
     * @param blockIndex the index of the block
     * @param blockHash  the hash of the specific block candidate
     * @return number of approval votes, or 0 if no votes yet
     * @throws IllegalArgumentException if blockHash is null
     */
    public int getVoteCount(int blockIndex, String blockHash) {
        if (blockHash == null) {
            throw new IllegalArgumentException("BlockHash is null");
        }

        if (votes == null || !votes.containsKey(blockIndex)) {
            return 0;
        }

        int approvalCount = 0;
        for (Boolean vote : votes.get(blockIndex).values()) {
            if (vote != null && vote) {
                approvalCount++;
            }
        }

        return approvalCount;
    }

    /**
     * Calculate the minimum number of votes required for consensus.
     * Majority is defined as more than 50% of authorized validators.
     * 
     * @return minimum number of approval votes needed
     */
    public int getRequiredVotes() {
        return (authorizedValidators.size() + 1) / 2;
    }

    /**
     * Determine which validator should propose the next block using round-robin.
     * The proposer is selected by:
     * {@code blockIndex % authorizedValidators.size()}.
     * This ensures fair rotation among all authorized validators.
     * 
     * @param blockIndex the index of the block to be proposed (must be >= 0)
     * @return the Validator whose turn it is to propose, or null if no validators
     *         exist
     * @throws IllegalArgumentException if blockIndex is negative
     */
    public Validator getCurrentProposer(int blockIndex) throws IllegalArgumentException {
        if (blockIndex < 0)
            throw new IllegalArgumentException("blockIndex must be >= 0");
        if (authorizedValidators == null || authorizedValidators.isEmpty())
            return null;
        List<Validator> activeValidators = new ArrayList<>();
        for (Validator v : authorizedValidators) {
            if (v.isActive())
                activeValidators.add(v);
        }
        if (activeValidators.isEmpty())
            return null;
        return activeValidators.get(blockIndex % activeValidators.size());

    }
}
