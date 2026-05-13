package com.immutable.credentials.model;

import java.util.Objects;

/**
 * Contains metadata for each block in the blockchain.
 * Stores index, timestamp, hashes, validator information, and signature.
 */
public class BlockHeader {

    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final String hash;
    private final String validatorId;
    private String signature;

    /**
     * Create a block header with all fields including signature.
     * 
     * @param index the block index in the chain
     * @param timestamp the timestamp when the block was created
     * @param previousHash the hash of the previous block
     * @param hash the hash of this block
     * @param validatorId the ID of the validator who created the block
     * @param signature the cryptographic signature of the block
     */
    public BlockHeader(int index, long timestamp, String previousHash,
            String hash, String validatorId, String signature) {
        this.index = index;
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = hash;
        this.validatorId = validatorId;
        this.signature = signature;
    }

    /**
     * Create a block header without a signature.
     * Signature can be added later via copy constructor.
     * 
     * @param index the block index in the chain
     * @param timestamp the timestamp when the block was created
     * @param previousHash the hash of the previous block
     * @param hash the hash of this block
     * @param validatorId the ID of the validator who created the block
     */
    public BlockHeader(int index, long timestamp, String previousHash,
            String hash, String validatorId) {
        this.index = index;
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = hash;
        this.validatorId = validatorId;
        this.signature = null;
    }

    /**
     * Create a copy of an existing block header.
     * 
     * @param other the block header to copy
     */
    public BlockHeader(BlockHeader other) {
        this.index = other.index;
        this.timestamp = other.timestamp;
        this.previousHash = other.previousHash;
        this.hash = other.hash;
        this.validatorId = other.validatorId;
        this.signature = other.signature;
    }

    /**
     * Create a copy of an existing block header with a new signature.
     * 
     * @param other the block header to copy
     * @param signature the signature to set on the new header
     */
    public BlockHeader(BlockHeader other, String signature) {
        this.index = other.index;
        this.timestamp = other.timestamp;
        this.previousHash = other.previousHash;
        this.hash = other.hash;
        this.validatorId = other.validatorId;
        this.signature = signature;
    }

    /**
     * Get the block index.
     * 
     * @return the block index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Get the block timestamp.
     * 
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the hash of the previous block.
     * 
     * @return the previous block hash
     */
    public String getPreviousHash() {
        return previousHash;
    }

    /**
     * Get the hash of this block.
     * 
     * @return the block hash
     */
    public String getHash() {
        return hash;
    }

    /**
     * Get the validator ID.
     * 
     * @return the ID of the validator who created this block
     */
    public String getValidatorId() {
        return validatorId;
    }

    /**
     * Get the block signature.
     * 
     * @return the cryptographic signature
     */
    public String getSignature() {
        return signature;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, timestamp, previousHash, hash, validatorId, signature);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        BlockHeader other = (BlockHeader) obj;
        return index == other.index &&
                timestamp == other.timestamp &&
                Objects.equals(previousHash, other.previousHash) &&
                Objects.equals(hash, other.hash) &&
                Objects.equals(validatorId, other.validatorId) &&
                Objects.equals(signature, other.signature);
    }

    @Override
    public String toString() {
        return "BlockHeader [index=" + index + ", timestamp=" + timestamp +
                ", previousHash=" + previousHash + ", hash=" + hash +
                ", validatorId=" + validatorId + ", signature=" + signature + "]";
    }
}