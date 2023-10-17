/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.tracer.jaeger.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
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

    private Environment environment;

    @Autowired
    public JaegerTracerConfiguration(Environment environment) {
        this.environment = environment;
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

    public List<String> getKeystorePemKeys() {
        if (keystorePemKeys == null) {
            keystorePemKeys = initializeKeystorePemCerts("services.tracing.jaeger.ssl.keystore.keys[%s]");
        }

        return keystorePemKeys;
    }
}
