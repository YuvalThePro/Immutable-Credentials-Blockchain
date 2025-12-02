package com.immutable.credentials.model;

import com.immutable.credentials.crypto.CryptoUtils;
import java.security.PublicKey;
import java.util.Date;
import java.util.Objects;

/**
 * Represents a single block in the immutable credentials blockchain.
 * Each block contains a header with metadata and a credential payload.
 * Provides methods to verify hash integrity and cryptographic signatures.
 */
public class Block {

    private final BlockHeader header;
    private final Credential credential;
    
    /**
     * Create a new block with calculated hash and signature.
     * 
     * @param index the block index in the chain
     * @param previousHash the hash of the previous block
     * @param credential the credential payload
     * @param validatorId the ID of the validator creating the block
     * @param signature the cryptographic signature
     * @throws IllegalArgumentException if credential or validatorId is invalid
     */
    public Block(int index, String previousHash, Credential credential, String validatorId, String signature) {
        if (credential == null) {
            throw new IllegalArgumentException("Credential is required");
        }
        if (validatorId == null || validatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validator ID is required");
        }
        
        long timestamp = new Date().getTime();
        String hash = calculateHash(index, timestamp, previousHash, credential, validatorId);
        
        this.header = new BlockHeader(index, timestamp, previousHash, hash, validatorId, signature);
        this.credential = credential;
    }
    
    /**
     * Create a new block with calculated hash without signature.
     * Signature can be added later.
     * 
     * @param index the block index in the chain
     * @param previousHash the hash of the previous block
     * @param credential the credential payload
     * @param validatorId the ID of the validator creating the block
     * @throws IllegalArgumentException if credential or validatorId is invalid
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
    
    /**
     * Create a copy of an existing block.
     * 
     * @param other the block to copy
     * @throws IllegalArgumentException if other is null
     */
    public Block(Block other) {
        if (other == null) {
            throw new IllegalArgumentException("Block to copy cannot be null");
        }
        this.header = new BlockHeader(other.header);
        this.credential = new Credential(other.credential);
    }
    
    /**
     * Create a copy of an existing block with a new signature.
     * 
     * @param other the block to copy
     * @param signature the signature to set on the new block
     * @throws IllegalArgumentException if other is null
     */
    public Block(Block other, String signature) {
        if (other == null) {
            throw new IllegalArgumentException("Block to copy cannot be null");
        }
        this.header = new BlockHeader(other.header, signature);
        this.credential = new Credential(other.credential);
    }

    /**
     * Calculate SHA-256 hash of the block's data.
     * 
     * @param index the block index
     * @param timestamp the block timestamp in milliseconds
     * @param previousHash the hash of the previous block
     * @param credential the credential payload
     * @param validatorId the ID of the validator
     * @return hex-encoded SHA-256 hash of the concatenated data
     */
    private String calculateHash(int index, long timestamp, String previousHash,
                                 Credential credential, String validatorId) {
        String data = index + timestamp + previousHash + credential.toString() + validatorId;
        return CryptoUtils.applySha256(data);
    }
    
    /**
     * Verify that the stored hash matches the recalculated hash.
     * 
     * @return true if the hash is valid, false otherwise
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
     * Verify the block's signature using the provided public key.
     * 
     * @param publicKey the public key of the validator
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(PublicKey publicKey) {
        String sig = getSignature();
        if (publicKey == null || sig == null || sig.trim().isEmpty()) {
            return false;
        }

        String data = calculateHash(
            header.getIndex(),
            header.getTimestamp(),
            header.getPreviousHash(),
            credential,
            header.getValidatorId()
        );

        return CryptoUtils.verifySignature(data, sig, publicKey);
    }
    
    /**
     * Check if this block correctly links to the previous block.
     * 
     * @param previousBlock the previous block in the chain
     * @return true if this block's previousHash matches the previous block's hash
     */
    public boolean isLinkedTo(Block previousBlock) {
        if (previousBlock == null) {
            return false;
        }
        return header.getPreviousHash().equals(previousBlock.getHash());
    }
    
    /**
     * Get the block header.
     * 
     * @return the block header
     */
    public BlockHeader getHeader() {
        return header;
    }
    
    /**
     * Get the credential payload.
     * 
     * @return the credential
     */
    public Credential getCredential() {
        return credential;
    }
    
    /**
     * Get the block index.
     * 
     * @return the block index
     */
    public int getIndex() {
        return header.getIndex();
    }
    
    /**
     * Get the block timestamp.
     * 
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return header.getTimestamp();
    }
    
    /**
     * Get the hash of the previous block.
     * 
     * @return the previous block hash
     */
    public String getPreviousHash() {
        return header.getPreviousHash();
    }
    
    /**
     * Get the hash of this block.
     * 
     * @return the block hash
     */
    public String getHash() {
        return header.getHash();
    }
    
    /**
     * Get the validator ID.
     * 
     * @return the ID of the validator who created this block
     */
    public String getValidatorId() {
        return header.getValidatorId();
    }
    
    /**
     * Get the block signature.
     * 
     * @return the cryptographic signature
     */
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
