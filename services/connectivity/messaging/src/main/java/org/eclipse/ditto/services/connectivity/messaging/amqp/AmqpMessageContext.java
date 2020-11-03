 /*
  * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.services.connectivity.messaging.amqp;

 import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

 /**
  * Lambda, to enclose the context into 'onPublishMessage'.
  * Is used in <code>AmqpPublisherActor</code> for the publishing message stream.
  */
 @FunctionalInterface
 interface AmqpMessageContext {

     void onPublishMessage(final ExternalMessage message);
 }
