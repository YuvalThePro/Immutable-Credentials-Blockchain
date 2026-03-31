package com.immutable.credentials.auth;

/**
 * Immutable data class holding the node configuration fetched from the database
 * after a successful login. Replaces reading node.properties for the node type,
 * validator ID, institution, port, and data directory.
 */
public class NodeConfig {

    private final int nodeUserId;
    private final String id;
    private final String displayName;
    private final String nodeType; // "VALIDATOR", "UNIVERSITY", or "READ_ONLY"
    private final String validatorId; // non-null only when nodeType is "VALIDATOR"
    private final String institution;
    private final String address;
    private final int port;
    private final boolean isActive;
    private final String dataDir;

    /**
     * Create a new NodeConfig populated from a database login result.
     *
     * @param nodeUserId  the primary key from node_users.id
     * @param id          the unique node ID (e.g., Israeli national ID or UUID)
     * @param displayName the person or institution display name stored in the DB
     * @param nodeType    one of "VALIDATOR", "UNIVERSITY", or "READ_ONLY"
     * @param validatorId the validator ID string, or null for non-validator nodes
     * @param institution the institution name associated with this node
     * @param address     network host/address stored for this node
     * @param port        the TCP port this node listens on
     * @param isActive    whether this node is marked online in the database
     * @param dataDir     the local directory used for blockchain storage
     */
    public NodeConfig(int nodeUserId, String id, String displayName, String nodeType,
            String validatorId, String institution, String address, int port, boolean isActive, String dataDir) {
        this.nodeUserId = nodeUserId;
        this.id = id;
        this.displayName = displayName;
        this.nodeType = nodeType;
        this.validatorId = validatorId;
        this.institution = institution;
        this.address = address;
        this.port = port;
        this.isActive = isActive;
        this.dataDir = dataDir;
    }

    /** Returns the node_users.id primary key for this account row. */
    public int getNodeUserId() {
        return nodeUserId;
    }

    /** Returns the unique node ID (e.g., Israeli national ID or UUID). */
    public String getId() {
        return id;
    }

    /** Returns the display name stored in the database for this user. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the node type string: "VALIDATOR", "UNIVERSITY", or "READ_ONLY".
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * Returns the validator ID, or null if this is not a validator node.
     */
    public String getValidatorId() {
        return validatorId;
    }

    /** Returns the institution name associated with this node. */
    public String getInstitution() {
        return institution;
    }

    /** Returns the network host/address stored for this node. */
    public String getAddress() {
        return address;
    }

    /** Returns the TCP port this node should listen on. */
    public int getPort() {
        return port;
    }

    /** Returns whether the node is marked online in the database. */
    public boolean isActive() {
        return isActive;
    }

    /** Returns the local data directory path for blockchain storage. */
    public String getDataDir() {
        return dataDir;
    }
}
