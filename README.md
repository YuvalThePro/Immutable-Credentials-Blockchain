# Immutable Academic Credentials Verification System

## Project Overview
A blockchain-based system for issuing and verifying academic credentials using Proof-of-Authority consensus.

## Architecture
- **Distributed Ledger**: Hash-linked blocks replicated across all nodes
- **Consensus**: Proof-of-Authority (PoA) - only authorized validators can create blocks
- **Network**: P2P overlay network for block propagation
- **GUI**: Java Swing interface for credential management

## Project Structure

### Core Components
```
src/main/java/com/immutable/credentials/
├── Main.java                    # Application entry point
├── core/
│   ├── Blockchain.java          # Blockchain data structure
│   └── Node.java                # Network node implementation
├── model/
│   ├── Block.java               # Block structure
│   ├── BlockHeader.java         # Block metadata
│   └── Credential.java          # Academic credential data
├── consensus/
│   ├── ProofOfAuthority.java    # PoA consensus mechanism
│   └── Validator.java           # Validator entity
├── crypto/
│   └── CryptoUtils.java         # Cryptographic utilities
├── network/
│   ├── P2PNetwork.java          # P2P network management
│   ├── Peer.java                # Peer representation
│   └── NetworkMessage.java      # Network messaging
├── storage/
│   ├── BlockchainStorage.java   # Persistent storage
│   └── CredentialIndex.java     # Fast credential lookup
├── gui/
│   ├── MainWindow.java          # Main GUI window
│   ├── IssueCredentialPanel.java    # Issue credential interface
│   ├── VerifyCredentialPanel.java   # Verify credential interface
│   ├── BlockchainViewerPanel.java   # Blockchain viewer
│   └── NetworkStatusPanel.java      # Network status display
└── util/
    ├── Logger.java              # Logging utility
    ├── JsonSerializer.java      # JSON serialization
    └── ConfigLoader.java        # Configuration loader
```

## Development Sprints

### Sprint 1 (v0.1.0): Core Data Structures
- Implement Block, BlockHeader, Credential models
- Implement basic Blockchain class with genesis block
- Implement hash calculation and chain linking

### Sprint 2 (v0.2.0): Cryptography & PoA
- Implement CryptoUtils (signing, verification)
- Implement Validator and ProofOfAuthority
- Key pair generation for validators

### Sprint 3 (v0.3.0): Storage & Persistence
- Implement BlockchainStorage (save/load chain)
- Implement CredentialIndex for fast lookups
- File-based persistence

### Sprint 4 (v0.4.0): P2P Network
- Implement P2PNetwork, Peer, NetworkMessage
- Block broadcasting and reception
- Chain synchronization between nodes

### Sprint 5 (v0.5.0): Basic GUI
- Implement MainWindow with Swing
- Implement IssueCredentialPanel
- Implement VerifyCredentialPanel

### Sprint 6 (v0.6.0): Advanced GUI
- Implement BlockchainViewerPanel
- Implement NetworkStatusPanel
- Polish UI/UX

### Sprint 7 (v0.7.0): Integration & Testing
- End-to-end testing
- Multi-node testing
- Bug fixes and optimization

### Sprint 8 (v1.0.0): Production Ready
- Documentation
- Deployment scripts
- Final testing and release

## Getting Started

### Prerequisites
- Java JDK 8 or higher
- No external build tools required (vanilla Java)

### Configuration
Edit configuration files in `config/` directory:
- `node.properties`: Node configuration
- `network.properties`: Network settings
- `validators.properties`: Authorized validators
- `blockchain.properties`: Blockchain settings

### Running a Node
```bash
# Compile (from project root)
javac -d bin -sourcepath src/main/java src/main/java/com/immutable/credentials/Main.java

# Run
java -cp bin com.immutable.credentials.Main
```

## Key Features
- **Immutable Credentials**: Once recorded, cannot be altered or deleted
- **Distributed Verification**: Any node can verify credentials
- **Authorized Issuance**: Only authorized validators (universities) can issue
- **Fast Lookup**: Indexed search by student ID or credential ID
- **User-Friendly**: Simple Swing GUI for all operations

## License
See LICENSE file for details.