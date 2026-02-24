package com.immutable.credentials.service;

import com.immutable.credentials.core.Node;
import com.immutable.credentials.model.Credential;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class CredentialService {

    /** The backend node that owns the blockchain and credential index. */
    private final Node node;

    /**
     * Construct a new {@code CredentialService} bound to the given node.
     *
     * @param node the running (or stopped) {@link Node} instance;
     *             must not be {@code null}
     * @throws IllegalArgumentException if {@code node} is {@code null}
     */
    public CredentialService(Node node) {
        this.node = node;
    }

    /**
     * Submit a new academic credential to the network for inclusion in a future
     * block.
     * 
     * @param studentName  the full name of the student; must not be blank
     * @param studentId    the unique student identifier; must not be blank
     * @param degree       the degree or certification earned; must not be blank
     * @param institution  the awarding institution; must not be blank
     * @param dateAwarded  the date the credential was awarded; must not be
     *                     {@code null}
     * @param credentialId a caller-supplied unique credential ID; must not be blank
     * @throws IllegalArgumentException if any parameter is null, blank, or invalid
     * @throws IllegalStateException    if the node is not running or is not a
     *                                  university (or validator) node
     */
    public void issueCredential(String studentName, String studentId,
            String degree, String institution,
            LocalDate dateAwarded, String credentialId) {
        if (!node.isRunning() || !isUniversityNode())
            throw new IllegalStateException("Node must be running and must be a university or validator node.");
        Date date = Date.from(dateAwarded.atStartOfDay(ZoneId.systemDefault()).toInstant());
        node.submitCredential(new Credential(studentName, date, degree, institution, studentId, credentialId));
    }

    /**
     * Look up a single credential by its unique credential ID.
     *
     * @param credentialId the credential ID to look up; must not be blank
     * @return the matching {@link Credential}, or {@code null} if not found
     * @throws IllegalArgumentException if {@code credentialId} is null or blank
     */
    public Credential getCredentialById(String credentialId) {
        return node.getCredentialIndex().getCredentialById(credentialId);
    }

    /**
     * Search for all credentials belonging to a specific student.
     * 
     * @param studentId the student identifier to search for; must not be blank
     * @return a non-null (possibly empty) list of matching {@link Credential}
     *         objects
     * @throws IllegalArgumentException if {@code studentId} is null or blank
     */
    public List<Credential> searchByStudentId(String studentId) {
        return node.getCredentialIndex().getCredentialsByStudentId(studentId);
    }

    /**
     * Check whether a credential with the given ID currently exists on the
     * local blockchain (i.e. has been finalised in a block).
     *
     * @param credentialId the credential ID to check; must not be blank
     * @return {@code true} if the credential exists on-chain; {@code false}
     *         otherwise
     * @throws IllegalArgumentException if {@code credentialId} is null or blank
     */
    public boolean isCredentialOnChain(String credentialId) {
        return node.getCredentialIndex().hasCredential(credentialId);
    }

    /**
     * Report whether this node is a <em>consensus validator</em> — one of the
     * major, globally-accredited institutions (e.g. MIT, Oxford) that are
     * authorised to propose and sign blocks in addition to issuing credentials. *
     * 
     * @return {@code true} if this node has an active validator identity;
     *         {@code false} for plain university nodes and read-only nodes
     */
    public boolean isValidatorNode() {
        return node.isValidator();
    }

    /**
     * Report whether this node belongs to an accredited institution
     * (university node <em>or</em> validator node) and is therefore
     * authorised to submit credentials.
     *
     * @return {@code true} if this node may submit credentials (university or
     *         validator); {@code false} for pure read-only nodes
     */
    public boolean isUniversityNode() {
        return node.isUniversity();
    }
}
