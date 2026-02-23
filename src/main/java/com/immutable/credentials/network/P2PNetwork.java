package com.immutable.credentials.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.core.Node;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.network.NetworkMessage.MessageType;
import com.immutable.credentials.util.JsonSerializer;

/**
 * P2PNetwork manages peer-to-peer network communication.
 *
 * Responsibilities:
 * - Establish peer connections
 * - Broadcast blocks
 * - Receive and validate incoming blocks
 * - Synchronize chains across peers
 * - Provide access to connected peer list
 *
 * This class delegates all blockchain and consensus operations to the owning
 * {@link Node} via its public API, rather than reaching into Node's internals.
 */
public class P2PNetwork {

	// ===== Core State =====
	private ServerSocket serverSocket;
	private Thread acceptThread;
	private ExecutorService connectionPool;
	private ScheduledExecutorService scheduler;

	private final ConcurrentMap<String, PeerConnection> connectionsByNodeId = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Peer> knownPeers = new ConcurrentHashMap<>();
	private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();

	private volatile boolean running;

	// ===== Configuration =====
	private final Node node;
	private int listenPort;
	private int maxConnections;
	private int connectTimeoutMillis;
	private long discoveryIntervalMillis;
	private long syncIntervalMillis;

	/**
	 * Construct a P2PNetwork with configuration parameters.
	 * 
	 * @param node                    the node that owns this network
	 * @param listenPort              the port to listen on
	 * @param maxConnections          maximum number of peer connections
	 * @param connectTimeoutMillis    connection timeout in milliseconds
	 * @param discoveryIntervalMillis peer discovery interval in milliseconds
	 * @param syncIntervalMillis      chain sync interval in milliseconds
	 */
	public P2PNetwork(Node node, int listenPort, int maxConnections, int connectTimeoutMillis,
			long discoveryIntervalMillis, long syncIntervalMillis) {
		this.node = node;
		this.listenPort = listenPort;
		this.maxConnections = maxConnections;
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.discoveryIntervalMillis = discoveryIntervalMillis;
		this.syncIntervalMillis = syncIntervalMillis;
		this.connectionPool = Executors.newFixedThreadPool(maxConnections);
		this.scheduler = Executors.newScheduledThreadPool(3);
	}

	/**
	 * Start the network listener and background workers.
	 */
	public synchronized void start() throws IOException {
		if (running) {
			return; // Already started
		}
		serverSocket = new ServerSocket(listenPort);
		running = true;

		acceptThread = new Thread(this::acceptLoop, "P2P-AcceptLoop");
		acceptThread.setDaemon(true);
		acceptThread.start();

		scheduler.scheduleAtFixedRate(this::requestPeerDiscovery,
				discoveryIntervalMillis, discoveryIntervalMillis,
				java.util.concurrent.TimeUnit.MILLISECONDS);

		scheduler.scheduleAtFixedRate(this::syncChain,
				syncIntervalMillis, syncIntervalMillis,
				java.util.concurrent.TimeUnit.MILLISECONDS);

		scheduler.scheduleAtFixedRate(this::pingPeers,
				10_000, 10_000,
				java.util.concurrent.TimeUnit.MILLISECONDS);

		System.out.println("[P2PNetwork] Started on port " + listenPort + " (max connections: " + maxConnections + ")");
	}

	/**
	 * Stop the network and release all resources gracefully.
	 */
	public synchronized void stop() {
		if (!running) {
			return;
		}
		running = false;

		// Close server socket → causes accept() to throw SocketException
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
		} catch (IOException e) {
			System.err.println("[P2PNetwork] Error closing server socket: " + e.getMessage());
		}
		if (acceptThread != null && acceptThread.isAlive()) {
			acceptThread.interrupt();
		}
		if (acceptThread != null) {
			try {
				// Wait UP TO 5000ms (5 seconds) for thread to finish
				// Blocks until thread dies OR timeout expires
				acceptThread.join(5000);
			} catch (InterruptedException e) {
				System.err.println("[P2PNetwork] Interrupted while waiting for accept thread");
			}

		}

