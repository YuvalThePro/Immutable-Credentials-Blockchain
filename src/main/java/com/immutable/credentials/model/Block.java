package com.immutable.credentials.model;

import com.immutable.credentials.crypto.CryptoUtils;
import java.security.PublicKey;
import java.util.Date;
import java.util.Objects;

/**
 * Represents a single block in the immutable credentials blockchain.
 *
 * <p>Each block contains a header (index, timestamp, previous hash, hash,
 * validator id and signature) and a payload which is the {@link Credential}.
 * The block provides helpers to verify its internal hash and signature.
 */
public class Block {

    /** Header containing index, timestamps and cryptographic fields. */
    private final BlockHeader header;

    /** Credential payload stored in this block. */
    private final Credential credential;
    
    /**
     * Constructor - creates new block with calculated hash and signature
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
     * Constructor - creates new block with calculated hash (signature to be added later)
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
     * Calculate SHA-256 hash of the block's canonical data.
     *
     * @param index block index
     * @param timestamp block timestamp (ms)
     * @param previousHash previous block's hash
     * @param credential credential payload
     * @param validatorId id of the validator who signed the block
     * @return hex-encoded SHA-256 of the concatenated data
     */
    private String calculateHash(int index, long timestamp, String previousHash,
                                 Credential credential, String validatorId) {
        String data = index + timestamp + previousHash + credential.toString() + validatorId;
        return CryptoUtils.applySha256(data);
    }
    
    /**
     * Recalculate hash and verify it matches stored hash
     */
    /**
     * Recalculate the canonical hash for this block and compare to the stored
     * header hash.
     *
     * @return true if the stored hash matches the recomputed canonical hash
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
     * <p>
     * This method recomputes the canonical block data (the same inputs used
     * when the block hash was created) and verifies the Base64 signature
     * against that data.
     *
     * @param publicKey public key of the validator
     * @return true when signature is present and valid for the recomputed data
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
