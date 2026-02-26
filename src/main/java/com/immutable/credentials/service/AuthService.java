package com.immutable.credentials.service;

import com.immutable.credentials.auth.NodeConfig;
import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.util.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Handles authentication against the cloud SQL database (Supabase PostgreSQL).
 * On a successful login it returns a NodeConfig populated with the row data
 * so that MainWindow can initialise the node without reading node.properties.
 *
 * Password storage: passwords are stored in the database as SHA-256 hex
 * digests computed by CryptoUtils.applySha256. The plain-text password is never
 * sent to or stored in the database.
 *
 * SQL schema required (run once in your Supabase SQL editor):
 *
 * CREATE TABLE node_users (
 * id SERIAL PRIMARY KEY,
 * israeli_id VARCHAR(10) UNIQUE NOT NULL,
 * password_hash VARCHAR(64) NOT NULL,
 * node_type VARCHAR(20) NOT NULL CHECK (node_type IN
 * ('VALIDATOR','UNIVERSITY','READ_ONLY')),
 * validator_id VARCHAR(100),
 * institution VARCHAR(200) NOT NULL,
 * port INTEGER NOT NULL DEFAULT 8080,
 * data_dir VARCHAR(500) NOT NULL DEFAULT './data/default',
 * display_name VARCHAR(200),
 * created_at TIMESTAMP DEFAULT NOW()
 * );
 *
 * CREATE TABLE validators (
 * id SERIAL PRIMARY KEY,
 * validator_id VARCHAR(100) UNIQUE NOT NULL,
 * institution VARCHAR(200) NOT NULL,
 * public_key TEXT NOT NULL,
 * is_active BOOLEAN DEFAULT TRUE,
 * created_at TIMESTAMP DEFAULT NOW()
 * );
 *
 * CREATE TABLE network_settings (
 * id SERIAL PRIMARY KEY,
 * port INTEGER NOT NULL DEFAULT 8080,
 * max_connections INTEGER NOT NULL DEFAULT 10,
 * connect_timeout BIGINT NOT NULL DEFAULT 5000,
 * sync_interval BIGINT NOT NULL DEFAULT 10000,
 * discovery_interval BIGINT NOT NULL DEFAULT 30000
 * );
 * -- Insert one row with your desired settings:
 * INSERT INTO network_settings (port, max_connections, connect_timeout,
 * sync_interval, discovery_interval)
 * VALUES (8080, 10, 5000, 10000, 30000);
 *
 * To insert a test user row (password hashed by Postgres sha256):
 *
 * INSERT INTO node_users (israeli_id, password_hash, node_type, validator_id,
 * institution, port, data_dir, display_name)
 * VALUES (
 * '123456789',
 * encode(sha256('mypassword'), 'hex'),
 * 'VALIDATOR',
 * 'VALIDATOR_UNIVERSITY_A',
 * 'University A',
 * 8080,
 * './data/node1',
 * 'University A Admin'
 * );
 */
public class AuthService {

    private static final String CONFIG_DIR = "config";
    private static final String DB_PROPERTIES_FILE = "database.properties";
    private static final String QUERY = "SELECT display_name, node_type, validator_id, institution, port, data_dir "
            + "FROM node_users WHERE israeli_id = ? AND password_hash = ?";

    private final String jdbcUrl;

    /**
     * Create an AuthService by loading the JDBC URL from
     * config/database.properties.
     *
     * @throws IOException if the properties file cannot be read
     */
    public AuthService() throws IOException {
        this.jdbcUrl = loadJdbcUrl();
    }

