package com.immutable.credentials.core;

import java.io.IOException;
import java.util.ArrayList;

import java.security.PublicKey;
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
 * Validator nodes are major accredited institutions that can propose and sign
 * blocks
 * and issue credentials via Proof-of-Authority consensus.
 * University nodes are accredited institutions that can issue credentials but
 * cannot propose or sign blocks.
 * Read-only nodes are participants such as students, employers, or public
 * explorers
 * that only verify credentials and cannot issue or seal anything.
 * Responsibilities include maintaining a local blockchain copy, connecting to
 * peers,
 * synchronizing the chain with the network, validating incoming blocks, and
 * handling
 * credential issuance for authorized nodes.
 */
public class Node {

    // ===== Identity =====
    private final String nodeId;
    private final String address;
    private final int port;

    /**
     * Classifies the role of this node in the network.
     * VALIDATOR is a major accredited institution that can issue credentials
     * and seal/approve blocks via Proof-of-Authority consensus.
     * UNIVERSITY is an accredited institution that can issue credentials but
     * cannot propose or sign blocks.
     * READ_ONLY is any participant such as a student, employer, or public explorer
     * that only verifies credentials.
     */
    public enum NodeType {
        VALIDATOR,
        UNIVERSITY,
        READ_ONLY
    }

    /** Classifies this node's role. Set once at construction and never changed. */
    private final NodeType nodeType;

    /**
     * The validator associated with this node.
     * Non-null for VALIDATOR nodes, null otherwise.
     * Use isValidator() or isUniversity() to check node type.
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
     * Credentials are added via submitCredential and drained by sealBlock
     * when the scheduler fires and it is this node's turn.
     */
    private final ArrayList<Credential> pendingCredentials;

    // ===== Block Scheduler =====
    /**
     * Scheduled executor that periodically triggers sealBlock.
     * Only active on validator nodes. Started in start(), shut down in stop().
     */
    private ScheduledExecutorService blockScheduler;

    // ===== Configuration =====
    private final String storageFileName;

    /** Interval in milliseconds between block-sealing attempts by the scheduler. */
    private static final long BLOCK_INTERVAL_MS = 30_000; // 30 seconds

    /** Maximum number of credentials that can be packed into a single block. */
    private static final int MAX_CREDENTIALS_PER_BLOCK = 50;

    /**
     * Validates the subset of constructor arguments that are common to all node
     * types.
     *
     * @throws IllegalArgumentException if any argument is null, blank, or out of
     *                                  range
     */
    private static void validateCommonNodeArgs(String nodeId, String address, int port,
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
    }

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

        validateCommonNodeArgs(nodeId, address, port, proofOfAuthority, storageFileName);
        if (validator == null) {
            throw new IllegalArgumentException("Validator cannot be null for a validator node");
        }

        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.nodeType = NodeType.VALIDATOR;
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

