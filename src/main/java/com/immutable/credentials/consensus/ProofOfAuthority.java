package com.immutable.credentials.consensus;

import com.immutable.credentials.model.Block;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implements Proof-of-Authority (PoA) consensus mechanism.
 * Manages authorized validators and enforces consensus rules for block acceptance.
 * 
 * Sprint 2 Implementation - Method signatures and documentation only.
 */
public class ProofOfAuthority {
    
    // Fields
    private final List<Validator> authorizedValidators;
    private final Map<Integer, List<Block>> pendingBlocks; // blockIndex -> list of proposed blocks
    private final Map<Integer, Map<String, Boolean>> votes; // blockIndex -> validatorId -> vote
    
    // Constructor
    
    /**
     * Initializes the PoA consensus engine with a list of authorized validators.
     * Loads the initial validator set (hardcoded for Sprint 2).
     * 
     * @param validators List of authorized Validator objects
     * @throws IllegalArgumentException if validators list is null or empty
     */
    public ProofOfAuthority(List<Validator> validators) {
        // TODO: Implementation required
        this.authorizedValidators = null;
        this.pendingBlocks = null;
        this.votes = null;
    }
    
    // Validator Management
    
    /**
     * Checks if a validator is authorized to propose or vote on blocks.
     * Compares the validator's public key against the authorized validator list.
     * 
     * @param validatorPublicKey The public key of the validator to check
     * @return true if the validator is authorized, false otherwise
     * @throws IllegalArgumentException if validatorPublicKey is null
     */
    public boolean isAuthorizedValidator(PublicKey validatorPublicKey) throws IllegalArgumentException {
        // TODO: Implementation required
        if(validatorPublicKey == null)
            throw new IllegalArgumentException("Public key is Null");
        
        if(authorizedValidators == null || authorizedValidators.isEmpty())
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
     * Retrieves the list of all authorized validators.
     * Returns an unmodifiable view to prevent external modification.
     * 
     * @return Unmodifiable list of authorized Validator objects
     */
    public List<Validator> getAuthorizedValidators() {
        // TODO: Implementation required
            // Return defensive copy - prevents external modification
        if (authorizedValidators == null || authorizedValidators.isEmpty()) {
            return Collections.emptyList();
        }
    
        return Collections.unmodifiableList(authorizedValidators);

    }
    
    /**
     * Retrieves a specific validator by their public key.
     * 
     * @param publicKey The public key of the validator to retrieve
     * @return The Validator object if found, null otherwise
     * @throws IllegalArgumentException if publicKey is null
     */
    public Validator getValidatorByPublicKey(PublicKey publicKey) throws IllegalArgumentException  {
        // TODO: Implementation required
        if(publicKey == null)
        {
            throw new IllegalArgumentException("Public key is Null");
        }

        for(Validator validator: authorizedValidators)
        {
            if(validator.getPublicKey() != null && validator.getPublicKey().equals(publicKey))
                return validator;
        }

        return null;
    }
    


    // Block Proposal and Validation
    
    /**
     * Retrieves a specific validator by their validator ID.
     * 
     * @param validatorId The ID of the validator to retrieve
     * @return The Validator object if found, null otherwise
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
     * Registers a proposed block from a validator.
     * Validates that the proposer is authorized before accepting the block.
     * Stores the block in pending blocks awaiting consensus.
     * 
     * @param block The proposed block
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
     * Validates that a block's signature matches the claimed validator.
     * Uses CryptoUtils to verify the block hash signature against validator's public key.
     * 
     * @param block The block to validate
     * @param validatorPublicKey The public key of the claimed validator
     * @return true if signature is valid, false otherwise
     * @throws IllegalArgumentException if block or validatorPublicKey is null
     */
    public boolean validateBlockSignature(Block block, PublicKey validatorPublicKey) {
        // TODO: Implementation required
        return block.verifySignature(validatorPublicKey);
    }
    
    // Voting and Consensusz
    
    /**
     * Records a validator's vote on a proposed block.
     * Validates that the voter is authorized before recording the vote.
     * 
     * @param blockIndex The index of the block being voted on
     * @param blockHash The hash of the specific block candidate
     * @param validatorPublicKey The public key of the voting validator
     * @param approve true to approve, false to reject
     * @return true if vote was recorded, false if validator not authorized
     * @throws IllegalArgumentException if validatorPublicKey or blockHash is null
     */
    public boolean recordVote(int blockIndex, String blockHash, PublicKey validatorPublicKey, boolean approve) 
    throws IllegalArgumentException{
        // TODO: Implementation required
        if(validatorPublicKey == null)
            throw new IllegalArgumentException("ValidatorPublicKey is Null");
        
        if(blockHash == null)
            throw new IllegalArgumentException("BlockHash is Null");
        if(blockIndex < 0)
            return false;
        

        Validator v = getValidatorByPublicKey(validatorPublicKey);
        
        if(v == null)
            return false;
        
        if(!isAuthorizedValidator(validatorPublicKey))
            return false;
        
        if(!votes.containsKey(blockIndex))
        {
            votes.put(blockIndex, new java.util.HashMap<>());
        }
        
        votes.get(blockIndex).put(v.getValidatorId(), approve);
        
        return true;
    }
    
    /**
     * Checks if consensus has been reached for a specific block.
     * Consensus requires majority approval (>50% of authorized validators).
     * 
     * @param blockIndex The index of the block to check
     * @param blockHash The hash of the specific block candidate
     * @return true if consensus reached, false otherwise
     * @throws IllegalArgumentException if blockHash is null
     */
    public boolean hasConsensus(int blockIndex, String blockHash) throws IllegalArgumentException {
        // getVoteCount will validate blockHash
        int approvalCount = getVoteCount(blockIndex, blockHash);
        int requiredVotes = getRequiredVotes();
        
        return approvalCount >= requiredVotes;
    }
    
    /**
     * Retrieves the block that achieved consensus for a given index.
     * Returns the first valid block with majority approval.
     * 
     * @param blockIndex The index of the block to retrieve
     * @return The Block that achieved consensus, or null if no consensus yet
     */
    public Block getConsensusBlock(int blockIndex) {
        if (blockIndex < 0) {
            return null;
        }

        if (pendingBlocks == null || !pendingBlocks.containsKey(blockIndex) || pendingBlocks.get(blockIndex) == null) {
            return null;
        }

        for (Block block : pendingBlocks.get(blockIndex)) {
            if (block == null) continue;
            String blockHash = block.getHash();
            if (blockHash == null) continue;
            if (hasConsensus(blockIndex, blockHash)) {
                return block;
            }
        }
        return null;
    }
    
    /**
     * Clears pending blocks and votes for a specific block index after consensus.
     * Called after a block is accepted into the blockchain.
     * 
     * @param blockIndex The index of the block to clear
     */
    public void clearPendingBlocks(int blockIndex) {
        if (blockIndex < 0) {
            return;
        }

        // Defensive: ensure maps are initialized
        if (pendingBlocks == null && votes == null) {
            return;
        }

        // If there are no pending entries or votes for this index, nothing to clear
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
    
    
    // Consensus Rules Enforcement
    
    /**
     * Validates that a block meets all PoA consensus rules:
     * - Block is proposed by an authorized validator
     * - Block signature is valid
     * - Block index follows the previous block
     * - Previous hash matches the actual previous block hash
     * 
     * @param block The block to validate
     * @param previousBlock The previous block in the chain (null for genesis)
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

        // Previous hash must match
        if (block.getPreviousHash() == null || !block.getPreviousHash().equals(previousBlock.getHash())) {
            return false;
        }

        // Timestamps should be non-decreasing
        if (block.getTimestamp() < previousBlock.getTimestamp()) {
            return false;
        }

        return true;
    }
    
    /**
     * Determines if a block should be accepted into the blockchain.
     * Combines signature validation, authorization check, and consensus verification.
     * 
     * @param block The block to evaluate
     * @param previousBlock The previous block in the chain (null for genesis)
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
    
    // Utility Methods
    
    /**
     * Retrieves all pending blocks for a specific block index.
     * Used for debugging and monitoring the consensus process.
     * 
     * @param blockIndex The index to query
     * @return Unmodifiable list of pending blocks for the index, empty list if none
     */
    public List<Block> getPendingBlocks(int blockIndex) {

        if (pendingBlocks == null || !pendingBlocks.containsKey(blockIndex)) {
            return Collections.emptyList(); // Return empty list if no blocks
        }

        return Collections.unmodifiableList(pendingBlocks.get(blockIndex));
    }
    /**
     * Retrieves vote count for a specific block candidate.
     * Returns the number of approval votes received.
     * 
     * @param blockIndex The index of the block
     * @param blockHash The hash of the specific block candidate
     * @return Number of approval votes, or 0 if no votes yet
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
     * Calculates the minimum number of votes required for consensus.
     * Majority is defined as more than 50% of authorized validators.
     * 
     * @return Minimum number of approval votes needed
     */
    public int getRequiredVotes() {
        return (authorizedValidators.size() + 1) / 2;
    }
}
