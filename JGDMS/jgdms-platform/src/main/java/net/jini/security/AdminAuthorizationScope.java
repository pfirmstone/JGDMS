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
 * <h2>The bind path is seam-guarded (2026-07-21 replay fix)</h2>
 * <strong>Binding is a privileged operation.</strong>  The carrier's provenance
 * token proves only that <em>a seam minted this carrier for some caller at some
 * time</em> &mdash; it does <em>not</em> prove the carrier belongs to the
 * <em>current</em> call.  So a token check alone does not stop a
 * <em>replay</em>: hosted code running inside a genuine admin dispatch can read
 * the live, genuinely seam-minted carrier via the public {@link #currentCarrier()},
 * stash it, and later re-{@linkplain #bindCarrier bind} it around a call on a
 * directly-held admin reference &mdash; the gate would then see
 * {@code isSeamMinted() == true} and authorize (a bind-to-known-good gap).  To
 * close that, {@link #bindCarrier} (the sole method that binds a carrier into
 * this {@code ScopedValue}, and hence {@link #runWithCarrier}, which routes
 * through it) performs a {@link SecurityManager#checkPermission(java.security.Permission)
 * permission check} for {@link AdminAuthorizationMintPermission} <em>with the
 * caller on the stack</em>, exactly mirroring how
 * {@link AdminAuthorizationContext#seamMint} guards <em>minting</em>.  The
 * deployed policy grants that permission only to the trusted seam code
 * source(s), so hosted code calling {@code bindCarrier}/{@code runWithCarrier}
 * directly (a replay) is refused before the carrier is ever bound, and the gate
 * never sees it.
 *
 * <p>The bind reuses {@code AdminAuthorizationMintPermission("mint")} rather
 * than a distinct bind permission: minting and binding are the <em>same</em>
 * trust boundary (only the JERI seam does either), so a single grant to the seam
 * code sources covers both, and there is no principal that should legitimately
 * hold one capability but not the other.  A finer sibling permission would only
 * enlarge the deployment surface without drawing a real privilege boundary.
 *
 * <h2>Defence-in-depth, not the sole guarantee</h2>
 * The provenance token remains the defence against <em>forgery</em> (a carrier
 * hosted code fabricated via the public {@link AdminAuthorizationContext}
 * constructor is not seam-minted and the gate rejects it &mdash; see
 * {@link AdminAuthorizationMintPermission}).  The bind guard above is the
 * defence against <em>replay</em> of a genuine carrier.  Together they bind
 * authorization to the current caller of the current operation.
 *
 * <h2>Child-thread propagation</h2>
 * A {@code ScopedValue} binding is visible to the binding thread and, within the
 * dispatch's dynamic extent, to tasks it forks through <em>structured
 * concurrency</em> (a {@code StructuredTaskScope} fork inherits the binding).
 * That inheritance is acceptable: such a fork is the admin dispatch's own work,
 * running inside the guarded invocation.  What does <em>not</em> inherit the
 * binding is an <em>unstructured</em> thread start &mdash; a bare
 * {@code new Thread(...).start()} or a manually started virtual thread
 * ({@code Thread.ofVirtual().start(...)}): those begin with no binding, so a
 * worker thread the invoked method spawns that way does not carry admin
 * authority.  We rely on the binding reaching exactly the dispatch thread's
 * synchronous call tree (plus any structured forks of it) and deliberately do
 * not use an {@code InheritableThreadLocal}-style broadcast to unrelated
 * threads.
 *
 * <h2>Reflective access</h2>
 * {@code jgdms-platform} compiles at {@code --release 21}, where
 * {@code java.lang.ScopedValue} is a <em>preview</em> API (finalized in JDK 25);
 * referencing it directly would force {@code --enable-preview} onto this core
 * module.  We therefore bind and read the value reflectively (the same
 * release-portability discipline the gate uses for {@code Subject.callAs}).  On
 * a JDK that lacks {@code ScopedValue} the conveyance degrades safely:
 * {@link #bindCarrier} still performs the seam permission check (so the bind
 * path stays seam-only) but returns a {@code null} handle, {@link #runBound}
 * then runs the action with no binding, and {@link #currentCarrier} returns
 * {@code null} &mdash; so the gate simply sees no carrier and fails closed.
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

    /**
     * The seam-only permission the bind path requires, deliberately the SAME
     * permission {@link AdminAuthorizationContext#seamMint} checks for minting:
     * minting and binding are one trust boundary (the JERI seam), so a single
     * policy grant to the seam code sources covers both.  Granted only to the
     * seam; hosted code calling {@link #bindCarrier} (a replay) is refused.
     */
    private static final AdminAuthorizationMintPermission BIND_PERMISSION =
            new AdminAuthorizationMintPermission("mint");

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
     * Seam-only guard on the bind path.  Performs
     * {@code SecurityManager.checkPermission(AdminAuthorizationMintPermission("mint"))}
     * <em>with the caller on the stack</em> &mdash; deliberately <strong>not</strong>
     * inside any {@code doPrivileged} of this class, so the caller's protection
     * domain(s) are evaluated: hosted code that lacks the seam grant is refused.
     * The seam invokes {@link #bindCarrier} from within its own
     * {@code doPrivileged} (see {@code BasicInvocationDispatcher}) so that only
     * the seam domains are evaluated and the requirement does not go viral to the
     * frames below dispatch.  A no-op when no {@code SecurityManager} is installed
     * &mdash; but so is the whole admin gate then (it denies every caller), so an
     * unguarded bind is inert.
     */
    private static void checkSeamBindPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(BIND_PERMISSION);
        }
    }

    /**
     * Seam-guarded bind step.  Checks the seam bind permission (see
     * {@link #checkSeamBindPermission}) and, if permitted, produces an opaque
     * <em>binding handle</em> for {@code carrier} that {@link #runBound} activates
     * for the dynamic extent of a subsequent action.  Splitting the bind (this
     * method) from running the action ({@link #runBound}) lets the seam perform
     * only the bind under {@code doPrivileged} &mdash; satisfying the seam-only
     * permission check without privileging the invoked method itself.
     *
     * <p>Only the trusted JERI seam holds the required permission, so only the
     * seam can obtain a non-{@code null} handle; hosted code attempting to
     * <em>replay</em> a captured carrier by calling this method directly is
     * refused here, before any binding exists.
     *
     * @param carrier the seam-minted carrier to convey (may be {@code null}, in
     *        which case a {@code null} carrier is bound &mdash; the gate then sees
     *        no identity and fails closed)
     * @return an opaque handle to pass to {@link #runBound}, or {@code null} on a
     *         JDK without {@code ScopedValue} (in which case {@link #runBound}
     *         simply runs the action with no binding)
     * @throws SecurityException if a {@code SecurityManager} is installed and the
     *         caller is not granted {@link AdminAuthorizationMintPermission}
     */
    public static Object bindCarrier(AdminAuthorizationContext carrier) {
        checkSeamBindPermission();
        if (KEY == null) {
            return null;   // no ScopedValue: runBound will run unbound (fail closed)
        }
        try {
            return WHERE.invoke(null, KEY, carrier);
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
     * Runs {@code action} on the calling thread with the binding produced by
     * {@link #bindCarrier} active for its dynamic extent.  Performs <em>no</em>
     * permission check and asserts <em>no</em> privilege: the guard is on
     * {@link #bindCarrier}, which alone can produce a {@code handle} that binds
     * this class's private {@code ScopedValue}.  A {@code null} handle (no
     * {@code ScopedValue} on this JDK) runs the action unbound.  A handle for an
     * unrelated {@code ScopedValue} cannot be fabricated by callers &mdash; the
     * key is private &mdash; so this method cannot be used to bind an
     * attacker-supplied carrier.
     *
     * @param handle the handle returned by {@link #bindCarrier}, or {@code null}
     * @param action the dispatch action to run under the binding; must not be
     *        {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public static void runBound(Object handle, Runnable action) {
        if (action == null) {
            throw new NullPointerException("action");
        }
        if (handle == null) {
            action.run();
            return;
        }
        try {
            RUN.invoke(handle, action);
        } catch (InvocationTargetException ite) {
            Throwable c = ite.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new IllegalStateException("admin carrier run failed", c);
        } catch (IllegalAccessException iae) {
            throw new IllegalStateException("ScopedValue not accessible", iae);
        }
    }

    /**
     * Convenience single-shot bind-and-run: {@code runBound(bindCarrier(carrier),
     * action)}.  Guarded exactly like {@link #bindCarrier} (the seam-only bind
     * permission is checked with the caller on the stack), so a non-seam caller
     * is refused.  The seam itself uses the {@link #bindCarrier}/{@link #runBound}
     * split so it can confine the bind (and its permission check) to a
     * {@code doPrivileged} without privileging the invoked method; this method is
     * for callers that do not need that separation (e.g. tests).
     *
     * @param carrier the seam-minted carrier to convey (may be {@code null})
     * @param action  the dispatch action to run under the binding; must not be
     *        {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     * @throws SecurityException if a {@code SecurityManager} is installed and the
     *         caller is not granted {@link AdminAuthorizationMintPermission}
     */
    public static void runWithCarrier(AdminAuthorizationContext carrier,
                                      Runnable action) {
        if (action == null) {
            throw new NullPointerException("action");
        }
        runBound(bindCarrier(carrier), action);
    }

    /**
     * @return the admin carrier the seam bound for the current call via
     *         {@link #bindCarrier}/{@link #runWithCarrier}, or {@code null} if
     *         none is bound (not in a seam dispatch, or this JDK has no
     *         {@code ScopedValue}).  Never throws: any failure resolves to
     *         {@code null} (fail closed).
     *
     * <p>Read is intentionally left <strong>public and unguarded</strong>.  It
     * must be public because the gate lives in a different package
     * ({@code au.net.zeus.jgdms.loader.isolation}).  Leaving it unguarded is
     * benign: the carrier exposes only principal <em>identities</em> in
     * {@code null}-class-loader domains (no live {@code ClassLoader} capability),
     * so reading it discloses nothing an authorized caller could not already see;
     * and reading it confers no authority, because <em>using</em> a read carrier
     * to authorize requires re-binding it, and the bind path
     * ({@link #bindCarrier}) is seam-guarded against exactly that replay.
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
