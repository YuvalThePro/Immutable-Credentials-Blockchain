package com.immutable.credentials.model;

import java.util.Objects;

/**
 * BlockHeader.java
 * Contains metadata for each block
 */
public class BlockHeader {
    
    /*
     * Fields: index, timestamp, previousHash, hash, validatorId, signature
     */
    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final String hash;
    private final String validatorId;
    private String signature;
    
    /**
     * Constructor
     */
    public BlockHeader(int index, long timestamp, String previousHash, 
                      String hash, String validatorId) {
        this.index = index;
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = hash;
        this.validatorId = validatorId;
        this.signature = null; // Set later by validator
    }

    /**
        * Copy Constructor
        */
        public BlockHeader(BlockHeader other) {
           this.index = other.index;
           this.timestamp = other.timestamp;
           this.previousHash = other.previousHash;
           this.hash = other.hash;
           this.validatorId = other.validatorId;
           this.signature = other.signature;
        }

    /*
     * Getters
     */
    public int getIndex() {
        return index;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getPreviousHash() {
        return previousHash;
    }
    
    public String getHash() {
        return hash;
    }
    
    public String getValidatorId() {
        return validatorId;
    }
    
    public String getSignature() {
        return signature;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(index, timestamp, previousHash, hash, validatorId, signature);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
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