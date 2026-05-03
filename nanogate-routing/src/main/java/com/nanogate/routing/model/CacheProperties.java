package com.nanogate.routing.model;

import java.time.Duration;
import java.util.List;

public class CacheProperties {

    private Boolean enabled;
    private Duration ttl;
    private List<String> varyByHeaders;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public List<String> getVaryByHeaders() {
        return varyByHeaders;
    }

    public void setVaryByHeaders(List<String> varyByHeaders) {
        this.varyByHeaders = varyByHeaders;
    }
}
