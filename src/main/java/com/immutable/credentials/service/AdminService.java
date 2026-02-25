package com.immutable.credentials.service;

import com.immutable.credentials.auth.NodeConfig;
import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.crypto.CryptoUtils;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;

/**
 * High-level admin operations for institutions (VALIDATOR and UNIVERSITY
 * nodes).
 * Provides password generation, user registration, and institution
 * registration.
 * All writes go through AuthService so the same JDBC connection is reused.
 *
 * Access rules enforced by the UI:
 * - All institution nodes (VALIDATOR and UNIVERSITY) can register staff and
 * students.
 * - Only VALIDATOR nodes can register a new institution (UNIVERSITY account).
 * - READ_ONLY nodes have no access to this service.
 */
public class AdminService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final NodeConfig adminConfig;

    /**
     * Create an AdminService bound to the given AuthService and the config of the
     * currently logged-in admin user.
     *
     * @param authService the AuthService used for all database operations
     * @param adminConfig the NodeConfig of the logged-in institution admin
     */
    public AdminService(AuthService authService, NodeConfig adminConfig) {
        this.authService = authService;
        this.adminConfig = adminConfig;
    }

    /**
     * Generate a cryptographically random plain-text password of PASSWORD_LENGTH
     * characters drawn from upper/lower-case letters and digits.
     *
     * @return a new random plain-text password
     */
    public String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Register a new staff member at this institution.
     * Staff accounts have node type UNIVERSITY, meaning they can issue credentials
     * but cannot propose or sign blocks.
     * The plain-text password is hashed with CryptoUtils.applySha256 before being
     * stored.
     *
     * @param israeliId   the Israeli national ID of the new staff member
     * @param plainPw     the plain-text password (will be hashed before storage)
     * @param displayName the person's display name
     * @param port        the TCP port for this node (typically the institution's
     *                    shared port)
     * @param dataDir     the local data directory path for blockchain storage
     * @throws SQLException if a database error occurs or israeliId is already taken
     */
    public void registerStaff(String israeliId, String plainPw,
            String displayName, int port, String dataDir) throws SQLException {
        if (!CryptoUtils.isValidIsraeliId(israeliId)) {
            throw new IllegalArgumentException("Invalid Israeli ID: " + israeliId);
        }
        String hash = CryptoUtils.applySha256(plainPw);
        authService.registerUser(israeliId, hash, "UNIVERSITY", null,
                adminConfig.getInstitution(), port, dataDir, displayName);
    }

    /**
     * Register a student (read-only) account at this institution.
     * Students can verify credentials but cannot issue them or access the admin
     * panel.
     * The plain-text password is hashed with CryptoUtils.applySha256 before being
     * stored.
     *
     * @param israeliId   the Israeli national ID of the student
     * @param plainPw     the plain-text password (will be hashed before storage)
     * @param displayName the student's display name
     * @param port        the TCP port for this node
     * @param dataDir     the local data directory path for blockchain storage
     * @throws SQLException if a database error occurs or israeliId is already taken
     */
    public void registerStudent(String israeliId, String plainPw,
            String displayName, int port, String dataDir) throws SQLException {
        if (!CryptoUtils.isValidIsraeliId(israeliId)) {
            throw new IllegalArgumentException("Invalid Israeli ID: " + israeliId);
        }
        String hash = CryptoUtils.applySha256(plainPw);
        authService.registerUser(israeliId, hash, "READ_ONLY", null,
                adminConfig.getInstitution(), port, dataDir, displayName);
    }

    /**
     * Register a new institution as a UNIVERSITY (non-validator) node.
     * Only VALIDATOR admins may call this method; the UI enforces this by checking
     * NodeConfig.getNodeType().equals("VALIDATOR") before showing the option.
     * The plain-text password is hashed with CryptoUtils.applySha256 before being
     * stored.
     *
     * @param israeliId       the Israeli national ID assigned to the institution
     *                        admin
     * @param plainPw         the plain-text password (will be hashed before
     *                        storage)
     * @param institutionName the name of the new institution
     * @param displayName     the display name for this admin account
     * @param port            the TCP port the institution node will listen on
     * @param dataDir         the local data directory path for blockchain storage
     * @throws SQLException          if a database error occurs or israeliId is
     *                               already taken
     * @throws IllegalStateException if the calling node is not a VALIDATOR
     */
    public void registerInstitution(String israeliId, String plainPw,
            String institutionName, String displayName,
            int port, String dataDir) throws SQLException {
        if (!CryptoUtils.isValidIsraeliId(israeliId)) {
            throw new IllegalArgumentException("Invalid Israeli ID: " + israeliId);
        }
        if (!"VALIDATOR".equals(adminConfig.getNodeType())) {
            throw new IllegalStateException("Only VALIDATOR nodes may register new institutions.");
        }
        String hash = CryptoUtils.applySha256(plainPw);
        authService.registerUser(israeliId, hash, "UNIVERSITY", null,
                institutionName, port, dataDir, displayName);
    }

    /**
     * Load all active validators from the database.
     * Used to refresh the PoA validator list without restarting the node.
     * Each Validator in the returned list has a null private key.
     *
     * @return list of public-key-only Validator objects, one per active validator
     * @throws SQLException if a database error occurs
     */
    public List<Validator> loadValidators() throws SQLException {
        return authService.loadValidators();
    }

    /**
     * Push the local validator's public key to the validators table so
     * other nodes can load it dynamically. Should be called once after node startup
     * for VALIDATOR nodes.
     *
     * @param validatorId     the unique validator identifier
     * @param institution     the institution name
     * @param publicKeyBase64 the Base64-encoded RSA public key
     * @throws SQLException if a database error occurs
     */
    public void syncValidatorKey(String validatorId, String institution,
            String publicKeyBase64) throws SQLException {
        authService.upsertValidatorKey(validatorId, institution, publicKeyBase64);
    }

    /**
     * Return the NodeConfig of the logged-in administrator.
     *
     * @return the admin NodeConfig
     */
    public NodeConfig getAdminConfig() {
        return adminConfig;
    }
}
