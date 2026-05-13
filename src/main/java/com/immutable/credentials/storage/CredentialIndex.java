package com.immutable.credentials.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.model.Block;
import com.immutable.credentials.model.Credential;

/**
 * Maintains in-memory indexes for fast credential lookups without scanning the
 * entire blockchain.
 * Provides O(1) lookup time for credentials by student ID or credential ID
 * instead of O(n) chain traversal.
 * 
 * The index must be rebuilt from the blockchain after loading from disk or if
 * corrupted.
 */
public class CredentialIndex {

    private final Map<String, List<Credential>> studentIdIndex;
    private final Map<String, Credential> credentialIdIndex;

    /**
     * Create a new empty credential index.
     * Index structures are initialized but empty until populated via
     * addCredential() or rebuildIndex().
     */
    public CredentialIndex() {
        studentIdIndex = new HashMap<>();
        credentialIdIndex = new HashMap<>();
    }

    /**
     * Add a credential to the index structures.
     * Updates both the student ID index and credential ID index.
     * 
     * @param credential the credential to add to the index
     * @throws IllegalArgumentException if credential is null
     */
    public void addCredentials(ArrayList<Credential> credentials) throws IllegalArgumentException {
        if (credentials == null)
            throw new IllegalArgumentException("Credentials cant be null");

        for (Credential credential : credentials) {
            studentIdIndex.computeIfAbsent(credential.getStudentId(), key -> new ArrayList<>()).add(credential);
            credentialIdIndex.put(credential.getCredentialId(), credential);
        }
    }

    /**
     * Retrieve all credentials associated with a specific student ID.
     * Returns an empty list if no credentials found for the student.
     * 
     * @param studentId the student ID to search for
     * @return list of credentials for the student (empty list if none found)
     * @throws IllegalArgumentException if studentId is null or empty
     */
    public List<Credential> getCredentialsByStudentId(String studentId) throws IllegalArgumentException {
        if (studentId == null || studentId.isEmpty())
            throw new IllegalArgumentException("Student id can't be null or empty.");

        List<Credential> credentials = studentIdIndex.get(studentId);
        if (credentials == null)
            return new ArrayList<>();

        List<Credential> result = new ArrayList<>();
        for (Credential c : credentials) {
            if (c != null)
                result.add(c);
        }
        return result;
    }

    /**
     * Retrieve a specific credential by its unique credential ID.
     * 
     * @param credentialId the credential ID to search for
     * @return the credential if found, null otherwise
     * @throws IllegalArgumentException if credentialId is null or empty
     */
    public Credential getCredentialById(String credentialId) throws IllegalArgumentException {
        if (credentialId == null || credentialId.isEmpty())
            throw new IllegalArgumentException("Credential cant be null or empty");
        return credentialIdIndex.get(credentialId);
    }

    /**
     * Rebuild the entire index from scratch by scanning the blockchain.
     * This should be called after loading the blockchain from disk or if the index
     * becomes corrupted.
     * 
     * The process:
     * 1. Clear existing indexes
     * 2. Iterate through all blocks in the chain
     * 3. Extract credentials from each block
     * 4. Add each credential to the index structures
     * 
     * @param blockchain the blockchain to rebuild the index from
     * @throws IllegalArgumentException if blockchain is null
     */
    public void rebuildIndex(Blockchain blockchain) throws IllegalArgumentException {
        if (blockchain == null)
            throw new IllegalArgumentException("Blockchain cant be null.");

        // Clear existing indexes
        clear();
        // Scan all blocks except genesis (block 0) and build index
        for (int i = 1; i < blockchain.size(); i++) {
            Block block = blockchain.getBlock(i);
            if (block != null) {
                addCredentials(block.getCredentials());
            }
        }
    }

    /**
     * Clear all index structures.
     * Used internally before rebuilding the index.
     */
    public void clear() {
        studentIdIndex.clear();
        credentialIdIndex.clear();
    }

    /**
     * Get the total number of unique students in the index.
     * 
     * @return the count of unique student IDs
     */
    public int getStudentCount() {
        return studentIdIndex.keySet().size();
    }

    /**
     * Get the total number of credentials in the index.
     * 
     * @return the count of indexed credentials
     */
    public int getCredentialCount() {
        return credentialIdIndex.size();
    }

    /**
     * Check if a credential exists in the index.
     * 
     * @param credentialId the credential ID to check
     * @return true if the credential exists, false otherwise
     * @throws IllegalArgumentException if credentialId is null or empty
     */
    public boolean hasCredential(String credentialId) {
        if (credentialId == null || credentialId.isEmpty())
            throw new IllegalArgumentException("Credential id cant be null or empty");
        return credentialIdIndex.containsKey(credentialId);
    }
}