    /**
     * Create an AuthService with an explicit JDBC URL, useful for testing.
     *
     * @param jdbcUrl the full JDBC URL including credentials and SSL parameters
     */
    public AuthService(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    /**
     * Attempt to log in with the given Israeli national ID and plain-text password.
     * The password is hashed with SHA-256 before the database query so it is
     * never transmitted as plain text.
     *
     * @param id       the 9-digit ID
     * @param password the plain-text password entered by the user
     * @return the NodeConfig for this user on success, or null if the credentials
     *         are invalid or no matching row exists
     * @throws SQLException if a database error occurs that is not simply a
     *                      wrong-password case
     */
    public NodeConfig login(String id, String password) throws SQLException {
        String hash = CryptoUtils.applySha256(password);
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = conn.prepareStatement(QUERY)) {
            ps.setString(1, id);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // no matching user
                }
                return new NodeConfig(
                        id,
                        rs.getString("display_name"),
                        rs.getString("node_type"),
                        rs.getString("validator_id"),
                        rs.getString("institution"),
                        rs.getInt("port"),
                        rs.getString("data_dir"));
            }
        }
    }

    /**
     * Load all active validators from the validators table.
     * Each row is decoded into a public-key-only Validator (private key is null).
     * Rows with a missing or unparseable public key are skipped with a warning.
     *
     * @return list of Validator objects ordered by validator_id; empty if no rows
     *         found
     * @throws SQLException if a database error occurs
     */
    public List<Validator> loadValidators() throws SQLException {
        List<Validator> result = new ArrayList<>();
        String sql = "SELECT validator_id, institution, public_key "
                + "FROM validators ORDER BY validator_id";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String validatorId = rs.getString("validator_id");
                String institution = rs.getString("institution");
                String pubKeyBase64 = rs.getString("public_key");
                if (pubKeyBase64 == null || pubKeyBase64.trim().isEmpty()) {
                    Logger.log("Warning: Skipping validator " + validatorId
                            + " (no public key in database)");
                    continue;
                }
                try {
                    PublicKey publicKey = CryptoUtils.publicKeyFromBase64(pubKeyBase64);
                    result.add(new Validator(validatorId, validatorId, publicKey, institution));
                } catch (Exception e) {
                    Logger.log("Warning: Failed to decode public key for validator "
                            + validatorId + ": " + e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * Register a new user in the node_users table.
     * The password must already be a 64-character SHA-256 hex digest (use
     * CryptoUtils.applySha256 before calling this method).
     *
     * @param israeliId    the 9-digit Israeli national ID; must be unique
     * @param passwordHash the SHA-256 hex digest of the password
     * @param nodeType     one of VALIDATOR, UNIVERSITY, or READ_ONLY
     * @param validatorId  the validator ID, or null for non-validator accounts
     * @param institution  the institution name; must not be null or blank
     * @param port         TCP port the node listens on
     * @param dataDir      local data directory path for blockchain storage
     * @param displayName  human-readable display name, or null
     * @throws SQLException if a database error occurs or israeliId is already taken
     */
    public void registerUser(String israeliId, String passwordHash, String nodeType,
            String validatorId, String institution, int port, String dataDir,
            String displayName) throws SQLException {
        String sql = "INSERT INTO node_users "
                + "(israeli_id, password_hash, node_type, validator_id, institution, port, data_dir, display_name) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, israeliId);
            ps.setString(2, passwordHash);
            ps.setString(3, nodeType);
            ps.setString(4, validatorId); // may be null
            ps.setString(5, institution);
            ps.setInt(6, port);
            ps.setString(7, dataDir);
            ps.setString(8, displayName);
            ps.executeUpdate();
        }
    }

    /**
     * Register or update a validator's public key in the validators table.
     * Uses an upsert (INSERT ... ON CONFLICT DO UPDATE) so calling this at startup
     * keeps the DB in sync with any locally regenerated key pair.
     *
     * @param validatorId     the unique validator identifier
     * @param institution     the institution name
     * @param publicKeyBase64 the Base64-encoded RSA public key
     * @throws SQLException if a database error occurs
     */
    public void upsertValidatorKey(String validatorId, String institution,
            String publicKeyBase64) throws SQLException {
        String sql = "INSERT INTO validators (validator_id, institution, public_key) "
                + "VALUES (?, ?, ?) "
                + "ON CONFLICT (validator_id) DO UPDATE "
                + "SET institution = EXCLUDED.institution, public_key = EXCLUDED.public_key, is_active = TRUE";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, validatorId);
            ps.setString(2, institution);
            ps.setString(3, publicKeyBase64);
            ps.executeUpdate();
        }
    }

    /**
     * Load network settings from the network_settings table.
     * Returns built-in defaults if the table has no rows.
     *
     * @return a NetworkSettings object populated from the database
     * @throws SQLException if a database error occurs
     */
    public NetworkSettings loadNetworkSettings() throws SQLException {
        String sql = "SELECT port, max_connections, connect_timeout, sync_interval, "
                + "discovery_interval FROM network_settings LIMIT 1";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new NetworkSettings(
                        rs.getInt("port"),
                        rs.getInt("max_connections"),
                        rs.getLong("connect_timeout"),
                        rs.getLong("sync_interval"),
                        rs.getLong("discovery_interval"));
            }
        }
        return new NetworkSettings(8080, 10, 5000L, 10000L, 30000L);
    }

    /**
     * Immutable record representing a row from the network_settings table.
     */
    public static class NetworkSettings {
        public final int port;
        public final int maxConnections;
        public final long connectTimeout;
        public final long syncInterval;
        public final long discoveryInterval;

        /**
         * Create a NetworkSettings record.
         *
         * @param port              the TCP listen port for P2P connections
         * @param maxConnections    the maximum number of simultaneous peer connections
         * @param connectTimeout    the connection timeout in milliseconds
         * @param syncInterval      the chain sync interval in milliseconds
         * @param discoveryInterval the peer discovery interval in milliseconds
         */
        public NetworkSettings(int port, int maxConnections, long connectTimeout,
                long syncInterval, long discoveryInterval) {
            this.port = port;
            this.maxConnections = maxConnections;
            this.connectTimeout = connectTimeout;
            this.syncInterval = syncInterval;
            this.discoveryInterval = discoveryInterval;
        }
    }

    /**
     * Check whether the JDBC URL has been configured (i.e. the user has replaced
     * the placeholder in database.properties).
     *
     * @return true if the URL does not contain the REPLACE_ME placeholder
     */
    public boolean isConfigured() {
        return !jdbcUrl.contains("REPLACE_ME");
    }

    /**
     * Load the db.url value from config/database.properties.
     *
     * @return the JDBC URL string
     * @throws IOException if the file cannot be found or read
     */
    private static String loadJdbcUrl() throws IOException {
        String path = CONFIG_DIR + "/" + DB_PROPERTIES_FILE;
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(path)) {
            props.load(in);
        }
        String url = props.getProperty("db.url");
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("db.url is not set in " + path);
        }
        return url.trim();
    }
}
