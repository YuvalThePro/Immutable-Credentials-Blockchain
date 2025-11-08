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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;

public class Validator {
    private final String validatorId;
    private final String validatorName;
    private final String institution;
    private final Blockchain blockchain;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private boolean isActive;

    // Full constructor
    public Validator(String validatorId, String validatorName,
            PublicKey publicKey, PrivateKey privateKey,
            String institution, Blockchain blockchain) {

        this.validatorId = validatorId;
        this.validatorName = validatorName;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.institution = institution;
        this.blockchain = blockchain;
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
    public boolean verifySignature(Block block, String signature) {
        if (this.publicKey == null || signature == null || block == null) {
            return false;
        }

        try {
            // Get block hash
            String blockHash = block.getHash();

            // Verify using CryptoUtils
            return CryptoUtils.verifySignature(blockHash, signature, this.publicKey);

        } catch (Exception e) {
            // Any exception means verification failed
            return false;
        }
    }

    // Validate a credential before adding to blockchain
    public boolean validateCredential(Credential credential) {
        if (credential == null) {
            System.out.println("Validation failed: Credential is null");
            return false;
        }

        // 1. Check student name is not null/empty
        if (credential.getStudentName() == null || credential.getStudentName().trim().isEmpty()) {
            System.out.println("Validation failed: Student name is null or empty");
            return false;
        }

        // 2. Check date awarded is valid
        if (credential.getDateAwarded() == null) {
            System.out.println("Validation failed: Date awarded is null");
            return false;
        }

        // Ensure date is not in the future
        Date now = new Date();
        if (credential.getDateAwarded().after(now)) {
            System.out.println("Validation failed: Date awarded is in the future");
            return false;
        }

        // 3. Check degree is not null/empty
        if (credential.getDegree() == null || credential.getDegree().trim().isEmpty()) {
            System.out.println("Validation failed: Degree is null or empty");
            return false;
        }

        // 4. Validate Israeli ID format
        if (!CryptoUtils.isValidIsraeliId(credential.getStudentId())) {
            System.out.println("Validation failed: Invalid Israeli ID format - " + credential.getStudentId());
            return false;
        }

        // 5. Check for duplicate credential ID in blockchain
        if (credential.getCredentialId() != null && blockchain.credentialIdExists(credential.getCredentialId())) {
            System.out.println("Validation failed: Credential ID already exists - " + credential.getCredentialId());
            return false;
        }

        // All validations passed
        System.out.println("Credential validation successful for student: " + credential.getStudentName());
        return true;
    }

    /**
     * Validates an entire block before adding to blockchain.
     * 
     * Checks:
     * 1. Block is not null
     * 2. Block hash is valid (recalculated hash matches stored hash)
     * 3. Block signature is valid
     * 4. Block is properly linked to previous block (except genesis)
     * 5. Credential data is valid
     * 
     * @param block The block to validate
     * @return true if block passes all validation checks, false otherwise
     */
    public boolean validateBlock(Block block) {
        // 1. Check block is not null
        if (block == null) {
            System.out.println("Block validation failed: Block is null");
            return false;
        }

        int index = block.getIndex();

        // 2. Verify block hash is valid (integrity check)
        if (!block.isHashValid()) {
            System.out.println("Block validation failed: Hash is invalid for block " + index);
            return false;
        }

        // 3. Verify block signature (authenticity check)
        if (!block.verifySignature(publicKey)) {
            System.out.println("Block validation failed: Signature verification failed for block " + index);
            return false;
        }

        // 4. Verify link to previous block (except genesis block at index 0)
        if (index != 0) {
            Block previousBlock = blockchain.getBlock(index - 1);

            if (previousBlock == null) {
                System.out.println("Block validation failed: Previous block not found for index " + index);
                return false;
            }

            if (!block.getPreviousHash().equals(previousBlock.getHash())) {
                System.out.println("Block validation failed: Block " + index + " not linked to previous block");
                return false;
            }
        }

        // 5. Validate the credential data
        if (!validateCredential(block.getCredential())) {
            System.out.println("Block validation failed: Credential validation failed for block " + index);
            return false;
        }

        // 6. Verify signature matches (double-check)
        if (!verifySignature(block, block.getSignature())) {
            System.out.println("Block validation failed: Signature does not match for block " + index);
            return false;
        }

        // All validations passed
        System.out.println("Block validation successful for block " + index);
        return true;
    }

    // Activate the validator
    public void activate() {
        this.isActive = true;
    }

    // Deactivate the validator
    public void deactivate() {
        this.isActive = false;
    }

    // May be addded
    // Export public validator info (without private key)
    // public ValidatorInfo exportPublicInfo(){}


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

    /**
     * Proposes a new block to be added to the blockchain.
     * 
     * @param credentials   List of credentials to include in the block
     * @param previousBlock The last block in the chain
     * @return A new signed block ready to be added to the blockchain
     * @throws IllegalStateException    if validator cannot propose blocks
     * @throws IllegalArgumentException if inputs are invalid
     */
    public Block proposeBlock(List<Credential> credentials, Block previousBlock) {
        // Validation 1: Check validator can propose
        if (!this.isActive) {
            throw new IllegalStateException("Cannot propose block: Validator is not active");
        }

        if (this.privateKey == null) {
            throw new IllegalStateException("Cannot propose block: Validator has no private key");
        }

        // Validation 2: Check inputs
        if (credentials == null || credentials.isEmpty()) {
            throw new IllegalArgumentException("Cannot propose block: Credentials list is null or empty");
        }

        if (previousBlock == null) {
            throw new IllegalArgumentException("Cannot propose block: Previous block is null");
        }

        // Validation 3: Validate the credential
        Credential credential = credentials.get(0);
        if (!validateCredential(credential)) {
            throw new IllegalArgumentException(
                    "Cannot propose block: Invalid credential for student " + credential.getStudentName());
        }

        // Validation 4: Validate the previous block (ensure chain integrity)
        if (!validateBlock(previousBlock)) {
            throw new IllegalArgumentException("Cannot propose block: Previous block is invalid");
        }

        // Step 1: Calculate new block index
        int newIndex = previousBlock.getIndex() + 1;

        // Step 2: Get previous block's hash for linking
        String previousHash = previousBlock.getHash();

        // Step 3: Get validator ID
        String validatorId = this.validatorId;

        // Step 4: Create unsigned block
        Block unsignedBlock = new Block(newIndex, previousHash, credential, validatorId);

        // Step 5: Sign the block
        String signature;
        try {
            signature = signBlock(unsignedBlock);
        } catch (SignatureException e) {
            throw new RuntimeException("Failed to sign proposed block: " + e.getMessage(), e);
        }

        // Step 6: Create the final signed block
        Block proposedBlock = new Block(newIndex, previousHash, credential, validatorId, signature);

        // Step 7: Log the proposal
        System.out.println(String.format(
                "[%s] Validator %s proposed block %d with hash: %s",
                LocalDateTime.now(),
                this.validatorId,
                newIndex,
                proposedBlock.getHash().substring(0, 8) + "..."));

        return proposedBlock;
    }

    // Vote on a proposed block
    // public Vote castVote(Block proposedBlock, boolean approve){}

    // Get validation history
    // public List<BlockValidation> getValidationHistory(){}

    // Log validation activity
    public void logValidation(Block block, boolean approved, String reason) {
    }

    // Broadcast signed block to network
    public void broadcastBlock(Block block) {
    }

    // Receive block proposals from other validators
    // public void receiveBlockProposal(BlockProposal proposal){}

}