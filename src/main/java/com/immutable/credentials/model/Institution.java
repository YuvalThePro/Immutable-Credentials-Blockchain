package com.immutable.credentials.model;

import java.security.PublicKey;
import com.immutable.credentials.crypto.CryptoUtils;

public class Institution {
    private final String id;
    private final String name;
    private final PublicKey publicKey;

    public Institution(int idFromDb, String name, String publicKeyBase64) {
        this.id = String.valueOf(idFromDb);
        this.name = name;
        this.publicKey = CryptoUtils.publicKeyFromBase64(publicKeyBase64);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "Institution: " + name + " (ID: " + id + ")";
    }
}