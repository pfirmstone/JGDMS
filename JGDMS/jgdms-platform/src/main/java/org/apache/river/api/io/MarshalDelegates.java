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

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.resource.Service;
import org.apache.river.resource.ServiceConfigurationError;

/**
 * Loader-scoped resolver of {@link MarshalDelegate}s for the marshalling
 * engines.  Given the concrete {@link Class} the engine is about to write or has
 * resolved on read, {@link #delegateFor(Class)} returns the delegate that is
 * <em>co-loaded with that class</em> (same defining loader, hence same runtime
 * package), or {@code null} when there is none — in which case the caller falls
 * back to its existing reflective path (the class is public, or no delegate has
 * been written for its package yet).
 *
 * <h2>Why the defining loader, not just the package name</h2>
 * Package-private access is governed by the runtime package =
 * (package&nbsp;name, defining class loader).  In JGDMS the same package may be
 * present under several loaders (per-codebase / preferred loaders) and, under
 * OSGi, several bundles.  Selecting the delegate whose
 * {@code getClass().getClassLoader()} equals the target's defining loader and
 * that {@linkplain MarshalDelegate#serves(Class) serves} the target class gives,
 * in one predicate: correct package-private access (same runtime package), the
 * correct <em>version</em> (same defining loader), per-class scoping (a delegate
 * serves only the classes it lists, so fully-public or incomplete classes in the
 * same package fall back to reflection), and robustness to parent-loader
 * visibility.  It is also necessary because the OSGi path of
 * {@link Service} ignores the {@code ClassLoader} argument and returns
 * registry-wide providers; the post-filter here is what re-imposes loader
 * scoping in that case.
 *
 * <p>Discovery rides on {@code org.apache.river.resource.Service} — the
 * JGDMS service-lookup that predates {@code java.util.ServiceLoader} and already
 * bridges OSGi — so delegates are found uniformly on the classpath, on JPMS, and
 * under OSGi without {@code exports}/{@code opens} of their packages.
 *
 * <p>Results are memoised in a {@link ClassValue}, which is keyed by the
 * marshalled {@code Class} and collected with it (no loader leak).  Because
 * {@code ClassValue.computeValue} may not return {@code null}, a {@link #NONE}
 * sentinel records the "no delegate" outcome so negative results are cached too.
 *
 * @see MarshalDelegate
 * @see Service
 * @since 3.2.0
 */
public final class MarshalDelegates {

    private static final Logger logger =
            Logger.getLogger(MarshalDelegates.class.getName());

    private MarshalDelegates() { }

    /**
     * Cache sentinel for "no delegate serves this class's runtime package".
     * Never invoked — {@link #delegateFor(Class)} maps it back to {@code null}.
     */
    private static final MarshalDelegate NONE = new MarshalDelegate() {
        @Override
        public AtomicSerial.SerialForm[] serialForm(Class<?> c) {
            throw new AssertionError("NONE MarshalDelegate must never be invoked");
        }

        @Override
        public void serialize(Class<?> c, AtomicSerial.PutArg arg, Object o) {
            throw new AssertionError("NONE MarshalDelegate must never be invoked");
        }

        @Override
        public Object create(Class<?> c, AtomicSerial.GetArg arg) {
            throw new AssertionError("NONE MarshalDelegate must never be invoked");
        }

        @Override
        public Class<?>[] servedClasses() {
            throw new AssertionError("NONE MarshalDelegate must never be invoked");
        }
    };

    private static final ClassValue<MarshalDelegate> CACHE =
            new ClassValue<MarshalDelegate>() {
        @Override
        protected MarshalDelegate computeValue(Class<?> type) {
            MarshalDelegate d = resolve(type);
            return d != null ? d : NONE;
        }
    };