        validateCommonNodeArgs(nodeId, address, port, proofOfAuthority, storageFileName);

        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.nodeType = NodeType.READ_ONLY;
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
     * Create a university (issuer-only) node.
     *
     * <p>
     * University nodes belong to accredited institutions that are authorised
     * to submit credentials but are <em>not</em> consensus validators. Their
     * submitted credentials are pooled and sealed only when a quorum of
     * validator-universities approves the block.
     * </p>
     *
     * @param nodeId           the unique identifier for this node
     * @param address          the IP address or hostname this node listens on
     * @param port             the port number this node listens on
     * @param proofOfAuthority the PoA consensus engine shared across the network
     * @param storageFileName  the filename used to persist the blockchain
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public Node(String nodeId, String address, int port,
            ProofOfAuthority proofOfAuthority, String storageFileName,
            boolean isUniversityNode) {

        validateCommonNodeArgs(nodeId, address, port, proofOfAuthority, storageFileName);

        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.nodeType = isUniversityNode ? NodeType.UNIVERSITY : NodeType.READ_ONLY;
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
     * Initialize this node as the founding node of the network.
     * Creates the genesis block on the local chain. Must be called before
     * {@link #start()} and only on the very first node in the network.
     * All other nodes receive the genesis block via chain synchronization.
     *
     * @throws IllegalStateException if the blockchain already has blocks
     */
    public void initializeAsFoundingNode() {
        blockchain.initializeGenesis();
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
        String nodeTypeLabel = nodeType == NodeType.VALIDATOR ? " [VALIDATOR]"
                : nodeType == NodeType.UNIVERSITY ? " [UNIVERSITY]"
                        : " [READ-ONLY]";
        Logger.log("Node " + nodeId + " started successfully" + nodeTypeLabel);
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
     * Submit a credential for inclusion in a future block.
     * Can be called by university or validator nodes only.
     * The credential is broadcast to all peers. Only the current
     * round-robin proposer will accept it into their pending pool.
     *
     * @param credential the credential to submit
     * @throws IllegalStateException    if this node is not running or is a
     *                                  read-only node
     * @throws IllegalArgumentException if credential is null or already exists
     *                                  on-chain
     */
    public void submitCredential(Credential credential) {
        if (!running) {
            throw new IllegalStateException("Node is not running");
        }
        if (!isUniversity()) {
            throw new IllegalStateException(
                    "Only university or validator nodes may submit credentials");
        }
        if (credential == null) {
            throw new IllegalArgumentException("Credential cannot be null");
        }

        // Reject if already on-chain
        if (credentialIndex.getCredentialById(credential.getCredentialId()) != null) {
            throw new IllegalArgumentException(
                    "Credential " + credential.getCredentialId() + " already exists in the chain");
        }

        // Broadcast to all peers so the current proposer receives it
        if (network != null) {
            network.broadcastCredential(credential);
        }

        // If we are the current proposer, accept into our mempool
        acceptIfCurrentProposer(credential);
    }

    /**
     * Handle a credential received from the network via SUBMIT_CREDENTIAL.
     * Only the current round-robin proposer stores it in the pending pool;
     * other nodes silently ignore it (P2PNetwork already relays the message).
     *
     * @param credential the credential received from a peer
     */
    public void handleIncomingCredential(Credential credential) {
        if (credential == null)
            return;
        if (credentialIndex.getCredentialById(credential.getCredentialId()) != null)
            return;
        acceptIfCurrentProposer(credential);
    }

    /**
     * Accept a credential into the pending pool if this node is the
     * current round-robin proposer. No-op otherwise.
     *
     * @param credential the credential to potentially accept
     */
    private void acceptIfCurrentProposer(Credential credential) {
        if (!isValidator())
            return;

        int nextIndex = blockchain.size();
        Validator currentProposer = proofOfAuthority.getCurrentProposer(nextIndex);
        if (currentProposer == null
                || !currentProposer.getValidatorId().equals(validator.getValidatorId())) {
            return; // not our turn
        }

        synchronized (pendingCredentials) {
            for (Credential pending : pendingCredentials) {
                if (pending.getCredentialId().equals(credential.getCredentialId())) {
                    return; // already queued
                }
            }
            pendingCredentials.add(credential);
        }

        Logger.log("Credential " + credential.getCredentialId()
                + " accepted into mempool (" + pendingCredentials.size() + " pending)");
    }

    /**
     * Attempt to seal pending credentials into a new block.
     * Called periodically by the {@link #blockScheduler}.
     *
     * <p>
     * This method implements the round-robin proposer check and block creation
     * flow:
     * </p>
     *
     * <p>
     * Note: The block is NOT added to the chain here. It will only be added
     * once consensus is reached (enough BLOCK_VOTE approvals).
     * </p>
     */
    public void sealBlock() {
        if (!running || !isValidator()) {
            return; // silently skip — scheduler may fire outside the active lifecycle
        }

        // Round-robin check: only the designated proposer seals
        int nextIndex = blockchain.size();
        Validator currentProposer = proofOfAuthority.getCurrentProposer(nextIndex);
        if (currentProposer == null
                || !currentProposer.getValidatorId().equals(validator.getValidatorId())) {
            return; // not our turn
        }

        // Drain up to MAX_CREDENTIALS_PER_BLOCK from the pending pool
        ArrayList<Credential> batch;
        synchronized (pendingCredentials) {
            if (pendingCredentials.isEmpty()) {
                return; // nothing to seal
            }
            int count = Math.min(pendingCredentials.size(), MAX_CREDENTIALS_PER_BLOCK);
            batch = new ArrayList<>(pendingCredentials.subList(0, count));
            pendingCredentials.subList(0, count).clear();
        }

        // Build unsigned block
        Block latestBlock = blockchain.getLatestBlock();
        String previousHash = (latestBlock != null) ? latestBlock.getHash() : "0";
        Block unsignedBlock = new Block(nextIndex, previousHash, batch, validator.getValidatorId());

        try {
            // Sign and broadcast the proposal (block is NOT added to chain yet)
            String signature = validator.signBlock(unsignedBlock);
            Block signedBlock = new Block(unsignedBlock, signature);

            handleProposedBlock(signedBlock);

            Logger.log("Proposed block #" + nextIndex + " with " + batch.size() + " credential(s)");
        } catch (Exception e) {
            // Re-queue credentials on failure so they are not lost
            synchronized (pendingCredentials) {
                pendingCredentials.addAll(0, batch);
            }
            Logger.warn("Failed to seal block #" + nextIndex + ": " + e.getMessage());
        }
    }

    /**
     * Handle a proposed block received from another validator via PROPOSE_BLOCK
     * message.
     * Validates the block and casts a vote (approve/reject) back to the network.
     *
     * @param block the proposed block from a peer validator
     * @throws IllegalArgumentException if block is null
     */
    public void handleProposedBlock(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        if (!isValidator()) {
            return; // only validators participate in voting
        }

        // Validate against PoA consensus rules
        Block previousBlock = blockchain.getBlock(block.getIndex() - 1);
        boolean valid = proofOfAuthority.enforceConsensusRules(block, previousBlock);

        // Register the block in PoA pending state (records proposer's vote)
        if (valid) {
            proofOfAuthority.proposeBlock(block);
        }

        // Record our own vote
        proofOfAuthority.recordVote(
                block.getIndex(), block.getHash(), validator.getPublicKey(), valid);

        // Broadcast vote to peers
        if (network != null) {
            network.broadcastBlockVote(block.getIndex(), block.getHash(), valid);
        }

        Logger.log("Voted " + (valid ? "APPROVE" : "REJECT")
                + " on block #" + block.getIndex() + " from " + block.getValidatorId());

        // Check if consensus is already reached
        if (valid) {
            checkAndFinalizeConsensus(block.getIndex(), block.getHash());
        }
    }

    /**
     * Handle a BLOCK_VOTE message received from a peer validator.
     * Records the vote and checks if consensus has been reached.
     *
     * @param blockIndex the index of the block being voted on
     * @param blockHash  the hash of the proposed block
     * @param voterId    the validator ID of the voter
     * @param approve    true if the voter approves, false if they reject
     * @throws IllegalArgumentException if blockHash or voterId is null
     */
    public void handleBlockVote(int blockIndex, String blockHash, String voterId, boolean approve) {
        if (blockHash == null) {
            throw new IllegalArgumentException("Block hash cannot be null");
        }
        if (voterId == null) {
            throw new IllegalArgumentException("Voter ID cannot be null");
        }

        // Look up the voter
        Validator voter = proofOfAuthority.getValidatorById(voterId);
        if (voter == null) {
            Logger.warn("Ignoring vote from unknown validator: " + voterId);
            return;
        }

        // Record the vote in the PoA engine
        boolean recorded = proofOfAuthority.recordVote(
                blockIndex, blockHash, voter.getPublicKey(), approve);
        if (!recorded) {
            Logger.warn("Vote from " + voterId + " on block #" + blockIndex + " was not recorded");
            return;
        }

        Logger.log("Recorded " + (approve ? "APPROVE" : "REJECT")
                + " vote from " + voterId + " on block #" + blockIndex);

        // Check if consensus is reached after this vote
        if (approve) {
            checkAndFinalizeConsensus(blockIndex, blockHash);
        }
    }

    /**
     * Check if consensus has been reached for a block and finalize it if so.
     * Called after every vote (from handleProposedBlock and handleBlockVote).
     *
     * @param blockIndex the block index to check consensus for
     * @param blockHash  the block hash to check consensus for
     */
    private void checkAndFinalizeConsensus(int blockIndex, String blockHash) {
        if (!proofOfAuthority.hasConsensus(blockIndex, blockHash)) {
            return;
        }

        // Prevent double-finalization (block may already have been added)
        if (blockIndex < blockchain.size()) {
            return;
        }

        Block consensusBlock = proofOfAuthority.getConsensusBlock(blockIndex);
        if (consensusBlock == null) {
            return;
        }

        // Finalize: add to chain and update index
        blockchain.addBlock(consensusBlock);
        credentialIndex.addCredentials(consensusBlock.getCredentials());

        // Clean up PoA pending state
        proofOfAuthority.clearPendingBlocks(blockIndex);

        Logger.log("Block #" + blockIndex + " finalized via consensus (" +
                consensusBlock.getCredentials().size() + " credentials)");
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
     * Falls back to keeping the current in-memory chain if no stored file is found.
     * This preserves the genesis block on a founding node's first run.
     */
    private void loadBlockchain() {
        try {
            blockchain = storage.loadChain(storageFileName);
            Logger.log("Loaded blockchain from " + storageFileName +
                    " (" + blockchain.size() + " blocks)");
        } catch (IOException e) {
            // No stored chain found. Keep the current in-memory chain.
            // - Founding node: already has genesis from initializeAsFoundingNode()
            // - Joining node: empty chain, will sync from peers
            Logger.warn("Could not load blockchain from disk, keeping current state: " + e.getMessage());

            if (blockchain.size() == 0) {
                initializeAsFoundingNode();
                Logger.log("No stored chain found — genesis block created automatically.");
            }
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
     * Derived directly from whether a Validator was supplied at construction.
     * 
     * @return true if this node can propose and sign blocks, false for read-only
     *         nodes
     */
    public boolean isValidator() {
        return nodeType == NodeType.VALIDATOR;
    }

    /**
     * Check whether this node belongs to an accredited institution and may
     * therefore submit credentials. Returns true for both UNIVERSITY and VALIDATOR
     * nodes, and false for READ_ONLY nodes.
     * 
     * @return true if this node may issue credentials
     */
    public boolean isUniversity() {
        return nodeType == NodeType.UNIVERSITY || nodeType == NodeType.VALIDATOR;
    }

    /**
     * Get the {@link NodeType} of this node.
     *
     * @return the node type
     */
    public NodeType getNodeType() {
        return nodeType;
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

    // ===== Blockchain Delegates =====
    // These methods expose only what the P2P layer needs,
    // keeping Blockchain and ProofOfAuthority as internal details.

    /**
     * Get the current height (number of blocks) of the local chain.
     *
     * @return the chain height
     */
    public int getChainHeight() {
        return blockchain.size();
    }

    /**
     * Get the latest block in the local chain.
     *
     * @return the latest block, or null if the chain is empty
     */
    public Block getLatestBlock() {
        return blockchain.getLatestBlock();
    }

    /**
     * Get a block by its index in the local chain.
     *
     * @param index the block index
     * @return the block at the given index, or null if not found
     */
    public Block getBlock(int index) {
        return blockchain.getBlock(index);
    }

    /**
     * Get the full ordered list of blocks in the local chain.
     *
     * @return the list of blocks
     */
    public ArrayList<Block> getChain() {
        return blockchain.getChain();
    }

    /**
     * Replace the local chain with a new list of blocks.
     * Used during chain synchronization when a peer has a longer valid chain.
     *
     * @param newChain the replacement chain
     */
    public void replaceChain(ArrayList<Block> newChain) {
        blockchain.replaceChain(newChain);
    }

    /**
     * Validate an incoming chain against the authorized validator keys.
     *
     * @param incomingChain the chain to validate
     * @return true if the incoming chain is valid
     */
    public boolean validateIncomingChain(Blockchain incomingChain) {
        java.util.HashMap<String, PublicKey> map = new java.util.HashMap<>();
        List<Validator> validators = proofOfAuthority.getAuthorizedValidators();
        for (Validator v : validators) {
            map.put(v.getValidatorId(), v.getPublicKey());
        }
        return incomingChain.validateChain(map);
    }

    /**
     * Add a block directly to the local chain (bypasses consensus — used by the
     * network layer after it has already performed signature and hash validation).
     *
     * @param block the validated block to append
     */
    public void addBlockToChain(Block block) {
        blockchain.addBlock(block);
        credentialIndex.addCredentials(block.getCredentials());
    }

    // ===== PoA Delegates =====

    /**
     * Get the list of authorized validators from the PoA consensus engine.
     *
     * @return the list of authorized validators
     */
    public List<Validator> getAuthorizedValidators() {
        return proofOfAuthority.getAuthorizedValidators();
    }

    /**
     * Look up a validator by their ID.
     *
     * @param validatorId the validator ID to look up
     * @return the Validator, or null if not found
     */
    public Validator getValidatorById(String validatorId) {
        return proofOfAuthority.getValidatorById(validatorId);
    }

    /**
     * Validate a block's signature using the given public key.
     *
     * @param block     the block whose signature to verify
     * @param publicKey the expected signer's public key
     * @return true if the signature is valid
     */
    public boolean validateBlockSignature(Block block, PublicKey publicKey) {
        return proofOfAuthority.validateBlockSignature(block, publicKey);
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
