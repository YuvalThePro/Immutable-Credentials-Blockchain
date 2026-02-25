package com.immutable.credentials.service;

import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.core.Node;
import com.immutable.credentials.model.Block;

import java.util.List;

public class BlockchainService {

    /** The backend node that owns the blockchain. */
    private final Node node;

    /**
     * Construct a new BlockchainService bound to the given node.
     * 
     * @param node the Node instance owning the blockchain; must not be null
     * @throws IllegalArgumentException if node is null
     */
    public BlockchainService(Node node) throws IllegalArgumentException {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        this.node = node;
    }

    /**
     * Retrieve a snapshot of all blocks currently on the local chain.
     * 
     * @return a non-null, possibly empty, ordered list of Block objects
     *         starting from the genesis block at index 0
     */
    public List<Block> getAllBlocks() {
        return node.getChain();
    }

    /**
     * Retrieve a specific block by its index in the chain.
     * 
     * @param index the zero-based block index
     * @return the Block at the given index, or null if the index is out of range or the chain is empty
     * @throws IllegalArgumentException if index is negative
     */
    public Block getBlockByIndex(int index) throws IllegalArgumentException {
        if (index < 0) {
            throw new IllegalArgumentException("Block index cannot be negative");
        }
        return node.getBlock(index);
    }

    /**
     * Retrieve the most recently finalised block on the local chain.
     * 
     * @return the latest Block, or null if the chain is empty
     */
    public Block getLatestBlock() {
        return node.getLatestBlock();
    }

    /**
     * Return the current number of blocks on the local chain (the chain height).
     *
     * @return the number of blocks; {@code 0} if the chain is empty
     */
    public int getChainHeight() {
        return node.getChainHeight();
    }

    /**
     * Validate the integrity of the entire local chain against the known validator public keys.
     * 
     * @return true if the chain passes all integrity checks;
     *         false if any block has an invalid hash, broken link, invalid timestamp, or invalid signature
     */
    public boolean isChainValid() {
        return node.validateIncomingChain(new Blockchain(node.getChain()));
    }

    /**
     * Return the timestamp (milliseconds since epoch) of the most recently
     * finalised block, or {@code -1} if the chain is empty.
     *
     * @return the latest block's timestamp in milliseconds, or {@code -1}
     */
    public long getLastBlockTimestamp() {
        if (node.getChainHeight() == 0) {
            return -1;
        }
        return node.getLatestBlock().getTimestamp();
    }

    /**
     * Return the total number of credentials stored across all blocks on
     * the local chain, excluding the genesis credential.
     * 
     * @return the total credential count; 0 if only the genesis block exists
     */
    public int getTotalCredentialCount() {
        return node.getCredentialIndex().getCredentialCount();
    }
}
