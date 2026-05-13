package com.immutable.credentials.gui;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.immutable.credentials.auth.CredentialValidator;
import com.immutable.credentials.auth.NodeConfig;
import com.immutable.credentials.service.AdminService;
import com.immutable.credentials.service.AuthService;
import com.immutable.credentials.consensus.ProofOfAuthority;
import com.immutable.credentials.consensus.Validator;
import com.immutable.credentials.core.Node;
import com.immutable.credentials.model.Institution;
import com.immutable.credentials.network.P2PNetwork;
import com.immutable.credentials.service.BlockchainService;
import com.immutable.credentials.service.CredentialService;
import com.immutable.credentials.service.NetworkService;
import com.immutable.credentials.service.NodeService;
import com.immutable.credentials.util.ConfigLoader;
import com.immutable.credentials.util.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Main application window for the Immutable Credentials Blockchain UI.
 *
 * <p>
 * This is the JavaFX entry point. It hosts the top-level UI shell:
 * a menu bar, a status bar at the bottom, and a {@link TabPane} that
 * provides access to each functional panel.
 * </p>
 *
 * <p>
 * This class owns the service instances and injects them into every
 * panel so that no panel ever talks directly to the backend {@code Node}.
 * </p>
 *
 * <p>
 * Layout summary:
 * </p>
 * 
 * <pre>
 *  ┌───────────────────────────────────────────┐
 *  │  MenuBar  (File | Node | Help)            │
 *  ├───────────────────────────────────────────┤
 *  │  TabPane                                  │
 *  │  ┌────────┬──────────┬───────┬─────────┐  │
 *  │  │ Issue  │  Verify  │ Chain │ Network │  │
 *  │  └────────┴──────────┴───────┴─────────┘  │
 *  ├───────────────────────────────────────────┤
 *  │  StatusBar  (node-id | block # | peers)   │
 *  └───────────────────────────────────────────┘
 * </pre>
 */
public class MainWindow extends Application {

    // ===== Service Layer =====
    private NodeService nodeService;
    private CredentialService credentialService;
    private BlockchainService blockchainService;
    private NetworkService networkService;
    private AdminService adminService;
    private ScheduledExecutorService syncScheduler;
    private static final long SYNC_INTERVAL_MS = 30_000;

    // ===== Auth / Session =====
    private AuthService authService;
    private NodeConfig nodeConfig;

    // ===== UI Shell =====
    private Stage primaryStage;
    private BorderPane rootLayout;
    private TabPane tabPane;
    private HBox statusBar;
    private Label statusNodeLabel;
    private Label statusBlockLabel;
    private Label statusPeerLabel;

    // ===== Panels =====
    private IssueCredentialPanel issuePanel;
    private VerifyCredentialPanel verifyPanel;
    private BlockchainViewerPanel blockchainPanel;
    private NetworkStatusPanel networkPanel;
    private AdminPanel adminPanel;

    /**
     * JavaFX application entry point.
     * Shows the login dialog, authenticates the user against the cloud database,
     * initialises service instances with the fetched node configuration, builds
     * all UI components, wires event handlers, and shows the primary stage.
     *
     * @param primaryStage the primary Stage provided by the JavaFX runtime
     */
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Load the AuthService from config/database.properties
        AuthService authService;
        try {
            authService = new AuthService();
        } catch (Exception e) {
            showStartupError("Cannot read database configuration:\n" + e.getMessage()
                    + "\n\nPlease fill in config/database.properties with your Supabase connection URL.");
            return;
        }

        if (!authService.isConfigured()) {
            showStartupError("Database is not yet configured.\n\n"
                    + "Edit config/database.properties and replace the REPLACE_ME "
                    + "placeholders with your Supabase connection details.");
            return;
        }

        // Show the login dialog — blocks until the user logs in or cancels
        LoginDialog loginDialog = new LoginDialog(primaryStage, authService);
        NodeConfig nodeConfig = loginDialog.getResult();
        if (nodeConfig == null) {
            Platform.exit();
            return;
        }

        this.authService = authService;
        this.nodeConfig = nodeConfig;
        initServices(nodeConfig);

        try {
            nodeService.startNode();
            markNodeOnlineInDatabase();
            tryBootstrapNetwork();
        } catch (Exception e) {
            showStartupError("Failed to start node:\n" + e.getMessage());
            return;
        }

        rootLayout = new BorderPane();
        rootLayout.setTop(buildMenuBar());
        rootLayout.setCenter(buildTabPane());
        rootLayout.setBottom(buildStatusBar());

        Scene scene = new Scene(rootLayout, 900, 650);
        applyStylesheet(scene);
        String windowTitle = "Immutable Credentials Blockchain";
        if (nodeConfig.getDisplayName() != null && !nodeConfig.getDisplayName().trim().isEmpty()) {
            windowTitle += " — " + nodeConfig.getDisplayName();
        }
        primaryStage.setTitle(windowTitle);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            onExit();
        });
        this.primaryStage.show();

        refreshStatusBar();
        syncScheduler = Executors.newSingleThreadScheduledExecutor();
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                List<Validator> fresh = authService.loadValidators();
                List<Institution> freshInstitutions = authService.getAllInstitutions();
                nodeService.syncValidators(fresh);
                nodeService.syncInstitutions(freshInstitutions);
                Platform.runLater(() -> {
                    refreshStatusBar();
                    if (blockchainPanel != null)
                        blockchainPanel.onRefresh();
                    if (networkPanel != null)
                        networkPanel.onRefresh();
                });
            } catch (Exception e) {
                Logger.warn("Validator sync failed: " + e.getMessage());
            }
        }, SYNC_INTERVAL_MS, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);
        Logger.log("Sync scheduler started (interval: " + SYNC_INTERVAL_MS + "ms)");

    }

    /**
     * Initialise all service instances that act as the middleware layer
     * between the UI and the backend Node.
     * All configuration is loaded from the cloud database via AuthService.
     * The only local file access is reading or generating the private key
     * for validator nodes in the keys/ directory.
     *
     * @param dbConfig the node configuration returned by the cloud database login
     * @throws RuntimeException if configuration cannot be loaded or the node cannot
     *                          be constructed
     */
    private void initServices(NodeConfig dbConfig) throws RuntimeException {
        try {
            String nodeType = dbConfig.getNodeType().toLowerCase().replace('_', '-');
            int port = dbConfig.getPort();
            String dataDir = dbConfig.getDataDir();
            String storageFile = dataDir + "/blockchain.jsonl";
            String address = "localhost";
            String runtimeNodeId = String.valueOf(dbConfig.getNodeUserId());

            // Load the shared validator list from the cloud database.
            List<Validator> validators = ConfigLoader.loadValidatorList(authService);
            List<Institution> institutions = ConfigLoader.loadInstitutionList(authService);
            CredentialValidator credentialValidator = new CredentialValidator();
            credentialValidator.addInstitutions(institutions);
            Node node;
            if ("validator".equals(nodeType)) {
                String validatorId = dbConfig.getValidatorId();
                if (validatorId == null || validatorId.trim().isEmpty()) {
                    throw new RuntimeException("validator_id is not set for this account in the database.");
                }
                // Loads or generates key pair; public key synced to DB if new
                Validator localValidator = ConfigLoader.loadLocalValidator(
                        validatorId, dbConfig.getInstitution(), validators, authService);
                localValidator.activate();
                ProofOfAuthority poa = new ProofOfAuthority(validators);
                node = new Node(runtimeNodeId, address, port, localValidator, poa, storageFile, credentialValidator);

            } else {
                if (validators.isEmpty()) {
                    throw new RuntimeException(
                            "No active validators found in the database. "
                                    + "At least one validator must register their public key first.");
                }
                ProofOfAuthority poa = new ProofOfAuthority(validators);
                if ("university".equals(nodeType)) {
                    ConfigLoader.loadLocalUniversityKey(runtimeNodeId, dbConfig.getInstitution(), institutions, authService);

                    node = new Node(runtimeNodeId, address, port, poa, storageFile, true);
                } else {
                    node = new Node(runtimeNodeId, address, port, poa, storageFile);
                }
            }

            // Load all network settings from the database.
            AuthService.NetworkSettings net = ConfigLoader.loadNetworkSettings(authService);
            P2PNetwork network = new P2PNetwork(node, node.getPort(), net.maxConnections,
                    (int) net.connectTimeout, net.discoveryInterval, net.syncInterval);
            node.setNetwork(network);

            nodeService = new NodeService(node);
            credentialService = new CredentialService(node);
            blockchainService = new BlockchainService(node);
            networkService = new NetworkService(node);

            // Create AdminService for institution nodes (VALIDATOR and UNIVERSITY).
            // READ_ONLY nodes do not get an admin service or panel.
            if (node.isUniversity()) {
                adminService = new AdminService(authService, dbConfig);
            }

        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to initialise node: " + e.getMessage(), e);
        }
    }

    /**
     * Construct and return the application {@link MenuBar}.
     *
     * <p>
     * Menus included:
     * </p>
     * <ul>
     * <li><b>File</b> – Exit</li>
     * <li><b>Node</b> – Start Node, Stop Node, separator, Connect to Peer</li>
     * <li><b>Help</b> – About</li>
     * </ul>
     *
     * @return a fully wired {@link MenuBar} ready to be placed in the root layout
     */
    private MenuBar buildMenuBar() {
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> onExit());
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().add(exitItem);

        MenuItem startItem = new MenuItem("Start Node");
        startItem.setOnAction(e -> onStartNode());

        MenuItem stopItem = new MenuItem("Stop Node");
        stopItem.setOnAction(e -> onStopNode());

        MenuItem connectItem = new MenuItem("Connect to Peer…");
        connectItem.setOnAction(e -> onConnectToPeer());

        Menu nodeMenu = new Menu("Node");
        nodeMenu.getItems().addAll(startItem, stopItem, new SeparatorMenuItem(), connectItem);

        // ----- Help menu -----
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> onAbout());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(aboutItem);

        MenuBar menuBar = new MenuBar(fileMenu, nodeMenu, helpMenu);
        menuBar.setUseSystemMenuBar(false);
        return menuBar;
    }

    /**
     * Construct and return the TabPane containing all panels.
     * Includes the Issue Credential, Verify Credential, Blockchain Viewer,
     * and Network Status tabs for all nodes. An additional Admin Panel tab
     * is added for institution nodes (VALIDATOR and UNIVERSITY) and is hidden
     * for READ_ONLY nodes.
     *
     * @return a configured TabPane with all panels attached
     */
    private TabPane buildTabPane() {
        issuePanel = new IssueCredentialPanel(credentialService, nodeService, authService, institutions);
        verifyPanel = new VerifyCredentialPanel(credentialService);
        blockchainPanel = new BlockchainViewerPanel(blockchainService);
        networkPanel = new NetworkStatusPanel(networkService, nodeService, getDisplayNodeId());

        Tab issueTab = new Tab("Issue Credential", issuePanel);
        Tab verifyTab = new Tab("Verify Credential", verifyPanel);
        Tab blockchainTab = new Tab("Blockchain Viewer", blockchainPanel);
        Tab networkTab = new Tab("Network Status", networkPanel);

        issueTab.setClosable(false);
        verifyTab.setClosable(false);
        blockchainTab.setClosable(false);
        networkTab.setClosable(false);

        tabPane = new TabPane(issueTab, verifyTab, blockchainTab, networkTab);

        // Admin tab — only for institution nodes
        if (adminService != null) {
            adminPanel = new AdminPanel(adminService);
            Tab adminTab = new Tab("Admin Panel", adminPanel);
            adminTab.setClosable(false);
            tabPane.getTabs().add(adminTab);
        }

        updateIssueTabVisibility();
        return tabPane;
    }

    /**
     * Construct and return the bottom status bar.
     *
     * <p>
     * Status bar items (left to right):
     * </p>
     * <ul>
     * <li>Node ID and type (Validator / Read-Only)</li>
     * <li>Current blockchain height (number of blocks)</li>
     * <li>Number of connected peers</li>
     * </ul>
     *
     * @return an {@link HBox} configured as the status bar
     */
    private HBox buildStatusBar() {
        statusNodeLabel = new Label("Node: -");
        statusBlockLabel = new Label("Blocks: 0");
        statusPeerLabel = new Label("Peers: 0");

        statusBar = new HBox(20, statusNodeLabel, statusBlockLabel, statusPeerLabel);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    /**
     * Refresh all status bar labels with the latest values from the service layer.
     * Should be called periodically via a JavaFX {@code Timeline} or after any
     * state-changing operation (node start/stop, sync, new block, etc.).
     */
    private void refreshStatusBar() {
        if (nodeService == null)
            return;
        String nodeId = nodeService.isRunning() ? getDisplayNodeId() : "-";
        String nodeType = nodeService.isValidator() ? "Validator"
                : (nodeService.isUniversity() ? "University" : "Read-Only");
        statusNodeLabel.setText("Node: " + nodeId + " (" + nodeType + ")");
        statusBlockLabel.setText("Blocks: " + (blockchainService != null ? blockchainService.getChainHeight() : 0));
        statusPeerLabel.setText("Peers: " + (networkService != null ? networkService.getPeerCount() : 0));
    }

    /**
     * Handler for <em>Node → Start Node</em> menu item.
     * Delegates to {@link NodeService#startNode()} and updates the UI state.
     * Shows an error alert on failure.
     */
    private void onStartNode() {
        try {
            nodeService.startNode();
            markNodeOnlineInDatabase();
            tryBootstrapNetwork();
            updateIssueTabVisibility();
            refreshStatusBar();
        } catch (Exception e) {
            showError("Start Node Failed", e.getMessage());
        }
    }

    /**
     * Handler for <em>Node → Stop Node</em> menu item.
     * Delegates to {@link NodeService#stopNode()} and updates the UI state.
     * Shows an error alert on failure.
     */
    private void onStopNode() {
        try {
            nodeService.stopNode();
            markNodeOfflineInDatabase();
            updateIssueTabVisibility();
            refreshStatusBar();
        } catch (Exception e) {
            showError("Stop Node Failed", e.getMessage());
        }
    }

    /**
     * Handler for <em>Node → Connect to Peer</em> menu item.
     * Opens a small input dialog asking for host and port, then delegates to
     * {@link NetworkService#connectToPeer(String, int)}.
     */
    private void onConnectToPeer() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Connect to Peer");
        dialog.setHeaderText("Enter the host and port of the peer to connect to.");

        TextField hostField = new TextField();
        hostField.setPromptText("e.g. 192.168.1.10");
        TextField portField = new TextField();
        portField.setPromptText("e.g. 6001");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                networkService.connectToPeer(hostField.getText().trim(), port);
                refreshStatusBar();
            } catch (NumberFormatException ex) {
                showError("Invalid Port", "Port must be a number between 1024 and 65535.");
            } catch (Exception ex) {
                showError("Connect Failed", ex.getMessage());
            }
        }
    }

    /**
     * Handler for <em>Help → About</em> menu item.
     * Displays a modal dialog with project name, version, and licence info.
     */
    private void onAbout() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Immutable Credentials Blockchain");
        alert.setContentText(
                "Version: 1.0.0\n" +
                        "A Proof-of-Authority blockchain for issuing and verifying academic credentials.\n\n");
        alert.showAndWait();
    }

    /**
     * Handler for <em>File → Exit</em> menu item.
     * Attempts a graceful node shutdown via {@link NodeService#stopNode()}
     * before closing the application stage.
     */
    private void onExit() {
        try {
            if (nodeService != null && nodeService.isRunning()) {
                nodeService.stopNode();
                markNodeOfflineInDatabase();
            }
            if (syncScheduler != null) {
                syncScheduler.shutdownNow();
            }
        } catch (Exception e) {
            // Best-effort shutdown — log but do not block exit
            e.printStackTrace();
        } finally {
            Platform.exit();
        }
    }

    /**
     * Show or hide the <em>Issue Credential</em> tab based on whether the
     * running node is a validator.
     * Called after node start/stop events and on initial load.
     */
    private void updateIssueTabVisibility() {
        if (tabPane == null || issuePanel == null)
            return;
        boolean isValidatorOrUniversity = nodeService.isRunning()
                && (nodeService.isValidator() || nodeService.isUniversity());
        Tab issueTab = tabPane.getTabs().get(0);
        issueTab.setDisable(!isValidatorOrUniversity);
    }

    // ===== Helpers =====

    /**
     * Show a blocking error alert before the main window is displayed.
     * Used when the database configuration is missing or invalid at startup.
     *
     * @param message the error text to display
     */
    private void showStartupError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Startup Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        Platform.exit();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "An unexpected error occurred.");
        alert.showAndWait();
    }

    private void applyStylesheet(Scene scene) {
        URL stylesheet = MainWindow.class.getResource("/com/immutable/credentials/gui/css/main.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    /**
     * Attempt startup bootstrap by connecting to one active validator endpoint.
     * This is best-effort and must not block UI startup on failure.
     */
    private void tryBootstrapNetwork() {
        if (authService == null || networkService == null) {
            return;
        }
        try {
            boolean connected = networkService.bootstrap(authService.loadActiveValidatorEndpoints());
            if (!connected) {
                Logger.warn("Startup bootstrap did not connect to any validator endpoint.");
            }
        } catch (Exception e) {
            Logger.warn("Startup bootstrap failed: " + e.getMessage());
        }
    }

    /**
     * Best-effort DB presence update when this node starts.
     */
    private void markNodeOnlineInDatabase() {
        if (authService == null || nodeConfig == null || nodeService == null) {
            return;
        }
        try {
            authService.markNodeOnline(nodeConfig.getNodeUserId(), nodeService.getNodeAddress(),
                    nodeService.getNodePort());
        } catch (Exception e) {
            Logger.warn("Failed to mark node online in database: " + e.getMessage());
        }
    }

    /**
     * Best-effort DB presence update when this node stops.
     */
    private void markNodeOfflineInDatabase() {
        if (authService == null || nodeConfig == null) {
            return;
        }
        try {
            authService.markNodeOffline(nodeConfig.getNodeUserId());
        } catch (Exception e) {
            Logger.warn("Failed to mark node offline in database: " + e.getMessage());
        }
    }

    /**
     * Returns a stable UI display ID for this node (DB row id), independent of
     * runtime network identity used by consensus/P2P internals.
     */
    private String getDisplayNodeId() {
        if (nodeConfig == null) {
            return "-";
        }
        return String.valueOf(nodeConfig.getNodeUserId());
    }
}
