package com.immutable.credentials.consensus;

// Validator.java
// Represents an authorized validator in the PoA system
// Properties:
// - validatorId: Unique identifier
// - publicKey: Public key for signature verification
// - privateKey: Private key for signing blocks (kept secure)
// - institution: Associated university/institution

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Objects;

import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;

public class Validator {
    private final String validatorId;
    private final String validatorName;
    private final String institution;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private boolean isActive;

    /**
     * Full constructor for creating a validator.
     * 
     * @param validatorId   Unique identifier for this validator
     * @param validatorName Human-readable name of the validator
     * @param publicKey     Public key for signature verification
     * @param privateKey    Private key for signing blocks (kept secure)
     * @param institution   Associated university/institution
     */
    public Validator(String validatorId, String validatorName,
            PublicKey publicKey, PrivateKey privateKey,
            String institution) {

        this.validatorId = validatorId;
        this.validatorName = validatorName;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.institution = institution;
        this.isActive = false; // Validators start inactive
    }

    // Sign a block with validator's private key
    public String signBlock(Block block) throws SignatureException {
        if (this.privateKey == null) {
            throw new IllegalStateException("Cannot sign block: Validator has no private key");
        }

        if (!this.isActive) {
            throw new IllegalStateException("Cannot sign block: Validator is not active");
        }

        if (block == null) {
            throw new IllegalArgumentException("Cannot sign null block");
        }
        try {
            // Step 1: Get the block's hash (the data to sign)
            String blockHash = block.getHash();

            // Step 2: Sign the hash using CryptoUtils with validator's private key
            String signature = CryptoUtils.signData(blockHash, this.privateKey);

            // Optional: Log this signing activity for audit trail

            return signature;

        } catch (Exception e) {
            throw new SignatureException("Failed to sign block: " + e.getMessage(), e);
        }
    }

    // Verify a signature using validator's public key
    public boolean verifySignature(Block block) {
        if (this.publicKey == null || block == null) {
            return false;
        }

        try {
            // Get block hash
            return block.verifySignature(publicKey);
        } catch (Exception e) {
            // Any exception means verification failed
            return false;
        }
    }

    // Activate the validator
    public void activate() {
        this.isActive = true;
    }

    // Deactivate the validator
    public void deactivate() {
        this.isActive = false;
    }

    // Getters

    public String getValidatorId() {
        return this.validatorId;
    }

    public String getValidatorName() {
        return this.validatorName;
    }

    public String getInstitution() {
        return this.institution;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    // Note: NO getter for privateKey (security)
    public boolean isActive() {
        return this.isActive;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Validator other = (Validator) obj;
        return Objects.equals(validatorId, other.validatorId) &&
                Objects.equals(validatorName, other.validatorName) &&
                Objects.equals(institution, other.institution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validatorId, validatorName, institution);
    }

    @Override
    public String toString() {
        return String.format(
                "Validator{id='%s', name='%s', institution='%s', active=%s, hasPrivateKey=%s}",
                validatorId,
                validatorName,
                institution,
                isActive,
                (privateKey != null) // Don't expose the key itself!
        );

    }

}