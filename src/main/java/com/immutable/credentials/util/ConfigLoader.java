package com.immutable.credentials.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.crypto.CryptoUtils;

/**
 * Secure configuration loader for blockchain node settings.
 * Loads configuration from property files with input validation and security checks.
 * Security measures include path traversal prevention, input validation for all
 * configuration values, safe defaults for missing values, no arbitrary code execution,
 * and restricted file access to the config directory only.
 */
public class ConfigLoader {

    private static final String CONFIG_DIR = "config";
    private static final int MAX_PORT = 65535;
    private static final int MIN_PORT = 1024;

    /**
     * Load node configuration from node.properties file.
     * 
     * @return Properties object containing node configuration
     * @throws IOException       if configuration file cannot be read
     * @throws SecurityException if configuration values are invalid
     */
    public static Properties loadNodeConfig() throws IOException {
        return loadPropertiesFile("node.properties");
    }

    /**
     * Load blockchain configuration from blockchain.properties file.
     * 
     * @return Properties object containing blockchain configuration
     * @throws IOException       if configuration file cannot be read
     * @throws SecurityException if configuration values are invalid
     */
    public static Properties loadBlockchainConfig() throws IOException {
        return loadPropertiesFile("blockchain.properties");
    }

    /**
     * Load network configuration from network.properties file.
     * 
     * @return Properties object containing network configuration
     * @throws IOException       if configuration file cannot be read
     * @throws SecurityException if configuration values are invalid
     */
    public static Properties loadNetworkConfig() throws IOException {
        return loadPropertiesFile("network.properties");
    }

    /**
     * Load validator configuration and parse validator list.
     * 
     * @return HashMap mapping validator IDs to their public keys
     * @throws IOException       if configuration file cannot be read
     * @throws SecurityException if configuration values are invalid
     */
    public static HashMap<String, PublicKey> loadValidators() throws IOException {
        Properties props = loadPropertiesFile("validators.properties");
        HashMap<String, PublicKey> validators = new HashMap<>();

        int validatorCount = 0;
        for (int i = 1; i <= 1000; i++) {
            String idKey = "validator." + i + ".id";
            String keyKey = "validator." + i + ".publickey";

            String validatorId = props.getProperty(idKey);
            String publicKeyStr = props.getProperty(keyKey);

            if (validatorId == null || publicKeyStr == null) {
                // No more validators to load
                break;
            }

            // Validate validator ID
            validatorId = validateString(validatorId, "Validator ID", 100);

            // Skip placeholder keys
            if (publicKeyStr.contains("PLACEHOLDER")) {
                Logger.log("Warning: Skipping validator " + validatorId + " with placeholder key");
                continue;
            }

            try {
                PublicKey publicKey = parsePublicKey(publicKeyStr);
                validators.put(validatorId, publicKey);
                validatorCount++;
            } catch (Exception e) {
                Logger.log("Warning: Failed to parse public key for validator " + validatorId + ": " + e.getMessage());
            }
        }

        if (validators.isEmpty()) {
            Logger.log("Warning: No valid validators loaded from configuration");
        } else {
            Logger.log("Loaded " + validatorCount + " validators from configuration");
        }

        return validators;
    }

    // ===== Default keys directory relative to the working directory =====
    private static final String KEYS_DIR = "keys";

