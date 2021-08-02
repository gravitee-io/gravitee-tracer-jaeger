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

import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.tracing.Tracer;
import io.gravitee.node.tracing.vertx.VertxTracer;
import io.gravitee.tracer.jaeger.configuration.JaegerTracerConfiguration;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.tracing.TracingPolicy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JaegerTracer
  extends AbstractService<Tracer>
  implements VertxTracer<Scope, Scope> {

  static String ACTIVE_CONTEXT = "tracing.context";

  private static final String KEYSTORE_FORMAT_JKS = "JKS";
  private static final String KEYSTORE_FORMAT_PEM = "PEM";
  private static final String KEYSTORE_FORMAT_PKCS12 = "PKCS12";

  private static final TextMapGetter<Iterable<Map.Entry<String, String>>> getter = new HeadersPropagatorGetter();
  private static final TextMapSetter<BiConsumer<String, String>> setter = new HeadersPropagatorSetter();

  private io.opentelemetry.api.trace.Tracer tracer;

  private ContextPropagators propagators;

  @Autowired
  private Environment environment;

  @Autowired
  private JaegerTracerConfiguration configuration;

  @Autowired
  private Node node;

  @Autowired
  private Vertx vertx;

  @Override
  protected void doStart() throws Exception {
    // Create a channel towards Jaeger end point
    final NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(
      configuration.getHost(),
      configuration.getPort()
    );

    final HttpClientOptions sslOptions = getHttpClientSSLOptionsFromConfiguration();

    if (sslOptions != null) {
      final SSLHelper helper = new SSLHelper(
        sslOptions,
        sslOptions.getKeyCertOptions(),
        sslOptions.getTrustOptions()
      );
      helper.setApplicationProtocols(
        Collections.singletonList(HttpVersion.HTTP_2.alpnName())
      );
      final SslContext ctx = helper.getContext((VertxInternal) this.vertx);

      channelBuilder
        .sslContext(
          new DelegatingSslContext(ctx) {
            protected void initEngine(SSLEngine engine) {
              helper.configureEngine(engine, null);
            }
          }
        )
        .useTransportSecurity()
        .build();
    } else {
      channelBuilder.usePlaintext();
    }

    final ManagedChannel channel = channelBuilder.build();
    final JaegerGrpcSpanExporter exporter = JaegerGrpcSpanExporter
      .builder()
      .setChannel(channel)
      .setTimeout(30, TimeUnit.SECONDS)
      .build();

    Resource serviceNameResource = Resource.create(
      Attributes.of(AttributeKey.stringKey("service.name"), node.application())
    );

    // Set to process the spans by the Jaeger Exporter
    SdkTracerProvider tracerProvider = SdkTracerProvider
      .builder()
      .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
      .setResource(Resource.getDefault().merge(serviceNameResource))
      .build();

    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk
      .builder()
      .setTracerProvider(tracerProvider)
      .setPropagators(
        ContextPropagators.create(W3CTraceContextPropagator.getInstance())
      )
      .build();

    this.tracer = openTelemetry.getTracer("io.gravitee");
    this.propagators = openTelemetry.getPropagators();
  }

  private HttpClientOptions getHttpClientSSLOptionsFromConfiguration() {
    if (!configuration.isSslEnabled()) {
      return null;
    }

    final HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setVerifyHost(configuration.isHostnameVerifier())
      .setTrustAll(configuration.isTrustAll());

    if (configuration.getKeystoreType() != null) {
      if (
        configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)
      ) {
        options.setKeyStoreOptions(
          new JksOptions()
            .setPath(configuration.getKeystorePath())
            .setPassword(configuration.getKeystorePassword())
        );
      } else if (
        configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)
      ) {
        options.setPfxKeyCertOptions(
          new PfxOptions()
            .setPath(configuration.getKeystorePath())
            .setPassword(configuration.getKeystorePassword())
        );
      } else if (
        configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PEM)
      ) {
        options.setPemKeyCertOptions(
          new PemKeyCertOptions()
            .setCertPaths(configuration.getKeystorePemCerts())
            .setKeyPaths(configuration.getKeystorePemKeys())
        );
      }
    }

    if (configuration.getTruststoreType() != null) {
      if (
        configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)
      ) {
        options.setTrustStoreOptions(
          new JksOptions()
            .setPath(configuration.getTruststorePath())
            .setPassword(configuration.getTruststorePassword())
        );
      } else if (
        configuration
          .getTruststoreType()
          .equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)
      ) {
        options.setPfxTrustOptions(
          new PfxOptions()
            .setPath(configuration.getTruststorePath())
            .setPassword(configuration.getTruststorePassword())
        );
      } else if (
        configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PEM)
      ) {
        options.setPemTrustOptions(
          new PemTrustOptions().addCertPath(configuration.getTruststorePath())
        );
      }
    }
    return options;
  }

  @Override
  public <R> Scope receiveRequest(
    final Context context,
    final SpanKind kind,
    final TracingPolicy policy,
    final R request,
    final String operation,
    final Iterable<Map.Entry<String, String>> headers,
    final TagExtractor<R> tagExtractor
  ) {
    if (TracingPolicy.IGNORE.equals(policy)) {
      return null;
    }

    io.opentelemetry.context.Context tracingContext = context.getLocal(
      ACTIVE_CONTEXT
    );
    if (tracingContext == null) {
      tracingContext = io.opentelemetry.context.Context.root();
    }
    tracingContext =
      propagators
        .getTextMapPropagator()
        .extract(tracingContext, headers, getter);

    // If no span, and policy is PROPAGATE, then don't create the span
    if (
      Span.fromContextOrNull(tracingContext) == null &&
      TracingPolicy.PROPAGATE.equals(policy)
    ) {
      return null;
    }

    final Span span = tracer
      .spanBuilder(operation)
      .setParent(tracingContext)
      .setSpanKind(
        SpanKind.RPC.equals(kind)
          ? io.opentelemetry.api.trace.SpanKind.SERVER
          : io.opentelemetry.api.trace.SpanKind.CONSUMER
      )
      .startSpan();

    tagExtractor.extractTo(request, span::setAttribute);

    return VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(
      context,
      tracingContext.with(span)
    );
  }

  @Override
  public <R> void sendResponse(
    final Context context,
    final R response,
    final Scope scope,
    final Throwable failure,
    final TagExtractor<R> tagExtractor
  ) {
    if (scope == null) {
      return;
    }

    Span span = Span.fromContext(context.getLocal(ACTIVE_CONTEXT));

    if (failure != null) {
      span.recordException(failure);
    }

    if (response != null) {
      tagExtractor.extractTo(response, span::setAttribute);
    }

    span.end();
    scope.close();
  }

  @Override
  public <R> Scope sendRequest(
    final Context context,
    final SpanKind kind,
    final TracingPolicy policy,
    final R request,
    final String operation,
    final BiConsumer<String, String> headers,
    final TagExtractor<R> tagExtractor
  ) {
    if (TracingPolicy.IGNORE.equals(policy) || request == null) {
      return null;
    }

    io.opentelemetry.context.Context tracingContext = context.getLocal(
      ACTIVE_CONTEXT
    );

    if (tracingContext == null && !TracingPolicy.ALWAYS.equals(policy)) {
      return null;
    }

    if (tracingContext == null) {
      tracingContext = io.opentelemetry.context.Context.root();
    }

    final Span span = tracer
      .spanBuilder(operation)
      .setParent(tracingContext)
      .setSpanKind(
        SpanKind.RPC.equals(kind)
          ? io.opentelemetry.api.trace.SpanKind.CLIENT
          : io.opentelemetry.api.trace.SpanKind.PRODUCER
      )
      .startSpan();
    tagExtractor.extractTo(request, span::setAttribute);

    tracingContext = tracingContext.with(span);
    propagators.getTextMapPropagator().inject(tracingContext, headers, setter);

    return VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(
      context,
      tracingContext
    );
  }

  @Override
  public <R> void receiveResponse(
    final Context context,
    final R response,
    final Scope scope,
    final Throwable failure,
    final TagExtractor<R> tagExtractor
  ) {
    this.sendResponse(context, response, scope, failure, tagExtractor);
  }

  @Override
  protected void doStop() throws Exception {
    this.close();
  }

  @Override
  public io.gravitee.tracing.api.Span trace(String spanName) {
    io.opentelemetry.context.Context parent = Vertx
      .currentContext()
      .getLocal(ACTIVE_CONTEXT);
    if (parent == null) {
      parent = io.opentelemetry.context.Context.root();
    }

    SpanBuilder builder = tracer.spanBuilder(spanName).setParent(parent);
    return new JaegerSpan(builder.startSpan());
  }
}
