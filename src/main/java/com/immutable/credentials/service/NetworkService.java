package com.immutable.credentials.service;

import com.immutable.credentials.core.Node;
import com.immutable.credentials.network.Peer;

import java.util.List;

public class NetworkService {

    /** The backend node that owns the P2P network. */
    private final Node node;

    /**
     * Construct a new {@code NetworkService} bound to the given node.
     *
     * @param node the {@link Node} instance owning the P2P network;
     *             must not be {@code null}
     * @throws IllegalArgumentException if {@code node} is {@code null}
     */
    public NetworkService(Node node) {
        this.node = node;
    }

    /**
     * Retrieve the list of peers that are currently connected to this node.
     *
     * @return a non-null, possibly empty, list of {@link Peer} objects
     */
    public List<Peer> getConnectedPeers() {
        return node.getNetwork().getPeerList();
    }

    /**
     * Return the number of peers currently connected to this node.
     *
     * @return the connected peer count; {@code 0} if isolated or network is stopped
     */
    public int getPeerCount() {
        return getConnectedPeers().size();
    }

    /**
     * Attempt to establish a TCP connection to the peer at the specified address.
     *
     * <p>
     * Validates the host and port before forwarding the call to
     * {@link com.immutable.credentials.network.P2PNetwork#connectToPeer(String, int)}.
     * This is a fire-and-forget operation; use {@link #getConnectedPeers()}
     * afterwards
     * to confirm the peer was added.
     * </p>
     *
     * @param host the hostname or IP address of the target peer; must not be blank
     * @param port the port number the target peer is listening on (1024–65535)
     * @throws IllegalArgumentException if {@code host} is blank or {@code port} is
     *                                  outside the valid range
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
     * @return {@code true} if the network is started and accepting connections;
     *         {@code false} otherwise
     */
    public boolean isNetworkRunning() {
        return node.getNetwork() != null && node.getNetwork().isRunning();
    }

    /**
     * Return a human-readable synchronisation status string suitable for
     * display in the Network Status panel.
     *
     * <p>
     * Examples: {@code "Synced"}, {@code "Syncing (block 42/100)"},
     * {@code "Isolated – no peers"}.
     * </p>
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
}
