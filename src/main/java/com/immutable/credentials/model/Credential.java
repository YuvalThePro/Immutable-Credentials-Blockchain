package com.immutable.credentials.model;

import java.util.Date;
import java.util.Objects;

// Credential.java
// Represents an academic credential stored in the blockchain
// Fields:
// - studentName: Name of the student
// - degree: Degree or certification earned
// - dateAwarded: Date the credential was issued
// - institution: Issuing university/institution
// - studentId: Unique identifier for the student
// - credentialId: (Optional) Unique credential identifier for quick lookup



public class Credential
{

    /*
     *-------------------------------------------------------
     * Fields:
     * studentName,dateAwarded, degree,institution,studentId,credentialId
     */

    private final String studentName; 
    private final Date dateAwarded;
    private final String degree;
    private final String institution;
    private final String studentId;
    private final String credentialId;




    
    public Credential(String studentName, Date dateAwarded, 
    String degree, String institution, 
    String studentId, String credentialId)
    {
        if (studentName == null || studentName.trim().isEmpty()) {
            throw new IllegalArgumentException("Student name is required");
        }
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (dateAwarded == null) {
            throw new IllegalArgumentException("Date awarded is required");
        }
        if(degree == null || degree.trim().isEmpty() ){
            throw new IllegalArgumentException("Degree type is required");
        }
        if(institution== null || institution.trim().isEmpty() ){
            throw new IllegalArgumentException("Institution is required");
        }


        
        this.studentName = studentName;
        this.dateAwarded = dateAwarded;
        this.degree = degree;
        this.institution = institution;
        this.studentId = studentId;
        this.credentialId = credentialId;
    }

    
    /**
        * Copy constructor - Creates a new Credential instance from an existing one
        * 
        * @param other The Credential object to copy
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
        }


    /*
     * -------------------------------------------------------
     * Methods
     * Getters
     */
    
    public String getStudentName() {
        return studentName;
    }
    public Date getDateAwarded() {
        return dateAwarded;
    }
    public String getDegree() {
        return degree;
    }
    public String getInstitution() {
        return institution;
    }
    public String getStudentId() {
        return studentId;
    }
    public String getCredentialId() {
        return credentialId;
    }



    /*
     * -------------------------------------------------------
     * Methods
     * hashCode(), equal(),toString()
     */


    @Override
    public int hashCode() {
        return Objects.hash(studentName, dateAwarded, degree, institution, studentId, credentialId);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Credential other = (Credential) obj;
        return Objects.equals(studentName, other.studentName) &&
               Objects.equals(dateAwarded, other.dateAwarded) &&
               Objects.equals(degree, other.degree) &&
               Objects.equals(institution, other.institution) &&
               Objects.equals(studentId, other.studentId) &&
               Objects.equals(credentialId, other.credentialId);
    }


    @Override
    public String toString() {
        return "Credential [studentName=" + studentName + ", dateAwarded=" + dateAwarded + ", degree=" + degree
                + ", institution=" + institution + ", studentId=" + studentId + ", credentialId=" + credentialId + "]";
    }
    
}