package com.immutable.credentials.service;

import com.immutable.credentials.core.Node;
import com.immutable.credentials.network.Peer;
import com.immutable.credentials.util.Logger;

import java.util.List;

public class NetworkService {

    private static final long BOOTSTRAP_CONNECT_WAIT_MS = 2500L;
    private static final long BOOTSTRAP_POLL_MS = 100L;

    /** The backend node that owns the P2P network. */
    private final Node node;

    /**
     * Construct a new NetworkService bound to the given node.
     * 
     * @param node the Node instance owning the P2P network; must not be null
     * @throws IllegalArgumentException if node is null
     */
    public NetworkService(Node node) {
        this.node = node;
    }

    /**
     * Retrieve the list of peers that are currently connected to this node.
     * 
     * @return a non-null, possibly empty, list of Peer objects
     */
    public List<Peer> getConnectedPeers() {
        return node.getNetwork().getPeerList();
    }

    /**
     * Return the number of peers currently connected to this node.
     * 
     * @return the connected peer count; 0 if isolated or network is stopped
     */
    public int getPeerCount() {
        return getConnectedPeers().size();
    }

    /**
     * Attempt to establish a TCP connection to the peer at the specified address.
     * Validates the host and port before forwarding the call to
     * P2PNetwork.connectToPeer.
     * This is a fire-and-forget operation; use getConnectedPeers() afterwards
     * to confirm the peer was added.
     * 
     * @param host the hostname or IP address of the target peer; must not be blank
     * @param port the port number the target peer is listening on (1024-65535)
     * @throws IllegalArgumentException if host is blank or port is outside the
     *                                  valid range
     * @throws IllegalStateException    if the network is not currently running
     */
    public void connectToPeer(String host, int port) {
        if (host == null || host.trim().isEmpty())
            throw new IllegalArgumentException("Host must not be blank.");
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("Port must be between 1024 and 65535.");
        if (!isNetworkRunning())
            throw new IllegalStateException("Network is not running.");
        node.getNetwork().connectToPeer(host, port);
    }

    /**
     * Report whether the P2P network listener is actively running on this node.
     * 
     * @return true if the network is started and accepting connections; false
     *         otherwise
     */
    public boolean isNetworkRunning() {
        return node.getNetwork() != null && node.getNetwork().isRunning();
    }

    /**
     * Return a human-readable synchronisation status string suitable for
     * display in the Network Status panel.
     * Examples include "Synced", "Syncing (block 42/100)", and "Isolated - no
     * peers".
     * 
     * @return a non-null status string
     */
    public String getSyncStatusDescription() {
        if (!isNetworkRunning())
            return "Network stopped";
        int peers = getPeerCount();
        if (peers == 0)
            return "Isolated \u2013 no peers";
        return "Synced (" + peers + " peer" + (peers == 1 ? "" : "s") + ")";
    }

    /**
     * Try to bootstrap the node by connecting to one active validator endpoint.
     *
     * @param validators candidate validator endpoints from the database
     * @return true if at least one validator connection was established
     */
    public boolean bootstrap(List<ValidatorEndpoint> validators) {
        if (validators == null || validators.isEmpty()) {
            Logger.warn("Bootstrap skipped: no validator endpoints provided.");
            return false;
        }
        if (!isNetworkRunning()) {
            Logger.warn("Bootstrap skipped: network is not running.");
            return false;
        }

        for (ValidatorEndpoint endpoint : validators) {
            if (endpoint == null) {
                continue;
            }

            String host = endpoint.getAddress();
            int port = endpoint.getPort();
            if (host == null || host.trim().isEmpty() || port < 1024 || port > 65535) {
                continue;
            }
            if (isSelfEndpoint(endpoint, host, port)) {
                continue;
            }

            int baselinePeerCount = getPeerCount();
            try {
                connectToPeer(host, port);
            } catch (RuntimeException e) {
                Logger.warn("Bootstrap connection attempt failed for "
                        + endpoint.getValidatorId() + " (" + host + ":" + port + "): " + e.getMessage());
                continue;
            }

            if (waitForPeerIncrease(baselinePeerCount, BOOTSTRAP_CONNECT_WAIT_MS)) {
                Logger.log("Bootstrap connected via validator "
                        + endpoint.getValidatorId() + " (" + host + ":" + port + ")");
                return true;
            }
        }

        Logger.warn("Bootstrap connection failed. Connect manually.");
        return false;
    }

    private boolean waitForPeerIncrease(int baselinePeerCount, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (getPeerCount() > baselinePeerCount) {
                return true;
            }
            try {
                Thread.sleep(BOOTSTRAP_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return getPeerCount() > baselinePeerCount;
    }

    private boolean isSelfEndpoint(ValidatorEndpoint endpoint, String host, int port) {
        // Skip if endpoint points to this node by network endpoint.
        if (node.getPort() == port && host.equalsIgnoreCase(node.getAddress())) {
            return true;
        }

        // Skip if this node is itself the validator in the endpoint list.
        if (node.getValidator() != null && endpoint.getValidatorId() != null) {
            return endpoint.getValidatorId().equals(node.getValidator().getValidatorId());
        }
        return false;
    }
}
