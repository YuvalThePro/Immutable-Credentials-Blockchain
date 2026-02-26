package com.immutable.credentials.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.immutable.credentials.model.Block;
import com.immutable.credentials.util.JsonSerializer;

/**
 * NetworkMessage represents all messages exchanged between nodes in the P2P network.
 * Each message has a type, sender information, timestamp, and a payload.
 * This class handles message creation and validation, serialization to JSON for
 * network transmission, deserialization from JSON when receiving messages, and
 * message integrity verification.
 * Message flow: the sender creates a NetworkMessage with a type and payload,
 * serializes it to a JSON string, sends it over a socket to peers, and the receiver
 * deserializes the JSON back to a NetworkMessage, then validates and processes it
 * based on the message type.
 */
public class NetworkMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String PROTOCOL_VERSION = "1.0";
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10MB limit

    /**
     * Defines all possible message types in the P2P network protocol.
     */
    public enum MessageType {
        // Block Operations
        /** Broadcast a newly created block to all peers */
        NEW_BLOCK,

        /** Request a specific block by index or hash */
        REQUEST_BLOCK,

        /** Send a specific block in response to REQUEST_BLOCK */
        SEND_BLOCK,

        // Consensus Voting
        /**
         * Propose a new block to the network for validator voting.
         * Sent by the current round-robin proposer to all peers.
         * Payload: the signed Block serialized to JSON.
         */
        PROPOSE_BLOCK,

        /**
         * Cast a vote (approve/reject) on a proposed block.
         * Sent by each validator after verifying a PROPOSE_BLOCK.
         * Payload: JSONObject with "blockIndex" (int), "blockHash" (String),
         * "voterId" (String), and "approve" (boolean).
         */
        BLOCK_VOTE,

        // Chain Synchronization
        /** Request the complete blockchain from a peer */
        REQUEST_CHAIN,

        /** Send the complete blockchain in response to REQUEST_CHAIN */
        SEND_CHAIN,

        /** Request only the blockchain height (block count) for quick comparison */
        CHAIN_HEIGHT,

        // Connection Management
        /** Initial handshake when establishing connection (exchange node info) */
        HANDSHAKE,

        /** Keep-alive message to check if peer is still active */
        PING,

        /** Response to PING message */
        PONG,

        /** Graceful disconnection notification */
        DISCONNECT,

        // Peer Discovery
        /** Request list of known peers for network discovery */
        REQUEST_PEERS,

        /** Send list of known peers in response to REQUEST_PEERS */
        SEND_PEERS,

        // Credential Submission
        /**
         * Broadcast a credential for inclusion in the next block.
         * Any node can send this. Only the current round-robin proposer
         * accepts it into their mempool.
         * Payload: JSONObject with credential fields.
         */
        SUBMIT_CREDENTIAL,

        // Reliability & Error Handling
        /** Acknowledgment that a message was received and processed */
        ACK,

        /** Error notification (invalid block, validation failure, etc.) */
        ERROR
    }

    private final String messageId;

    private final String protocolVersion;

    private final MessageType type;

    private final String senderId;

    private final long timestamp;

    private final String responseToId;

    // Message Content
    /**
     * The actual message payload. Type depends on MessageType:
     * NEW_BLOCK and SEND_BLOCK carry a Block object serialized to JSON.
     * SEND_CHAIN carries an ArrayList of Block objects serialized to JSON.
     * SEND_PEERS carries an ArrayList of Peer objects serialized to JSON.
     * CHAIN_HEIGHT carries an Integer representing the block count.
     * HANDSHAKE carries node info such as nodeId, isValidator, and version.
     * ERROR carries an error message string.
     * PING, PONG, and ACK can be empty or carry a simple string.
     */
    private final Object payload;

    /**
     * Create a new message with automatic ID generation.
     * Used for initiating new messages (not responses).
     * 
     * @param type     the message type
     * @param senderId the ID of the sending node
     * @param payload  the message payload (can be null for simple messages)
     */
    public NetworkMessage(MessageType type, String senderId, Object payload) {
        if (type == null || senderId == null) {
            throw new IllegalArgumentException("Required parameters type and senderId cannot be null.");
        }

        this.messageId = UUID.randomUUID().toString();
        this.protocolVersion = PROTOCOL_VERSION;
        this.timestamp = new Date().getTime();
        this.responseToId = null;
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }

    /**
     * Create a response message to an existing message.
     * Links this message to the original via responseToId.
     * 
     * @param type         the message type
     * @param senderId     the ID of the sending node
     * @param payload      the message payload
     * @param responseToId the ID of the message this is responding to
     */
    public NetworkMessage(MessageType type, String senderId, Object payload, String responseToId) {
        if (type == null || senderId == null || responseToId == null) {
            throw new IllegalArgumentException("Required parameters type, senderId, responseToId cannot be null.");
        }
        this.responseToId = responseToId;
        this.messageId = UUID.randomUUID().toString();
        this.protocolVersion = PROTOCOL_VERSION;
        this.timestamp = new Date().getTime();
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }

    /**
     * Private constructor for deserialization.
     * Used internally when recreating message from JSON.
     * 
     * @param messageId       the message ID
     * @param protocolVersion the protocol version
     * @param type            the message type
     * @param senderId        the sender node ID
     * @param timestamp       the timestamp
     * @param responseToId    the response-to ID (can be null)
     * @param payload         the payload
     */
    private NetworkMessage(String messageId, String protocolVersion, MessageType type,
            String senderId, long timestamp, String responseToId, Object payload) {
        if (messageId == null || protocolVersion == null || type == null || senderId == null) {
            throw new IllegalArgumentException(
                    "Required parameters messageId, protocolVersion, type, and senderId cannot be null.");
        }

        this.messageId = messageId;
        this.protocolVersion = protocolVersion;
        this.type = type;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.responseToId = responseToId;
        this.payload = payload;
    }

    // Serialization Methods

    /**
     * Convert this message to a JSON string for network transmission.
     * 
     * JSON Structure:
     * {
     * "messageId": "uuid-string",
     * "protocolVersion": "1.0",
     * "type": "NEW_BLOCK",
     * "senderId": "node-123",
     * "timestamp": 1234567890,
     * "responseToId": "uuid-of-original" (or null),
     * "payload": { ... } (or null)
     * }
     * 
     * @return JSON string representation of this message
     */
    public String toJson() {
        JSONObject json = new JSONObject();

        json.put("messageId", this.messageId);
        json.put("protocolVersion", this.protocolVersion);
        json.put("type", this.type.name()); // Convert enum to string
        json.put("senderId", this.senderId);
        json.put("timestamp", this.timestamp);
        json.put("responseToId", responseToId != null ? responseToId : JSONObject.NULL);

        // Handle payload with Block/Chain serialization support
        if (payload == null) {
            json.put("payload", JSONObject.NULL);
        } else if (payload instanceof Block) {
            json.put("payload", new JSONObject(JsonSerializer.blockToJson((Block) payload)));
        } else if (payload instanceof ArrayList) {
            ArrayList<Block> blockList = new ArrayList<>();
            for (Object item : (ArrayList<?>) payload) {
                if (item instanceof Block) {
                    blockList.add((Block) item);
                }
            }
            json.put("payload", new JSONArray(JsonSerializer.chainToJson(blockList)));
        } else if (payload instanceof JSONObject || payload instanceof JSONArray) {
            json.put("payload", payload);
        } else {
            // For String, Integer, or other primitive types
            json.put("payload", payload);
        }

        return json.toString();
    }

    /**
     * Deserialize a NetworkMessage from JSON string.
     * 
     * @param json the JSON string received from network
     * @return the deserialized NetworkMessage
     * @throws IllegalArgumentException if JSON is invalid or missing required
     *                                  fields
     */
    public static NetworkMessage fromJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        try {
            JSONObject json = new JSONObject(jsonString);

            String messageId = json.getString("messageId");
            String protocolVersion = json.getString("protocolVersion");
            String typeString = json.getString("type");
            String senderId = json.getString("senderId");
            long timestamp = json.getLong("timestamp");

            String responseToId = json.isNull("responseToId") ? null : json.getString("responseToId");
            Object payload = json.isNull("payload") ? null : json.get("payload");

            MessageType type = MessageType.valueOf(typeString);

            return new NetworkMessage(messageId, protocolVersion, type, senderId,
                    timestamp, responseToId, payload);

        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid message type: " + jsonString, e);
        }
    }

    // Validation Methods

    /**
     * Validate that this message is well-formed and ready to send.
     * Checks:
     * - Required fields are not null
     * - MessageType is valid
     * - SenderId is not empty
     * - Timestamp is reasonable (not in future, not too old)
     * - Payload matches expected type for this MessageType
     * 
     * @return true if message is valid, false otherwise
     */
    public boolean isValid() {
        if (messageId == null || type == null || senderId == null) {
            return false;
        }

        if (senderId.isEmpty()) {
            return false;
        }

        long now = new Date().getTime();
        long tenMinutesAgo = now - (10 * 60_000); // Extended to 10 minutes for distributed systems

        if (timestamp > now || timestamp < tenMinutesAgo) {
            return false;
        }

        // Validate payload size if serialized
        if (payload != null) {
            try {
                String payloadJson = new JSONObject().put("test", payload).toString();
                if (payloadJson.length() > MAX_PAYLOAD_SIZE) {
                    return false;
                }
            } catch (Exception e) {
                // If payload can't be serialized, consider it invalid
                return false;
            }
        }

        return true;
    }

    /**
     * Check if this message is a response to another message.
     * 
     * @return true if this is a response message
     */
    public boolean isResponse() {
        return responseToId != null;
    }

    // Getters

    /**
     * Get the unique message ID.
     * 
     * @return the message ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Get the protocol version.
     * 
     * @return the protocol version string
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Get the message type.
     * 
     * @return the MessageType enum value
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Get the sender's node ID.
     * 
     * @return the sender node ID
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Get the message timestamp.
     * 
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the ID of the message this is responding to.
     * 
     * @return the response-to message ID, or null if not a response
     */
    public String getResponseToId() {
        return responseToId;
    }

    /**
     * Get the message payload.
     * The caller must cast to the appropriate type based on MessageType.
     * 
     * @return the payload object
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * Get payload as a Block (type-safe).
     * Use this for NEW_BLOCK or SEND_BLOCK message types.
     * 
     * @return the Block payload, or null if payload is not a Block
     */
    public Block getBlockPayload() {
        if (payload instanceof Block) {
            return (Block) payload;
        }
        return null;
    }

    /**
     * Get payload as a blockchain (type-safe).
     * Use this for SEND_CHAIN message type.
     * 
     * @return the chain as ArrayList<Block>, or null if payload is not a chain
     */
    @SuppressWarnings("unchecked")
    public ArrayList<Block> getChainPayload() {
        if (payload instanceof ArrayList) {
            return (ArrayList<Block>) payload;
        }
        return null;
    }

    /**
     * Get payload as a String (type-safe).
     * Use this for ERROR or simple message types.
     * 
     * @return the String payload, or null if payload is not a String
     */
    public String getStringPayload() {
        if (payload instanceof String) {
            return (String) payload;
        }
        return null;
    }

    /**
     * Get payload as an Integer (type-safe).
     * Use this for CHAIN_HEIGHT message type.
     * 
     * @return the Integer payload, or null if payload is not an Integer
     */
    public Integer getIntegerPayload() {
        if (payload instanceof Integer) {
            return (Integer) payload;
        }
        return null;
    }

    // Utility Methods

    /**
     * Get a string representation of this message for logging/debugging.
     * 
     * @return a formatted string showing message details
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NetworkMessage{");
        sb.append("type=").append(type.name());
        sb.append(", id=").append(messageId);
        sb.append(", sender=").append(senderId);
        sb.append(", timestamp=").append(timestamp);
        if (responseToId != null) {
            sb.append(", responseToId=").append(responseToId);
        }
        sb.append(", payloadType=").append(payload != null ? payload.getClass().getSimpleName() : "null");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Create a quick PING message.
     * Convenience method for creating keep-alive messages.
     * 
     * @param senderId the sender node ID
     * @return a PING message
     */
    public static NetworkMessage createPing(String senderId) {

        return new NetworkMessage(MessageType.PING, senderId, null);
    }

    /**
     * Create a PONG response to a PING message.
     * 
     * @param senderId      the sender node ID
     * @param pingMessageId the ID of the PING message being responded to
     * @return a PONG message
     */
    public static NetworkMessage createPong(String senderId, String pingMessageId) {
        return new NetworkMessage(MessageType.PONG, senderId, null, pingMessageId);
    }

    /**
     * Create an ERROR message.
     * 
     * @param senderId     the sender node ID
     * @param errorMessage the error description
     * @return an ERROR message
     */
    public static NetworkMessage createError(String senderId, String errorMessage) {
        return new NetworkMessage(MessageType.ERROR, senderId, errorMessage);
    }

    /**
     * Create an ACK (acknowledgment) message.
     * 
     * @param senderId          the sender node ID
     * @param originalMessageId the ID of the message being acknowledged
     * @return an ACK message
     */
    public static NetworkMessage createAck(String senderId, String originalMessageId) {
        return new NetworkMessage(MessageType.ACK, senderId, null, originalMessageId);
    }
}
