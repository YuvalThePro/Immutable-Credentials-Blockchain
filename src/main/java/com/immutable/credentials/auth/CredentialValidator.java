package com.immutable.credentials.auth;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import com.immutable.credentials.crypto.CryptoUtils;
import com.immutable.credentials.model.Credential;
import com.immutable.credentials.model.Institution;
import com.immutable.credentials.util.Logger;

public class CredentialValidator {

    private final Map<String, Institution> trustedInstitutions = new HashMap<>();

    /**
     * Adds a single institution to the trusted cache.
     *
     * @param inst the institution to add; if null or has null name, it is ignored
     */
    public void addInstitution(Institution inst) {
        if (inst == null || inst.getName() == null) {
            return;
        }
        synchronized (trustedInstitutions) {
            trustedInstitutions.put(inst.getName(), inst);
        }
        Logger.log("Institution added to cache: " + inst.getName());
    }

    /**
     * Adds a list of institutions to the trusted cache.
     *
     * @param institutions the list of institutions to add; null is ignored
     */
    public void addInstitutions(List<Institution> institutions) {
        if (institutions == null)
            return;

        synchronized (trustedInstitutions) {
            for (Institution inst : institutions) {
                if (inst != null && inst.getName() != null) {
                    trustedInstitutions.put(inst.getName(), inst);
                }
            }
        }
        Logger.log("Batch loaded " + institutions.size() + " institutions into cache.");
    }

    /**
     * Validates a credential by checking if its issuing institution is trusted and
     * verifying its signature with the institution's public key.
     *
     * @param cred the credential to validate
     * @return true if the credential is valid and signed by a trusted institution; false otherwise
     */
    public boolean isCredentialValid(Credential cred) {
        Institution inst;
        synchronized (trustedInstitutions) {
            inst = trustedInstitutions.get(cred.getInstitution());
        }

        if (inst == null) {
            Logger.warn("Verification failed: Institution '" + cred.getInstitution() + "' not found in cache.");
            return false;
        }
        if (inst != null) {
            String currentKeyBase64 = CryptoUtils.keyToString(inst.getPublicKey());
            String shortKey = currentKeyBase64.substring(0, 10) + "..."
                    + currentKeyBase64.substring(currentKeyBase64.length() - 10);

            Logger.log("[CACHE-CHECK] Validating against: " + inst.getName() + " | Key in Cache: [" + shortKey + "]");
        }
        String dataToVerify = cred.calculateDataForSigning();
        Logger.log("[DEBUG-VERIFY] Data being verified: [" + dataToVerify + "]");
        Logger.log("[DEBUG-VERIFY] Using Public Key of: " + inst.getName());
        return CryptoUtils.verifySignature(
                cred.calculateDataForSigning(),
                cred.getSignature(),
                inst.getPublicKey());
    }

    /**
     * Replaces the current trusted institution cache with a new list.
     *
     * @param fresh the new list of institutions to trust; must not be null or empty
     * @throws IllegalArgumentException if fresh is null or empty
     */
    public synchronized void syncInstitutions(List<Institution> fresh) {
        if (fresh == null || fresh.isEmpty()) {
            throw new IllegalArgumentException("List of institutions cannot be null or empty.");
        }

        Map<String, Institution> nextCache = new HashMap<>();

        for (Institution incoming : fresh) {
            if (incoming != null && incoming.getName() != null) {
                nextCache.put(incoming.getName(), incoming);
            }
        }

        synchronized (trustedInstitutions) {
            trustedInstitutions.clear();
            trustedInstitutions.putAll(nextCache);
        }

        Logger.log("Identity Cache fully synchronized with " + fresh.size() + " institutions.");
    }

    /**
     * Clears all institutions from the trusted cache.
     */
    public void clearCache() {
        synchronized (trustedInstitutions) {
            trustedInstitutions.clear();

        }
    }
}