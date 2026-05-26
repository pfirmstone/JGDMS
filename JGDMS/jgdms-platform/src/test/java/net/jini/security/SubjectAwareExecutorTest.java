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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.Subject;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link SubjectAwareExecutor}.
 *
 * @since 3.1.0
 */
public class SubjectAwareExecutorTest {

    /** A minimal Subject usable with Subject.callAs(). */
    private static Subject makeSubject(String principalName) {
        Subject s = new Subject();
        s.getPrincipals().add((java.security.Principal) () -> principalName);
        return s;
    }

    private ExecutorService pool;
    private ExecutorService safePool;

    @After
    public void shutdown() throws Exception {
        if (safePool != null) {
            safePool.shutdown();
            safePool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void constructor_nullDelegate_throws() {
        new SubjectAwareExecutor(null);
    }

    // -----------------------------------------------------------------------
    // Subject propagation via execute(Runnable)
    // -----------------------------------------------------------------------

    @Test
    public void execute_propagatesSubjectToWorkerThread() throws Exception {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);

        Subject subject = makeSubject("alice");
        AtomicReference<String> captured = new AtomicReference<>();

        // Run inside Subject.callAs so Subject.current() returns "alice".
        Subject.callAs(subject, () -> {
            safePool.execute(() ->
                    captured.set(Subject.current() != null
                            ? Subject.current().getPrincipals().iterator().next().getName()
                            : null));
            return null;
        });

        // Wait for the task to complete.
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals("Subject principal should be propagated to worker thread",
                "alice", captured.get());
    }

    @Test
    public void execute_noSubject_propagatesNull() throws Exception {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);

        AtomicReference<Subject> captured = new AtomicReference<>();
        // Submit with no Subject bound.
        safePool.execute(() -> captured.set(Subject.current()));

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertNull("No Subject bound at submission should result in null on worker thread",
                captured.get());
    }

    // -----------------------------------------------------------------------
    // Subject propagation via submit(Callable)
    // -----------------------------------------------------------------------

    @Test
    public void submit_callable_propagatesSubject() throws Exception {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);

        Subject subject = makeSubject("bob");

        Future<String> future = Subject.callAs(subject, () ->
                safePool.submit(() ->
                        Subject.current() != null
                                ? Subject.current().getPrincipals().iterator().next().getName()
                                : null));

        assertEquals("bob", future.get(5, TimeUnit.SECONDS));
    }

    // -----------------------------------------------------------------------
    // Exception propagation
    // -----------------------------------------------------------------------

    @Test
    public void submit_callable_checkedExceptionPropagatedThroughFuture() throws Exception {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);

        Exception sentinelException = new Exception("sentinel checked exception");
        Future<Void> future = safePool.submit((Callable<Void>) () -> {
            throw sentinelException;
        });

        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Expected ExecutionException");
        } catch (ExecutionException ee) {
            assertSame("Original checked exception should be the cause",
                    sentinelException, ee.getCause());
        }
    }

    @Test
    public void submit_runnable_runtimeExceptionPropagated() throws Exception {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);

        RuntimeException sentinelException = new RuntimeException("sentinel runtime");
        Future<?> future = safePool.submit((Runnable) () -> {
            throw sentinelException;
        });

        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Expected ExecutionException");
        } catch (ExecutionException ee) {
            assertSame("Original RuntimeException should be the cause",
                    sentinelException, ee.getCause());
        }
    }

    // -----------------------------------------------------------------------
    // invokeAll
    // -----------------------------------------------------------------------

    @Test
    public void invokeAll_propagatesSubjectToAllTasks() throws Exception {
        pool = Executors.newFixedThreadPool(2);
        safePool = new SubjectAwareExecutor(pool);

        Subject subject = makeSubject("carol");

        List<Callable<String>> tasks = Arrays.asList(
                () -> Subject.current() != null
                        ? Subject.current().getPrincipals().iterator().next().getName()
                        : null,
                () -> Subject.current() != null
                        ? Subject.current().getPrincipals().iterator().next().getName()
                        : null);

        List<Future<String>> futures = Subject.callAs(subject,
                () -> safePool.invokeAll(tasks));

        for (Future<String> f : futures) {
            assertEquals("carol", f.get(5, TimeUnit.SECONDS));
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle delegation
    // -----------------------------------------------------------------------

    @Test
    public void shutdown_delegatesToUnderlying() {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);
        safePool.shutdown();
        assertTrue("isShutdown() should delegate", safePool.isShutdown());
    }

    @Test
    public void shutdownNow_delegatesToUnderlying() {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);
        List<Runnable> unrun = safePool.shutdownNow();
        assertNotNull(unrun);
        assertTrue("isShutdown() should delegate", safePool.isShutdown());
    }

    @Test
    public void isTerminated_delegatesToUnderlying() throws Exception {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);
        safePool.shutdown();
        assertTrue(safePool.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue("isTerminated() should delegate", safePool.isTerminated());
    }

    // -----------------------------------------------------------------------
    // Null task guards
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void execute_nullTask_throws() {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);
        safePool.execute(null);
    }

    @Test(expected = NullPointerException.class)
    public void submitCallable_nullTask_throws() {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);
        safePool.submit((Callable<Void>) null);
    }

    @Test(expected = NullPointerException.class)
    public void invokeAll_nullCollection_throws() throws InterruptedException {
        pool = Executors.newSingleThreadExecutor();
        safePool = new SubjectAwareExecutor(pool);
        safePool.invokeAll(null);
    }
}
