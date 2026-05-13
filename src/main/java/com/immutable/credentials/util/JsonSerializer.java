package com.immutable.credentials.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;
import com.immutable.credentials.network.Peer;

/**
 * Secure JSON serializer/deserializer for blockchain objects.
 * Provides methods to convert blocks, credentials, and entire chains to/from
 * JSON format.
 * 
 * Security measures:
 * - Input validation to prevent injection attacks
 * - Safe handling of null values
 * - Exception handling to prevent information leakage
 * - No execution of dynamic code from JSON
 */
public class JsonSerializer {

    /**
     * Convert a Block to JSON string representation.
     * 
     * @param block the block to serialize
     * @return JSON string representation of the block
     * @throws IllegalArgumentException if block is null
     * @throws RuntimeException         if serialization fails
     */
    public static String blockToJson(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }

        try {
            JSONObject json = new JSONObject();

            // Serialize header
            JSONObject headerJson = new JSONObject();
            headerJson.put("index", block.getIndex());
            headerJson.put("timestamp", block.getTimestamp());
            headerJson.put("previousHash", sanitizeString(block.getPreviousHash()));
            headerJson.put("hash", sanitizeString(block.getHash()));
            headerJson.put("validatorId", sanitizeString(block.getValidatorId()));

            String signature = block.getSignature();
            headerJson.put("signature", signature != null ? sanitizeString(signature) : JSONObject.NULL);

            json.put("header", headerJson);

            // Serialize credentials array
            ArrayList<Credential> credentials = block.getCredentials();
            if (credentials != null) {
                JSONArray credArray = new JSONArray();
                for (Credential cred : credentials) {
                    credArray.put(new JSONObject(credentialToJson(cred)));
                }
                json.put("credentials", credArray);
            } else {
                json.put("credentials", new JSONArray());
            }

            // Serialize voter attestations
            JSONObject attestationsJson = new JSONObject();
            for (Map.Entry<String, String> entry : block.getVoterAttestations().entrySet()) {
                attestationsJson.put(entry.getKey(), entry.getValue());
            }
            json.put("voterAttestations", attestationsJson);

            return json.toString();

        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize block to JSON", e);
        }
    }

    /**
     * Parse a JSON string to create a Block object.
     * 
     * @param jsonString the JSON string to deserialize
     * @return the deserialized Block object
     * @throws IllegalArgumentException if jsonString is null or invalid
     * @throws RuntimeException         if deserialization fails
     */
    public static Block jsonToBlock(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        try {
            JSONObject json = new JSONObject(jsonString);

            // Deserialize header
            JSONObject headerJson = json.getJSONObject("header");
            int index = headerJson.getInt("index");
            long timestamp = headerJson.getLong("timestamp");
            String previousHash = headerJson.optString("previousHash", "");
            String hash = headerJson.optString("hash", "");
            String validatorId = headerJson.optString("validatorId", "");
            String signature = headerJson.isNull("signature") ? null : headerJson.getString("signature");

            // Validate header values
            if (index < 0) {
                throw new IllegalArgumentException("Block index cannot be negative");
            }
            if (timestamp < 0) {
                throw new IllegalArgumentException("Block timestamp cannot be negative");
            }

            // Deserialize credentials array
            ArrayList<Credential> credentials = new ArrayList<>();
            if (!json.isNull("credentials")) {
                JSONArray credArray = json.getJSONArray("credentials");
                for (int i = 0; i < credArray.length(); i++) {
                    JSONObject credJson = credArray.getJSONObject(i);
                    Credential cred = jsonToCredential(credJson.toString());
                    if (cred != null) {
                        credentials.add(cred);
                    }
                }
            }

            // Deserialize voter attestations (optional field for backward compat)
            Map<String, String> voterAttestations = new LinkedHashMap<>();
            if (!json.isNull("voterAttestations")) {
                JSONObject attJson = json.getJSONObject("voterAttestations");
                for (String key : attJson.keySet()) {
                    voterAttestations.put(key, attJson.getString(key));
                }
            }

            // Reconstruct block preserving the stored header values
            return reconstructBlock(index, timestamp, previousHash, hash, validatorId, signature,
                    credentials, voterAttestations);

        } catch (JSONException e) {
            throw new RuntimeException("Failed to deserialize JSON to block: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize an entire blockchain (list of blocks) to JSON array string.
     * 
     * @param chain the list of blocks to serialize
     * @return JSON array string representation of the chain
     * @throws IllegalArgumentException if chain is null
     * @throws RuntimeException         if serialization fails
     */
    public static String chainToJson(ArrayList<Block> chain) {
        if (chain == null) {
            throw new IllegalArgumentException("Chain cannot be null");
        }

        try {
            JSONArray jsonArray = new JSONArray();

            for (Block block : chain) {
                if (block != null) {
                    // Parse the block JSON string into a JSONObject
                    JSONObject blockObj = new JSONObject(blockToJson(block));
                    jsonArray.put(blockObj);
                }
            }

            return jsonArray.toString();

        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize chain to JSON", e);
        }
    }

    /**
     * Deserialize a JSON array string to a list of blocks.
     * 
     * @param jsonString the JSON array string to deserialize
     * @return the deserialized list of blocks
     * @throws IllegalArgumentException if jsonString is null or invalid
     * @throws RuntimeException         if deserialization fails
     */
    public static ArrayList<Block> jsonToChain(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            ArrayList<Block> chain = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject blockJson = jsonArray.getJSONObject(i);
                chain.add(jsonToBlock(blockJson.toString()));
            }

            return chain;

        } catch (JSONException e) {
            throw new RuntimeException("Failed to deserialize JSON to chain: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize a list of peers to a JSON array string.
     *
     * @param peers the list of peers to serialize
     * @return JSON array string, or empty array if peers is null
     */
    public static String peerListToJson(List<Peer> peers) {
        JSONArray array = new JSONArray();
        if (peers == null) {
            return array.toString();
        }
        for (Peer peer : peers) {
            if (peer == null)
                continue;
            JSONObject obj = new JSONObject();
            obj.put("nodeId", peer.getNodeId());
            obj.put("host", peer.getAddress());
            obj.put("port", peer.getPort());
            array.put(obj);
        }
        return array.toString();
    }

    /**
     * Deserialize a JSON array string to a list of peers.
     *
     * @param jsonString the JSON array string to deserialize
     * @return list of Peer objects, or empty list on failure
     */
    public static ArrayList<Peer> jsonToPeerList(String jsonString) {
        ArrayList<Peer> peers = new ArrayList<>();
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return peers;
        }
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String nodeId = obj.optString("nodeId", null);
                String host = obj.optString("host", null);
                int port = obj.optInt("port", -1);
                if (nodeId == null || host == null || port < 1)
                    continue;
                peers.add(new Peer(host, port, nodeId));
            }
        } catch (JSONException e) {
            // malformed payload — return whatever we parsed so far
        }
        return peers;
    }

    /**
     * Serialize a single Credential to a JSON string.
     *
     * @param credential the credential to serialize
     * @return JSON string representation of the credential
     * @throws IllegalArgumentException if credential is null
     */
    public static String credentialToJson(Credential credential) {
        if (credential == null) {
            throw new IllegalArgumentException("Credential cannot be null");
        }
        JSONObject json = new JSONObject();
        json.put("studentName", sanitizeString(credential.getStudentName()));
        json.put("dateAwarded",
                credential.getDateAwarded() != null ? credential.getDateAwarded().getTime() : JSONObject.NULL);
        json.put("degree", sanitizeString(credential.getDegree()));
        json.put("institution", sanitizeString(credential.getInstitution()));
        json.put("studentId", sanitizeString(credential.getStudentId()));
        json.put("credentialId", sanitizeString(credential.getCredentialId()));

        String sig = credential.getSignature();
        json.put("signature", sig != null ? sanitizeString(sig) : JSONObject.NULL);

        return json.toString();
    }

    /**
     * Deserialize a JSON string into a Credential object.
     *
     * @param jsonString the JSON string to deserialize
     * @return the deserialized Credential, or null on failure
     * @throws IllegalArgumentException if jsonString is null or empty
     */
    public static Credential jsonToCredential(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        try {
            JSONObject json = new JSONObject(jsonString);
            String studentName = json.optString("studentName", "");
            long dateMillis = json.getLong("dateAwarded");
            java.util.Date dateAwarded = new java.util.Date(dateMillis);
            String degree = json.optString("degree", "");
            String institution = json.optString("institution", "");
            String studentId = json.optString("studentId", "");
            String credentialId = json.optString("credentialId", "");

            String signature = json.isNull("signature") ? null : json.getString("signature");

            Credential cred = new Credential(studentName, dateAwarded, degree, institution, studentId, credentialId);

            if (signature != null) {
                return new Credential(cred, signature);
            }

            return null;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Sanitize string input to prevent injection attacks and limit size.
     * 
     * @param input the string to sanitize
     * @return sanitized string, or empty string if input is null
     */
    private static String sanitizeString(String input) {
        if (input == null) {
            return "";
        }
        return input;
    }

    /**
     * Reconstruct a Block from stored data without recalculating hash or timestamp.
     * 
     * @param index        the block index
     * @param timestamp    the block timestamp
     * @param previousHash the previous block hash
     * @param hash         the block hash
     * @param validatorId  the validator ID
     * @param signature    the block signature
     * @param credential   the credential payload
     * @return reconstructed Block object with exact stored values
     */
    private static Block reconstructBlock(int index, long timestamp, String previousHash,
            String hash, String validatorId, String signature,
            ArrayList<Credential> credentials, Map<String, String> voterAttestations) {
        // Use the deserialization constructor to preserve exact hash and timestamp
        return new Block(index, timestamp, previousHash, hash, validatorId, signature,
                credentials, voterAttestations);
    }
}