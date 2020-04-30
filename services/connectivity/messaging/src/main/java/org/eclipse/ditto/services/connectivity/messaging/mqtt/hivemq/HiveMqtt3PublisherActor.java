/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AbstractMqttValidator;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttPublishTarget;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor responsible for publishing messages to an MQTT broker using the given {@link Mqtt3Client}.
 */
public final class HiveMqtt3PublisherActor extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;
    static final String NAME = "HiveMqtt3PublisherActor";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final Mqtt3AsyncClient client;
    private final boolean dryRun;

    @SuppressWarnings("squid:UnusedPrivateConstructor") // used by akka
    private HiveMqtt3PublisherActor(final Connection connection, final Mqtt3Client client, final boolean dryRun) {
        super(connection);
        this.client = checkNotNull(client).toAsync();
        this.dryRun = dryRun;
    }

    /**
     * Create Props object for this publisher actor.
     *
     * @param connection the connection the publisher actor belongs to.
     * @param client the HiveMQ client.
     * @param dryRun whether this publisher is only created for a test or not.
     * @return the Props object.
     */
    public static Props props(final Connection connection, final Mqtt3Client client, final boolean dryRun) {
        return Props.create(HiveMqtt3PublisherActor.class, connection, client, dryRun);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.Mapped.class, this::isDryRun,
                outbound -> log().info("Message dropped in dry run mode: {}", outbound));
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // not needed
    }

    @Override
    protected MqttPublishTarget toPublishTarget(final String address) {
        return MqttPublishTarget.of(address);
    }

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected CompletionStage<Acknowledgement> publishMessage(final Signal<?> signal,
            @Nullable final Target target,
            final MqttPublishTarget publishTarget,
            final ExternalMessage message, int ackSizeQuota) {

        try {
            final MqttQos qos = determineQos(target);
            final Mqtt3Publish mqttMessage = mapExternalMessageToMqttMessage(publishTarget, qos, message);
            if (log().isDebugEnabled()) {
                log().debug("Publishing MQTT message to topic <{}>: {}", mqttMessage.getTopic(),
                        decodeAsHumanReadable(mqttMessage.getPayload().orElse(null), message));
            }
            // TODO: check against broker ack
            return client.publish(mqttMessage).thenApply(msg -> null);
        } catch (final Exception e) {
            // TODO: log() needed? - also applies for HiveMqtt5
            // log().info("Won't publish message, since currently in disconnected state.");
            return CompletableFuture.failedFuture(e);
        }
    }

    private MqttQos determineQos(@Nullable final Target target) {
        if (target == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = target.getQos().orElse(DEFAULT_TARGET_QOS);
            return AbstractMqttValidator.getHiveQoS(qos);
        }
    }

    private Mqtt3Publish mapExternalMessageToMqttMessage(final MqttPublishTarget mqttTarget, final MqttQos qos,
            final ExternalMessage externalMessage) {

        final ByteBuffer payload;
        if (externalMessage.isTextMessage()) {
            final Charset charset = getCharsetFromMessage(externalMessage);
            payload = externalMessage
                    .getTextPayload()
                    .map(text -> ByteBuffer.wrap(text.getBytes(charset)))
                    .orElse(ByteBufferUtils.empty());
        } else if (externalMessage.isBytesMessage()) {
            payload = externalMessage.getBytePayload()
                    .orElse(ByteBufferUtils.empty());
        } else {
            payload = ByteBufferUtils.empty();
        }
        return Mqtt3Publish.builder().topic(mqttTarget.getTopic()).qos(qos).payload(payload).build();
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
