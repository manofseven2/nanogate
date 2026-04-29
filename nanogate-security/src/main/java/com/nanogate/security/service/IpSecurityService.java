package com.nanogate.security.service;

import com.nanogate.security.config.IpSecurityProperties;
import com.nanogate.security.model.IpSet;
import com.nanogate.security.model.IpSetType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IpSecurityService {

    private static final Logger log = LoggerFactory.getLogger(IpSecurityService.class);

    private final IpSecurityProperties properties;
    private final Map<String, CompiledIpSet> compiledIpSets = new HashMap<>();

    public IpSecurityService(IpSecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void compileIpSets() {
        if (properties.getIpSets() == null) {
            return;
        }

        for (IpSet ipSet : properties.getIpSets()) {
            if (ipSet.getName() == null || ipSet.getType() == null || ipSet.getIps() == null) {
                log.warn("Invalid IpSet configuration found, missing name, type, or ips. Skipping.");
                continue;
            }

            List<IpAddressMatcher> matchers = ipSet.getIps().stream()
                    .map(IpAddressMatcher::new)
                    .collect(Collectors.toList());

            compiledIpSets.put(ipSet.getName(), new CompiledIpSet(ipSet.getType(), matchers));
            log.info("Compiled IpSet '{}' of type {} with {} rules.", ipSet.getName(), ipSet.getType(), matchers.size());
        }
    }

    public boolean isAllowed(String ipSetName, String clientIp) {
        CompiledIpSet compiledSet = compiledIpSets.get(ipSetName);
        if (compiledSet == null) {
            log.error("Route refers to a non-existent IpSet: '{}'. Denying access by default.", ipSetName);
            return false;
        }

        boolean matches = false;
        for (IpAddressMatcher matcher : compiledSet.matchers()) {
            if (matcher.matches(clientIp)) {
                matches = true;
                break;
            }
        }

        if (compiledSet.type() == IpSetType.ALLOW) {
            return matches;
        } else { // BLOCK
            return !matches;
        }
    }

    private record CompiledIpSet(IpSetType type, List<IpAddressMatcher> matchers) {}
}
