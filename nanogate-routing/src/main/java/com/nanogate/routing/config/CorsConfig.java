package com.nanogate.routing.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;

/**
 * Registers the CorsFilter with a high precedence, ensuring it runs
 * early in the filter chain, right after RouteResolutionFilter.
 */
@Configuration
public class CorsConfig {

    private final NanoGateCorsConfigurationSource corsConfigurationSource;

    public CorsConfig(NanoGateCorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        
        FilterRegistrationBean<CorsFilter> registrationBean = new FilterRegistrationBean<>(corsFilter);
        
        // Order 1 is RouteResolutionFilter. We want to run immediately after it.
        registrationBean.setOrder(5); 
        
        return registrationBean;
    }
}
