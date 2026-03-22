package com.immutable.credentials.service;

import java.util.Objects;

/**
 * Immutable data object representing a validator network endpoint.
 *
 * <p>
 * Used by AuthService to return bootstrap connection candidates
 * (validator ID + host/address + port) from the database layer.
 * </p>
 */
public final class ValidatorEndpoint {

    private final String validatorId;
    private final String address;
    private final int port;

    /**
     * Construct a validator endpoint value object.
     *
     * @param validatorId the unique validator identifier
     * @param address     host name or IP address reachable by peers
     * @param port        TCP listening port
     */
    public ValidatorEndpoint(String validatorId, String address, int port) {
        this.validatorId = Objects.requireNonNull(validatorId, "validatorId must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.port = port;
    }

    /** Returns the unique validator identifier. */
    public String getValidatorId() {
        return validatorId;
    }

    /** Returns the host name or IP address. */
    public String getAddress() {
        return address;
    }

    /** Returns the TCP listening port. */
    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValidatorEndpoint)) {
            return false;
        }
        ValidatorEndpoint that = (ValidatorEndpoint) o;
        return port == that.port
                && validatorId.equals(that.validatorId)
                && address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validatorId, address, port);
    }

    @Override
    public String toString() {
        return "ValidatorEndpoint{" +
                "validatorId='" + validatorId + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                '}';
    }
}
