/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Concurrency regression for the {@code obtain()} / handout TOCTOU an
 * adversarial board review reproduced: a 6-thread, 20&nbsp;000-round stress
 * racing {@link SubProcessPool#obtain} against a concurrent last-reference
 * teardown ({@link SubProcessHandle#referenceRetired}) produced 11 dead-handle
 * handouts and 23 {@link IllegalStateException}s before the fix, because
 * {@code obtain()} did a check-then-return with no reference pinned atomically:
 * between {@code obtain()} returning and the caller's separate
 * {@code hostReference()}, a concurrent last-reference retirement could tear the
 * handle down.
 *
 * <p>With {@code obtain()} pinning a {@link SubProcessHandle#reserve()
 * reservation} atomically before returning (teardown suppressed while any
 * reservation is outstanding), no caller can ever receive a handle that dies in
 * that window: this test asserts <strong>zero</strong> dead-handle handouts and
 * <strong>zero</strong> unexpected exceptions, and that spawn/shutdown
 * accounting stays exactly balanced across all the respawns the race drives.
 */
public class IsolationPoolConcurrencyTest {

    private static final int THREADS = 6;
    private static final int ROUNDS = 20_000;

    private static Principal name(final String n) {
        return new Principal() {
            public String getName() { return n; }
            public boolean equals(Object o) {
                return o instanceof Principal && n.equals(((Principal) o).getName());
            }
            public int hashCode() { return n.hashCode(); }
            public String toString() { return n; }
        };
    }

    static final class FakeAdmin implements SubProcessAdministrable {
        public PolicyAdmin getSubProcessPolicyAdmin() throws RemoteException {
            throw new SecurityException("fail-closed (test stub)");
        }
    }

    /** Counts launches and shutdowns; each launch is a distinct Spawned. */
    static final class CountingLauncher implements SubProcessLauncher {
        final AtomicInteger launches = new AtomicInteger();
        final AtomicInteger shutdowns = new AtomicInteger();
        public Spawned launch(final IsolationPoolingKey key) {
            launches.incrementAndGet();
            final FakeAdmin admin = new FakeAdmin();
            return new Spawned() {
                public SubProcessAdministrable adminSurface() { return admin; }
                public void shutdown() { shutdowns.incrementAndGet(); }
            };
        }
    }

    private static IsolationPoolingKey key(String spiffe) throws IOException {
        return IsolationPoolingKey.derive(new Principal[]{ name(spiffe) });
    }

    /**
     * The reviewer's exact stress shape: many threads racing {@code obtain()}
     * against a concurrent last-reference teardown on the <em>same</em> key,
     * over a large round count.  Each round mirrors the real caller contract
     * &mdash; {@code obtain()} then a <em>separate</em> {@code hostReference()}
     * &mdash; then retires the reference (which, when it empties the set with no
     * outstanding reservation, tears the subprocess down, forcing the next
     * {@code obtain()} to respawn).  The fix must make every handed-out handle
     * live-and-pinned at the moment of return.
     */
    @Test
    public void obtainNeverHandsOutADeadOrTearingDownHandle() throws Exception {
        final CountingLauncher launcher = new CountingLauncher();
        final SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        final SubProcessPool pool = new SubProcessPool(launcher, registry);
        final IsolationPoolingKey k = key("spiffe://example/race");

        final AtomicInteger rounds = new AtomicInteger();
        final AtomicInteger deadHandouts = new AtomicInteger();
        final AtomicInteger illegalState = new AtomicInteger();
        final AtomicReference<Throwable> unexpected = new AtomicReference<Throwable>();
        final CyclicBarrier startLine = new CyclicBarrier(THREADS);

        Thread[] workers = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            workers[t] = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        startLine.await();
                    } catch (Exception e) {
                        unexpected.compareAndSet(null, e);
                        return;
                    }
                    while (rounds.getAndIncrement() < ROUNDS) {
                        try {
                            SubProcessHandle h = pool.obtain(k);
                            // A handle handed out must already be pinned live;
                            // a dead handout would mean the TOCTOU is open.
                            if (!h.isAlive()) {
                                deadHandouts.incrementAndGet();
                            }
                            Object id = new Object();
                            try {
                                // The separate-pin step: must never see a
                                // torn-down handle now that obtain() reserved.
                                h.hostReference(id);
                            } catch (IllegalStateException ise) {
                                illegalState.incrementAndGet();
                                continue;
                            }
                            // Retire it: the last retirement (empty set, no
                            // reservation) races the next obtain()'s reuse.
                            h.referenceRetired(id,
                                SubProcessHandle.RetirementReason.CLEAN_CALL_PROCESSED);
                        } catch (Throwable other) {
                            unexpected.compareAndSet(null, other);
                            return;
                        }
                    }
                }
            }, "obtain-race-" + t);
        }
        for (Thread w : workers) w.start();
        for (Thread w : workers) w.join();

        if (unexpected.get() != null) {
            throw new AssertionError("unexpected exception during stress",
                    unexpected.get());
        }
        assertEquals("obtain() must never hand out a dead handle",
                0, deadHandouts.get());
        assertEquals("hostReference() after obtain() must never hit a"
                + " torn-down handle (TOCTOU closed)", 0, illegalState.get());

        // Every launch is either a currently-pooled live handle or a torn-down
        // one shut down exactly once: launches == shutdowns + pooled(0 or 1).
        int pooled = pool.size();
        assertTrue("at most one live handle may remain for the key",
                pooled == 0 || pooled == 1);
        assertEquals("spawn/shutdown accounting must balance across respawns",
                launcher.launches.get(), launcher.shutdowns.get() + pooled);
        assertTrue("the race must actually have driven respawns/teardowns",
                launcher.launches.get() > 1);
    }
}
