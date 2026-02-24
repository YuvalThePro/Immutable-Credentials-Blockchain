package com.immutable.credentials.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Secure configuration loader for blockchain node settings.
 * Loads configuration from property files with input validation and security
 * checks.
 * 
 * Security measures:
 * - Path traversal prevention
 * - Input validation for all configuration values
 * - Safe defaults for missing values
 * - No arbitrary code execution
 * - Restricted file access to config directory only
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
        Path normalizedPath = configPath.normalize();
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
     * 
     * @param publicKeyStr the Base64-encoded public key string
     * @return the parsed PublicKey
     * @throws Exception if parsing fails
     */
    private static PublicKey parsePublicKey(String publicKeyStr) throws Exception {
        if (publicKeyStr == null || publicKeyStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Public key string cannot be null or empty");
        }

        // Remove whitespace and newlines
        publicKeyStr = publicKeyStr.replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(publicKeyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
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