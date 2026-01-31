package com.immutable.credentials.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.immutable.credentials.model.Block;

/**
 * P2PNetwork manages peer-to-peer network communication.
 *
 * Responsibilities:
 * - Establish peer connections
 * - Broadcast blocks
 * - Receive and validate incoming blocks
 * - Synchronize chains across peers
 * - Provide access to connected peer list
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
	private int listenPort;
	private int maxConnections;
	private int connectTimeoutMillis;
	private long discoveryIntervalMillis;
	private long syncIntervalMillis;

	/**
	 * Construct a P2PNetwork with configuration parameters.
	 * 
	 * @param listenPort              the port to listen on
	 * @param maxConnections          maximum number of peer connections
	 * @param connectTimeoutMillis    connection timeout in milliseconds
	 * @param discoveryIntervalMillis peer discovery interval in milliseconds
	 * @param syncIntervalMillis      chain sync interval in milliseconds
	 */
	public P2PNetwork(int listenPort, int maxConnections, int connectTimeoutMillis,
			long discoveryIntervalMillis, long syncIntervalMillis) {
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
		}
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
		// TODO: accept incoming sockets and handle handshake
	}

	/**
	 * Connect to a peer by host and port.
	 *
	 * @param host the peer host
	 * @param port the peer port
	 */
	public void connectToPeer(String host, int port) {
		// TODO: implement outbound connection
	}

	/**
	 * Establish a new connection from an accepted socket.
	 * Uses length-prefixed framing: [4-byte length][message bytes]
	 */
	private void handleNewConnection(Socket socket, boolean outbound) {
		// TODO: create DataInputStream/DataOutputStream
		// TODO: perform handshake (send/receive)
		// TODO: register peer and store PeerConnection
		// TODO: start read loop in connection pool
	}

	/**
	 * Broadcast a newly created block to all peers.
	 *
	 * @param block the block to broadcast
	 */
	public void broadcastBlock(Block block) {
		// TODO: implement broadcast logic
	}

	/**
	 * Handle a block received from a peer.
	 *
	 * @param block the incoming block
	 */
	public void receiveBlock(Block block) {
		// TODO: implement receive logic
	}

	/**
	 * Synchronize the local chain with peers.
	 */
	public void syncChain() {
		// TODO: implement chain sync logic
	}

	/**
	 * Request peer list from all peers.
	 */
	public void requestPeerDiscovery() {
		// TODO: send REQUEST_PEERS
	}

	/**
	 * Send heartbeat to all peers.
	 */
	public void pingPeers() {
		// TODO: send PING
	}

	/**
	 * Broadcast any NetworkMessage to peers, with optional exclusion.
	 */
	public void broadcastMessage(NetworkMessage message, String excludeNodeId) {
		// TODO: send to all connections
	}

	/**
	 * Get the list of connected peers.
	 *
	 * @return list of peers
	 */
	public List<Peer> getPeerList() {
		// TODO: return connected peers
		return List.of();
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
		// TODO: switch on message type
	}

	/**
	 * Handle a NEW_BLOCK message.
	 */
	private void handleNewBlockMessage(NetworkMessage message, PeerConnection connection) {
		// TODO: parse block, validate, append, rebroadcast
	}

	/**
	 * Handle a SEND_CHAIN message.
	 */
	private void handleChainMessage(NetworkMessage message, PeerConnection connection) {
		// TODO: parse chain and reconcile
	}

	/**
	 * Handle a SEND_PEERS message.
	 */
	private void handlePeersMessage(NetworkMessage message) {
		// TODO: parse peers and connect if needed
	}

	/**
	 * Send a message to a specific peer connection.
	 * Uses length-prefixed framing: writes 4-byte length, then message bytes.
	 */
	private void sendMessage(PeerConnection connection, NetworkMessage message) {
		// TODO: serialize message to JSON bytes
		// TODO: write length (int) with dataOut.writeInt(bytes.length)
		// TODO: write bytes with dataOut.write(bytes)
		// TODO: flush
	}

	/**
	 * Read a single message from the input stream.
	 * Uses length-prefixed framing: reads 4-byte length, then message bytes.
	 */
	private NetworkMessage readMessage(DataInputStream dataIn) throws IOException {
		// TODO: read length with dataIn.readInt()
		// TODO: allocate byte array of that length
		// TODO: read exact bytes with dataIn.readFully(bytes)
		// TODO: deserialize bytes to JSON string
		// TODO: parse NetworkMessage.fromJson()
		return null;
	}

	/**
	 * Build a HANDSHAKE message payload.
	 */
	private NetworkMessage buildHandshakeMessage() {
		// TODO: include nodeId, port, validator flag, version
		return null;
	}

	/**
	 * Parse and register peer data from handshake.
	 */
	private Peer registerPeerFromHandshake(NetworkMessage handshake, Socket socket) {
		// TODO: create peer object and store
		return null;
	}

	/**
	 * Periodically evict timed out peers.
	 */
	private void cleanupTimedOutPeers() {
		// TODO: remove stale peers and close sockets
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

		private void send(NetworkMessage message) {
			// TODO: synchronized(sendLock) for thread safety
			// TODO: serialize, write length + bytes, flush
		}

		private void close() {
			// TODO: mark inactive, close socket, remove from registry
		}
	}
}
