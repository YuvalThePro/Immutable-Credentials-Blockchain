package com.immutable.credentials.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.immutable.credentials.consensus.ProofOfAuthority;
import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;
import com.immutable.credentials.network.P2PNetwork;
import com.immutable.credentials.storage.BlockchainStorage;
import com.immutable.credentials.storage.CredentialIndex;
import com.immutable.credentials.util.Logger;

/**
 * Represents a node in the P2P network.
 *
 * Node types:
 * - Validator Node: an authorized participant that can propose and sign blocks
 * - Non-Validator Node: a read-only participant that stores and verifies the
 * chain
 *
 * Responsibilities:
 * - Maintain a local copy of the blockchain
 * - Connect to peer nodes via the P2P network
 * - Synchronize the blockchain with the network
 * - Process and validate incoming blocks
 * - Handle credential issuance (validator nodes only)
 */
public class Node {

    // ===== Identity =====
    private final String nodeId;
    private final String address;
    private final int port;

    /**
     * The validator associated with this node.
     * Non-null for validator nodes, null for read-only nodes.
     * Use {@link #isValidator()} to check node type.
     */
    private final Validator validator;

    // ===== Core Components =====
    private Blockchain blockchain;
    private P2PNetwork network;
    private final ProofOfAuthority proofOfAuthority;

    // ===== Storage & Indexing =====
    private final BlockchainStorage storage;
    private final CredentialIndex credentialIndex;

    // ===== State =====
    private volatile boolean running;

    // ===== Pending Credential Pool =====
    /**
     * Thread-safe pool of credentials waiting to be sealed into the next block.
     * Credentials are added via {@link #submitCredential(Credential)} and drained
     * by {@link #sealBlock()} when the scheduler fires and it is this node's turn.
     */
    private final ArrayList<Credential> pendingCredentials;

    // ===== Block Scheduler =====
    /**
     * Scheduled executor that periodically triggers {@link #sealBlock()}.
     * Only active on validator nodes. Started in {@link #start()}, shut down in {@link #stop()}.
     */
    private ScheduledExecutorService blockScheduler;

    // ===== Configuration =====
    private final String storageFileName;

    /** Interval in milliseconds between block-sealing attempts by the scheduler. */
    private static final long BLOCK_INTERVAL_MS = 30_000; // 30 seconds

    /** Maximum number of credentials that can be packed into a single block. */
    private static final int MAX_CREDENTIALS_PER_BLOCK = 50;

