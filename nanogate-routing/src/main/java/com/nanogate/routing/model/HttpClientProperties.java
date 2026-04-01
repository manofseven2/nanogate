package com.nanogate.routing.model;

import java.time.Duration;
import java.util.Objects;

/**
 * A model to hold configurable properties for the backend HttpClient.
 * An instance of this can exist at the global, backend-set, and route level.
 *
 * This class correctly implements equals() and hashCode() to be used as a key in a cache.
 */
public class HttpClientProperties {

    private Duration connectTimeout;
    private Duration responseTimeout;

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    /**
     * Merges another properties object into this one.
     * Properties from the 'other' object will override properties of this object if they are not null.
     *
     * @param other The properties to merge in.
     * @return A new, merged HttpClientProperties instance.
     */
    public HttpClientProperties merge(HttpClientProperties other) {
        if (other == null) {
            return this;
        }
        HttpClientProperties merged = new HttpClientProperties();
        merged.setConnectTimeout(other.getConnectTimeout() != null ? other.getConnectTimeout() : this.getConnectTimeout());
        merged.setResponseTimeout(other.getResponseTimeout() != null ? other.getResponseTimeout() : this.getResponseTimeout());
        return merged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpClientProperties that = (HttpClientProperties) o;
        return Objects.equals(connectTimeout, that.connectTimeout) &&
               Objects.equals(responseTimeout, that.responseTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectTimeout, responseTimeout);
    }
}