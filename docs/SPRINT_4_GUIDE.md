# Development Guide

## Sprint 4: P2P Network (v0.4.0)

### Objectives
- Implement peer-to-peer networking
- Enable block broadcasting
- Implement chain synchronization

### Tasks
1. **NetworkMessage.java**
   - Define message types (NEW_BLOCK, REQUEST_CHAIN, etc.)
   - Implement message serialization
   - Add message validation

2. **Peer.java**
   - Create peer data structure
   - Store connection information
   - Track peer status

3. **P2PNetwork.java**
   - Implement socket-based communication
   - Create peer connection management
   - Implement broadcastBlock()
   - Implement receiveBlock()
   - Add chain synchronization logic
   - Handle peer discovery

4. **Node.java**
   - Integrate P2P network
   - Handle incoming blocks
   - Coordinate blockchain updates
   - Manage node state

### Testing
- Test peer connections
- Test block broadcasting
- Test chain sync between 2+ nodes
- Test network resilience

### Deliverables
- Working P2P network
- Block propagation system
- Chain synchronization