    /**
     * Create a validator node that can propose and sign blocks.
     *
     * @param nodeId           the unique identifier for this node
     * @param address          the IP address or hostname this node listens on
     * @param port             the port number this node listens on
     * @param validator        the validator identity with signing keys
     * @param proofOfAuthority the PoA consensus engine shared across the network
     * @param storageFileName  the filename used to persist the blockchain
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public Node(String nodeId, String address, int port,
            Validator validator, ProofOfAuthority proofOfAuthority,
            String storageFileName) {

        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be null or empty");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be null or empty");
        }
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1024 and 65535");
        }
        if (validator == null) {
            throw new IllegalArgumentException("Validator cannot be null for a validator node");
        }
        if (proofOfAuthority == null) {
            throw new IllegalArgumentException("ProofOfAuthority cannot be null");
        }
        if (storageFileName == null || storageFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage file name cannot be null or empty");
        }

        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.validator = validator;
        this.proofOfAuthority = proofOfAuthority;
        this.storageFileName = storageFileName;
        this.blockchain = new Blockchain();
        this.storage = new BlockchainStorage();
        this.credentialIndex = new CredentialIndex();
        this.pendingCredentials = new ArrayList<>();
        this.running = false;
    }

    /**
     * Create a non-validator (read-only) node.
     *
     * @param nodeId           the unique identifier for this node
     * @param address          the IP address or hostname this node listens on
     * @param port             the port number this node listens on
     * @param proofOfAuthority the PoA consensus engine shared across the network
     * @param storageFileName  the filename used to persist the blockchain
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public Node(String nodeId, String address, int port,
            ProofOfAuthority proofOfAuthority, String storageFileName) {

        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be null or empty");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be null or empty");
        }
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1024 and 65535");
        }
        if (proofOfAuthority == null) {
            throw new IllegalArgumentException("ProofOfAuthority cannot be null");
        }
        if (storageFileName == null || storageFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage file name cannot be null or empty");
        }

        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.validator = null;
        this.proofOfAuthority = proofOfAuthority;
        this.storageFileName = storageFileName;
        this.blockchain = new Blockchain();
        this.storage = new BlockchainStorage();
        this.credentialIndex = new CredentialIndex();
        this.pendingCredentials = new ArrayList<>();
        this.running = false;
    }

    /**
     * Start this node: load the blockchain from persistent storage, rebuild the
     * credential index, and bring up the P2P network listener.
     *
     * @throws IOException           if the blockchain cannot be loaded from disk
     * @throws IllegalStateException if the node is already running
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Node is already running");
        }

        Logger.log("Starting node: " + nodeId);

        loadBlockchain();
        credentialIndex.rebuildIndex(blockchain);

        if (network != null) {
            network.start();
        }

        // Start the block-sealing scheduler for validator nodes
        if (isValidator()) {
            blockScheduler = Executors.newSingleThreadScheduledExecutor();
            blockScheduler.scheduleAtFixedRate(this::sealBlock,
                    BLOCK_INTERVAL_MS, BLOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
            Logger.log("Block scheduler started (interval: " + BLOCK_INTERVAL_MS + "ms)");
        }

        running = true;
        Logger.log("Node " + nodeId + " started successfully" +
                (isValidator() ? " [VALIDATOR]" : " [READ-ONLY]"));
    }

    /**
     * Stop this node gracefully: shut down the P2P network and persist the
     * current blockchain state to disk.
     *
     * @throws IOException           if the blockchain cannot be saved to disk
     * @throws IllegalStateException if the node is not currently running
     */
    public void stop() throws IOException {
        if (!running) {
            throw new IllegalStateException("Node is not running");
        }

        Logger.log("Stopping node: " + nodeId);

        // Shut down the block-sealing scheduler
        if (blockScheduler != null && !blockScheduler.isShutdown()) {
            blockScheduler.shutdown();
            try {
                blockScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                blockScheduler.shutdownNow();
            }
            Logger.log("Block scheduler stopped");
        }

        if (network != null) {
            network.stop();
        }

        saveBlockchain();
        running = false;
        Logger.log("Node " + nodeId + " stopped");
    }

