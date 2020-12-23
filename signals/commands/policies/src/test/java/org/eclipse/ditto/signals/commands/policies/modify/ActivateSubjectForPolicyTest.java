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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ActivateSubjectForPolicy}.
 */
public final class ActivateSubjectForPolicyTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ActivateSubjectForPolicy.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ActivateSubjectForPolicy.JsonFields.SUBJECT_ID, TestConstants.Policy.SUBJECT_ID.toString())
            .set(ActivateSubjectForPolicy.JsonFields.EXPIRY, Instant.EPOCH.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivateSubjectForPolicy.class,
                areImmutable(),
                provided(Label.class, SubjectId.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ActivateSubjectForPolicy.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ActivateSubjectForPolicy.of(null, TestConstants.Policy.SUBJECT_ID, Instant.EPOCH,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        ActivateSubjectForPolicy.of(TestConstants.Policy.POLICY_ID, null, Instant.EPOCH,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiry() {
        ActivateSubjectForPolicy.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ActivateSubjectForPolicy underTest =
                ActivateSubjectForPolicy.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID,
                        Instant.EPOCH, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ActivateSubjectForPolicy underTest =
                ActivateSubjectForPolicy.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final ActivateSubjectForPolicy expectedCommand =
                ActivateSubjectForPolicy.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID,
                        Instant.EPOCH, TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

    @Test(expected = JsonParseException.class)
    public void tryToCreateInstanceFromInvalidTimestampInJson() {
        final JsonObject jsonWithInvalidTimestamp = KNOWN_JSON.toBuilder()
                .set(ActivateSubjectForPolicy.JsonFields.EXPIRY, "not-a-timestamp")
                .build();

        ActivateSubjectForPolicy.fromJson(jsonWithInvalidTimestamp, TestConstants.EMPTY_DITTO_HEADERS);
    }

}
