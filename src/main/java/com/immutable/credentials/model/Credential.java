package com.immutable.credentials.model;

import java.util.Date;
import java.util.Objects;

/**
 * Represents an academic credential stored in the blockchain.
 * Contains student information, degree details, and institutional data.
 */
public class Credential {

    private final String studentName;
    private final Date dateAwarded;
    private final String degree;
    private final String institution;
    private final String studentId;
    private final String credentialId;

    private final String signature;

    /**
     * Create a new credential with complete student and degree information.
     * 
     * @param studentName  the name of the student
     * @param dateAwarded  the date the credential was awarded
     * @param degree       the degree or certification earned
     * @param institution  the issuing university or institution
     * @param studentId    the unique identifier for the student
     * @param credentialId the unique credential identifier for quick lookup
     * @throws IllegalArgumentException if any required field is null or empty
     */
    public Credential(String studentName, Date dateAwarded,
            String degree, String institution,
            String studentId, String credentialId) {
        if (studentName == null || studentName.trim().isEmpty()) {
            throw new IllegalArgumentException("Student name is required");
        }
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (dateAwarded == null) {
            throw new IllegalArgumentException("Date awarded is required");
        }
        if (degree == null || degree.trim().isEmpty()) {
            throw new IllegalArgumentException("Degree type is required");
        }
        if (institution == null || institution.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution is required");
        }

        this.studentName = studentName;
        this.dateAwarded = dateAwarded;
        this.degree = degree;
        this.institution = institution;
        this.studentId = studentId;
        this.credentialId = credentialId;
        this.signature = null;
    }

    /**
     * Create a new credential by copying an existing one.
     * 
     * @param other the credential object to copy
     * @throws IllegalArgumentException if other is null
     */
    public Credential(Credential other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot copy from null Credential");
        }
        this.studentName = other.studentName;
        this.dateAwarded = other.dateAwarded != null ? new Date(other.dateAwarded.getTime()) : null;
        this.degree = other.degree;
        this.institution = other.institution;
        this.studentId = other.studentId;
        this.credentialId = other.credentialId;
        this.signature = other.signature;
    }

    public Credential(Credential unsignedCredential, String signature) {
        if (unsignedCredential == null) {
            throw new IllegalArgumentException("Cannot copy from null Credential");
        }
        if (signature == null || signature.trim().isEmpty()) {
            throw new IllegalArgumentException("Signature cannot be null or empty");
        }

        this.studentName = unsignedCredential.studentName;
        this.dateAwarded = unsignedCredential.getDateAwarded();
        this.degree = unsignedCredential.degree;
        this.institution = unsignedCredential.institution;
        this.studentId = unsignedCredential.studentId;
        this.credentialId = unsignedCredential.credentialId;

        this.signature = signature;
    }

    /**
     * Get the student's name.
     * 
     * @return the student name
     */
    public String getStudentName() {
        return studentName;
    }

    /**
     * Get the date the credential was awarded.
     * Returns a defensive copy to preserve immutability.
     * 
     * @return a copy of the date awarded, or null if not set
     */
    public Date getDateAwarded() {
        return dateAwarded != null ? new Date(dateAwarded.getTime()) : null;
    }

    /**
     * Get the degree or certification earned.
     * 
     * @return the degree
     */
    public String getDegree() {
        return degree;
    }

    /**
     * Get the issuing institution.
     * 
     * @return the institution
     */
    public String getInstitution() {
        return institution;
    }

    /**
     * Get the unique student identifier.
     * 
     * @return the student ID
     */
    public String getStudentId() {
        return studentId;
    }

    /**
     * Get the unique credential identifier.
     * 
     * @return the credential ID
     */
    public String getCredentialId() {
        return credentialId;
    }

    public String getSignature() {
        return signature;
    }

    public String calculateDataForSigning() {
        long timestamp = (this.dateAwarded != null) ? this.dateAwarded.getTime() : 0L;

        return this.studentName + "|" +
                this.studentId + "|" +
                this.degree + "|" +
                this.institution + "|" +
                timestamp + "|" +
                this.credentialId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentName, dateAwarded, degree, institution, studentId, credentialId, signature);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Credential other = (Credential) obj;
        return Objects.equals(studentName, other.studentName) &&
                Objects.equals(dateAwarded, other.dateAwarded) &&
                Objects.equals(degree, other.degree) &&
                Objects.equals(institution, other.institution) &&
                Objects.equals(studentId, other.studentId) &&
                Objects.equals(credentialId, other.credentialId) &&
                Objects.equals(signature, other.signature);
    }

    @Override
    public String toString() {
        return "Credential [studentName=" + studentName +
                ", dateAwarded=" + dateAwarded +
                ", degree=" + degree +
                ", institution=" + institution +
                ", studentId=" + studentId +
                ", credentialId=" + credentialId +
                ", signature=" + (signature != null ? "SIGNED" : "UNSIGNED") + "]";
    }
}