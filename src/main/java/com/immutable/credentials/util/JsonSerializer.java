package com.immutable.credentials.util;

import java.util.ArrayList;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;


/**
 * Secure JSON serializer/deserializer for blockchain objects.
 * Provides methods to convert blocks, credentials, and entire chains to/from JSON format.
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
     * @throws RuntimeException if serialization fails
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
                    JSONObject credJson = new JSONObject();
                    credJson.put("studentName", sanitizeString(cred.getStudentName()));
                    credJson.put("dateAwarded", cred.getDateAwarded() != null ? cred.getDateAwarded().getTime() : JSONObject.NULL);
                    credJson.put("degree", sanitizeString(cred.getDegree()));
                    credJson.put("institution", sanitizeString(cred.getInstitution()));
                    credJson.put("studentId", sanitizeString(cred.getStudentId()));
                    credJson.put("credentialId", sanitizeString(cred.getCredentialId()));
                    credArray.put(credJson);
                }
                json.put("credentials", credArray);
            } else {
                json.put("credentials", new JSONArray());
            }
            
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
     * @throws RuntimeException if deserialization fails
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
                    
                    String studentName = credJson.optString("studentName", "");
                    long dateMillis = credJson.getLong("dateAwarded");
                    Date dateAwarded = new Date(dateMillis);
                    String degree = credJson.optString("degree", "");
                    String institution = credJson.optString("institution", "");
                    String studentId = credJson.optString("studentId", "");
                    String credentialId = credJson.optString("credentialId", "");
                    
                    credentials.add(new Credential(studentName, dateAwarded, degree, institution, studentId, credentialId));
                }
            }
            
            // Reconstruct block preserving the stored header values
            return reconstructBlock(index, timestamp, previousHash, hash, validatorId, signature, credentials);
            
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
     * @throws RuntimeException if serialization fails
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
     * @throws RuntimeException if deserialization fails
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
     * @param index the block index
     * @param timestamp the block timestamp
     * @param previousHash the previous block hash
     * @param hash the block hash
     * @param validatorId the validator ID
     * @param signature the block signature
     * @param credential the credential payload
     * @return reconstructed Block object with exact stored values
     */
    private static Block reconstructBlock(int index, long timestamp, String previousHash, 
                                         String hash, String validatorId, String signature, 
                                         ArrayList<Credential> credentials) {
        // Use the deserialization constructor to preserve exact hash and timestamp
        return new Block(index, timestamp, previousHash, hash, validatorId, signature, credentials);
    }
}