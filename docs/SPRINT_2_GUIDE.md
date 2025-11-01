# Development Guide

## Sprint 2: Cryptography & PoA (v0.2.0)

### Objectives
- Implement cryptographic signing and verification
- Set up Proof-of-Authority consensus
- Create validator management

### Tasks
1. **CryptoUtils.java** (complete)
   - Generate RSA/ECDSA key pairs
   - Implement digital signature creation
   - Implement signature verification
   - Add key serialization/deserialization

2. **Validator.java**
   - Create validator data structure
   - Store validator ID, keys, institution
   - Implement key management

3. **ProofOfAuthority.java**
   - Load hardcoded validator list
   - Implement validator authorization check
   - Add block signature validation
   - Enforce consensus rules

4. **Block.java** (update)
   - Add signature field to block
   - Implement signing during block creation
   - Add signature verification method

### Testing
- Test key generation
- Test signature creation/verification
- Test validator authorization
- Test unauthorized validator rejection

### Deliverables
- Working cryptographic utilities
- PoA consensus implementation
- Signed blocks with validation
