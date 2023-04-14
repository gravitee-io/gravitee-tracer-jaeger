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
package io.gravitee.tracer.jaeger;

import io.gravitee.tracer.jaeger.configuration.JaegerTracerConfiguration;
import io.grpc.ManagedChannel;
import io.vertx.core.Vertx;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.grpc.VertxChannelBuilder;

public class JaegerGrpcChannelBuilder {

    private static final String KEYSTORE_FORMAT_JKS = "JKS";
    private static final String KEYSTORE_FORMAT_PEM = "PEM";
    private static final String KEYSTORE_FORMAT_PKCS12 = "PKCS12";

    private final Vertx vertx;
    private final JaegerTracerConfiguration configuration;

    private JaegerGrpcChannelBuilder(Vertx vertx, JaegerTracerConfiguration configuration) {
        this.vertx = vertx;
        this.configuration = configuration;
    }

    public static JaegerGrpcChannelBuilder from(Vertx vertx, JaegerTracerConfiguration configuration) {
        return new JaegerGrpcChannelBuilder(vertx, configuration);
    }

    public ManagedChannel build() {
        var channelBuilder = VertxChannelBuilder.forAddress(vertx, configuration.getHost(), configuration.getPort());

        if (configuration.isSslEnabled()) {
            channelBuilder.useSsl(this::configureSsl).useTransportSecurity();
        } else {
            channelBuilder.usePlaintext();
        }

        return channelBuilder.build();
    }

    private void configureSsl(ClientOptionsBase options) {
        options.setSsl(true).setUseAlpn(true).setTrustAll(configuration.isTrustAll());

        if (configuration.getKeystoreType() != null) {
            if (configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)) {
                options.setKeyStoreOptions(
                    new JksOptions().setPath(configuration.getKeystorePath()).setPassword(configuration.getKeystorePassword())
                );
            } else if (configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)) {
                options.setPfxKeyCertOptions(
                    new PfxOptions().setPath(configuration.getKeystorePath()).setPassword(configuration.getKeystorePassword())
                );
            } else if (configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PEM)) {
                options.setPemKeyCertOptions(
                    new PemKeyCertOptions()
                        .setCertPaths(configuration.getKeystorePemCerts())
                        .setKeyPaths(configuration.getKeystorePemKeys())
                );
            }
        }

        if (configuration.getTruststoreType() != null) {
            if (configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)) {
                options.setTrustStoreOptions(
                    new JksOptions().setPath(configuration.getTruststorePath()).setPassword(configuration.getTruststorePassword())
                );
            } else if (configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)) {
                options.setPfxTrustOptions(
                    new PfxOptions().setPath(configuration.getTruststorePath()).setPassword(configuration.getTruststorePassword())
                );
            } else if (configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PEM)) {
                options.setPemTrustOptions(new PemTrustOptions().addCertPath(configuration.getTruststorePath()));
            }
        }
    }
}
