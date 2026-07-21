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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Dedicated {@link java.lang.ScopedValue}-based conveyance of the current remote
 * call's {@link AdminAuthorizationContext} from the trusted JERI dispatch seam
 * to the pure admin gate
 * ({@code au.net.zeus.jgdms.loader.isolation.AdminPrincipalAuthenticator}).
 *
 * <h2>Why not the {@code ServerContext} ThreadLocal?</h2>
 * The prior handoff published the carrier as a
 * {@link net.jini.export.ServerContext} element, i.e. through a bare
 * {@code ThreadLocal} whose {@code doWithServerContext} setter performs no
 * permission check &mdash; so any code could publish an element, and a
 * {@code ThreadLocal} does not propagate correctly under virtual threads
 * ({@code ThreadLocal} is banned for that reason).  This class instead binds a
 * private {@link java.lang.ScopedValue} for the dynamic extent of the dispatch;
 * the gate reads it back.  It is a <em>dedicated</em> conveyance for this one
 * carrier &mdash; the broad {@code ServerContext} public API is left untouched.
 *
 * <h2>Defence-in-depth, not the load-bearing guarantee</h2>
 * The conveyance is confidentiality/plumbing hardening only.  The
 * <em>authorization</em> guarantee rests entirely on the carrier's provenance
 * token: even if hosted code could reference this {@code ScopedValue} key and
 * bind a forged carrier, the gate rejects any carrier for which
 * {@link AdminAuthorizationContext#isSeamMinted()} is false.  See
 * {@link AdminAuthorizationMintPermission}.
 *
 * <h2>Child-thread propagation</h2>
 * A {@code ScopedValue} binding is visible to the binding thread and to threads
 * it forks through structured concurrency; it is <em>not</em> inherited by
 * unstructured {@code new Thread()} starts.  For the admin carrier this is the
 * desired confinement: the gate is only ever invoked <em>synchronously</em> on
 * the dispatch thread (during the guarded remote method invocation), and a
 * worker thread the invoked method may spawn must NOT inherit admin authority.
 * We therefore rely on the binding reaching exactly the dispatch thread's
 * synchronous call tree and deliberately do not use any inheritable mechanism.
 *
 * <h2>Reflective access</h2>
 * {@code jgdms-platform} compiles at {@code --release 21}, where
 * {@code java.lang.ScopedValue} is a <em>preview</em> API (finalized in JDK 25);
 * referencing it directly would force {@code --enable-preview} onto this core
 * module.  We therefore bind and read the value reflectively (the same
 * release-portability discipline the gate uses for {@code Subject.callAs}).  On
 * a JDK that lacks {@code ScopedValue} the conveyance degrades safely:
 * {@link #runWithCarrier} runs the action with no binding and
 * {@link #currentCarrier} returns {@code null}, so the gate simply sees no
 * carrier and fails closed.
 *
 * @since 3.1.1
 */
public final class AdminAuthorizationScope {

    /** The {@code ScopedValue<AdminAuthorizationContext>} key, or {@code null}
     *  if this JDK has no {@code ScopedValue}. */
    private static final Object KEY;
    /** {@code ScopedValue.where(ScopedValue, Object) -> ScopedValue.Carrier}. */
    private static final Method WHERE;
    /** {@code ScopedValue.Carrier.run(Runnable)}. */
    private static final Method RUN;
    /** {@code ScopedValue.isBound()}. */
    private static final Method IS_BOUND;
    /** {@code ScopedValue.get()}. */
    private static final Method GET;

    static {
        Object key = null;
        Method where = null, run = null, isBound = null, get = null;
        try {
            Class<?> sv = Class.forName("java.lang.ScopedValue");
            key = sv.getMethod("newInstance").invoke(null);
            where = sv.getMethod("where", sv, Object.class);
            Class<?> carrier = Class.forName("java.lang.ScopedValue$Carrier");
            run = carrier.getMethod("run", Runnable.class);
            isBound = sv.getMethod("isBound");
            get = sv.getMethod("get");
        } catch (Throwable noScopedValue) {
            // Pre-21 / ScopedValue absent: degrade to no binding (fail closed).
            key = null; where = null; run = null; isBound = null; get = null;
        }
        KEY = key;
        WHERE = where;
        RUN = run;
        IS_BOUND = isBound;
        GET = get;
    }

    private AdminAuthorizationScope() { }

    /**
     * Binds {@code carrier} as the current admin carrier for the dynamic extent
     * of {@code action} and runs {@code action} on the calling thread.  Only the
     * trusted seam calls this; the value bound here is what
     * {@link #currentCarrier()} returns inside {@code action}'s synchronous call
     * tree.
     *
     * @param carrier the seam-minted carrier to convey (may be {@code null}, in
     *        which case a {@code null} carrier is bound &mdash; the gate then
     *        sees no identity and fails closed)
     * @param action  the dispatch action to run under the binding; must not be
     *        {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public static void runWithCarrier(AdminAuthorizationContext carrier,
                                      Runnable action) {
        if (action == null) {
            throw new NullPointerException("action");
        }
        if (KEY == null) {
            // No ScopedValue on this JDK: run without a binding.  The gate will
            // see no carrier and fail closed -- safe, if the subsystem is unusable.
            action.run();
            return;
        }
        try {
            Object boundCarrier = WHERE.invoke(null, KEY, carrier);
            RUN.invoke(boundCarrier, action);
        } catch (InvocationTargetException ite) {
            Throwable c = ite.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new IllegalStateException("admin carrier binding failed", c);
        } catch (IllegalAccessException iae) {
            // ScopedValue methods are public; should never happen.
            throw new IllegalStateException("ScopedValue not accessible", iae);
        }
    }

    /**
     * @return the admin carrier the seam bound for the current call via
     *         {@link #runWithCarrier}, or {@code null} if none is bound (not in a
     *         seam dispatch, or this JDK has no {@code ScopedValue}).  Never
     *         throws: any failure resolves to {@code null} (fail closed).
     */
    public static AdminAuthorizationContext currentCarrier() {
        if (KEY == null) {
            return null;
        }
        try {
            Object bound = IS_BOUND.invoke(KEY);
            if (!Boolean.TRUE.equals(bound)) {
                return null;
            }
            return (AdminAuthorizationContext) GET.invoke(KEY);
        } catch (Exception failClosed) {
            return null;
        }
    }
}
