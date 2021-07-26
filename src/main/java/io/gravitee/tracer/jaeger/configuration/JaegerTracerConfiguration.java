/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.tracer.jaeger.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JaegerTracerConfiguration {

    @Value("${services.tracing.jaeger.ssl.enabled:false}")
    private boolean sslEnabled;

    /**
     * Jaeger ssl keystore type. (jks, pkcs12,)
     */
    @Value("${services.tracing.jaeger.ssl.keystore.type:#{null}}")
    private String keystoreType;

    /**
     * Jaeger ssl keystore path.
     */
    @Value("${services.tracing.jaeger.ssl.keystore.path:#{null}}")
    private String keystorePath;

    /**
     * Jaeger ssl keystore password.
     */
    @Value("${services.tracing.jaeger.ssl.keystore.password:#{null}}")
    private String keystorePassword;

    /**
     * Jaeger ssl pem certs paths
     */
    private List<String> keystorePemCerts;

    /**
     * Jaeger ssl pem keys paths
     */
    private List<String> keystorePemKeys;

    /**
     * Jaeger ssl truststore trustall.
     */
    @Value("${services.tracing.jaeger.ssl.trustall:false}")
    private boolean trustAll;

    /**
     * Jaeger ssl truststore hostname verifier.
     */
    @Value("${services.tracing.jaeger.ssl.verifyHostname:true}")
    private boolean hostnameVerifier;

    /**
     * Jaeger ssl truststore type.
     */
    @Value("${services.tracing.jaeger.ssl.truststore.type:#{null}}")
    private String truststoreType;

    /**
     * Jaeger ssl truststore path.
     */
    @Value("${services.tracing.jaeger.ssl.truststore.path:#{null}}")
    private String truststorePath;

    /**
     * Jaeger ssl truststore password.
     */
    @Value("${services.tracing.jaeger.ssl.truststore.password:#{null}}")
    private String truststorePassword;

    @Value("${services.tracing.jaeger.host:localhost}")
    private String host;

    @Value("${services.tracing.jaeger.port:14250}")
    private int port;

    @Autowired
    private Environment environment;

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public List<String> getKeystorePemCerts() {
        if (keystorePemCerts == null) {
            keystorePemCerts = initializeKeystorePemCerts("services.tracing.jaeger.ssl.keystore.certs[%s]");
        }

        return keystorePemCerts;
    }

    private List<String> initializeKeystorePemCerts(String property) {
        String key = String.format(property, 0);
        List<String> values = new ArrayList<>();

        while (environment.containsProperty(key)) {
            values.add(environment.getProperty(key));
            key = String.format(property, values.size());
        }

        return values;
    }

    public void setKeystorePemCerts(List<String> keystorePemCerts) {
        this.keystorePemCerts = keystorePemCerts;
    }

    public List<String> getKeystorePemKeys() {
        if (keystorePemKeys == null) {
            keystorePemKeys = initializeKeystorePemCerts("services.tracing.jaeger.ssl.keystore.keys[%s]");
        }

        return keystorePemKeys;
    }

    public void setKeystorePemKeys(List<String> keystorePemKeys) {
        this.keystorePemKeys = keystorePemKeys;
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public boolean isHostnameVerifier() {
        return hostnameVerifier;
    }

    public void setHostnameVerifier(boolean hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public String getTruststoreType() {
        return truststoreType;
    }

    public void setTruststoreType(String truststoreType) {
        this.truststoreType = truststoreType;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