    /**
     * Returns the {@link MarshalDelegate} co-loaded with {@code c} (same defining
     * loader, same runtime package), or {@code null} if none is registered for
     * {@code c}'s package under {@code c}'s loader.  A {@code null} return tells
     * the caller to use its reflective fallback.
     *
     * @param c the concrete class about to be written, or resolved on read
     * @return the loader-scoped delegate, or {@code null}
     */
    public static MarshalDelegate delegateFor(Class<?> c) {
        if (c == null) {
            return null;
        }
        MarshalDelegate d = CACHE.get(c);
        return d == NONE ? null : d;
    }

    // ---- forbid the setAccessible reflective fallback (4.0.0: always enforced) ----
    //
    // A non-public @AtomicSerial class, member, or (GetArg) constructor that is reached
    // on the reflective fallback path -- i.e. no MarshalDelegate serves it -- is rejected:
    // it must be served by a MarshalDelegate (generated or hand-written), or be made
    // public. The setAccessible fallback and its opt-out toggle have been removed; this
    // is now unconditional.

    /**
     * Gate for a static contract member ({@code serialForm} / {@code serialize})
     * reached on the reflective fallback (no delegate). Returns a diagnostic message
     * when {@code c} is not public -- and therefore unreachable without
     * {@code setAccessible}, which is no longer permitted -- otherwise {@code null}.
     * A {@code null} return means a public member of a public class, reachable
     * reflectively without {@code setAccessible}.
     *
     * @param c      the {@code @AtomicSerial} class whose member is being invoked
     * @param member a human label for the member, e.g. {@code "serialForm()"}
     * @return a violation message, or {@code null} if permitted
     */
    public static String strictBlockClass(Class<?> c, String member) {
        if (!Modifier.isPublic(c.getModifiers())) {
            return strictMessage(c, member);
        }
        return null;
    }

    /**
     * Gate for the {@code (GetArg)} constructor reached on the reflective fallback.
     * Blocks when the constructor or its declaring class is not public (so reflective
     * construction would need {@code setAccessible}, which is no longer permitted).
     *
     * @param c             the {@code @AtomicSerial} class being constructed
     * @param ctorModifiers the modifiers of its {@code (GetArg)} constructor
     * @return a violation message, or {@code null} if permitted
     */
    public static String strictBlockCtor(Class<?> c, int ctorModifiers) {
        if (!(Modifier.isPublic(ctorModifiers) && Modifier.isPublic(c.getModifiers()))) {
            return strictMessage(c, "(GetArg) constructor");
        }
        return null;
    }

    private static String strictMessage(Class<?> c, String member) {
        return member + " of @AtomicSerial class " + c.getName()
                + " is not reachable without setAccessible, and no MarshalDelegate is "
                + "registered for package " + c.getPackageName()
                + "; add a MarshalDelegate for this package (or make the class and "
                + "member public). See " + MarshalDelegate.class.getName() + ".";
    }

    /**
     * Performs the loader-scoped lookup.  Any {@link ServiceConfigurationError}
     * — a malformed provider list, or a provider that cannot be instantiated —
     * must never break marshalling, so it is swallowed and reported as "no
     * delegate", leaving the caller on its reflective path.
     */
    private static MarshalDelegate resolve(Class<?> c) {
        final ClassLoader loader = c.getClassLoader();
        try {
            Iterator<MarshalDelegate> it =
                    Service.providers(MarshalDelegate.class, loader);
            while (it.hasNext()) {
                MarshalDelegate d = it.next();
                // Defining-loader filter (only a co-loaded delegate can serve c,
                // and co-loading gives correct package-private access and the
                // correct version) plus the delegate's own served-class set.
                // Classes no delegate serves -- fully-public classes, or classes
                // in not-yet-delegated or mixed packages -- resolve to null and
                // use the reflective path.
                if (d.getClass().getClassLoader() == loader && d.serves(c)) {
                    return d;
                }
            }
        } catch (ServiceConfigurationError e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "MarshalDelegate discovery failed for " + c.getName()
                        + "; using reflective fallback", e);
            }
        }
        return null;
    }
}
