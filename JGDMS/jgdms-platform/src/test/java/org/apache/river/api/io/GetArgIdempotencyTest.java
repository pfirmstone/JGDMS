/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Discriminating test for the Inc2 idempotency guarantee.
 *
 * <p>The security argument for dropping the {@code SerializablePermission}
 * subclass guard is: even an untrusted {@code GetArg} subclass cannot mount a
 * TOCTOU attack, because the typed {@code get} accessors are {@code final} in
 * {@link GetArg} and memoize per {@code (callerClass, fieldName)} -- the
 * subclass-provided {@code lookup} hook is invoked AT MOST ONCE per field, so it
 * cannot return a benign value to a class's {@code check(GetArg)} invariant check
 * and then a different, malicious value to that class's {@code (GetArg)}
 * constructor.
 *
 * <p>This test makes the attack concrete: {@link HostileGetArg#lookup} returns a
 * <em>different value on every invocation</em>. If memoization were absent or
 * could be bypassed, the assertions below would fail. The tests call {@code get}
 * directly from methods of this class, and {@link HostileGetArg#serialClasses()}
 * names this class, so the base's {@code StackWalker} caller resolution resolves
 * the caller to {@code GetArgIdempotencyTest}.
 */
public class GetArgIdempotencyTest {

    /**
     * A deliberately hostile {@link GetArg}: its {@code lookup} hook would return
     * a fresh (incrementing) value on each call. The base must defeat that by
     * caching the first result.
     */
    private static final class HostileGetArg extends GetArg {

        final AtomicInteger lookupCalls = new AtomicInteger();
        final AtomicInteger defaultedCalls = new AtomicInteger();
        private final Class<?> caller;

        HostileGetArg(Class<?> caller) {
            super(false); // package-private bypass (same package); not under test here
            this.caller = caller;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            int n = lookupCalls.incrementAndGet();
            // "absent" signals a field that is not present in the stream.
            if ("absent".equals(name)) {
                return ABSENT;
            }
            // Every call yields a different value -- a memoizing base must only
            // ever observe the FIRST one.
            return Integer.valueOf(n);
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            defaultedCalls.incrementAndGet();
            return "absent".equals(name);
        }

        @Override
        public Class[] serialClasses() {
            return new Class[]{ caller };
        }

        @Override
        public ReadObject getReader() {
            return null;
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    /**
     * The crux: a hostile lookup cannot change a field's value between the
     * check phase and the construct phase.
     */
    @Test
    public void getIsIdempotent_hostileLookupCannotTOCTOU() throws Exception {
        HostileGetArg arg = new HostileGetArg(GetArgIdempotencyTest.class);
        int atCheck = arg.get("field", -1);     // "check(GetArg)" reads the field
        int atConstruct = arg.get("field", -1); // "(GetArg) constructor" re-reads it
        assertEquals("get() must be memoized: a hostile lookup must not be able to "
                + "return a different value on the second read", atCheck, atConstruct);
        assertEquals("lookup() must be invoked at most once per (caller,name)",
                1, arg.lookupCalls.get());
    }

    /**
     * The primitive, Object and type-checked accessors for the same field must
     * all observe the single memoized value (one lookup total).
     */
    @Test
    public void allAccessorsShareTheSingleMemoizedValue() throws Exception {
        HostileGetArg arg = new HostileGetArg(GetArgIdempotencyTest.class);
        int viaInt = arg.get("f", -1);
        Object viaObject = arg.get("f", (Object) null);
        Integer viaTyped = arg.get("f", null, Integer.class);
        assertEquals(Integer.valueOf(viaInt), viaObject);
        assertEquals(Integer.valueOf(viaInt), viaTyped);
        assertEquals("the int, Object and 3-arg accessors must not each trigger a lookup",
                1, arg.lookupCalls.get());
    }

    /**
     * Distinct field names are resolved independently (the memo is keyed by
     * name), but each stays memoized after its first read.
     */
    @Test
    public void distinctNamesResolveIndependentlyThenMemoize() throws Exception {
        HostileGetArg arg = new HostileGetArg(GetArgIdempotencyTest.class);
        int x = arg.get("x", -1);
        int y = arg.get("y", -1);
        assertTrue("distinct names must resolve to distinct lookups", x != y);
        assertEquals(2, arg.lookupCalls.get());
        assertEquals("x stays memoized", x, arg.get("x", -1));
        assertEquals("y stays memoized", y, arg.get("y", -1));
        assertEquals("no further lookups after memoization", 2, arg.lookupCalls.get());
    }

    /**
     * An absent field: {@code lookup} returns the {@code ABSENT} sentinel exactly
     * once (memoized), while the per-call caller default is applied by the base on
     * every read.
     */
    @Test
    public void absentFieldMemoizesAbsenceButAppliesPerCallDefault() throws Exception {
        HostileGetArg arg = new HostileGetArg(GetArgIdempotencyTest.class);
        assertEquals(42, arg.get("absent", 42));
        assertEquals(7, arg.get("absent", 7));
        assertEquals("ABSENT must be memoized like any other lookup result",
                1, arg.lookupCalls.get());
    }

    /**
     * defaulted() must route through the decode-free isDefaulted hook and must NOT
     * trigger a value lookup (no decode side effect).
     */
    @Test
    public void defaultedUsesPresenceHookNotValueLookup() throws Exception {
        HostileGetArg arg = new HostileGetArg(GetArgIdempotencyTest.class);
        assertTrue(arg.defaulted("absent"));
        assertTrue(!arg.defaulted("present"));
        assertEquals("defaulted() must not invoke the value lookup hook",
                0, arg.lookupCalls.get());
        assertEquals(2, arg.defaultedCalls.get());
    }

    /**
     * validateInvariants() reads through the final memoizing accessors, so the
     * value it validates is the same one a later get() returns (one lookup), and
     * a non-null nullable field is accepted.
     */
    @Test
    public void validateInvariantsSharesMemoizedValues() throws Exception {
        HostileGetArg arg = new HostileGetArg(GetArgIdempotencyTest.class);
        GetArg same = arg.validateInvariants(
                new String[]{ "f" },
                new Class[]{ Number.class },
                new boolean[]{ true });
        assertSame(arg, same);
        int afterValidate = arg.get("f", -1);
        assertEquals("validateInvariants must read through the memoizing accessors",
                1, arg.lookupCalls.get());
        assertEquals(1, afterValidate);
    }
}
