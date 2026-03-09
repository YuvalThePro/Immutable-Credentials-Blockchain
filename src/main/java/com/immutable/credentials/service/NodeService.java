package com.immutable.credentials.service;

import com.immutable.credentials.core.Node;

import java.io.IOException;
import com.immutable.credentials.consensus.Validator;
import java.util.List;

public class NodeService {

    /** The backend node managed by this service. */
    private final Node node;

    /**
     * Construct a new NodeService bound to the given node.
     * 
     * @param node the Node instance to manage and expose to the UI; must not be
     *             null
     * @throws IllegalArgumentException if node is null
     */
    public NodeService(Node node) {
        this.node = node;
    }

    /**
     * Start the node: load the blockchain from disk, rebuild the credential
     * index, and bring up the P2P network listener.
     *
     * @throws IOException           if the blockchain cannot be loaded from disk
     * @throws IllegalStateException if the node is already running
     */
    public void startNode() throws IOException {
        node.start();
    }

    /**
     * Stop the node gracefully: shut down the P2P network and persist the
     * current blockchain state to disk.
     * 
     * @throws IOException           if the blockchain cannot be saved to disk
     * @throws IllegalStateException if the node is not currently running
     */
    public void stopNode() throws IOException {
        node.stop();
    }

    /**
     * Initialise this node as the founding (genesis) node of the network.
     * Creates the genesis block on the local chain.
     *
     * @throws IllegalStateException if the blockchain already contains blocks
     */
    public void initializeAsFoundingNode() {
        node.initializeAsFoundingNode();
    }

    /**
     * Return the unique identifier of this node.
     * 
     * @return the node ID string; never null
     */
    public String getNodeId() {
        return node.getId();
    }

    /**
     * Return the IP address or hostname this node listens on.
     * 
     * @return the node address; never null
     */
    public String getNodeAddress() {
        return node.getAddress();
    }

    /**
     * Return the port number this node listens on.
     *
     * @return the port number in the range 1024–65535
     */
    public int getNodePort() {
        return node.getPort();
    }

    /**
     * Report whether this node is a consensus validator, meaning one of the
     * major globally-accredited institutions that may propose and sign blocks
     * in addition to issuing credentials.
     * 
     * @return true if this node is a NodeType.VALIDATOR
     */
    public boolean isValidator() {
        return node.isValidator();
    }

    /**
     * Report whether this node belongs to an accredited institution and is
     * therefore authorised to submit credentials.
     *
     * @return {@code true} if this node may submit credentials
     */
    public boolean isUniversity() {
        return node.isUniversity();
    }

    /**
     * Return the validator ID of this node, or null if the node is not a validator.
     * 
     * @return the validator ID string, or null
     */
    public String getValidatorId() {
        return node.getValidator() != null ? node.getValidator().getValidatorId() : null;
    }

    /**
     * Report whether the node is currently running, meaning it has been started
     * and not yet stopped.
     * 
     * @return true if the node is running; false otherwise
     */
    public boolean isRunning() {
        return node.isRunning();
    }

    /**
     * Synchronize the authorized validator list with a fresh copy from the
     * database. Delegates to {@link Node#syncValidators(List)}.
     *
     * @param fresh the up-to-date validator list; must not be {@code null} or empty
     * @throws IllegalArgumentException if {@code fresh} is {@code null} or empty
     */
    public void syncValidators(List<Validator> fresh) {
        node.syncValidators(fresh);
    }
}
