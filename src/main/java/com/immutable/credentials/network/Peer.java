package com.immutable.credentials.network;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class Peer implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The IP address or hostname of the peer node.
     */
    private final String address;
    
    /**
     * The port number on which the peer is listening.
     */
    private final int port;
    
    /**
     * Unique identifier for this peer node.
     * Used to distinguish nodes in the network.
     */
    private final String nodeId;
    
    /**
     * Indicates whether this peer is a validator node.
     * Validator nodes can participate in block validation and consensus.
     */
    private boolean isValidator;
    
    /**
     * Indicates whether this peer is currently connected.
     */
    private boolean connected;
    
    /**
     * Timestamp of the last successful communication with this peer.
     * Used for connection health monitoring and timeout detection.
     */
    private Instant lastSeen;
    
    
    /**
     * Constructs a new Peer with the specified network address and node ID.
     * 
     * @param address the IP address or hostname of the peer
     * @param port the port number on which the peer listens
     * @param nodeId the unique identifier for this peer node
     * @throws IllegalArgumentException if address is null or empty, 
     *         port is invalid, or nodeId is null or empty
     */
    public Peer(String address, int port, String nodeId) throws IllegalArgumentException{
        if(address == null || address.isEmpty())
            throw new IllegalArgumentException("Address cant be null or empty.");
        if(port < 1024 || port > 65535)
            throw new IllegalArgumentException("Port should be in between 8080 - 65535.");
        if(nodeId == null || nodeId.isEmpty())
            throw new IllegalArgumentException("NodeId cant be null or empty.");
        

        this.address = address;
        this.port = port;
        this.nodeId = nodeId;
        this.connected = false;
        this.isValidator = false;
        this.lastSeen = null;
    }
    
    /**
     * Gets the IP address or hostname of this peer.
     * 
     * @return the peer's address
     */
    public String getAddress() {
        return address;
    }
    
    /**
     * Gets the port number on which this peer is listening.
     * 
     * @return the peer's port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Gets the unique identifier for this peer node.
     * 
     * @return the node ID
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * Checks whether this peer is a validator node.
     * 
     * @return true if this peer can validate blocks, false otherwise
     */
    public boolean isValidator() {
        return isValidator;
    }
    
    /**
     * Sets the validator status of this peer.
     * 
     * @param validator true if this peer should be marked as a validator
     */
    public void setValidator(boolean validator) {
        this.isValidator = validator;
    }
    
    /**
     * Checks whether this peer is currently connected.
     * 
     * @return true if the peer is connected, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Sets the connection status of this peer.
     * 
     * @param connected true if the peer is connected, false otherwise
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    

    
    /**
     * Gets the timestamp of the last successful communication with this peer.
     * 
     * @return the last seen timestamp
     */
    public Instant getLastSeen() {
        return lastSeen;
    }
    
    /**
     * Updates the last seen timestamp to the current time.
     * Should be called whenever successful communication occurs.
     */
    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }
    
    
    
    
    /**
     * Returns the full network address of this peer in "host:port" format.
     * 
     * @return the network address string
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(address);
        sb.append(":");
        sb.append(port);
        return sb.toString();
    }
    
    /**
     * Checks if this peer has been inactive for longer than the specified timeout.
     * 
     * @param timeoutSeconds the timeout duration in seconds
     * @return true if the peer has been inactive longer than the timeout
     */
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Checks if this peer has timed out using the default timeout (30 seconds).
     *
     * @return true if the peer has been inactive longer than the default timeout
     */
    public boolean isTimedOut() {
        return isTimedOut(DEFAULT_TIMEOUT_SECONDS);
    }

    public boolean isTimedOut(long timeoutSeconds) {
        if (lastSeen == null) return true;
        return Instant.now().getEpochSecond() - lastSeen.getEpochSecond() > timeoutSeconds;
    }
    
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        return port == peer.port && 
               Objects.equals(address, peer.address) && 
               Objects.equals(nodeId, peer.nodeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(address, port, nodeId);
    }
    
    @Override
    public String toString() {
        return "Peer{" +
                "address='" + address + '\'' +
                ", port=" + port +
                ", nodeId='" + nodeId + '\'' +
                ", isValidator=" + isValidator +
                ", connected=" + connected +
                ", lastSeen=" + lastSeen +
                '}';
    }
}
