package com.immutable.credentials.core;

import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

// Blockchain.java
// Main blockchain data structure
// Manages the chain of blocks:
// - addBlock(): Add new block to chain
// - validateChain(): Verify integrity of entire chain
// - getBlock(): Retrieve specific block
// - getLatestBlock(): Get most recent block
// - searchByStudentId(): Find credentials by student ID

/**
 * Simple in-memory blockchain for immutable credentials.
 *
 * <p>This class stores a linear list of {@link Block} instances and provides
 * basic helpers to add blocks, search by student id and validate the chain
 * integrity and signatures. Validation is conservative and checks index
 * monotonicity, previous-hash linkage, recalculated hash equality,
 * timestamp ordering and validator signatures.
 */
public class Blockchain {

    /** Ordered list of blocks (index 0 is genesis). */
    private ArrayList<Block> chain;

    /**
     * Create a new blockchain and initialize it with a genesis block.
     */
    public Blockchain() {
        this.chain = new ArrayList<>();
        this.chain.add(createGenesisBlock());
    }
    
    /**
     * Build the canonical genesis block for a fresh chain.
     *
     * @return genesis {@link Block}
     */
    private Block createGenesisBlock() {
        Credential genesisCredential = new Credential(
            "Genesis Student",
            new Date(0),
            "Genesis Degree",
            "System",
            "GENESIS-000",
            "GENESIS-CRED-000"
        );
        
        return new Block(0, "0", genesisCredential, "SYSTEM");
    }
    
    /**
     * Construct a blockchain by copying an existing list of blocks.
     *
     * @param existingChain the list of blocks to copy (may be null)
     */
    public Blockchain(ArrayList<Block> existingChain) {
        this.chain = new ArrayList<>();
        if (existingChain != null) {
            for (Block block : existingChain) {
                this.chain.add(new Block(block));
            }
        }
    }
    
    /**
     * Append a block to the end of the chain. Callers should ensure the
     * block's header fields (index/previousHash/signature) are valid before
     * adding; this method performs no checks itself.
     *
     * @param block block to append
     */
    public void addBlock(Block block) {
        this.chain.add(block);
    }
    
    /**
     * Retrieve a block by index.
     *
     * @param index block index
     * @return block or null when index out of range
     */
    public Block getBlock(int index) {
        if (index < 0 || index >= chain.size()) {
            return null;
        }
        return chain.get(index);
    }
    
    /**
     * Return the most recent block in the chain, or null when empty.
     */
    public Block getLatestBlock() {
        if (chain.isEmpty()) {
            return null;
        }
        return chain.get(chain.size() - 1);
    }
    
    /**
     * Search for blocks whose credential contains the given student id.
     *
     * @param studentId student identifier to search for
     * @return list of matching blocks (may be empty)
     */
    public ArrayList<Block> searchByStudentId(String studentId) {
        ArrayList<Block> results = new ArrayList<>();

        if (studentId == null || studentId.trim().isEmpty()) {
            return results;
        }

        for (Block block : chain) {
            // defensive: credential may be null in malformed blocks
            if (block.getCredential() != null && studentId.equals(block.getCredential().getStudentId())) {
                results.add(block);
            }
        }

        return results;
    }
    /**
     * Validate the entire chain.
     *
     * <p>The provided {@code map} must contain the public keys for validators
     * referenced by blocks; when a validator's public key is missing the
     * validation currently fails (policy decision).
     *
     * @param map mapping from validator id to their public key (required)
     * @return true when chain is valid, false otherwise
     */
    public boolean validateChain(ConcurrentHashMap<String, PublicKey> map) {
        if (chain == null || chain.size() == 0) {
            return false;
        }

        if (map == null) {
            // Validator public key registry is required for signature checks
            return false;
        }

        Block genesis = chain.get(0);

        // Basic genesis sanity check
        if (genesis.getIndex() != 0) {
            return false;
        }

        // Validate each consecutive block
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            // Index must be exactly previous + 1
            if (current.getIndex() != previous.getIndex() + 1) {
                return false;
            }

            // Must link to the previous hash
            if (!current.getPreviousHash().equals(previous.getHash())) {
                return false;
            }

            // Stored hash must equal recalculated canonical hash
            if (!current.isHashValid()) {
                return false;
            }

            // Timestamps should be non-decreasing
            if (current.getTimestamp() < previous.getTimestamp()) {
                return false;
            }

            PublicKey pk = map.get(current.getValidatorId());
            if (pk != null) {
                String data = current.getHash();
                if (!CryptoUtils.verifySignature(data, current.getSignature(), pk)) {
                    return false;
                }
            }
        }

        return true;
    }
}