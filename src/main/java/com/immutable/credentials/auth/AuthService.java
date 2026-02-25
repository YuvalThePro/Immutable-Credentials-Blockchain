package com.immutable.credentials.auth;

import com.immutable.credentials.crypto.CryptoUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * digests computed by CryptoUtils.sha256. The plain-text password is never
 * sent to or stored in the database.
 *
 * SQL schema required (run once in your Supabase SQL editor):
 *
 *   CREATE TABLE node_users (
 *       id            SERIAL PRIMARY KEY,
 *       israeli_id    VARCHAR(10)   UNIQUE NOT NULL,
 *       password_hash VARCHAR(64)   NOT NULL,
 *       node_type     VARCHAR(20)   NOT NULL CHECK (node_type IN ('VALIDATOR','UNIVERSITY','READ_ONLY')),
 *       validator_id  VARCHAR(100),
 *       institution   VARCHAR(200)  NOT NULL,
 *       port          INTEGER       NOT NULL DEFAULT 8080,
 *       data_dir      VARCHAR(500)  NOT NULL DEFAULT './data/default',
 *       display_name  VARCHAR(200),
 *       created_at    TIMESTAMP     DEFAULT NOW()
 *   );
 *
 *   CREATE TABLE validator_registrations (
 *       id            SERIAL PRIMARY KEY,
 *       validator_id  VARCHAR(100)  UNIQUE NOT NULL,
 *       institution   VARCHAR(200)  NOT NULL,
 *       public_key    TEXT          NOT NULL,
 *       is_active     BOOLEAN       DEFAULT TRUE,
 *       created_at    TIMESTAMP     DEFAULT NOW()
 *   );
 *
 * To insert a test user row (password hashed by Postgres sha256):
 *
 *   INSERT INTO node_users (israeli_id, password_hash, node_type, validator_id,
 *       institution, port, data_dir, display_name)
 *   VALUES (
 *       '123456789',
 *       encode(sha256('mypassword'::bytea), 'hex'),
 *       'VALIDATOR',
 *       'VALIDATOR_UNIVERSITY_A',
 *       'University A',
 *       8080,
 *       './data/node1',
 *       'University A Admin'
 *   );
 */
public class AuthService {

    private static final String CONFIG_DIR = "config";
    private static final String DB_PROPERTIES_FILE = "database.properties";
    private static final String QUERY =
            "SELECT display_name, node_type, validator_id, institution, port, data_dir "
            + "FROM node_users WHERE israeli_id = ? AND password_hash = ?";

    private final String jdbcUrl;

    /**
     * Create an AuthService by loading the JDBC URL from config/database.properties.
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
     * @param israeliId the 9-digit Israeli national ID
     * @param password  the plain-text password entered by the user
     * @return the NodeConfig for this user on success, or null if the credentials
     *         are invalid or no matching row exists
     * @throws SQLException if a database error occurs that is not simply a
     *                      wrong-password case
     */
    public NodeConfig login(String israeliId, String password) throws SQLException {
        String hash = CryptoUtils.sha256(password);
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(QUERY)) {
            ps.setString(1, israeliId);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // no matching user
                }
                return new NodeConfig(
                        israeliId,
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
     * Load all active validator registrations from the validator_registrations table.
     * Returns a list of ValidatorRecord objects containing the validator ID, institution
     * name, and Base64-encoded public key. Returns an empty list if the table has no rows.
     *
     * @return a list of active validator records ordered by validator_id
     * @throws SQLException if a database error occurs
     */
    public List<ValidatorRecord> loadValidators() throws SQLException {
        List<ValidatorRecord> result = new ArrayList<>();
        String sql = "SELECT validator_id, institution, public_key "
                + "FROM validator_registrations WHERE is_active = TRUE ORDER BY validator_id";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ValidatorRecord(
                        rs.getString("validator_id"),
                        rs.getString("institution"),
                        rs.getString("public_key")));
            }
        }
        return result;
    }

    /**
     * Register a new user in the node_users table.
     * The password must already be a 64-character SHA-256 hex digest (use
     * CryptoUtils.sha256 before calling this method).
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
     * Register or update a validator's public key in the validator_registrations table.
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
        String sql = "INSERT INTO validator_registrations (validator_id, institution, public_key) "
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
     * Immutable record representing a row in the validator_registrations table.
     */
    public static class ValidatorRecord {
        public final String validatorId;
        public final String institution;
        public final String publicKeyBase64;

        /**
         * Create a ValidatorRecord.
         *
         * @param validatorId     the unique validator identifier
         * @param institution     the institution name
         * @param publicKeyBase64 the Base64-encoded public key
         */
        public ValidatorRecord(String validatorId, String institution, String publicKeyBase64) {
            this.validatorId = validatorId;
            this.institution = institution;
            this.publicKeyBase64 = publicKeyBase64;
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
        if (url == null || url.isBlank()) {
            throw new IOException("db.url is not set in " + path);
        }
        return url.trim();
    }
}
