package com.immutable.credentials.auth;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Handles authentication against the cloud SQL database (Supabase PostgreSQL).
 * On a successful login it returns a NodeConfig populated with the row data
 * so that MainWindow can initialise the node without reading node.properties.
 *
 * Password storage: passwords are stored in the database as SHA-256 hex
 * digests. This class hashes the raw password before querying so the
 * plain-text password is never sent to or stored in the database.
 *
 * SQL schema required (run once in your Supabase SQL editor):
 *
 *   CREATE TABLE node_users (
 *       id           SERIAL PRIMARY KEY,
 *       israeli_id   VARCHAR(10)  UNIQUE NOT NULL,
 *       password_hash VARCHAR(64) NOT NULL,
 *       node_type    VARCHAR(20)  NOT NULL CHECK (node_type IN ('VALIDATOR','UNIVERSITY','READ_ONLY')),
 *       validator_id VARCHAR(100),
 *       institution  VARCHAR(200) NOT NULL,
 *       port         INTEGER      NOT NULL DEFAULT 8080,
 *       data_dir     VARCHAR(500) NOT NULL DEFAULT './data/default',
 *       display_name VARCHAR(200),
 *       created_at   TIMESTAMP    DEFAULT NOW()
 *   );
 *
 * To insert a test row (replace values as needed):
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
        String hash = sha256(password);
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

    /**
     * Compute the SHA-256 hex digest of the given plain-text string.
     *
     * @param input the plain-text value to hash (typically a password)
     * @return a 64-character lowercase hex string representing the digest
     * @throws IllegalStateException if SHA-256 is not available on this JVM
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every Java SE implementation
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
