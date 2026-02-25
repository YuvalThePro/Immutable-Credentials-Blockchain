package com.immutable.credentials.gui;

import java.io.IOException;
import java.util.Optional;

import com.immutable.credentials.service.BlockchainService;
import com.immutable.credentials.service.CredentialService;
import com.immutable.credentials.service.NetworkService;
import com.immutable.credentials.service.NodeService;

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

    /**
     * JavaFX application entry point.
     * Initialises service instances, builds all UI components, wires
     * event handlers, and shows the primary stage.
     *
     * @param primaryStage the primary {@link Stage} provided by the JavaFX runtime
     */
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initServices();

        rootLayout = new BorderPane();
        rootLayout.setTop(buildMenuBar());
        rootLayout.setCenter(buildTabPane());
        rootLayout.setBottom(buildStatusBar());

        Scene scene = new Scene(rootLayout, 900, 650);
        primaryStage.setTitle("Immutable Credentials Blockchain");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            onExit();
        });
        primaryStage.show();

        refreshStatusBar();
    }

    /**
     * Initialise all four service instances that act as the middleware layer
     * between the UI and the backend {@code Node}.
     * Must be called before {@link #buildTabPane()} or any panel is constructed.
     */
    private void initServices() throws RuntimeException {

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
     * Construct and return the {@link TabPane} containing all four panels.
     *
     * <p>
     * Tabs (in order):
     * </p>
     * <ol>
     * <li><b>Issue Credential</b> – visible only when the node is a validator</li>
     * <li><b>Verify Credential</b> – available to all node types</li>
     * <li><b>Blockchain Viewer</b> – available to all node types</li>
     * <li><b>Network Status</b> – available to all node types</li>
     * </ol>
     *
     * @return a configured {@link TabPane} with all panels attached
     */
    private TabPane buildTabPane() {
        issuePanel = new IssueCredentialPanel(credentialService, nodeService);
        verifyPanel = new VerifyCredentialPanel(credentialService);
        blockchainPanel = new BlockchainViewerPanel(blockchainService);
        networkPanel = new NetworkStatusPanel(networkService, nodeService);

        Tab issueTab = new Tab("Issue Credential", issuePanel);
        Tab verifyTab = new Tab("Verify Credential", verifyPanel);
        Tab blockchainTab = new Tab("Blockchain Viewer", blockchainPanel);
        Tab networkTab = new Tab("Network Status", networkPanel);

        issueTab.setClosable(false);
        verifyTab.setClosable(false);
        blockchainTab.setClosable(false);
        networkTab.setClosable(false);

        tabPane = new TabPane(issueTab, verifyTab, blockchainTab, networkTab);
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
        statusNodeLabel = new Label("Node: –");
        statusBlockLabel = new Label("Blocks: 0");
        statusPeerLabel = new Label("Peers: 0");

        statusBar = new HBox(20, statusNodeLabel, statusBlockLabel, statusPeerLabel);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
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
        String nodeId = nodeService.isRunning() ? nodeService.getNodeId() : "–";
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
                        "A Proof-of-Authority blockchain for issuing and verifying academic credentials.\n\n" +
                        "License: MIT");
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

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "An unexpected error occurred.");
        alert.showAndWait();
    }
}
