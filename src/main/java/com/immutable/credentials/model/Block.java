package com.immutable.credentials.model;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.immutable.credentials.crypto.CryptoUtils;

/**
 * Represents a single block in the immutable credentials blockchain.
 * Each block contains a header with metadata and a credential payload.
 * Provides methods to verify hash integrity and cryptographic signatures.
 */
public class Block {

    private final BlockHeader header;
    private final ArrayList<Credential> credentials;
    // voterId -> Base64-encoded RSA signature of blockHash. NOT part of the hash.
    private final Map<String, String> voterAttestations;

    /**
     * Create a new block with calculated hash and signature.
     * 
     * @param index        the block index in the chain
     * @param previousHash the hash of the previous block
     * @param credential   the credential payload
     * @param validatorId  the ID of the validator creating the block
     * @param signature    the cryptographic signature
     * @throws IllegalArgumentException if credential or validatorId is invalid
     */
    public Block(int index, String previousHash, ArrayList<Credential> credentials, String validatorId,
            String signature) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials are required");
        }
        if (validatorId == null || validatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validator ID is required");
        }

        long timestamp = new Date().getTime();
        String hash = calculateHash(index, timestamp, previousHash, credentials, validatorId);

        this.header = new BlockHeader(index, timestamp, previousHash, hash, validatorId, signature);
        this.credentials = new ArrayList<>(credentials);
        this.voterAttestations = new LinkedHashMap<>();
    }

    /**
     * Create a new block with calculated hash without signature.
     * Signature can be added later.
     * 
     * @param index        the block index in the chain
     * @param previousHash the hash of the previous block
     * @param credential   the credential payload
     * @param validatorId  the ID of the validator creating the block
     * @throws IllegalArgumentException if credential or validatorId is invalid
     */
    public Block(int index, String previousHash, ArrayList<Credential> credentials, String validatorId) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials are required");
        }
        if (validatorId == null || validatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validator ID is required");
        }

        long timestamp = new Date().getTime();
        String hash = calculateHash(index, timestamp, previousHash, credentials, validatorId);

        this.header = new BlockHeader(index, timestamp, previousHash, hash, validatorId);
        this.credentials = new ArrayList<>(credentials);
        this.voterAttestations = new LinkedHashMap<>();
    }

    /**
     * Create a new block with a caller-supplied fixed timestamp and calculated
     * hash.
     * Use this for deterministic blocks (e.g. genesis) where every node must
     * produce the exact same hash regardless of wall-clock time.
     *
     * @param index          the block index in the chain
     * @param fixedTimestamp the exact timestamp to embed (milliseconds since epoch)
     * @param previousHash   the hash of the previous block
     * @param credentials    the credential payload
     * @param validatorId    the ID of the validator creating the block
     * @param signature      the cryptographic signature
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public Block(int index, long fixedTimestamp, String previousHash,
            ArrayList<Credential> credentials, String validatorId, String signature) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials are required");
        }
        if (validatorId == null || validatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validator ID is required");
        }
        String hash = calculateHash(index, fixedTimestamp, previousHash, credentials, validatorId);
        this.header = new BlockHeader(index, fixedTimestamp, previousHash, hash, validatorId, signature);
        this.credentials = new ArrayList<>(credentials);
        this.voterAttestations = new LinkedHashMap<>();
    }

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
        this.credentials = new ArrayList<>(other.credentials);
        this.voterAttestations = new LinkedHashMap<>(other.voterAttestations);
    }

    /**
     * Create a copy of an existing block with a new signature.
     * 
     * @param other     the block to copy
     * @param signature the signature to set on the new block
     * @throws IllegalArgumentException if other is null
     */
    public Block(Block other, String signature) {
        if (other == null) {
            throw new IllegalArgumentException("Block to copy cannot be null");
        }
        this.header = new BlockHeader(other.header, signature);
        this.credentials = new ArrayList<>(other.credentials);
        this.voterAttestations = new LinkedHashMap<>(other.voterAttestations);
    }

    /**
     * Create a block from stored data during deserialization.
     * This constructor preserves exact header values without recalculating hash.
     * Used by JsonSerializer when loading blocks from storage.
     * Public to allow deserialization from outside the package.
     * 
     * @param index        the block index
     * @param timestamp    the block timestamp
     * @param previousHash the previous block hash
     * @param hash         the block hash (not recalculated)
     * @param validatorId  the validator ID
     * @param signature    the block signature (may be null)
     * @param credential   the credential payload
     * @throws IllegalArgumentException if any required field is invalid
     */
    public Block(int index, long timestamp, String previousHash, String hash,
            String validatorId, String signature, ArrayList<Credential> credentials) {
        this(index, timestamp, previousHash, hash, validatorId, signature, credentials,
                new LinkedHashMap<>());
    }

    /**
     * Create a block from stored data during deserialization, including voter attestations.
     * This constructor preserves exact header values without recalculating hash.
     *
     * @param index              the block index
     * @param timestamp          the block timestamp
     * @param previousHash       the previous block hash
     * @param hash               the block hash (not recalculated)
     * @param validatorId        the validator ID
     * @param signature          the block signature (may be null)
     * @param credentials        the credential payload
     * @param voterAttestations  map of voterId -> Base64 vote signature (may be empty)
     * @throws IllegalArgumentException if any required field is invalid
     */
    public Block(int index, long timestamp, String previousHash, String hash,
            String validatorId, String signature, ArrayList<Credential> credentials,
            Map<String, String> voterAttestations) {
        // Validate credential first
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials are required");
        }

        // Validate validatorId
        if (validatorId == null || validatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validator ID is required");
        }

        // Validate index
        if (index < 0) {
            throw new IllegalArgumentException("Block index cannot be negative");
        }

        // Validate timestamp
        if (timestamp < 0) {
            throw new IllegalArgumentException("Block timestamp cannot be negative");
        }

        // Validate hash format (must be exactly 64 hex chars for SHA-256)
        if (hash == null || !hash.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException("Hash must be a valid 64-character hexadecimal string");
        }

        // Validate previousHash (genesis can be "0", others must be 64 hex chars)
        if (previousHash == null ||
                (!previousHash.equals("0") && !previousHash.matches("^[a-fA-F0-9]{64}$"))) {
            throw new IllegalArgumentException("Previous hash must be '0' or a valid 64-character hexadecimal string");
        }

        // Validate signature format if present (Base64 encoded)
        if (signature != null && !signature.trim().isEmpty()) {
            if (!signature.matches("^[A-Za-z0-9+/]+={0,2}$")) {
                throw new IllegalArgumentException("Signature must be a valid Base64 string");
            }
        }

        this.header = new BlockHeader(index, timestamp, previousHash, hash, validatorId, signature);
        this.credentials = new ArrayList<>(credentials);
        this.voterAttestations = voterAttestations != null
                ? new LinkedHashMap<>(voterAttestations)
                : new LinkedHashMap<>();
    }

    /**
     * Calculate SHA-256 hash of the block's data.
     * 
     * @param index        the block index
     * @param timestamp    the block timestamp in milliseconds
     * @param previousHash the hash of the previous block
     * @param credential   the credential payload
     * @param validatorId  the ID of the validator
     * @return hex-encoded SHA-256 hash of the concatenated data
     */
    private String calculateHash(int index, long timestamp, String previousHash,
            ArrayList<Credential> credentials, String validatorId) {
        String data = index + timestamp + previousHash + credentials.toString() + validatorId;
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
                credentials,
                header.getValidatorId());
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
                credentials,
                header.getValidatorId());

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
    public ArrayList<Credential> getCredentials() {
        return credentials;
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

    /**
     * Record a validator's attestation (vote signature) on this block.
     * Attestations are added after the block is created but before finalization.
     *
     * @param voterId   the validator ID of the voter
     * @param signature Base64-encoded RSA signature of this block's hash
     * @throws IllegalArgumentException if voterId or signature is null/blank, or voterId already recorded
     */
    public void addVoterAttestation(String voterId, String signature) {
        if (voterId == null || voterId.trim().isEmpty()) {
            throw new IllegalArgumentException("Voter ID cannot be null or empty");
        }
        if (signature == null || signature.trim().isEmpty()) {
            throw new IllegalArgumentException("Attestation signature cannot be null or empty");
        }
        if (voterAttestations.containsKey(voterId)) {
            return; // idempotent -- same voter already recorded
        }
        voterAttestations.put(voterId, signature);
    }

    /**
     * Get an unmodifiable view of all voter attestations recorded on this block.
     *
     * @return map of voterId -> Base64 vote signature
     */
    public Map<String, String> getVoterAttestations() {
        return Collections.unmodifiableMap(voterAttestations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, credentials, voterAttestations);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Block other = (Block) obj;
        return Objects.equals(header, other.header) &&
                Objects.equals(credentials, other.credentials) &&
                Objects.equals(voterAttestations, other.voterAttestations);
    }

    @Override
    public String toString() {
        return "Block [header=" + header + ", credentials=" + credentials
                + ", voters=" + voterAttestations.size() + "]";
    }
}