		for (PeerConnection conn : connectionsByNodeId.values()) {
			conn.close();
			conn.peer.setConnected(false);
		}
		connectionsByNodeId.clear();
		connectionPool.shutdown();
		scheduler.shutdown();
		try {
			connectionPool.awaitTermination(5, TimeUnit.SECONDS);
			scheduler.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			connectionPool.shutdownNow();
			scheduler.shutdownNow();
		}
		System.out.println("[P2PNetwork] Stopped on port " + listenPort);
	}

	/**
	 * Starts listening on the configured port.
	 */
	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				handleNewConnection(socket, false);
			} catch (IOException e) {
				if (!running)
					break;
				System.err.println("[P2PNetwork] Accept error: " + e.getMessage());
			}
		}
	}

	/**
	 * Connect to a peer by host and port.
	 *
	 * @param host the peer host
	 * @param port the peer port
	 */
	public void connectToPeer(String host, int port) {
		if (!running)
			return;
		if (connectionsByNodeId.size() >= maxConnections) {
			System.err.println("[P2PNetwork] Max connections reached");
			return;
		}

		if (knownPeers.values().stream()
				.anyMatch(p -> p.getAddress().equals(host) && p.getPort() == port)) {
			System.out.println("[P2PNetwork] Already connected to " + host + ":" + port);
			return;
		}
		connectionPool.submit(() -> {
			Socket socket = null;
			try {
				socket = new Socket();
				socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
				socket.setSoTimeout(30000);
				System.out.println("[P2PNetwork] Connected to peer " + host + ":" + port);
				handleNewConnection(socket, true);

			} catch (java.net.SocketTimeoutException e) {
				System.err.println("[P2PNetwork] Connection timeout to " + host + ":" + port);
				closeSocketSafely(socket);
			} catch (java.net.ConnectException e) {
				System.err.println("[P2PNetwork] Connection refused by " + host + ":" + port);
				closeSocketSafely(socket);
			} catch (IOException e) {
				System.err.println("[P2PNetwork] Failed to connect to " + host + ":" + port + ": " + e.getMessage());
				closeSocketSafely(socket);
			}
		});

	}

	/**
	 * Safely close a socket, ignoring errors.
	 */
	private static void closeSocketSafely(Socket socket) {
		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	/**
	 * Establish a new connection from an accepted socket.
	 * Uses length-prefixed framing: [4-byte length][message bytes]
	 */
	private void handleNewConnection(Socket socket, boolean outbound) throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		try {
			NetworkMessage myHandshake = buildHandshakeMessage(node.getId(), listenPort, node.isValidator());
			byte[] handshakeBytes = myHandshake.toJson().getBytes(StandardCharsets.UTF_8);

			NetworkMessage theirHandshake;
			if (outbound) {
				out.writeInt(handshakeBytes.length);
				out.write(handshakeBytes);
				out.flush();
				theirHandshake = readMessage(in);
			} else {
				theirHandshake = readMessage(in);
				out.writeInt(handshakeBytes.length);
				out.write(handshakeBytes);
				out.flush();
			}

			if (theirHandshake.getType() != NetworkMessage.MessageType.HANDSHAKE) {
				throw new IOException("Expected HANDSHAKE, got: " + theirHandshake.getType());
			}

			if (node.getId().equals(theirHandshake.getSenderId())) {
				System.out.println("[P2PNetwork] Rejected self-connection");
				closeSocketSafely(socket);
				return;
			}

			String peerId = theirHandshake.getSenderId();
			if (connectionsByNodeId.containsKey(peerId)) {
				System.out.println("[P2PNetwork] Already connected to node: " + peerId);
				closeSocketSafely(socket);
				return;
			}

			Peer peer = registerPeerFromHandshake(theirHandshake, socket);
			peer.setConnected(true);
			peer.updateLastSeen();

			PeerConnection connection = new PeerConnection(peer, socket, in, out);
			connectionsByNodeId.put(peerId, connection);

			System.out.println("[P2PNetwork] Handshake complete with " + peer.getFullAddress() + " [" + peerId + "]");

			// Trigger initial chain sync by exchanging heights with the new peer.
			// This ensures joining nodes receive the full chain (including genesis)
			// from existing nodes immediately after connecting.
			JSONObject heightPayload = new JSONObject();
			heightPayload.put("currentHeight", node.getChainHeight());
			NetworkMessage heightMsg = new NetworkMessage(MessageType.CHAIN_HEIGHT, node.getId(), heightPayload);
			sendMessage(connection, heightMsg);

			connectionPool.submit(() -> {
				try {
					while (running && connection.isActive()) {
						NetworkMessage message = readMessage(connection.dataIn);
						handleMessage(message, connection);
					}
				} catch (IOException e) {
					if (running) {
						System.err.println("[P2PNetwork] Lost connection to "
								+ peer.getNodeId() + ": " + e.getMessage());
					}
				} finally {
					connection.close();
					connectionsByNodeId.remove(peerId);
					peer.setConnected(false);
				}
			});

		} catch (IOException e) {
			closeSocketSafely(socket);
			throw e;
		}
	}

	/**
	 * Broadcast a newly created block to all peers.
	 *
	 * @param block the block to broadcast
	 */
	public void broadcastBlock(Block block) {
		NetworkMessage message = new NetworkMessage(NetworkMessage.MessageType.NEW_BLOCK, node.getId(), block);
		seenMessageIds.add(message.getMessageId()); // prevent echoes of our own block from being reprocessed
		broadcastMessage(message, null);
	}

	/**
	 * Broadcast a credential to all peers for mempool inclusion.
	 * Any node can call this. The current round-robin proposer will
	 * accept the credential into their pending pool.
	 *
	 * @param credential the credential to broadcast
	 */
	public void broadcastCredential(com.immutable.credentials.model.Credential credential) {
		JSONObject credJson = new JSONObject(JsonSerializer.credentialToJson(credential));
		NetworkMessage message = new NetworkMessage(MessageType.SUBMIT_CREDENTIAL, node.getId(), credJson);
		seenMessageIds.add(message.getMessageId());
		broadcastMessage(message, null);
	}

	/**
	 * Broadcast a proposed block to all peers for voting.
	 * Sent by the current round-robin proposer after creating and signing a block.
	 * Peers will validate the block and respond with BLOCK_VOTE messages.
	 *
	 * <p>
	 * Steps to implement:
	 * </p>
	 * <p>
	 * Steps to implement:
	 * </p>
	 * <ol>
	 * <li>Serialize the block to JSON using
	 * {@code JsonSerializer.blockToJson(block)}</li>
	 * <li>Create a {@link NetworkMessage} with type {@code PROPOSE_BLOCK}</li>
	 * <li>Add the message ID to {@code seenMessageIds} to prevent echo</li>
	 * <li>Call {@code broadcastMessage(message, null)} to send to all peers</li>
	 * <li>Serialize the block to JSON using
	 * {@code JsonSerializer.blockToJson(block)}</li>
	 * <li>Create a {@link NetworkMessage} with type {@code PROPOSE_BLOCK}</li>
	 * <li>Add the message ID to {@code seenMessageIds} to prevent echo</li>
	 * <li>Call {@code broadcastMessage(message, null)} to send to all peers</li>
	 * </ol>
	 *
	 * @param block the signed block to propose for voting
	 */
	public void broadcastProposedBlock(Block block) {
		JSONObject blockJson = new JSONObject(JsonSerializer.blockToJson(block));
		NetworkMessage message = new NetworkMessage(MessageType.PROPOSE_BLOCK, node.getId(), blockJson);
		seenMessageIds.add(message.getMessageId());
		broadcastMessage(message, null);
	}

	/**
	 * Broadcast a vote on a proposed block to all peers.
	 * Sent by each validator after verifying a received PROPOSE_BLOCK.
	 *
	 * <p>
	 * Steps to implement:
	 * </p>
	 * <p>
	 * Steps to implement:
	 * </p>
	 * <ol>
	 * <li>Create a {@link JSONObject} payload with keys:
	 * "blockIndex" (int), "blockHash" (String),
	 * "voterId" ({@code node.getId()}), "approve" (boolean)</li>
	 * <li>Create a {@link NetworkMessage} with type {@code BLOCK_VOTE}</li>
	 * <li>Add the message ID to {@code seenMessageIds} to prevent echo</li>
	 * <li>Call {@code broadcastMessage(message, null)} to send to all peers</li>
	 * <li>Create a {@link JSONObject} payload with keys:
	 * "blockIndex" (int), "blockHash" (String),
	 * "voterId" ({@code node.getId()}), "approve" (boolean)</li>
	 * <li>Create a {@link NetworkMessage} with type {@code BLOCK_VOTE}</li>
	 * <li>Add the message ID to {@code seenMessageIds} to prevent echo</li>
	 * <li>Call {@code broadcastMessage(message, null)} to send to all peers</li>
	 * </ol>
	 *
	 * @param blockIndex the index of the block being voted on
	 * @param blockHash  the hash of the block being voted on
	 * @param approve    true to approve, false to reject
	 */
	public void broadcastBlockVote(int blockIndex, String blockHash, boolean approve) {
		JSONObject payload = new JSONObject();
		payload.put("blockIndex", blockIndex);
		payload.put("blockHash", blockHash);
		payload.put("voterId", node.getId());
		payload.put("approve", approve);
		payload.put("blockIndex", blockIndex);
		payload.put("blockHash", blockHash);
		payload.put("voterId", node.getId());
		payload.put("approve", approve);
		NetworkMessage message = new NetworkMessage(MessageType.BLOCK_VOTE, node.getId(), payload);
		seenMessageIds.add(message.getMessageId());
		broadcastMessage(message, null);
	}

	/**
	 * Synchronize the local chain with peers.
	 */
	public void syncChain() {
		if (connectionsByNodeId.isEmpty())
			return;
		for (PeerConnection peer : connectionsByNodeId.values()) {
			JSONObject payload = new JSONObject();
			payload.put("currentHeight", node.getChainHeight());
			NetworkMessage message = new NetworkMessage(MessageType.CHAIN_HEIGHT, node.getId(), payload);
			sendMessage(peer, message);
		}
	}

	/**
	 * Request peer list from all peers.
	 */
	public void requestPeerDiscovery() {
		if (connectionsByNodeId.isEmpty())
			return;
		for (PeerConnection peer : connectionsByNodeId.values()) {
			NetworkMessage message = new NetworkMessage(MessageType.REQUEST_PEERS, node.getId(), new JSONObject());
			sendMessage(peer, message);
		}

	}

	/**
	 * Send heartbeat to all peers.
	 */
	public void pingPeers() {
		for (PeerConnection peer : connectionsByNodeId.values()) {
			JSONObject payload = new JSONObject();
			payload.put("timestamp", System.currentTimeMillis());
			sendMessage(peer, new NetworkMessage(NetworkMessage.MessageType.PING, node.getId(), payload));
		}
		cleanupTimedOutPeers();
	}

	/**
	 * Broadcast any NetworkMessage to peers, with optional exclusion.
	 */
	public void broadcastMessage(NetworkMessage message, String excludeNodeId) {
		for (PeerConnection connection : connectionsByNodeId.values()) {
			if (excludeNodeId != null && connection.peer.getNodeId().equals(excludeNodeId))
				continue;
			sendMessage(connection, message);
		}

	}

	/**
	 * Get the list of connected peers.
	 *
	 * @return list of peers
	 */
	public List<Peer> getPeerList() {
		List<Peer> peers = new ArrayList<>();
		for (PeerConnection conn : connectionsByNodeId.values()) {
			peers.add(conn.peer);
		}
		return peers;
	}

	/**
	 * Get all known peers (connected or not).
	 */
	public Collection<Peer> getKnownPeers() {
		return knownPeers.values();
	}

	/**
	 * Handle an incoming NetworkMessage.
	 */
	private void handleMessage(NetworkMessage message, PeerConnection connection) {
		if (!seenMessageIds.add(message.getMessageId())) {
			return;
		}
		switch (message.getType()) {
			case SUBMIT_CREDENTIAL:
				handleSubmitCredentialMessage(message, connection);
				break;
			case NEW_BLOCK:
				handleNewBlockMessage(message, connection);
				break;
			case PROPOSE_BLOCK:
				handleProposeBlockMessage(message, connection);
				break;
			case BLOCK_VOTE:
				handleBlockVoteMessage(message, connection);
				break;
			case REQUEST_BLOCK:
				handleRequestBlockMessage(message, connection);
				break;
			case SEND_BLOCK:
				handleSendBlockMessage(message, connection);
				break;
			case REQUEST_CHAIN:
				handleRequestChainMessage(message, connection);
				break;
			case SEND_CHAIN:
				handleChainMessage(message, connection);
				break;
			case CHAIN_HEIGHT:
				handleChainHeightMessage(message, connection);
				break;
			case REQUEST_PEERS:
				handleRequestPeersMessage(message, connection);
				break;
			case SEND_PEERS:
				handlePeersMessage(message);
				break;
			case DISCONNECT:
				handleDisconnectMessage(connection);
				break;
			case HANDSHAKE:
				break;
			case ACK:
				break;
			case PING:
				handlePingMessage(message, connection);
				break;
			case PONG:
				handlePongMessage(connection);
				break;
			default:
				System.err.println("[P2PNetwork] Unknown message type: " + message.getType());
				break;
		}
	}

	/**
	 * Handle a PROPOSE_BLOCK message — a validator is proposing a new block for
	 * voting.
	 * Deserializes the block and delegates to
	 * {@code node.handleProposedBlock(block)}.
	 * Handle a SUBMIT_CREDENTIAL message — a peer is broadcasting a credential
	 * for mempool inclusion. Delegates to {@code node.handleIncomingCredential}.
	 *
	 * @param message    the incoming SUBMIT_CREDENTIAL message
	 * @param connection the connection the message arrived on
	 */
	private void handleSubmitCredentialMessage(NetworkMessage message, PeerConnection connection) {
		String payload = message.getPayload().toString();
		com.immutable.credentials.model.Credential credential = JsonSerializer.jsonToCredential(payload);
		if (credential == null)
			return;
		node.handleIncomingCredential(credential);
		broadcastMessage(message, connection.peer.getNodeId());
	}

	/**
	 * Handle a PROPOSE_BLOCK message — a validator is proposing a new block for
	 * voting.
	 * Deserializes the block and delegates to
	 * {@code node.handleProposedBlock(block)}.
	 *
	 * @param message    the incoming PROPOSE_BLOCK message
	 * @param connection the connection the message arrived on
	 */
	private void handleProposeBlockMessage(NetworkMessage message, PeerConnection connection) {
		String payload = message.getPayload().toString();
		Block block = JsonSerializer.jsonToBlock(payload);
		if (block == null || !block.isHashValid())
		if (block == null || !block.isHashValid())
			return;
		node.handleProposedBlock(block);
		broadcastMessage(message, connection.peer.getNodeId());
	}

	/**
	 * Handle a BLOCK_VOTE message — a validator is casting a vote on a proposed
	 * block.
	 * Handle a BLOCK_VOTE message — a validator is casting a vote on a proposed
	 * block.
	 * Extracts vote data from the payload and delegates to
	 * {@code node.handleBlockVote(blockIndex, blockHash, voterId, approve)}.
	 * 
	 * 
	 * @param message    the incoming BLOCK_VOTE message
	 * @param connection the connection the message arrived on
	 */
	private void handleBlockVoteMessage(NetworkMessage message, PeerConnection connection) {
		if (!(message.getPayload() instanceof JSONObject))
			return;
		JSONObject payload = (JSONObject) message.getPayload();
		int blockIndex = payload.getInt("blockIndex");
		String blockHash = payload.getString("blockHash");
		String voterId = payload.getString("voterId");
		boolean approve = payload.getBoolean("approve");
		node.handleBlockVote(blockIndex, blockHash, voterId, approve);
		broadcastMessage(message, connection.peer.getNodeId());
	}

	/**
	 * Handle a PING message — respond with a PONG linked to the original PING.
	 */
	private void handlePingMessage(NetworkMessage ping, PeerConnection connection) {
		JSONObject payload = new JSONObject();
		payload.put("timestamp", System.currentTimeMillis());
		NetworkMessage pong = new NetworkMessage(
				NetworkMessage.MessageType.PONG, node.getId(), payload, ping.getMessageId());
		sendMessage(connection, pong);
	}

	/**
	 * Handle a PONG message — update the peer's last seen timestamp.
	 */
	private void handlePongMessage(PeerConnection connection) {
		connection.peer.updateLastSeen();
	}

	/**
	 * Handle a NEW_BLOCK message.
	 */
	private void handleNewBlockMessage(NetworkMessage message, PeerConnection connection) {
		String payload = message.getPayload().toString();
		Block block = JsonSerializer.jsonToBlock(payload);
		if (block == null || !block.isHashValid())
			return;
		Block last = node.getLatestBlock();
		if (last == null) {
			// Empty chain — need full sync to get genesis and all blocks
			syncChain();
			return;
		}
		if (block.getIndex() != last.getIndex() + 1) {
			syncChain();
			return;
		}
		if (!block.getPreviousHash().equals(last.getHash())) {
			syncChain();
			return;
		}
		Validator validator = node.getValidatorById(block.getValidatorId());
		if (validator == null)
			return;
		if (!node.validateBlockSignature(block, validator.getPublicKey()))
			return;
		node.addBlockToChain(block);
		broadcastMessage(message, connection.peer.getNodeId());
	}

	/**
	 * Handle a SEND_CHAIN message.
	 */
	private void handleChainMessage(NetworkMessage message, PeerConnection connection) {
		String payload = message.getPayload().toString();

		Blockchain incomingChain = new Blockchain(JsonSerializer.jsonToChain(payload));
		if (!node.validateIncomingChain(incomingChain))
			return;
		if (incomingChain.getChain().size() <= node.getChainHeight())
			return;
		node.replaceChain(incomingChain.getChain());
	}

	/**
	 * Handle a REQUEST_CHAIN message — peer is asking for our full chain.
	 * Sends our chain back only if we are ahead of the requester.
	 *
	 * @param message    the incoming REQUEST_CHAIN message (payload contains
	 *                   "currentHeight")
	 * @param connection the connection to reply on
	 */
	private void handleRequestChainMessage(NetworkMessage message, PeerConnection connection) {
		JSONObject payload = (JSONObject) message.getPayload();
		int theirHeight = payload.optInt("currentHeight", 0);
		int ourHeight = node.getChainHeight();
		if (ourHeight <= theirHeight)
			return;
		String chainJson = JsonSerializer.chainToJson(node.getChain());
		JSONArray chainArray = new JSONArray(chainJson);
		NetworkMessage response = new NetworkMessage(MessageType.SEND_CHAIN, node.getId(), chainArray);
		sendMessage(connection, response);
		System.out.println("[P2PNetwork] Sent chain (height=" + ourHeight + ") to " + connection.peer.getNodeId());
	}

	/**
	 * Handle a REQUEST_BLOCK message — peer is asking for a specific block by
	 * index.
	 * Sends the block back only if we have it.
	 *
	 * @param message    the incoming REQUEST_BLOCK message (payload contains
	 *                   "blockIndex")
	 * @param connection the connection to reply on
	 */
	private void handleRequestBlockMessage(NetworkMessage message, PeerConnection connection) {
		JSONObject payload = (JSONObject) message.getPayload();
		int blockIndex = payload.optInt("blockIndex", -1);
		Block block = node.getBlock(blockIndex);
		if (block == null) {
			System.err.println("[P2PNetwork] Requested block " + blockIndex + " not found");
			return;
		}
		JSONObject blockJson = new JSONObject(JsonSerializer.blockToJson(block));
		NetworkMessage response = new NetworkMessage(MessageType.SEND_BLOCK, node.getId(), blockJson);
		sendMessage(connection, response);
	}

	/**
	 * Handle a SEND_BLOCK message — peer is responding to our REQUEST_BLOCK.
	 * Validates and appends the block if it fits the local chain.
	 *
	 * @param message    the incoming SEND_BLOCK message (payload is a serialized
	 *                   Block)
	 * @param connection the connection the block arrived on
	 */
	private void handleSendBlockMessage(NetworkMessage message, PeerConnection connection) {
		String payload = message.getPayload().toString();
		Block block = JsonSerializer.jsonToBlock(payload);
		if (block == null || !block.isHashValid())
			return;
		Block last = node.getLatestBlock();
		if (last == null) {
			// Empty chain — need full sync to get genesis and all blocks
			syncChain();
			return;
		}
		if (block.getIndex() != last.getIndex() + 1)
			return;
		if (!block.getPreviousHash().equals(last.getHash()))
			return;
		Validator validator = node.getValidatorById(block.getValidatorId());
		if (validator == null)
			return;
		if (!node.validateBlockSignature(block, validator.getPublicKey()))
			return;
		node.addBlockToChain(block);
		System.out.println("[P2PNetwork] Appended block " + block.getIndex() + " from " + connection.peer.getNodeId());
	}

	/**
	 * Handle a CHAIN_HEIGHT message — peer is sharing their current chain height.
	 * Used for quick comparison without transferring the full chain.
	 *
	 * @param message    the incoming CHAIN_HEIGHT message (payload contains
	 *                   "height")
	 * @param connection the connection the message arrived on
	 */
	private void handleChainHeightMessage(NetworkMessage message, PeerConnection connection) {
		JSONObject payload = (JSONObject) message.getPayload();
		int peerHeight = payload.optInt("currentHeight", 0);
		if (peerHeight > node.getChainHeight()) {
			JSONObject requestPayload = new JSONObject();
			requestPayload.put("currentHeight", node.getChainHeight());
			NetworkMessage request = new NetworkMessage(MessageType.REQUEST_CHAIN, node.getId(), requestPayload);
			sendMessage(connection, request);
			System.out.println("[P2PNetwork] Peer " + connection.peer.getNodeId() + " is ahead (" + peerHeight + " vs "
					+ node.getChainHeight() + "), requesting chain");
		} else if (peerHeight < node.getChainHeight()) {
			// We're ahead — push our chain to the lagging peer so they can sync
			String chainJson = JsonSerializer.chainToJson(node.getChain());
			JSONArray chainArray = new JSONArray(chainJson);
			NetworkMessage response = new NetworkMessage(MessageType.SEND_CHAIN, node.getId(), chainArray);
			sendMessage(connection, response);
			System.out.println("[P2PNetwork] Pushing chain (height=" + node.getChainHeight() + ") to lagging peer "
					+ connection.peer.getNodeId());
		}
	}

	/**
	 * Handle a REQUEST_PEERS message — peer is asking for our known peer list.
	 * Responds with a SEND_PEERS message containing our knownPeers.
	 *
	 * @param message    the incoming REQUEST_PEERS message
	 * @param connection the connection to reply on
	 */
	private void handleRequestPeersMessage(NetworkMessage message, PeerConnection connection) {
		List<Peer> peers = new ArrayList<>(knownPeers.values());
		String peersJson = JsonSerializer.peerListToJson(peers);
		JSONArray peersArray = new JSONArray(peersJson);
		NetworkMessage response = new NetworkMessage(MessageType.SEND_PEERS, node.getId(), peersArray);
		sendMessage(connection, response);
	}

	/**
	 * Handle a DISCONNECT message — peer is gracefully closing the connection.
	 * Cleans up the connection and marks the peer as offline.
	 *
	 * @param connection the connection that sent the DISCONNECT
	 */
	private void handleDisconnectMessage(PeerConnection connection) {
		System.out.println("[P2PNetwork] Peer disconnected gracefully: " + connection.peer.getNodeId());
		connection.close();
		connection.peer.setConnected(false);
	}

	/**
	 * Handle a SEND_PEERS message.
	 */
	private void handlePeersMessage(NetworkMessage message) {
		String payload = message.getPayload().toString();
		List<Peer> peers = JsonSerializer.jsonToPeerList(payload);
		for (Peer peer : peers) {
			if (node.getId().equals(peer.getNodeId()))
				continue;
			if (connectionsByNodeId.containsKey(peer.getNodeId()))
				continue;
			knownPeers.putIfAbsent(peer.getNodeId(), peer);
			connectToPeer(peer.getAddress(), peer.getPort());
		}
	}

	/**
	 * Send a message to a specific peer connection.
	 * Uses length-prefixed framing: writes 4-byte length, then message bytes.
	 */
	private void sendMessage(PeerConnection connection, NetworkMessage message) {
		if (connection == null || !connection.isActive() || message == null) {
			return;
		}
		try {
			byte[] bytes = message.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			synchronized (connection.sendLock) {
				connection.dataOut.writeInt(bytes.length);
				connection.dataOut.write(bytes);
				connection.dataOut.flush();
			}
		} catch (IOException e) {
			System.err.println("[P2PNetwork] Failed to send message to "
					+ connection.peer.getNodeId() + ": " + e.getMessage());
			connection.close();
		}
	}

	/**
	 * Read a single message from the input stream.
	 * Uses length-prefixed framing: reads 4-byte length, then message bytes.
	 */
	private NetworkMessage readMessage(DataInputStream dataIn) throws IOException {
		try {
			int length = dataIn.readInt();
			if (length <= 0 || length > NetworkMessage.MAX_PAYLOAD_SIZE) {
				throw new IOException("Invalid message length: " + length);
			}
			byte[] bytes = new byte[length];
			dataIn.readFully(bytes);
			String json = new String(bytes, StandardCharsets.UTF_8);
			return NetworkMessage.fromJson(json);
		} catch (IOException e) {
			throw new IOException("[P2PNetwork] Failed to read message.", e);
		}
	}

	/**
	 * Build a HANDSHAKE message payload.
	 */
	private NetworkMessage buildHandshakeMessage(String nodeId, int port, boolean isValidator) {
		JSONObject payload = new JSONObject();
		payload.put("nodeId", nodeId);
		payload.put("port", port);
		payload.put("isValidator", isValidator);
		return new NetworkMessage(NetworkMessage.MessageType.HANDSHAKE, nodeId, payload);

	}

	/**
	 * Parse and register peer data from handshake.
	 */
	private Peer registerPeerFromHandshake(NetworkMessage handshake, Socket socket) throws IOException {
		if (!(handshake.getPayload() instanceof JSONObject)) {
			throw new IOException("Invalid handshake payload");
		}
		JSONObject payload = (JSONObject) handshake.getPayload();
		String nodeId = payload.getString("nodeId");
		int port = payload.getInt("port");
		boolean isValidator = payload.getBoolean("isValidator");
		Peer peer = new Peer(socket.getInetAddress().getHostAddress(), port, nodeId);
		peer.setValidator(isValidator);
		knownPeers.put(nodeId, peer);
		return peer;
	}

	/**
	 * Periodically evict timed out peers.
	 */
	private void cleanupTimedOutPeers() {
		List<String> timedOut = new ArrayList<>();
		for (Entry<String, PeerConnection> entry : connectionsByNodeId.entrySet()) {
			if (entry.getValue().peer.isTimedOut()) {
				timedOut.add(entry.getKey());
			}
		}
		for (String nodeId : timedOut) {
			PeerConnection conn = connectionsByNodeId.remove(nodeId);
			if (conn != null) {
				conn.close();
				conn.peer.setConnected(false);
				System.out.println("[P2PNetwork] Peer timed out: " + nodeId);
			}
		}
	}

	// ===== Connection Wrapper =====
	/**
	 * Wraps a peer socket connection with binary streams.
	 * Uses DataInputStream/DataOutputStream for length-prefixed framing.
	 * This prevents corruption of binary crypto data (signatures, keys, hashes).
	 */
	private static class PeerConnection {
		private final Peer peer;
		private final Socket socket;
		private final DataInputStream dataIn;
		private final DataOutputStream dataOut;
		private volatile boolean active = true;
		private final Object sendLock = new Object();

		private PeerConnection(Peer peer, Socket socket, DataInputStream dataIn, DataOutputStream dataOut) {
			this.peer = peer;
			this.socket = socket;
			this.dataIn = dataIn;
			this.dataOut = dataOut;
		}

		private boolean isActive() {
			return active && !socket.isClosed();
		}

		private void close() {
			active = false;
			closeSocketSafely(socket);
		}
	}
}
