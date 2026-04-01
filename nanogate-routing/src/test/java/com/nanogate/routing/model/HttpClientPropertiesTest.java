package com.nanogate.routing.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientPropertiesTest {

    @Test
    void testGetAndSetProperties() {
        HttpClientProperties props = new HttpClientProperties();

        Duration connectTimeout = Duration.ofSeconds(5);
        props.setConnectTimeout(connectTimeout);
        assertEquals(connectTimeout, props.getConnectTimeout());

        Duration responseTimeout = Duration.ofSeconds(10);
        props.setResponseTimeout(responseTimeout);
        assertEquals(responseTimeout, props.getResponseTimeout());
    }

    @Test
    void testMerge_WithNullOther_ShouldReturnSelf() {
        HttpClientProperties baseProps = new HttpClientProperties();
        baseProps.setConnectTimeout(Duration.ofSeconds(5));

        HttpClientProperties mergedProps = baseProps.merge(null);

        assertSame(baseProps, mergedProps);
    }

    @Test
    void testMerge_WithNonNullOther_ShouldOverrideExistingValues() {
        HttpClientProperties baseProps = new HttpClientProperties();
        baseProps.setConnectTimeout(Duration.ofSeconds(5));
        baseProps.setResponseTimeout(Duration.ofSeconds(10));

        HttpClientProperties otherProps = new HttpClientProperties();
        otherProps.setConnectTimeout(Duration.ofSeconds(20)); // Override
        // responseTimeout is left null in otherProps, should not override

        HttpClientProperties mergedProps = baseProps.merge(otherProps);

        assertNotSame(baseProps, mergedProps);
        assertEquals(Duration.ofSeconds(20), mergedProps.getConnectTimeout());
        assertEquals(Duration.ofSeconds(10), mergedProps.getResponseTimeout());
    }

    @Test
    void testMerge_WithBothEmpty_ShouldReturnNewEmptyInstance() {
        HttpClientProperties baseProps = new HttpClientProperties();
        HttpClientProperties otherProps = new HttpClientProperties();

        HttpClientProperties mergedProps = baseProps.merge(otherProps);

        assertNull(mergedProps.getConnectTimeout());
        assertNull(mergedProps.getResponseTimeout());
    }

    @Test
    void testEqualsAndHashCode() {
        HttpClientProperties props1 = new HttpClientProperties();
        props1.setConnectTimeout(Duration.ofSeconds(5));
        props1.setResponseTimeout(Duration.ofSeconds(10));

        HttpClientProperties props2 = new HttpClientProperties();
        props2.setConnectTimeout(Duration.ofSeconds(5));
        props2.setResponseTimeout(Duration.ofSeconds(10));

        HttpClientProperties props3 = new HttpClientProperties();
        props3.setConnectTimeout(Duration.ofSeconds(20)); // Different value

        assertEquals(props1, props2);
        assertEquals(props1.hashCode(), props2.hashCode());
        
        assertNotEquals(props1, props3);
        assertNotEquals(props1.hashCode(), props3.hashCode());
        
        assertNotEquals(props1, null);
        assertNotEquals(props1, new Object());
    }
}
