package com.nanogate.routing.model;

import java.net.URI;
import java.util.List;

/**
 * Represents a routing rule defining how incoming requests should be forwarded.
 */
public class Route {
    private String id;
    private String path;
    private List<URI> targetUris;
    private String loadBalancer;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<URI> getTargetUris() {
        return targetUris;
    }

    public void setTargetUris(List<URI> targetUris) {
        this.targetUris = targetUris;
    }

    public String getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(String loadBalancer) {
        this.loadBalancer = loadBalancer;
    }
}