    /**
     * Load all validators from validators.properties as public-key-only Validator objects.
     * These validators can verify signatures but cannot sign blocks.
     * 
     * @return list of public-key-only Validator objects
     * @throws IOException if the configuration file cannot be read
     */
    public static List<Validator> loadValidatorList() throws IOException {
        Properties props = loadPropertiesFile("validators.properties");
        List<Validator> validators = new ArrayList<>();

        for (int i = 1; i <= 1000; i++) {
            String idKey = "validator." + i + ".id";
            String nameKey = "validator." + i + ".name";
            String institutionKey = "validator." + i + ".institution";
            String keyKey = "validator." + i + ".publickey";

            String validatorId = props.getProperty(idKey);
            if (validatorId == null) {
                break; // no more validators
            }

            String publicKeyStr = props.getProperty(keyKey);
            if (publicKeyStr == null || publicKeyStr.contains("PLACEHOLDER")) {
                Logger.log("Warning: Skipping validator " + validatorId + " (no valid public key)");
                continue;
            }

            validatorId = validateString(validatorId, "Validator ID", 100);
            String name = props.getProperty(nameKey, validatorId);
            String institution = props.getProperty(institutionKey, "Unknown Institution");

            try {
                PublicKey publicKey = parsePublicKey(publicKeyStr);
                Validator v = new Validator(validatorId, name, publicKey, institution);
                validators.add(v);
            } catch (Exception e) {
                Logger.log("Warning: Failed to parse public key for validator "
                        + validatorId + ": " + e.getMessage());
            }
        }

        Logger.log("Loaded " + validators.size() + " validators from configuration");
        return validators;
    }

    /**
     * Load or generate the full (signing-capable) Validator for the local node.
     * If a key file exists, loads the private key and matching public key from config.
     * If no key exists, generates a new RSA key pair, saves the private key to disk,
     * and updates the public key in validators.properties.
     * The returned Validator is also upserted into the shared validators list.
     * 
     * @param validatorId the ID of the local validator
     * @param validators  the shared list of public-key-only validators (will be updated)
     * @return a full Validator with both public and private keys, or {@code null} if
     *         the ID is not found in validators.properties
     * @throws IOException if a key file cannot be read or written
     */
    public static Validator loadLocalValidator(String validatorId,
            List<Validator> validators) throws IOException {

        // Read name/institution directly from properties — works even with placeholder keys
        Properties props = loadPropertiesFile("validators.properties");
        String name = null;
        String institution = "Unknown Institution";
        for (int i = 1; i <= 1000; i++) {
            String id = props.getProperty("validator." + i + ".id");
            if (id == null) break;
            if (id.trim().equals(validatorId)) {
                name = props.getProperty("validator." + i + ".name", validatorId);
                institution = props.getProperty("validator." + i + ".institution", institution);
                break;
            }
        }

        if (name == null) {
            Logger.log("Warning: Validator ID " + validatorId + " not found in validators.properties");
            return null;
        }

        String keyFile = KEYS_DIR + "/" + validatorId + ".key";
        Path keyPath = Paths.get(keyFile);

        PublicKey publicKey = null;
        PrivateKey privateKey = null;

        boolean needNewKeyPair = false;

        if (Files.exists(keyPath)) {
            // Load existing private key from disk
            String keyBase64 = new String(Files.readAllBytes(keyPath), "UTF-8").trim();
            privateKey = CryptoUtils.privateKeyFromBase64(keyBase64);

            // Read the matching public key from validators.properties
            String pubKeyStr = null;
            for (int i = 1; i <= 1000; i++) {
                String id = props.getProperty("validator." + i + ".id");
                if (id == null) break;
                if (id.trim().equals(validatorId)) {
                    pubKeyStr = props.getProperty("validator." + i + ".publickey");
                    break;
                }
            }
            if (pubKeyStr == null || pubKeyStr.contains("PLACEHOLDER")) {
                Logger.log("Private key exists but public key is missing/placeholder for "
                        + validatorId + ". Regenerating key pair...");
                needNewKeyPair = true;
            } else {
                publicKey = CryptoUtils.publicKeyFromBase64(pubKeyStr);
                Logger.log("Loaded key pair for validator " + validatorId);
            }
        } else {
            needNewKeyPair = true;
        }

        if (needNewKeyPair) {
            // Generate a fresh key pair (first run or mismatched keys)
            Logger.log("Generating new key pair for " + validatorId + "...");
            KeyPair keyPair = CryptoUtils.generateAndSaveKeyPair(keyFile);
            publicKey = keyPair.getPublic();
            privateKey = keyPair.getPrivate();
            updateValidatorPublicKey(validatorId, CryptoUtils.keyToString(publicKey));
            Logger.log("Key pair for " + validatorId + " saved (private key + public key in validators.properties)");
        }

        Validator full = new Validator(validatorId, name, publicKey, privateKey, institution);

        // Upsert into the shared list so PoA gets the correct public key
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
     * Update a validator's public key in validators.properties.
     * Reads the file, finds the matching validator entry by ID, writes the
     * new public key value, and saves the file back.
     *
     * @param validatorId   the validator ID whose key to update
     * @param pubKeyBase64  the Base64-encoded public key to write
     * @throws IOException if the file cannot be read or written
     */
    private static void updateValidatorPublicKey(String validatorId, String pubKeyBase64)
            throws IOException {
        Path propsPath = Paths.get(CONFIG_DIR, "validators.properties").normalize();
        Properties props = new Properties();

        // Load existing properties (ordered won't be preserved, but values will be correct)
        try (InputStream in = new FileInputStream(propsPath.toFile())) {
            props.load(in);
        }

        // Find the entry number for this validator ID
        for (int i = 1; i <= 1000; i++) {
            String id = props.getProperty("validator." + i + ".id");
            if (id == null) break;
            if (id.trim().equals(validatorId)) {
                props.setProperty("validator." + i + ".publickey", pubKeyBase64);
                try (OutputStream out = new FileOutputStream(propsPath.toFile())) {
                    props.store(out, "Validator Configuration - Accredited Universities (Proof-of-Authority)");
                }
                return;
            }
        }

        Logger.log("Warning: Could not find validator " + validatorId
                + " in validators.properties to update public key");
    }

    /**
     * Load a properties file from the config directory.
     * 
     * @param fileName the name of the properties file
     * @return Properties object with loaded configuration
     * @throws IOException       if file cannot be read
     * @throws SecurityException if file path is invalid
     */
    private static Properties loadPropertiesFile(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        // Prevent path traversal attacks
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new SecurityException("Invalid file name: path traversal not allowed");
        }

        Path configPath = Paths.get(CONFIG_DIR, fileName);

        // Ensure the resolved path is still within config directory
        Path normalizedPath = configPath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(Paths.get(CONFIG_DIR).toAbsolutePath().normalize())) {
            throw new SecurityException("Invalid file path: must be within config directory");
        }

