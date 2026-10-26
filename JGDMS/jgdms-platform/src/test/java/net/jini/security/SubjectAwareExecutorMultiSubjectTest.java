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

package net.jini.security;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.apache.river.api.security.UserSubjectSupport;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Behavioural proof that {@link SubjectAwareExecutor} carries <em>every</em>
 * submitting-thread user {@code Subject} onto the worker thread.
 *
 * <p>Before the multi-Subject {@code callAs} lookup was fixed, the executor's
 * cached {@code Method} was always {@code null} (it asked for the non-existent
 * {@code callAs(Callable, Subject[])} signature), so
 * {@code SubjectAwareExecutor.callAsSubjects} always took the "first Subject
 * only" branch: a task submitted under two identities observed exactly one on
 * the worker thread, silently narrowing the authorization context.
 *
 * <p>Reflective throughout for {@code UserSubject}/{@code Subject.currentAll()},
 * so this compiles on a stock OpenJDK and self-skips there.
 */
public class SubjectAwareExecutorMultiSubjectTest {

    private static final X500Principal ALICE = new X500Principal("CN=alice");
    private static final X500Principal BOB   = new X500Principal("CN=bob");

    private ExecutorService delegate;
    private SubjectAwareExecutor executor;

    @Before
    public void setUp() {
        delegate = Executors.newFixedThreadPool(2);
        executor = new SubjectAwareExecutor(delegate);
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------
    // Independent probes (not routed through the class under test)
    // ------------------------------------------------------------------

    private static boolean dirtyChai() {
        try {
            Class<?> us = Class.forName("javax.security.auth.UserSubject");
            Subject.class.getMethod("callAs", Callable.class,
                    Array.newInstance(us, 0).getClass());
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException notDirtyChai) {
            return false;
        }
    }

    private static Method currentAll() throws NoSuchMethodException {
        return Subject.class.getMethod("currentAll");
    }

    private static Subject userSubject(Principal... principals) {
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return UserSubjectSupport.newUserSubject(
                true, ps, Collections.emptySet(), Collections.emptySet());
    }

    /** Principals of every Subject bound on the calling thread. */
    private static Set<Principal> boundPrincipals() throws Exception {
        Subject[] all = (Subject[]) currentAll().invoke(null);
        Set<Principal> ps = new LinkedHashSet<>();
        if (all != null) {
            for (Subject s : all) ps.addAll(s.getPrincipals());
        }
        return ps;
    }

    /** Runs {@code action} with both ALICE and BOB bound as separate user Subjects. */
    private static <V> V underTwoSubjects(Callable<V> action) throws Exception {
        Subject[] subjects = { userSubject(ALICE), userSubject(BOB) };
        return UserSubjectSupport.callAsAll(subjects, action);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * The submitting thread really does have two identities bound — otherwise
     * the assertions below would be vacuous.
     */
    @Test
    public void submittingThreadHasBothIdentitiesBound() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Set<Principal> ps = underTwoSubjects(
                SubjectAwareExecutorMultiSubjectTest::boundPrincipals);
        assertTrue(ps.contains(ALICE));
        assertTrue(ps.contains(BOB));
    }

    /** {@code submit(Callable)} must restore all captured identities. */
    @Test
    public void submitCallableCarriesEveryUserSubject() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());

        Future<Set<Principal>> f = underTwoSubjects(() ->
                executor.submit(
                    (Callable<Set<Principal>>)
                        SubjectAwareExecutorMultiSubjectTest::boundPrincipals));

        Set<Principal> onWorker = f.get(60, TimeUnit.SECONDS);
        assertTrue("alice must reach the worker thread", onWorker.contains(ALICE));
        assertTrue("bob must reach the worker thread — dropping him is the bug",
                onWorker.contains(BOB));
    }

    /** {@code submit(Runnable)} must restore all captured identities. */
    @Test
    public void submitRunnableCarriesEveryUserSubject() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());

        @SuppressWarnings("unchecked")
        final Set<Principal>[] onWorker = new Set[1];
        final Throwable[] failure = new Throwable[1];

        Future<?> f = underTwoSubjects(() -> executor.submit((Runnable) () -> {
            try {
                onWorker[0] = boundPrincipals();
            } catch (Exception e) {
                failure[0] = e;
            }
        }));
        f.get(60, TimeUnit.SECONDS);

        assertNull(String.valueOf(failure[0]), failure[0]);
        assertNotNull(onWorker[0]);
        assertTrue("alice must reach the worker thread", onWorker[0].contains(ALICE));
        assertTrue("bob must reach the worker thread — dropping him is the bug",
                onWorker[0].contains(BOB));
    }

    /** Exactly two Subjects, not one merged Subject and not a truncated set. */
    @Test
    public void workerThreadSeesTheSameNumberOfSubjects() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());

        Future<Integer> f = underTwoSubjects(() ->
                executor.submit((Callable<Integer>) () -> {
                    Subject[] all = (Subject[]) currentAll().invoke(null);
                    return all == null ? 0 : all.length;
                }));

        assertEquals("both user Subjects must be restored on the worker thread",
                Integer.valueOf(2), f.get(60, TimeUnit.SECONDS));
    }

    /** Single-Subject submission is unchanged: the one identity is carried over. */
    @Test
    public void singleSubjectIsStillCarried() throws Exception {
        Subject[] one = { userSubject(ALICE) };
        Future<Subject> f = UserSubjectSupport.callAsAll(one, () ->
                executor.submit((Callable<Subject>) Subject::current));
        Subject onWorker = f.get(60, TimeUnit.SECONDS);
        assertNotNull("the submitting identity must reach the worker thread", onWorker);
        assertTrue(onWorker.getPrincipals().contains(ALICE));
    }

    /** With no identity bound, submission still works and binds nothing. */
    @Test
    public void noSubjectSubmissionStillRuns() throws Exception {
        Future<String> f = executor.submit(() -> "ran");
        assertEquals("ran", f.get(60, TimeUnit.SECONDS));
    }
}
