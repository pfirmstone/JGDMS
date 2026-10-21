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

package org.apache.river.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies the documented {@link ThreadPool} contract: a task always sees
 * {@code Subject.current() == null}, whatever subject was active on the
 * submitting thread.
 *
 * <p>On a stock JDK a new virtual thread never inherits the submitter's
 * subject, so these tests pass trivially there.  They are only meaningful on
 * DirtyChai, where a thread captures {@code Subject.current()} of the thread
 * that constructs it.
 */
public class ThreadPoolSubjectTest {

    private static final long TIMEOUT_SECONDS = 30L;

    /** Submits a task that reports its ambient subject, from within {@code submitter}. */
    private static Subject subjectSeenByTask(ThreadPool pool, Subject submitter)
            throws Exception {
        final CompletableFuture<Object> seen = new CompletableFuture<>();
        final Runnable probe = () -> {
            try {
                // Wrap null: CompletableFuture would otherwise lose the distinction
                // between "task ran, subject null" and "task never ran".
                Subject s = Subject.current();
                seen.complete(s == null ? Boolean.FALSE : s);
            } catch (Throwable t) {
                seen.completeExceptionally(t);
            }
        };
        if (submitter == null) {
            pool.execute(probe, "subject probe");
        } else {
            Subject.callAs(submitter, (Callable<Void>) () -> {
                pool.execute(probe, "subject probe");
                return null;
            });
        }
        Object result = seen.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return result instanceof Subject ? (Subject) result : null;
    }

    @Test
    public void taskSubmittedUnderSubjectSeesNoSubject() throws Exception {
        ThreadPool pool = new ThreadPool();
        Subject alice = new Subject();
        assertNull("worker thread must not inherit the submitter's subject",
                subjectSeenByTask(pool, alice));
    }

    @Test
    public void taskSubmittedWithoutSubjectSeesNoSubject() throws Exception {
        ThreadPool pool = new ThreadPool();
        assertNull(subjectSeenByTask(pool, null));
    }

    @Test
    public void executeWithoutNameAlsoClearsSubject() throws Exception {
        ThreadPool pool = new ThreadPool();
        final CompletableFuture<Object> seen = new CompletableFuture<>();
        Subject.callAs(new Subject(), (Callable<Void>) () -> {
            pool.execute(() -> {
                Subject s = Subject.current();
                seen.complete(s == null ? Boolean.FALSE : s);
            });
            return null;
        });
        assertEquals(Boolean.FALSE, seen.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void submitterKeepsItsOwnSubject() throws Exception {
        ThreadPool pool = new ThreadPool();
        final Subject alice = new Subject();
        Subject.callAs(alice, (Callable<Void>) () -> {
            subjectSeenByTask(pool, alice);
            assertSame("submitting thread's subject must be unaffected",
                    alice, Subject.current());
            return null;
        });
    }
}
