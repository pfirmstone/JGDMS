/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import org.junit.Test;

/**
 * Tests the constraint-aware MarshalledInstance construction path and the shared
 * {@link MarshalledInstance#chooseMarshalFactory} primitive (JGDMS-STD-008 sec.13,
 * enforcement surface 1).
 *
 * <p>Note: the DER {@code MarshalFactoryProvider} lives in {@code jgdms-der}, which is
 * NOT on this module's test classpath. So here a <em>required</em>
 * {@link MarshallingFormat#DER} is correctly unsatisfiable
 * ({@link UnsupportedConstraintException}); the round-trip-with-DER case is covered in
 * {@code jgdms-der}'s {@code DerConstraintConstructionTest}.
 */
public class MarshalledInstanceConstraintTest {

    @Test
    public void nullConstraintsSelectJoss() throws Exception {
	MarshalFactory f = MarshalledInstance.chooseMarshalFactory(null);
	assertTrue(f instanceof MarshalledInstance.MarshalFactoryInstance);
    }

    @Test
    public void requiredJossSelectsJoss() throws Exception {
	MarshalFactory f = MarshalledInstance.chooseMarshalFactory(
		new InvocationConstraints(MarshallingFormat.JOSS, null));
	assertTrue(f instanceof MarshalledInstance.MarshalFactoryInstance);
    }

    @Test(expected = UnsupportedConstraintException.class)
    public void requiredDerWithoutProviderIsUnsatisfiable() throws Exception {
	// No DER provider on this module's classpath -> required DER cannot be honoured.
	MarshalledInstance.chooseMarshalFactory(
		new InvocationConstraints(MarshallingFormat.DER, null));
    }

    @Test(expected = UnsupportedConstraintException.class)
    public void conflictingRequiredFormatsAreUnsatisfiable() throws Exception {
	MarshalledInstance.chooseMarshalFactory(new InvocationConstraints(
		new InvocationConstraint[]{ MarshallingFormat.DER, MarshallingFormat.JOSS },
		null));
    }

    @Test
    public void preferredDerFallsBackToJossWhenUnavailable() throws Exception {
	// Only PREFERRED (not required) and unavailable here -> default (JOSS), no throw.
	MarshalFactory f = MarshalledInstance.chooseMarshalFactory(
		new InvocationConstraints(null, MarshallingFormat.DER));
	assertTrue(f instanceof MarshalledInstance.MarshalFactoryInstance);
    }

    @Test
    public void constraintAwareCtorRoundTripsUnderJoss() throws Exception {
	MarshalledInstance mi = new MarshalledInstance("hello",
		Collections.EMPTY_SET, new InvocationConstraints(MarshallingFormat.JOSS, null));
	assertEquals("hello", mi.get(false, String.class));
    }

    @Test
    public void constraintAwareCtorAcceptsNullConstraints() throws Exception {
	MarshalledInstance mi = new MarshalledInstance("x",
		Collections.EMPTY_SET, (InvocationConstraints) null);
	assertEquals("x", mi.get(false, String.class));
    }
}
