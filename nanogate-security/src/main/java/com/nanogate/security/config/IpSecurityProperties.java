package com.nanogate.security.config;

import com.nanogate.security.model.IpSet;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "nanogate.security")
public class IpSecurityProperties {

    private List<IpSet> ipSets = new ArrayList<>();

    public List<IpSet> getIpSets() {
        return ipSets;
    }

    public void setIpSets(List<IpSet> ipSets) {
        this.ipSets = ipSets;
    }
}
