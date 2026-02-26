package com.immutable.credentials.auth;

/**
 * Immutable data class holding the node configuration fetched from the database
 * after a successful login. Replaces reading node.properties for the node type,
 * validator ID, institution, port, and data directory.
 */
public class NodeConfig {

    private final String id;
    private final String displayName;
    private final String nodeType; // "VALIDATOR", "UNIVERSITY", or "READ_ONLY"
    private final String validatorId; // non-null only when nodeType is "VALIDATOR"
    private final String institution;
    private final int port;
    private final String dataDir;

    /**
     * Create a new NodeConfig populated from a database login result.
     *
     * @param id          the unique node ID (e.g., Israeli national ID or UUID)
     * @param displayName the person or institution display name stored in the DB
     * @param nodeType    one of "VALIDATOR", "UNIVERSITY", or "READ_ONLY"
     * @param validatorId the validator ID string, or null for non-validator nodes
     * @param institution the institution name associated with this node
     * @param port        the TCP port this node listens on
     * @param dataDir     the local directory used for blockchain storage
     */
    public NodeConfig(String id, String displayName, String nodeType,
            String validatorId, String institution, int port, String dataDir) {
        this.id = id;
        this.displayName = displayName;
        this.nodeType = nodeType;
        this.validatorId = validatorId;
        this.institution = institution;
        this.port = port;
        this.dataDir = dataDir;
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

    /** Returns the TCP port this node should listen on. */
    public int getPort() {
        return port;
    }

    /** Returns the local data directory path for blockchain storage. */
    public String getDataDir() {
        return dataDir;
    }
}
