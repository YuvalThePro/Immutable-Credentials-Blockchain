package com.immutable.credentials.consensus;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Objects;

import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;

/**
 * Represents a validator in the Proof-of-Authority consensus mechanism.
 * Validators can sign blocks and verify signatures using their key pairs.
 */
public class Validator {
    private final String validatorId;
    private final String validatorName;
    private final String institution;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private boolean isActive;

    /**
     * Create a new validator with complete identification and keys.
     * 
     * @param validatorId   the unique identifier for this validator
     * @param validatorName the human-readable name of the validator
     * @param publicKey     the public key for signature verification
     * @param privateKey    the private key for signing blocks (kept secure)
     * @param institution   the associated university or institution
     */
    public Validator(String validatorId, String validatorName,
            PublicKey publicKey, PrivateKey privateKey,
            String institution) {

        this.validatorId = validatorId;
        this.validatorName = validatorName;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.institution = institution;
        this.isActive = false;
    }

    /**
     * Sign a block with the validator's private key.
     * 
     * @param block the block to sign
     * @return the Base64-encoded signature
     * @throws SignatureException       if signing fails
     * @throws IllegalStateException    if validator lacks private key or is
     *                                  inactive
     * @throws IllegalArgumentException if block is null
     */
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
            String blockHash = block.getHash();
            String signature = CryptoUtils.signData(blockHash, this.privateKey);
            return signature;

        } catch (Exception e) {
            throw new SignatureException("Failed to sign block: " + e.getMessage(), e);
        }
    }

    /**
     * Verify a block's signature using the validator's public key.
     * 
     * @param block the block to verify
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(Block block) {
        if (this.publicKey == null || block == null) {
            return false;
        }

        try {
            return block.verifySignature(publicKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Activate the validator.
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Deactivate the validator.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Get the validator ID.
     * 
     * @return the validator ID
     */
    public String getValidatorId() {
        return this.validatorId;
    }

    /**
     * Get the validator name.
     * 
     * @return the validator name
     */
    public String getValidatorName() {
        return this.validatorName;
    }

    /**
     * Get the institution.
     * 
     * @return the associated institution
     */
    public String getInstitution() {
        return this.institution;
    }

    /**
     * Get the public key.
     * 
     * @return the public key
     */
    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    /**
     * Check if the validator is active.
     * 
     * @return true if active, false otherwise
     */
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
                (privateKey != null));

    }

}