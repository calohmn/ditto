/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.internal.utils.pubsub.ddata.compressed.CompressedDData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link SubUpdater}.
 */
public final class SubUpdaterTest {

    private ActorSystem system;

    @Before
    public void setUpCluster() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        system = ActorSystem.create("actorSystem", getTestConf());
        final Cluster cluster1 = Cluster.get(system);
        cluster1.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        latch.await();
    }

    @After
    public void shutdownCluster() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void clusterStateOutOfSync() {
        final var system2 = ActorSystem.create("system2", getTestConf());
        try {
            new TestKit(system) {{
                // GIVEN: distributed data contains entry for nonexistent cluster member
                final var subscriber = TestProbe.apply(system);
                final var config = PubSubConfig.of(system);
                final var unknownActor = TestProbe.apply(system2).ref();
                final var cluster2 = Cluster.get(system2);
                final var addressMap = Map.of(mockActorRefWithAddress(unknownActor, cluster2), "unknown-value");
                final CompressedDData ddata = mockDistributedData(addressMap);
                final ActorRef underTest = system.actorOf(SubUpdater.props(config, subscriber.ref(), ddata));

                // WHEN: AckUpdater is requested to sync against the cluster state
                underTest.tell(ClusterStateSyncBehavior.Control.SYNC_CLUSTER_STATE, ActorRef.noSender());

                // THEN: AckUpdater removes the extraneous entry
                Mockito.verify(ddata.getReader(), Mockito.timeout(5000))
                        .getAllShards(eq((Replicator.ReadConsistency) Replicator.readLocal()));
                Mockito.verify(ddata.getWriter(), Mockito.timeout(5000))
                        .removeAddress(eq(cluster2.selfAddress()),
                                eq((Replicator.WriteConsistency) Replicator.writeLocal()));
            }};
        } finally {
            TestKit.shutdownActorSystem(system2);
        }
    }

    @Test
    public void clusterStateInSync() {
        new TestKit(system) {{
            // GIVEN: distributed data contains entry for the current cluster member and no extraneous entries
            final var subscriberRef = TestProbe.apply(system).ref();
            final var cluster = Cluster.get(system);
            final var config = PubSubConfig.of(system);
            final var addressMap = Map.of(mockActorRefWithAddress(subscriberRef, cluster), "unknown-value");
            final CompressedDData ddata = mockDistributedData(addressMap);
            final ActorRef underTest = system.actorOf(SubUpdater.props(config, subscriberRef, ddata));

            // WHEN: AckUpdater is requested to sync against the cluster state
            underTest.tell(ClusterStateSyncBehavior.Control.SYNC_CLUSTER_STATE, ActorRef.noSender());

            // THEN: AckUpdater does not modify ddata more than needed
            Mockito.verify(ddata.getReader(), Mockito.timeout(5000))
                    .getAllShards(eq((Replicator.ReadConsistency) Replicator.readLocal()));
            Mockito.verify(ddata.getWriter(), Mockito.never()).put(any(), any(), any());
            Mockito.verify(ddata.getWriter(), Mockito.never()).removeAddress(any(), any());
        }};
    }

    private Config getTestConf() {
        return ConfigFactory.load("pubsub-factory-test.conf");
    }

    private static ActorRef mockActorRefWithAddress(final ActorRef originalRef, final Cluster cluster) {
        final var mock = Mockito.mock(ActorRef.class);
        Mockito.when(mock.path()).thenReturn(cluster.remotePathOf(originalRef));
        return mock;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompressedDData mockDistributedData(final Map<ActorRef, String> result) {
        final ORMultiMap map = Mockito.mock(ORMultiMap.class);
        Mockito.when(map.getEntries()).thenReturn(result);
        final var mock = Mockito.mock(CompressedDData.class);
        final var reader = Mockito.mock(DDataReader.class);
        final var writer = Mockito.mock(DDataWriter.class);
        Mockito.when(mock.getReader()).thenReturn(reader);
        Mockito.when(mock.getWriter()).thenReturn(writer);
        Mockito.when(reader.get(any(), any())).thenReturn(CompletableFuture.completedStage(Optional.of(map)));
        Mockito.when(reader.getAllShards(any())).thenReturn(CompletableFuture.completedStage(List.of(map)));
        Mockito.when(writer.put(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
        return mock;
    }

}