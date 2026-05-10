package com.nanogate.security.service;

import com.nanogate.security.model.GlobalSecuritySettings;

/**
 * Interface to provide global security configuration.
 */
public interface GlobalSecurityProvider {
    GlobalSecuritySettings getSettings();
}
