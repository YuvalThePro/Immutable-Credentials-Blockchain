# Development Guide

## Sprint 1: Core Data Structures (v0.1.0)

### Objectives
- Create the foundational data models
- Implement basic blockchain structure
- Set up hash-linking mechanism

### Tasks
1. **Credential.java**
   - Create fields for student information
   - Implement getters/setters
   - Add toString() for display

2. **BlockHeader.java**
   - Implement header fields (index, timestamp, hashes, validator info)
   - Create constructor
   - Add serialization support

3. **Block.java**
   - Combine header and credential payload
   - Implement block creation logic
   - Add validation methods

4. **Blockchain.java**
   - Initialize with genesis block
   - Implement addBlock() method
   - Create chain validation logic
   - Implement getBlock() and getLatestBlock()

5. **CryptoUtils.java** (basic)
   - Implement SHA-256 hash calculation
   - Create hash function for blocks

### Testing
- Unit tests for each model
- Test chain integrity with multiple blocks
- Verify hash linking works correctly

### Deliverables
- Working data models
- Basic blockchain with genesis block
- Unit tests passing
