package com.immutable.credentials.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.SQLException;
import java.util.List;

import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.service.AuthService;

/**
 * Configuration loader for blockchain node settings.
 * All configuration (validator list, network settings) is read from the cloud
 * database via AuthService. The only local file access performed by this class
 * is reading or generating the RSA private key for validator nodes, which must
 * remain on disk and is never stored in the database.
 *
 * Three public entry points are provided:
 *   loadValidatorList   - build the shared PoA validator list from the database
 *   loadLocalValidator  - resolve the signing-capable local validator, managing
 *                         the on-disk private key
 *   loadNetworkSettings - fetch P2P network parameters from the database
 */
public class ConfigLoader {

    private static final String KEYS_DIR = "keys";
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    /**
     * Load all active validators from the cloud database.
     * Each validators row is converted to a public-key-only Validator
     * that can verify signatures but cannot produce them.
     * The validator ID is reused as the display name when no separate name is stored.
     *
     * @param authService the connected AuthService used to query the database
     * @return list of public-key-only Validator objects; empty if no rows are found
     * @throws SQLException if a database error occurs
     */
    public static List<Validator> loadValidatorList(AuthService authService)
            throws SQLException {
        List<Validator> validators = authService.loadValidators();
        Logger.log("Loaded " + validators.size() + " validators from database");
        return validators;
    }

    /**
     * Load or generate the full (signing-capable) Validator for the local node.
     * Resolution order:
     *   1. If a private key file exists in the keys/ directory, load it from disk.
     *      The matching public key is taken from the already-loaded validators list.
     *   2. If no private key file exists, generate a new RSA key pair, write the
     *      private key to keys/VALIDATOR_ID.key, and call authService.upsertValidatorKey
     *      to store the new public key in the database.
     * The returned full Validator is upserted into the shared validators list so that
     * the rest of the node sees the correct public key immediately.
     *
     * @param validatorId the ID of the local validator
     * @param institution the institution name for this validator (from NodeConfig)
     * @param validators  the shared validator list to update in-place
     * @param authService the connected AuthService used to persist new public keys
     * @return the full Validator with both keys (never null)
     * @throws IOException  if the key file cannot be read or written
     * @throws SQLException if the database upsert fails
     */
    public static Validator loadLocalValidator(String validatorId, String institution,
            List<Validator> validators, AuthService authService)
            throws IOException, SQLException {

        Validator existing = null;
        for (Validator v : validators) {
            if (v.getValidatorId().equals(validatorId)) {
                existing = v;
                break;
            }
        }

        // existing may be null when the DB row had a PENDING/invalid public key and was skipped.
        // Fall through to key generation; the real public key will be upserted below.
        if (existing == null) {
            Logger.log("Validator " + validatorId
                    + " not yet active (pending key) - will generate key pair.");
        }

        String keyFile = KEYS_DIR + "/" + validatorId + ".key";
        Path keyPath = Paths.get(keyFile);

        PublicKey publicKey;
        PrivateKey privateKey;

        // If the key file exists but this validator's public key was never properly
        // synced to the DB (existing == null means it was skipped as PENDING/invalid),
        // delete the stale key file and regenerate a fresh pair so DB and disk stay in sync.
        if (existing == null && Files.exists(keyPath)) {
            Logger.log("Stale key file found for " + validatorId
                    + " with no matching DB public key - regenerating...");
            Files.delete(keyPath);
        }

        if (Files.exists(keyPath)) {
            String keyBase64 = new String(Files.readAllBytes(keyPath), "UTF-8").trim();
            privateKey = CryptoUtils.privateKeyFromBase64(keyBase64);
            publicKey = existing.getPublicKey();
            // Always re-upload so DB stays in sync with the key on disk
            authService.upsertValidatorKey(validatorId, institution,
                    CryptoUtils.keyToString(publicKey));
            Logger.log("Loaded private key for validator " + validatorId + " from " + keyFile);
        } else {
            Logger.log("No private key found for " + validatorId + " - generating new RSA key pair...");
            Files.createDirectories(keyPath.getParent());
            KeyPair keyPair = CryptoUtils.generateAndSaveKeyPair(keyFile);
            publicKey = keyPair.getPublic();
            privateKey = keyPair.getPrivate();
            authService.upsertValidatorKey(validatorId, institution,
                    CryptoUtils.keyToString(publicKey));
            Logger.log("New key pair generated and public key uploaded to database for " + validatorId);
        }

        Validator full = new Validator(validatorId, validatorId, publicKey, privateKey, institution);

        boolean found = false;
        for (int i = 0; i < validators.size(); i++) {
            if (validators.get(i).getValidatorId().equals(validatorId)) {
                validators.set(i, full);
                found = true;
                break;
            }
        }
        if (!found) {
            validators.add(full);
        }

        return full;
    }

    /**
     * Load P2P network settings from the cloud database.
     * Delegates directly to AuthService.loadNetworkSettings.
     * Returns sensible built-in defaults if the network_settings table has no rows.
     *
     * @param authService the connected AuthService used to query the database
     * @return a NetworkSettings object with port, maxConnections, and timing values
     * @throws SQLException if a database error occurs
     */
    public static AuthService.NetworkSettings loadNetworkSettings(AuthService authService)
            throws SQLException {
        AuthService.NetworkSettings settings = authService.loadNetworkSettings();
        validatePort(settings.port);
        Logger.log("Loaded network settings from database: port=" + settings.port
                + " maxConnections=" + settings.maxConnections);
        return settings;
    }

    // ===== Private helpers =====

    /**
     * Validate that a port number is in the legal range 1024-65535.
     *
     * @param port the port number to check
     * @throws IllegalArgumentException if the port is out of range
     */
    private static void validatePort(int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException(
                    "Port " + port + " is out of the allowed range "
                            + MIN_PORT + "\u2013" + MAX_PORT);
        }
    }
}