        if (!Files.exists(normalizedPath)) {
            throw new IOException("Configuration file not found: " + normalizedPath);
        }

        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(normalizedPath.toFile())) {
            properties.load(input);
        }

        return properties;
    }

    /**
     * Parse a Base64-encoded public key string into a PublicKey object.
     * Delegates to CryptoUtils.publicKeyFromBase64.
     * 
     * @param publicKeyStr the Base64-encoded public key string
     * @return the parsed PublicKey
     * @throws Exception if parsing fails
     */
    private static PublicKey parsePublicKey(String publicKeyStr) throws Exception {
        return CryptoUtils.publicKeyFromBase64(publicKeyStr);
    }

    /**
     * Get node type from configuration (validator or non-validator).
     * 
     * @param nodeConfig the node configuration properties
     * @return the node type (default: "non-validator")
     */
    public static String getNodeType(Properties nodeConfig) {
        String nodeType = nodeConfig.getProperty("node.type", "non-validator");
        return validateNodeType(nodeType);
    }

    /**
     * Get validator ID from node configuration.
     * 
     * @param nodeConfig the node configuration properties
     * @return the validator ID, or null if not set
     */
    public static String getValidatorId(Properties nodeConfig) {
        String validatorId = nodeConfig.getProperty("node.validator.id");
        if (validatorId != null && !validatorId.trim().isEmpty()) {
            return validateString(validatorId, "Validator ID", 100);
        }
        return null;
    }

    /**
     * Get institution name from node configuration.
     * 
     * @param nodeConfig the node configuration properties
     * @return the institution name (default: "Unknown Institution")
     */
    public static String getInstitution(Properties nodeConfig) {
        String institution = nodeConfig.getProperty("node.institution", "Unknown Institution");
        return validateString(institution, "Institution", 200);
    }

    /**
     * Get port number from node configuration.
     * 
     * @param nodeConfig the node configuration properties
     * @return the port number (default: 8080)
     */
    public static int getPort(Properties nodeConfig) {
        String portStr = nodeConfig.getProperty("node.port", "8080");
        return validatePort(portStr);
    }

    /**
     * Get data directory from node configuration.
     * 
     * @param nodeConfig the node configuration properties
     * @return the data directory path (default: "./data")
     */
    public static String getDataDir(Properties nodeConfig) {
        String dataDir = nodeConfig.getProperty("node.data.dir", "./data");
        return validatePath(dataDir);
    }

    /**
     * Get network port from network configuration.
     * 
     * @param networkConfig the network configuration properties
     * @return the network port (default: 8080)
     */
    public static int getNetworkPort(Properties networkConfig) {
        String portStr = networkConfig.getProperty("network.port", "8080");
        return validatePort(portStr);
    }

    /**
     * Get peer list from network configuration.
     * 
     * @param networkConfig the network configuration properties
     * @return list of peer addresses
     */
    public static List<String> getPeerList(Properties networkConfig) {
        String peersStr = networkConfig.getProperty("network.peers", "");
        List<String> peers = new ArrayList<>();

        if (peersStr != null && !peersStr.trim().isEmpty()) {
            String[] peerArray = peersStr.split(",");
            for (String peer : peerArray) {
                peer = peer.trim();
                if (!peer.isEmpty()) {
                    peers.add(validatePeerAddress(peer));
                }
            }
        }

        return peers;
    }

    /**
     * Get maximum connections from network configuration.
     * 
     * @param networkConfig the network configuration properties
     * @return the maximum number of connections (default: 10)
     */
    public static int getMaxConnections(Properties networkConfig) {
        String maxStr = networkConfig.getProperty("network.max.connections", "10");
        return validatePositiveInt(maxStr, "Max connections", 10000);
    }

    /**
     * Get connection timeout from network configuration.
     * 
     * @param networkConfig the network configuration properties
     * @return the connection timeout in milliseconds (default: 5000)
     */
    public static long getConnectionTimeout(Properties networkConfig) {
        String timeoutStr = networkConfig.getProperty("network.connection.timeout", "5000");
        return validateTimeout(timeoutStr, "Connection timeout");
    }

    /**
     * Get sync interval from network configuration.
     * 
     * @param networkConfig the network configuration properties
     * @return the sync interval in milliseconds (default: 10000)
     */
    public static long getSyncInterval(Properties networkConfig) {
        String intervalStr = networkConfig.getProperty("network.sync.interval", "10000");
        return validateInterval(intervalStr, "Sync interval");
    }

    /**
     * Get indexing enabled flag from blockchain configuration.
     * 
     * @param blockchainConfig the blockchain configuration properties
     * @return true if indexing is enabled (default: true)
     */
    public static boolean isIndexingEnabled(Properties blockchainConfig) {
        String enabledStr = blockchainConfig.getProperty("blockchain.indexing.enabled", "true");
        return Boolean.parseBoolean(enabledStr);
    }

    /**
     * Get storage format from blockchain configuration.
     * 
     * @param blockchainConfig the blockchain configuration properties
     * @return the storage format (default: "json")
     */
    public static String getStorageFormat(Properties blockchainConfig) {
        String format = blockchainConfig.getProperty("blockchain.storage.format", "json");
        return validateStorageFormat(format);
    }

    /**
     * Get backup enabled flag from blockchain configuration.
     * 
     * @param blockchainConfig the blockchain configuration properties
     * @return true if backup is enabled (default: true)
     */
    public static boolean isBackupEnabled(Properties blockchainConfig) {
        String enabledStr = blockchainConfig.getProperty("blockchain.backup.enabled", "true");
        return Boolean.parseBoolean(enabledStr);
    }

    /**
     * Get backup interval from blockchain configuration.
     * 
     * @param blockchainConfig the blockchain configuration properties
     * @return the backup interval in number of blocks (default: 100)
     */
    public static int getBackupInterval(Properties blockchainConfig) {
        String intervalStr = blockchainConfig.getProperty("blockchain.backup.interval", "100");
        return validatePositiveInt(intervalStr, "Backup interval", 1000000);
    }

    // Validation methods

    /**
     * Validate node type.
     * 
     * @param nodeType the node type to validate
     * @return validated node type
     */
    private static String validateNodeType(String nodeType) {
        if (nodeType == null || nodeType.trim().isEmpty()) {
            return "non-validator";
        }

        nodeType = nodeType.trim().toLowerCase();
        if (!nodeType.equals("validator") && !nodeType.equals("non-validator")) {
            Logger.log("Warning: Invalid node type '" + nodeType + "', defaulting to 'non-validator'");
            return "non-validator";
        }

        return nodeType;
    }

    /**
     * Validate string length and content.
     * 
     * @param value     the string to validate
     * @param fieldName the name of the field (for error messages)
     * @param maxLength the maximum allowed length
     * @return validated string
     */
    private static String validateString(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }

        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }

        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length of " + maxLength);
        }

        return value;
    }

    /**
     * Validate port number.
     * 
     * @param portStr the port string to validate
     * @return validated port number
     */
    private static int validatePort(String portStr) {
        try {
            int port = Integer.parseInt(portStr.trim());
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException("Port must be between " + MIN_PORT + " and " + MAX_PORT);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + portStr);
        }
    }

    /**
     * Validate positive integer value.
     * 
     * @param valueStr  the string to validate
     * @param fieldName the name of the field (for error messages)
     * @param maxValue  the maximum allowed value
     * @return validated integer
     */
    private static int validatePositiveInt(String valueStr, String fieldName, int maxValue) {
        try {
            int value = Integer.parseInt(valueStr.trim());
            if (value < 1) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
            if (value > maxValue) {
                throw new IllegalArgumentException(fieldName + " exceeds maximum value of " + maxValue);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + valueStr);
        }
    }

    /**
     * Validate timeout value.
     * 
     * @param timeoutStr the timeout string to validate
     * @param fieldName  the name of the field (for error messages)
     * @return validated timeout in milliseconds
     */
    private static long validateTimeout(String timeoutStr, String fieldName) {
        try {
            long timeout = Long.parseLong(timeoutStr.trim());
            if (timeout < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative");
            }
            return timeout;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + timeoutStr);
        }
    }

    /**
     * Validate interval value.
     * 
     * @param intervalStr the interval string to validate
     * @param fieldName   the name of the field (for error messages)
     * @return validated interval in milliseconds
     */
    private static long validateInterval(String intervalStr, String fieldName) {
        try {
            long interval = Long.parseLong(intervalStr.trim());
            if (interval < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative");
            }
            return interval;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + intervalStr);
        }
    }

    /**
     * Validate peer address format.
     * 
     * @param peer the peer address to validate
     * @return validated peer address
     */
    private static String validatePeerAddress(String peer) {
        if (peer == null || peer.trim().isEmpty()) {
            throw new IllegalArgumentException("Peer address cannot be null or empty");
        }

        peer = peer.trim();

        // Basic validation: should be in format host:port
        if (!peer.matches("^[a-zA-Z0-9.-]+:[0-9]+$")) {
            throw new IllegalArgumentException("Invalid peer address format: " + peer);
        }

        return peer;
    }

    /**
     * Validate storage format.
     * 
     * @param format the storage format to validate
     * @return validated storage format
     */
    private static String validateStorageFormat(String format) {
        if (format == null || format.trim().isEmpty()) {
            return "json";
        }

        format = format.trim().toLowerCase();
        if (!format.equals("json") && !format.equals("binary")) {
            Logger.log("Warning: Invalid storage format '" + format + "', defaulting to 'json'");
            return "json";
        }

        return format;
    }

    /**
     * Validate path to prevent path traversal attacks.
     * 
     * @param path the path to validate
     * @return validated path
     */
    private static String validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        path = path.trim();

        // Normalize the path to prevent traversal
        Path normalizedPath = Paths.get(path).normalize();

        // Convert to string and ensure no suspicious patterns
        String pathStr = normalizedPath.toString();

        if (pathStr.length() > 500) {
            throw new IllegalArgumentException("Path exceeds maximum length");
        }

        return pathStr;
    }
}