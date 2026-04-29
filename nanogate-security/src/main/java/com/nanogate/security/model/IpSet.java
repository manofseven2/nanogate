package com.nanogate.security.model;

import java.util.List;

public class IpSet {
    private String name;
    private IpSetType type;
    private List<String> ips;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IpSetType getType() {
        return type;
    }

    public void setType(IpSetType type) {
        this.type = type;
    }

    public List<String> getIps() {
        return ips;
    }

    public void setIps(List<String> ips) {
        this.ips = ips;
    }
}
