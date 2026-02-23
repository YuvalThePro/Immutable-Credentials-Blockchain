package com.immutable.credentials.core;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;

/**
 * Simple in-memory blockchain for immutable credentials.
 * Stores a linear list of blocks and provides methods to add blocks,
 * search by student ID, and validate chain integrity.
 */
public class Blockchain {

    private ArrayList<Block> chain;

    /**
     * Create an empty blockchain with no blocks.
     * Use {@link #initializeGenesis()} to add a genesis block for founding nodes,
     * or synchronize from a peer to receive the chain including genesis.
     */
    public Blockchain() {
        this.chain = new ArrayList<>();
    }

    /**
     * Initialize this blockchain with a genesis block.
     * Should only be called once by the founding node that creates the network.
     * Other nodes receive the genesis block (and the rest of the chain) via sync.
     *
     * @throws IllegalStateException if the chain already contains blocks
     */
    public void initializeGenesis() {
        if (!chain.isEmpty()) {
            throw new IllegalStateException("Cannot initialize genesis on a non-empty chain");
        }
        chain.add(createGenesisBlock());
    }

    /**
     * Create the genesis block for a fresh chain.
     * 
     * @return the genesis block
     */
    private Block createGenesisBlock() {
        Credential genesisCredential = new Credential(
                "Genesis Student",
                new Date(0),
                "Genesis Degree",
                "System",
                "GENESIS-000",
                "GENESIS-CRED-000");

        ArrayList<Credential> genesisCredentials = new ArrayList<>();
        genesisCredentials.add(genesisCredential);
        return new Block(0, "0", genesisCredentials, "SYSTEM", "GENESIS");
    }

    /**
     * Create a blockchain by copying an existing list of blocks.
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
     * Append a block to the end of the chain.
     * Does not perform validation - caller should validate before adding.
     * 
     * @param block the block to append
     */
    public void addBlock(Block block) {
        this.chain.add(block);
    }

    /**
     * Retrieve a block by its index.
     * 
     * @param index the block index
     * @return the block at the specified index, or null if index is out of range
     */
    public Block getBlock(int index) {
        if (index < 0 || index >= chain.size()) {
            return null;
        }
        return chain.get(index);
    }

    /**
     * Get the most recent block in the chain.
     * 
     * @return the latest block, or null if the chain is empty
     */
    public Block getLatestBlock() {
        if (chain.isEmpty()) {
            return null;
        }
        return chain.get(chain.size() - 1);
    }

    /**
     * Search for blocks whose credential contains the given student ID.
     * 
     * @param studentId the student identifier to search for
     * @return list of matching blocks (may be empty)
     */
    public ArrayList<Block> searchByStudentId(String studentId) {
        ArrayList<Block> results = new ArrayList<>();

        if (studentId == null || studentId.trim().isEmpty()) {
            return results;
        }

        for (Block block : chain) {
            ArrayList<Credential> credentials = block.getCredentials();
            if (credentials != null) {
                for (Credential cred : credentials) {
                    if (studentId.equals(cred.getStudentId())) {
                        results.add(block);
                        break; // Found match in this block, move to next block
                    }
                }
            }
        }

        return results;
    }

    /**
     * Get the number of blocks in the chain.
     *
     * @return size of the blockchain
     */
    public int size() {
        return chain.size();
    }

    /**
     * Get a copy of the entire chain.
     *
     * @return copy of the blockchain
     */
    public ArrayList<Block> getChain() {
        return new ArrayList<>(chain);
    }

    /**
     * Validate the entire blockchain against consensus rules.
     * Checks index ordering, hash linkage, hash validity, timestamps, and
     * signatures.
     * 
     * @param map mapping from validator ID to their public key
     * @return true if the chain is valid, false otherwise
     */
    public boolean validateChain(HashMap<String, PublicKey> map) {
        if (chain == null || chain.size() == 0) {
            return false;
        }

        if (map == null) {
            return false;
        }

        Block genesis = chain.get(0);

        if (genesis.getIndex() != 0) {
            return false;
        }

        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            if (current.getIndex() != previous.getIndex() + 1) {
                return false;
            }

            if (!current.getPreviousHash().equals(previous.getHash())) {
                return false;
            }

            if (!current.isHashValid()) {
                return false;
            }

            if (current.getTimestamp() < previous.getTimestamp()) {
                return false;
            }

            String validatorId = current.getValidatorId();
            PublicKey pk = map.get(validatorId);
            if (pk == null) {
                return false;
            }

            String signature = current.getSignature();
            if (signature == null || signature.trim().isEmpty()) {
                return false;
            }

            if (!CryptoUtils.verifySignature(current.getHash(), signature, pk)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Replace the entire chain with a new one.
     * Caller is responsible for validating the new chain before calling this.
     *
     * @param newChain the new chain to replace with
     */
    public synchronized void replaceChain(ArrayList<Block> newChain) {
        this.chain = new ArrayList<>(newChain);
    }

    /**
     * Check if a credential ID already exists in the blockchain.
     * 
     * @param credentialId the credential ID to check
     * @return true if the credential ID exists, false otherwise
     */
    public boolean credentialIdExists(String credentialId) {
        if (credentialId == null) {
            return false;
        }

        for (Block block : chain) {
            ArrayList<Credential> credentials = block.getCredentials();
            if (credentials != null) {
                for (Credential cred : credentials) {
                    if (cred.getCredentialId() != null &&
                            credentialId.equals(cred.getCredentialId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}