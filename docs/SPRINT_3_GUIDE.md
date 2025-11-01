# Development Guide

## Sprint 3: Storage & Persistence (v0.3.0)

### Objectives
- Implement persistent storage for blockchain
- Create indexing for fast lookups
- Add backup functionality

### Tasks
1. **JsonSerializer.java**
   - Implement JSON serialization for blocks
   - Implement JSON deserialization
   - Handle entire chain serialization

2. **BlockchainStorage.java**
   - Implement saveChain() to write to disk
   - Implement loadChain() to read from disk
   - Add backup creation functionality
   - Handle storage errors gracefully

3. **CredentialIndex.java**
   - Create in-memory index structures
   - Index by student ID
   - Index by credential ID (optional)
   - Implement index rebuild from chain

4. **ConfigLoader.java**
   - Load node configuration
   - Load blockchain settings
   - Load validator list

### Testing
- Test save/load chain
- Test index accuracy
- Test persistence across restarts
- Test backup creation

### Deliverables
- Persistent blockchain storage
- Working index system
- Configuration management