    /**
     * Submit a single credential to the pending pool for future block inclusion.
     * The credential will be batched with others and sealed into a block when
     * the scheduler fires and this node is the current round-robin proposer.
     *
     * <p>Thread-safe: synchronizes on the pending pool to allow concurrent submissions.</p>
     *
     * <p>Steps to implement:</p>
     * <ol>
     *   <li>Validate that this node is a validator and is running</li>
     *   <li>Validate the credential is not null</li>
     *   <li>Check that the credential ID does not already exist in the blockchain
     *       (use {@code blockchain.credentialIdExists()})</li>
     *   <li>Synchronize on {@code pendingCredentials} and add the credential</li>
     *   <li>Log the submission</li>
     * </ol>
     *
     * @param credential the credential to add to the pending pool
     * @throws IllegalStateException    if this node is not a validator or is not running
     * @throws IllegalArgumentException if credential is null or already exists in the chain
     */
    public void submitCredential(Credential credential) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Attempt to seal pending credentials into a new block.
     * Called periodically by the {@link #blockScheduler}.
     *
     * <p>This method implements the round-robin proposer check and block creation flow:</p>
     * <ol>
     *   <li>Check that the node is a running validator</li>
     *   <li>Check that the pending pool is not empty (synchronized on {@code pendingCredentials})</li>
     *   <li>Determine the next block index from {@code blockchain.getLatestBlock()}</li>
     *   <li>Use {@code proofOfAuthority.getCurrentProposer(nextIndex)} to check if it's this
     *       validator's turn — if not, return silently</li>
     *   <li>Drain up to {@link #MAX_CREDENTIALS_PER_BLOCK} credentials from the pool
     *       into a local list (synchronized)</li>
     *   <li>Create an unsigned {@link Block} with the drained credentials</li>
     *   <li>Sign the block using {@code validator.signBlock()}</li>
     *   <li>Call {@code proofOfAuthority.proposeBlock(signedBlock)} to register it locally
     *       and auto-vote YES</li>
     *   <li>Broadcast a {@code PROPOSE_BLOCK} message via the network using
     *       {@code network.broadcastProposedBlock(signedBlock)}</li>
     *   <li>Log the proposal</li>
     * </ol>
     *
     * <p>Note: The block is NOT added to the chain here. It will only be added
     * once consensus is reached (enough BLOCK_VOTE approvals).</p>
     */
    public void sealBlock() {
        // TODO: implement
    }

    /**
     * Handle a proposed block received from another validator via PROPOSE_BLOCK message.
     * Validates the block and casts a vote (approve/reject) back to the network.
     *
     * <p>Steps to implement:</p>
     * <ol>
     *   <li>Validate the block is not null</li>
     *   <li>Verify it's the proposer's turn using
     *       {@code proofOfAuthority.getCurrentProposer(block.getIndex())}</li>
     *   <li>Validate the block against consensus rules using
     *       {@code proofOfAuthority.enforceConsensusRules(block, previousBlock)}</li>
     *   <li>Register the proposal locally via {@code proofOfAuthority.proposeBlock(block)}</li>
     *   <li>If all checks pass, broadcast an approving BLOCK_VOTE via
     *       {@code network.broadcastBlockVote(block.getIndex(), block.getHash(), true)}</li>
     *   <li>If any check fails, broadcast a rejecting BLOCK_VOTE via
     *       {@code network.broadcastBlockVote(block.getIndex(), block.getHash(), false)}</li>
     *   <li>After voting, call {@link #checkAndFinalizeConsensus(int, String)} to see
     *       if consensus was just reached</li>
     * </ol>
     *
     * @param block the proposed block from a peer validator
     * @throws IllegalArgumentException if block is null
     */
    public void handleProposedBlock(Block block) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Handle a BLOCK_VOTE message received from a peer validator.
     * Records the vote and checks if consensus has been reached.
     *
     * <p>Steps to implement:</p>
     * <ol>
     *   <li>Look up the voting validator using
     *       {@code proofOfAuthority.getValidatorById(voterId)}</li>
     *   <li>Record the vote via
     *       {@code proofOfAuthority.recordVote(blockIndex, blockHash, voterPublicKey, approve)}</li>
     *   <li>Call {@link #checkAndFinalizeConsensus(int, String)} to see if we just
     *       reached majority</li>
     * </ol>
     *
     * @param blockIndex the index of the block being voted on
     * @param blockHash  the hash of the proposed block
     * @param voterId    the validator ID of the voter
     * @param approve    true if the voter approves, false if they reject
     * @throws IllegalArgumentException if blockHash or voterId is null
     */
    public void handleBlockVote(int blockIndex, String blockHash, String voterId, boolean approve) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Check if consensus has been reached for a block and finalize it if so.
     * Called after every vote (from handleProposedBlock and handleBlockVote).
     *
     * <p>Steps to implement:</p>
     * <ol>
     *   <li>Call {@code proofOfAuthority.hasConsensus(blockIndex, blockHash)}</li>
     *   <li>If false, return — still waiting for more votes</li>
     *   <li>If true, retrieve the block via
     *       {@code proofOfAuthority.getConsensusBlock(blockIndex)}</li>
     *   <li>Add the block to the local chain: {@code blockchain.addBlock(block)}</li>
     *   <li>Index the credentials: {@code credentialIndex.addCredentials(block.getCredentials())}</li>
     *   <li>Persist to storage: {@code storage.saveBlock(block, storageFileName)}</li>
     *   <li>Clean up PoA state: {@code proofOfAuthority.clearPendingBlocks(blockIndex)}</li>
     *   <li>Broadcast the finalized block to non-validator peers:
     *       {@code network.broadcastBlock(block)}</li>
     *   <li>Log the finalization</li>
     * </ol>
     *
     * @param blockIndex the block index to check consensus for
     * @param blockHash  the block hash to check consensus for
     */
    private void checkAndFinalizeConsensus(int blockIndex, String blockHash) {
        // TODO: implement
    }

    /**
     * Process a block received from a peer.
     * Validates it against PoA consensus rules before adding it to the local chain.
     *
     * @param block the incoming block to process
     * @return true if the block was accepted and added, false if rejected
     * @throws IllegalArgumentException if block is null
     */
    public boolean processIncomingBlock(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }

        Block previousBlock = blockchain.getBlock(block.getIndex() - 1);

        if (!proofOfAuthority.enforceConsensusRules(block, previousBlock)) {
            Logger.warn("Rejected block #" + block.getIndex() +
                    " from " + block.getValidatorId() + ": consensus rules not satisfied");
            return false;
        }

        blockchain.addBlock(block);
        credentialIndex.addCredentials(block.getCredentials());

        Logger.log("Accepted block #" + block.getIndex() + " from " + block.getValidatorId());
        return true;
    }

    /**
     * Attach a P2P network to this node.
     * Must be called before {@link #start()} to enable network communication.
     *
     * @param network the P2P network instance to attach
     * @throws IllegalArgumentException if network is null
     */
    public void setNetwork(P2PNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("Network cannot be null");
        }
        this.network = network;
    }

    /**
     * Load the blockchain from disk.
     * Falls back to a fresh genesis chain if no stored file is found.
     */
    private void loadBlockchain() {
        try {
            blockchain = storage.loadChain(storageFileName);
            Logger.log("Loaded blockchain from " + storageFileName +
                    " (" + blockchain.size() + " blocks)");
        } catch (IOException e) {
            Logger.warn("Could not load blockchain from disk, starting fresh: " + e.getMessage());
            blockchain = new Blockchain();
        }
    }

    /**
     * Save the current blockchain state to disk.
     *
     * @throws IOException if the blockchain cannot be written
     */
    private void saveBlockchain() throws IOException {
        storage.saveChain(blockchain, storageFileName);
        Logger.log("Blockchain saved to " + storageFileName);
    }

    // ===== Getters =====

    /**
     * Get the unique identifier of this node.
     *
     * @return the node ID
     */
    public String getId() {
        return nodeId;
    }

    /**
     * Get the network address of this node.
     *
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Get the port this node listens on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Check whether this node is a validator node.
     * Derived directly from whether a {@link Validator} was supplied at construction.
     *
     * @return true if this node can propose and sign blocks, false for read-only nodes
     */
    public boolean isValidator() {
        return validator != null;
    }

    /**
     * Get the validator associated with this node.
     * Returns null for non-validator nodes.
     *
     * @return the Validator, or null if this is not a validator node
     */
    public Validator getValidator() {
        return validator;
    }

    /**
     * Get the local copy of the blockchain.
     *
     * @return the blockchain
     */
    public Blockchain getBlockchain() {
        return blockchain;
    }

    /**
     * Get the ProofOfAuthority consensus engine used by this node.
     *
     * @return the proofOfAuthority
     */
    public ProofOfAuthority getProofOfAuthority() {
        return proofOfAuthority;
    }

    /**
     * Get the credential index for fast lookups without scanning the full chain.
     *
     * @return the credentialIndex
     */
    public CredentialIndex getCredentialIndex() {
        return credentialIndex;
    }

    /**
     * Get the P2P network attached to this node.
     *
     * @return the network, or null if no network has been attached yet
     */
    public P2PNetwork getNetwork() {
        return network;
    }

    /**
     * Check whether this node is currently running.
     *
     * @return true if the node has been started and not yet stopped
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the list of credentials currently waiting in the pending pool.
     * Returns a defensive copy to prevent external modification.
     *
     * @return a copy of the pending credentials list
     */
    public ArrayList<Credential> getPendingCredentials() {
        synchronized (pendingCredentials) {
            return new ArrayList<>(pendingCredentials);
        }
    }
}
