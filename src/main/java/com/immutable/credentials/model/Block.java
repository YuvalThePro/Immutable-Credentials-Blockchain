package com.immutable.credentials.model;

import com.immutable.credentials.crypto.CryptoUtils;
import java.util.Date;
import java.util.Objects;

/**
 * Block.java
 * Represents a block in the blockchain
 * Contains: Header (index, timestamp, previousHash, hash, validatorId, signature)
 *           Payload (Credential data)
 */
public class Block {
    
    /*
     * Fields: header and credential
     */
    private final BlockHeader header;
    private final Credential credential;
    
    /**
     * Constructor - creates new block with calculated hash
     */
    public Block(int index, String previousHash, Credential credential, String validatorId) {
        if (credential == null) {
            throw new IllegalArgumentException("Credential is required");
        }
        if (validatorId == null || validatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validator ID is required");
        }
        
        long timestamp = new Date().getTime();
        String hash = calculateHash(index, timestamp, previousHash, credential, validatorId);
        
        this.header = new BlockHeader(index, timestamp, previousHash, hash, validatorId);
        this.credential = credential;
    }
    /**
     * Copy constructor - creates a copy of an existing block
     */
    public Block(Block other) {
        if (other == null) {
            throw new IllegalArgumentException("Block to copy cannot be null");
        }
        this.header = new BlockHeader(other.header);
        this.credential = new Credential(other.credential);
    }
    
    /**
     * Calculate SHA-256 hash of block data
     */
    private String calculateHash(int index, long timestamp, String previousHash, 
                                 Credential credential, String validatorId) {
        String data = index + timestamp + previousHash + credential.toString() + validatorId;
        return CryptoUtils.applySha256(data);
    }
    
    /**
     * Recalculate hash and verify it matches stored hash
     */
    public boolean isHashValid() {
        String calculatedHash = calculateHash(
            header.getIndex(), 
            header.getTimestamp(), 
            header.getPreviousHash(), 
            credential, 
            header.getValidatorId()
        );
        return header.getHash().equals(calculatedHash);
    }
    
    /**
     * Check if this block links to the previous block
     */
    public boolean isLinkedTo(Block previousBlock) {
        if (previousBlock == null) {
            return false;
        }
        return header.getPreviousHash().equals(previousBlock.getHash());
    }
    
    /*
     * Getters
     */
    public BlockHeader getHeader() {
        return header;
    }
    
    public Credential getCredential() {
        return credential;
    }
    
    // Convenience getters for header fields
    public int getIndex() {
        return header.getIndex();
    }
    
    public long getTimestamp() {
        return header.getTimestamp();
    }
    
    public String getPreviousHash() {
        return header.getPreviousHash();
    }
    
    public String getHash() {
        return header.getHash();
    }
    
    public String getValidatorId() {
        return header.getValidatorId();
    }
    
    public String getSignature() {
        return header.getSignature();
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(header, credential);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Block other = (Block) obj;
        return Objects.equals(header, other.header) &&
               Objects.equals(credential, other.credential);
    }
    
    @Override
    public String toString() {
        return "Block [header=" + header + ", credential=" + credential + "]";
    }
}