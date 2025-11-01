package com.immutable.credentials.core;

import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;
import java.util.ArrayList;
import java.util.Date;

// Blockchain.java
// Main blockchain data structure
// Manages the chain of blocks:
// - addBlock(): Add new block to chain
// - validateChain(): Verify integrity of entire chain
// - getBlock(): Retrieve specific block
// - getLatestBlock(): Get most recent block
// - searchByStudentId(): Find credentials by student ID

public class Blockchain {

    private ArrayList<Block> chain;
    
    public Blockchain() {
        this.chain = new ArrayList<>();
        this.chain.add(createGenesisBlock());
    }
    
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
    
    public Blockchain(ArrayList<Block> existingChain) {
        this.chain = new ArrayList<>();
        if (existingChain != null) {
            for (Block block : existingChain) {
                this.chain.add(new Block(block));
            }
        }
    }
    
    public void addBlcok(Block block) {
        this.chain.add(block);
    }
    
    public Block getBlock(int index) {
        if (index < 0 || index >= chain.size()) {
            return null;
        }
        return chain.get(index);
    }
    
    public Block getLatestBlock() {
        if (chain.isEmpty()) {
            return null;
        }
        return chain.get(chain.size() - 1);
    }
    
    public ArrayList<Block> searchByStudentId(String studentId) {
        ArrayList<Block> results = new ArrayList<>();
        
        if (studentId == null || studentId.trim().isEmpty()) {
            return results;
        }
        
        for (Block block : chain) {
            if (block.getCredential().getStudentId().equals(studentId)) {
                results.add(block);
            }
        }
        
        return results;
    }
}