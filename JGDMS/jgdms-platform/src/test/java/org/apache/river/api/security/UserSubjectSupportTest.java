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

package org.apache.river.api.security;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link UserSubjectSupport}, the single caller site for the DirtyChai
 * multi-{@code Subject} API.
 *
 * <h2>What was broken</h2>
 *
 * <p>Two production classes ({@code net.jini.jeri.BasicInvocationDispatcher} and
 * {@link net.jini.security.SubjectAwareExecutor}) each cached
 * {@code Subject.class.getMethod("callAs", Callable.class, Subject[].class)}.
 * On DirtyChai {@code Subject} is sealed and the only public multi-argument
 * overload takes {@code UserSubject...}, so that exact-signature lookup always
 * threw {@code NoSuchMethodException}; both classes swallowed it and kept a null
 * constant, silently falling back to the <em>first</em> user Subject.
 * Multi-Subject dispatch therefore never ran in production, while a third,
 * correct copy of the lookup in the test sources kept passing.
 *
 * <p>{@link #theOldSubjectArrayLookupDoesNotResolve()} and
 * {@link #aPlainSubjectArrayIsRejectedByTheJdkMethod()} pin both halves of that
 * defect: the lookup misses, and fixing only the lookup would still fail at
 * invoke time because a {@code Subject[]} is not assignable to
 * {@code UserSubject[]}.
 *
 * <p>Reflection is used throughout for {@code UserSubject} and
 * {@code Subject.currentAll()} so this test compiles on a stock OpenJDK, where
 * neither exists, and self-skips there via {@link Assume}.
 */
public class UserSubjectSupportTest {

    // ------------------------------------------------------------------
    // Independent probes — deliberately NOT routed through the class under
    // test, so that the guard really guards.
    // ------------------------------------------------------------------

    /** {@code javax.security.auth.UserSubject}, or null on a stock OpenJDK. */
    private static Class<?> probeUserSubjectClass() {
        try {
            return Class.forName("javax.security.auth.UserSubject");
        } catch (ClassNotFoundException notDirtyChai) {
            return null;
        }
    }

    /** True when this JVM offers the DirtyChai multi-Subject API. */
    private static boolean dirtyChai() {
        Class<?> us = probeUserSubjectClass();
        if (us == null) return false;
        try {
            Subject.class.getMethod("callAs", Callable.class,
                    Array.newInstance(us, 0).getClass());
            return true;
        } catch (NoSuchMethodException notDirtyChai) {
            return false;
        }
    }

    /** {@code Subject.currentAll()}, or null on a stock OpenJDK. */
    private static Method currentAll() {
        try {
            return Subject.class.getMethod("currentAll");
        } catch (NoSuchMethodException notDirtyChai) {
            return null;
        }
    }

    private static Subject plainSubject(boolean readOnly, Principal... principals) {
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return new Subject(readOnly, ps, Collections.emptySet(), Collections.emptySet());
    }

    private static Subject userSubject(Principal... principals) {
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return UserSubjectSupport.newUserSubject(
                true, ps, Collections.emptySet(), Collections.emptySet());
    }

    /** All principals of every Subject currently bound, via {@code Subject.currentAll()}. */
    private static Set<Principal> boundPrincipals() throws Exception {
        Method m = currentAll();
        assertNotNull("Subject.currentAll() must exist on a DirtyChai JDK", m);
        Subject[] all = (Subject[]) m.invoke(null);
        Set<Principal> ps = new LinkedHashSet<>();
        if (all != null) {
            for (Subject s : all) ps.addAll(s.getPrincipals());
        }
        return ps;
    }

    private static int boundSubjectCount() throws Exception {
        Subject[] all = (Subject[]) currentAll().invoke(null);
        return all == null ? 0 : all.length;
    }

    // ------------------------------------------------------------------
    // Guard: production's cached capability actually resolves
    // ------------------------------------------------------------------

    /**
     * The guard.  When {@code javax.security.auth.UserSubject} is present, the
     * production constant MUST be non-null and MUST be the very method an
     * independent probe finds; on a stock OpenJDK it MUST be null.  Before the
     * fix the production constants were null on <em>both</em> platforms — the
     * DirtyChai half of this assertion is what failed.
     */
    @Test
    public void productionCapabilityConstantsMatchThisJdk() throws Exception {
        Class<?> probe = probeUserSubjectClass();
        if (probe == null) {
            assertNull("stock OpenJDK: USER_SUBJECT_CLASS must be null",
                    UserSubjectSupport.USER_SUBJECT_CLASS);
            assertNull("stock OpenJDK: CALL_AS_MULTI must be null",
                    UserSubjectSupport.CALL_AS_MULTI);
            assertFalse(UserSubjectSupport.isMultiSubjectSupported());
            return;
        }
        assertSame("USER_SUBJECT_CLASS must be the JDK's UserSubject",
                probe, UserSubjectSupport.USER_SUBJECT_CLASS);
        Method expected = Subject.class.getMethod("callAs", Callable.class,
                Array.newInstance(probe, 0).getClass());
        assertNotNull("UserSubject is present, so the multi-Subject callAs MUST"
                + " have been resolved; a null constant means multi-Subject"
                + " dispatch silently degrades to a single identity",
                UserSubjectSupport.CALL_AS_MULTI);
        assertEquals(expected, UserSubjectSupport.CALL_AS_MULTI);
        assertTrue(UserSubjectSupport.isMultiSubjectSupported());
    }

    /**
     * Root cause, pinned: the signature the old production code asked for does
     * not exist.  {@code getMethod} is an exact match and the {@code Subject...}
     * overload is {@code protected} on {@code Subject.NoCheck}.
     */
    @Test
    public void theOldSubjectArrayLookupDoesNotResolve() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        try {
            Subject.class.getMethod("callAs", Callable.class, Subject[].class);
            fail("callAs(Callable, Subject[]) unexpectedly resolved; if the JDK"
                    + " has gained this overload, revisit UserSubjectSupport");
        } catch (NoSuchMethodException expected) {
            // This is precisely the exception the old production code swallowed.
        }
    }

    /**
     * Second half of the root cause: even with a correct lookup, passing a
     * {@code Subject[]} fails at invoke time, so the reconstructed identities
     * must be {@code UserSubject}s in the first place.
     */
    @Test
    public void aPlainSubjectArrayIsRejectedByTheJdkMethod() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject[] plain = {
            plainSubject(true, new X500Principal("CN=alice")),
            plainSubject(true, new X500Principal("CN=bob"))
        };
        Callable<String> action = () -> "unreachable";
        try {
            UserSubjectSupport.CALL_AS_MULTI.invoke(null, action, (Object) plain);
            fail("a Subject[] must not be assignable to the UserSubject[] parameter");
        } catch (IllegalArgumentException expected) {
            // "argument type mismatch"
        }
    }

    // ------------------------------------------------------------------
    // Fail-loud invariant (test seam)
    // ------------------------------------------------------------------

    /**
     * If the {@code UserSubject} class is present but the expected
     * {@code callAs} signature is not, the helper must throw rather than degrade
     * to a single identity.  The seam is
     * {@link UserSubjectSupport#lookupCallAsMulti}, driven here with a class for
     * which no such overload can exist.  JDK-agnostic: runs everywhere.
     */
    @Test
    public void callAsLookupFailureFailsLoudly() {
        try {
            UserSubjectSupport.lookupCallAsMulti(String.class);
            fail("a missing callAs overload must fail loudly, not return null");
        } catch (IllegalStateException expected) {
            String msg = expected.getMessage();
            assertTrue("the message must name the expected method: " + msg,
                    msg.contains("callAs"));
            assertTrue("the message must name the parameter type: " + msg,
                    msg.contains(String.class.getName()));
            assertTrue("the message must say why silence is unacceptable: " + msg,
                    msg.contains("silently"));
        }
    }

    /** Same fail-loud invariant for the {@code UserSubject} constructor lookup. */
    @Test
    public void constructorLookupFailureFailsLoudly() {
        try {
            UserSubjectSupport.lookupConstructor(String.class);
            fail("a missing (boolean, Set, Set, Set) constructor must fail loudly");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("constructor"));
        }
    }

    // ------------------------------------------------------------------
    // newUserSubject
    // ------------------------------------------------------------------

    @Test
    public void newUserSubjectProducesAUserSubjectOnDirtyChai() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        X500Principal alice = new X500Principal("CN=alice");
        Subject s = userSubject(alice);
        assertTrue("must be a " + UserSubjectSupport.USER_SUBJECT_CLASS_NAME
                        + ", was " + s.getClass().getName(),
                UserSubjectSupport.USER_SUBJECT_CLASS.isInstance(s));
        assertTrue("must be read-only so callAs accepts it", s.isReadOnly());
        assertTrue(s.getPrincipals().contains(alice));
    }

    @Test
    public void newUserSubjectAlwaysProducesASubject() {
        // Holds on both platforms: the declared type never widens beyond Subject.
        Subject s = userSubject(new X500Principal("CN=carol"));
        assertNotNull(s);
        assertTrue(s.getPrincipals().contains(new X500Principal("CN=carol")));
    }

    // ------------------------------------------------------------------
    // toUserSubjectArray — fails closed
    // ------------------------------------------------------------------

    @Test
    public void toUserSubjectArrayProducesAGenuineUserSubjectArray() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject[] in = { userSubject(new X500Principal("CN=alice")),
                         userSubject(new X500Principal("CN=bob")) };
        Object out = UserSubjectSupport.toUserSubjectArray(in);
        assertEquals("component type must be UserSubject, not Subject",
                UserSubjectSupport.USER_SUBJECT_CLASS,
                out.getClass().getComponentType());
        assertEquals(2, Array.getLength(out));
        assertSame(in[0], Array.get(out, 0));
        assertSame(in[1], Array.get(out, 1));
    }

    @Test
    public void toUserSubjectArrayRejectsAPlainSubject() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject[] in = { userSubject(new X500Principal("CN=alice")),
                         plainSubject(true, new X500Principal("CN=bob")) };
        try {
            UserSubjectSupport.toUserSubjectArray(in);
            fail("a plain Subject must fail closed, not be silently dropped");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("subjects[1]"));
        }
    }

    @Test
    public void toUserSubjectArrayRejectsAMutableSubject() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject mutable = UserSubjectSupport.newUserSubject(false,
                Collections.singleton(new X500Principal("CN=bob")),
                Collections.emptySet(), Collections.emptySet());
        Subject[] in = { userSubject(new X500Principal("CN=alice")), mutable };
        try {
            UserSubjectSupport.toUserSubjectArray(in);
            fail("a mutable UserSubject must fail closed");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("read-only"));
        }
    }

    @Test
    public void toUserSubjectArrayRejectsANullElement() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject[] in = { userSubject(new X500Principal("CN=alice")), null };
        try {
            UserSubjectSupport.toUserSubjectArray(in);
            fail("a null element must fail closed");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("subjects[1]"));
        }
    }

    // ------------------------------------------------------------------
    // callAsAll — the behavioural proof
    // ------------------------------------------------------------------

    /**
     * The behavioural proof of the fix: with two user Subjects bound,
     * {@code Subject.currentAll()} <em>inside</em> the action must observe both.
     * Before the fix it observed exactly one.
     */
    @Test
    public void callAsAllBindsEveryIdentity() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        final X500Principal alice = new X500Principal("CN=alice");
        final X500Principal bob   = new X500Principal("CN=bob");
        Subject[] subjects = { userSubject(alice), userSubject(bob) };

        final int[] count = new int[1];
        @SuppressWarnings("unchecked")
        final Set<Principal>[] seen = new Set[1];
        UserSubjectSupport.callAsAll(subjects, () -> {
            count[0] = boundSubjectCount();
            seen[0] = boundPrincipals();
            return null;
        });

        assertEquals("both user Subjects must be bound simultaneously", 2, count[0]);
        Set<Principal> principals = seen[0];
        assertTrue("alice must be in the authorization context", principals.contains(alice));
        assertTrue("bob must be in the authorization context — dropping him is the bug",
                principals.contains(bob));
    }

    @Test
    public void callAsAllBindsThreeIdentities() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject[] subjects = { userSubject(new X500Principal("CN=a")),
                               userSubject(new X500Principal("CN=b")),
                               userSubject(new X500Principal("CN=c")) };
        int count = UserSubjectSupport.callAsAll(subjects,
                UserSubjectSupportTest::boundSubjectCount);
        assertEquals(3, count);
    }

    /** One Subject: unchanged behaviour, and it is genuinely bound. */
    @Test
    public void callAsAllWithOneSubjectBindsIt() throws Exception {
        final X500Principal alice = new X500Principal("CN=alice");
        Subject[] subjects = { userSubject(alice) };
        Subject seen = UserSubjectSupport.callAsAll(subjects, Subject::current);
        assertNotNull(seen);
        assertTrue(seen.getPrincipals().contains(alice));
    }

    /** No Subjects: the action runs directly, with nothing bound. */
    @Test
    public void callAsAllWithNoSubjectsRunsTheActionDirectly() throws Exception {
        Object result = UserSubjectSupport.callAsAll(new Subject[0], () -> "ran");
        assertEquals("ran", result);
    }

    /** Exceptions from the action propagate unwrapped through the reflective path. */
    @Test
    public void callAsAllPropagatesTheActionsException() {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Subject[] subjects = { userSubject(new X500Principal("CN=alice")),
                               userSubject(new X500Principal("CN=bob")) };
        try {
            UserSubjectSupport.callAsAll(subjects, () -> {
                throw new java.io.IOException("boom");
            });
            fail("the action's exception must propagate");
        } catch (Exception e) {
            Throwable t = e;
            // The JDK wraps action failures in CompletionException; unwrap.
            while (t.getCause() != null && !(t instanceof java.io.IOException)) {
                t = t.getCause();
            }
            assertTrue("expected the IOException, got " + e, t instanceof java.io.IOException);
            assertEquals("boom", t.getMessage());
        }
    }

    @Test(expected = NullPointerException.class)
    public void callAsAllRejectsNullSubjects() throws Exception {
        UserSubjectSupport.callAsAll(null, () -> "x");
    }

    @Test(expected = NullPointerException.class)
    public void callAsAllRejectsANullAction() throws Exception {
        UserSubjectSupport.callAsAll(new Subject[0], null);
    }
}
