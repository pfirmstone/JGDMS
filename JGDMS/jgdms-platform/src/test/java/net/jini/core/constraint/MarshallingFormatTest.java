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

package net.jini.core.constraint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.junit.Test;

/**
 * Contract tests for {@link MarshallingFormat} (the DER-format invocation
 * constraint, JGDMS-STD-008 sec.13): construction/validation, value equality,
 * well-known constants, AtomicSerial round-trip, and use as a real requirement.
 */
public class MarshallingFormatTest {

    @Test(expected = NullPointerException.class)
    public void nullFormatThrows() {
	new MarshallingFormat((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyFormatThrows() {
	new MarshallingFormat("");
    }

    @Test
    public void getFormatReturnsValue() {
	assertEquals("application/x-test", new MarshallingFormat("application/x-test").getFormat());
    }

    @Test
    public void derConstantHasCanonicalFormatId() {
	assertEquals("JGDMS-STD-006/DER", MarshallingFormat.DER.getFormat());
    }

    @Test
    public void jossConstantMatchesMarshalledInstanceFormatJoss() {
	assertEquals(MarshalledInstance.FORMAT_JOSS, MarshallingFormat.JOSS.getFormat());
	assertEquals("JOSS", MarshallingFormat.JOSS.getFormat());
    }

    @Test
    public void equalsAndHashCodeBySameFormat() {
	MarshallingFormat der2 = new MarshallingFormat("JGDMS-STD-006/DER");
	assertEquals(MarshallingFormat.DER, der2);
	assertEquals(MarshallingFormat.DER.hashCode(), der2.hashCode());
    }

    @Test
    public void differentFormatsNotEqual() {
	assertNotEquals(MarshallingFormat.DER, MarshallingFormat.JOSS);
	assertFalse(MarshallingFormat.DER.equals(null));
	assertFalse(MarshallingFormat.DER.equals("JGDMS-STD-006/DER"));
    }

    @Test
    public void isAnInvocationConstraint() {
	assertTrue((Object) MarshallingFormat.DER instanceof InvocationConstraint);
    }

    @Test
    public void toStringContainsFormat() {
	assertTrue(MarshallingFormat.DER.toString().contains("JGDMS-STD-006/DER"));
    }

    /**
     * AtomicSerial round-trip: serialize the constraint inside an
     * AtomicMarshalledInstance and read it back; the format must survive and the
     * decoded constraint must equal the original.
     */
    @Test
    public void atomicSerialRoundTrip() throws Exception {
	for (MarshallingFormat orig : new MarshallingFormat[]{
		MarshallingFormat.DER, MarshallingFormat.JOSS,
		new MarshallingFormat("application/cbor") }) {
	    MarshallingFormat copy = new AtomicMarshalledInstance(orig)
		    .get(false, MarshallingFormat.class);
	    assertEquals(orig, copy);
	    assertEquals(orig.getFormat(), copy.getFormat());
	}
    }

    /**
     * Usable as a requirement: equals-based set membership in
     * {@link InvocationConstraints} works, so a method constraint of
     * {@code MarshallingFormat.DER} can be expressed and detected.
     */
    @Test
    public void usableAsRequirement() {
	InvocationConstraints ic = new InvocationConstraints(MarshallingFormat.DER, null);
	assertTrue(ic.requirements().contains(MarshallingFormat.DER));
	assertTrue(ic.requirements().contains(new MarshallingFormat("JGDMS-STD-006/DER")));
	assertFalse(ic.requirements().contains(MarshallingFormat.JOSS));
    }
}
