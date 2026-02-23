package com.immutable.credentials.core;

import java.io.IOException;
import java.util.ArrayList;

import java.security.PublicKey;
import java.util.List;

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

    // ===== Configuration =====
    private final String storageFileName;

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

        if (network != null) {
            network.stop();
        }

        saveBlockchain();
        running = false;
        Logger.log("Node " + nodeId + " stopped");
    }

    /**
     * Issue a new credential by creating a signed block and broadcasting it to
     * the network. Only validator nodes may call this method.
     *
     * @param credentials the list of credentials to include in the new block
     * @return the newly created and signed block
     * @throws IllegalStateException    if this node is not a validator or is not
     *                                  running
     * @throws IllegalArgumentException if the credentials list is null or empty
     * @throws Exception                if signing the block fails
     */
    public Block issueCredential(ArrayList<Credential> credentials) throws Exception {
        if (!isValidator()) {
            throw new IllegalStateException("Only validator nodes can issue credentials");
        }
        if (!running) {
            throw new IllegalStateException("Node is not running");
        }
        if (credentials == null || credentials.isEmpty()) {
            throw new IllegalArgumentException("Credentials cannot be null or empty");
        }

        Block latestBlock = blockchain.getLatestBlock();
        String previousHash = latestBlock != null ? latestBlock.getHash() : "0";
        int nextIndex = latestBlock != null ? latestBlock.getIndex() + 1 : 0;

        Block unsignedBlock = new Block(nextIndex, previousHash, credentials, validator.getValidatorId());
        String signature = validator.signBlock(unsignedBlock);
        Block signedBlock = new Block(unsignedBlock, signature);

        proofOfAuthority.proposeBlock(signedBlock);

        blockchain.addBlock(signedBlock);
        credentialIndex.addCredentials(credentials);
        storage.saveBlock(signedBlock, storageFileName);

        if (network != null) {
            network.broadcastBlock(signedBlock);
        }

        Logger.log("Credential issued by " + validator.getValidatorId() +
                " in block #" + signedBlock.getIndex());
        return signedBlock;
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
}
