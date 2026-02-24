package com.immutable.credentials.service;

import com.immutable.credentials.core.Node;
import com.immutable.credentials.model.Block;

import java.util.List;

/**
 * Middleware service that bridges the UI layer and the blockchain-related
 * data operations exposed by {@link Node} and its underlying
 * {@link com.immutable.credentials.core.Blockchain}.
 *
 * <p>
 * No GUI class should call {@link Node} or
 * {@link com.immutable.credentials.core.Blockchain} directly for chain queries.
 * Instead, every blockchain read must go through this service, which:
 * </p>
 * <ul>
 * <li>Returns defensive copies of chain data so the UI cannot mutate state</li>
 * <li>Aggregates statistics (height, last block time, validity) into simple
 * types</li>
 * <li>Handles edge cases such as an empty or uninitialised chain
 * gracefully</li>
 * </ul>
 *
 * <p>
 * All methods are read-only with respect to the blockchain state.
 * </p>
 */
public class BlockchainService {

    /** The backend node that owns the blockchain. */
    private final Node node;

    /**
     * Construct a new {@code BlockchainService} bound to the given node.
     *
     * @param node the {@link Node} instance owning the blockchain;
     *             must not be {@code null}
     * @throws IllegalArgumentException if {@code node} is {@code null}
     */
    public BlockchainService(Node node) {
        this.node = node;
    }

    /**
     * Retrieve a snapshot of all blocks currently on the local chain.
     *
     * <p>
     * The returned list is a defensive copy; modifications to it
     * do not affect the underlying blockchain.
     * </p>
     *
     * @return a non-null, possibly empty, ordered list of {@link Block} objects
     *         starting from the genesis block (index 0)
     */
    public List<Block> getAllBlocks() {
        return null;
    }

    /**
     * Retrieve a specific block by its index in the chain.
     *
     * @param index the zero-based block index
     * @return the {@link Block} at the given index, or {@code null} if the
     *         index is out of range or the chain is empty
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Block getBlockByIndex(int index) {
        return null;
    }

    /**
     * Retrieve the most recently finalised block on the local chain.
     *
     * @return the latest {@link Block}, or {@code null} if the chain is empty
     */
    public Block getLatestBlock() {
        return null;
    }

    /**
     * Return the current number of blocks on the local chain (the chain height).
     *
     * @return the number of blocks; {@code 0} if the chain is empty
     */
    public int getChainHeight() {
        return 0;
    }

    /**
     * Validate the integrity of the entire local chain against the known
     * validator public keys.
     *
     * <p>
     * This is an expensive operation for large chains; consider running it
     * on a background thread.
     * </p>
     *
     * @return {@code true} if the chain passes all integrity checks;
     *         {@code false} if any block has an invalid hash, broken link,
     *         invalid timestamp, or invalid signature
     */
    public boolean isChainValid() {
        return false;
    }

    /**
     * Return the timestamp (milliseconds since epoch) of the most recently
     * finalised block, or {@code -1} if the chain is empty.
     *
     * @return the latest block's timestamp in milliseconds, or {@code -1}
     */
    public long getLastBlockTimestamp() {
        return -1;
    }

    /**
     * Return the total number of credentials stored across all blocks on
     * the local chain (excluding the genesis credential).
     *
     * @return the total credential count; {@code 0} if only the genesis block
     *         exists
     */
    public int getTotalCredentialCount() {
        return 0;
    }
}
