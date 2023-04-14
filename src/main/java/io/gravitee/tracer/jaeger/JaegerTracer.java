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
import io.vertx.grpc.VertxChannelBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JaegerTracer extends AbstractService<Tracer> implements VertxTracer<Span, Span> {

    private static final TextMapGetter<Iterable<Map.Entry<String, String>>> getter = new HeadersPropagatorGetter();
    private static final TextMapSetter<BiConsumer<String, String>> setter = new HeadersPropagatorSetter();

    private io.opentelemetry.api.trace.Tracer tracer;
    private ContextPropagators propagators;

    @Autowired
    private JaegerTracerConfiguration configuration;

    @Autowired
    private Node node;

    @Autowired
    private Vertx vertx;

    @Override
    protected void doStart() {
        // Create a channel towards Jaeger end point
        final ManagedChannel channel = JaegerGrpcChannelBuilder.from(vertx, configuration).build();
        final JaegerGrpcSpanExporter exporter = JaegerGrpcSpanExporter
            .builder()
            .setChannel(channel)
            .setTimeout(30, TimeUnit.SECONDS)
            .build();

        Resource serviceNameResource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), node.application()));

        // Set to process the spans by the Jaeger Exporter
        SdkTracerProvider tracerProvider = SdkTracerProvider
            .builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(Resource.getDefault().merge(serviceNameResource))
            .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk
            .builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

        this.tracer = openTelemetry.getTracer("io.gravitee");
        this.propagators = openTelemetry.getPropagators();
    }

    @Override
    public <R> Span receiveRequest(
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

        io.opentelemetry.context.Context tracingContext = propagators
            .getTextMapPropagator()
            .extract(io.opentelemetry.context.Context.root(), headers, getter);

        // If no span, and policy is PROPAGATE, then don't create the span
        if (Span.fromContextOrNull(tracingContext) == null && TracingPolicy.PROPAGATE.equals(policy)) {
            return null;
        }

        final Span span = reportTagsAndStart(
            tracer
                .spanBuilder(operation)
                .setParent(tracingContext)
                .setSpanKind(
                    SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.CLIENT : io.opentelemetry.api.trace.SpanKind.PRODUCER
                ),
            request,
            tagExtractor
        );

        VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(context, tracingContext.with(span));

        return span;
    }

    @Override
    public <R> void sendResponse(
        final Context context,
        final R response,
        final Span span,
        final Throwable failure,
        final TagExtractor<R> tagExtractor
    ) {
        if (span != null) {
            VertxContextStorageProvider.VertxContextStorage.INSTANCE.clear(context);
            end(span, response, tagExtractor, failure);
        }
    }

    private <R> void end(Span span, R response, TagExtractor<R> tagExtractor, Throwable failure) {
        if (failure != null) {
            span.recordException(failure);
        }

        if (response != null) {
            tagExtractor.extractTo(response, span::setAttribute);
        }
        span.end();
    }

    @Override
    public <R> Span sendRequest(
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

        io.opentelemetry.context.Context tracingContext = VertxContextStorageProvider.VertxContextStorage.INSTANCE.current(context);
        if (tracingContext == null && !TracingPolicy.ALWAYS.equals(policy)) {
            return null;
        }

        if (tracingContext == null) {
            tracingContext = io.opentelemetry.context.Context.root();
        }

        final Span span = reportTagsAndStart(
            tracer
                .spanBuilder(operation)
                .setParent(tracingContext)
                .setSpanKind(
                    SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.CLIENT : io.opentelemetry.api.trace.SpanKind.PRODUCER
                ),
            request,
            tagExtractor
        );
        tracingContext = tracingContext.with(span);
        propagators.getTextMapPropagator().inject(tracingContext, headers, setter);

        return span;
    }

    @Override
    public <R> void receiveResponse(
        final Context context,
        final R response,
        final Span span,
        final Throwable failure,
        final TagExtractor<R> tagExtractor
    ) {
        if (span != null) {
            end(span, response, tagExtractor, failure);
        }
    }

    // tags need to be set before start, otherwise any sampler registered won't have access to it
    private <T> Span reportTagsAndStart(SpanBuilder span, T obj, TagExtractor<T> tagExtractor) {
        int len = tagExtractor.len(obj);
        for (int idx = 0; idx < len; idx++) {
            span.setAttribute(tagExtractor.name(obj, idx), tagExtractor.value(obj, idx));
        }
        return span.startSpan();
    }

    @Override
    protected void doStop() {
        this.close();
    }

    @Override
    public io.gravitee.tracing.api.Span trace(String spanName) {
        io.opentelemetry.context.Context tracingContext = VertxContextStorageProvider.VertxContextStorage.INSTANCE.current();
        if (tracingContext == null) {
            tracingContext = io.opentelemetry.context.Context.root();
        }
        Span span = tracer.spanBuilder(spanName).setParent(tracingContext).startSpan();
        Scope scope = VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(tracingContext.with(span));
        return new JaegerSpan(span, scope);
    }
}
