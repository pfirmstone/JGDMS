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
package net.jini.jeri.ssl;

import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs in a JVM of its own, started by {@code TimeoutSchedulerPermissionTest}:
 * initialises and uses a copy of the connection timeout scheduler, as a
 * security manager would see it, and reports the permissions that are
 * demanded.
 *
 * <p>The copy of the scheduler and of the TLS watchdog is defined in a
 * protection domain that stands for jgdms-jeri's, with the permissions named on
 * the command line (or all of them, for {@code ALL}).  The code that starts
 * the scheduler runs in a domain with no permission, as an application's code
 * does.  The security manager checks only the thread that runs the scenario,
 * against the access control context, so it takes no part in anything else.
 *
 * <p>Output: one line {@code DEMANDED permission} for each distinct permission
 * demanded, then {@code OK}, or {@code FAILED} and the exception, or {@code
 * UNSUPPORTED} where a security manager cannot be installed.  Each permission
 * is written as {@code class|name|actions}.
 */
@SuppressWarnings("removal")
public final class TimeoutSchedulerProbe {

    /** Whether the permissions that the current thread demands are checked. */
    public static final ThreadLocal<Boolean> ARMED = ThreadLocal.withInitial(() -> false);

    private static final List<String> DEMANDED = new ArrayList<>();

    private TimeoutSchedulerProbe() { }

    /** Notes a permission that the thread under test demanded. */
    public static void demanded(Permission p) {
        synchronized (DEMANDED) {
            DEMANDED.add(format(p));
        }
    }

    public static void main(String[] args) throws Exception {
        Permissions granted = new Permissions();
        for (String spec : args) {
            if (spec.equals("ALL")) {
                granted.add(new AllPermission());
            } else {
                granted.add(parse(spec));
            }
        }
        Permissions all = new Permissions();
        all.add(new AllPermission());
        IsolatedLoader managers = new IsolatedLoader(
            TimeoutSchedulerProbe.class.getClassLoader(),
            Set.of("net.jini.jeri.ssl.ProbeSecurityManager"),
            new ProtectionDomain(null, all));
        SecurityManager manager = (SecurityManager) managers
            .loadClass("net.jini.jeri.ssl.ProbeSecurityManager")
            .getDeclaredConstructor().newInstance();
        ProtectionDomain jeri = new ProtectionDomain(null, granted);
        ProtectionDomain application = new ProtectionDomain(null, new Permissions());
        AccessControlContext context = new AccessControlContext(
            new ProtectionDomain[] { application });
        IsolatedLoader loader = new IsolatedLoader(
            TimeoutSchedulerProbe.class.getClassLoader(),
            Set.of("org.apache.river.jeri.internal.TimeoutScheduler",
                   "net.jini.jeri.ssl.HandshakeWatchdog"),
            jeri);
        String result = "OK";
        try {
            /* Looked up before the check is on: reflection is the probe's own. */
            Class<?> watchdog = Class.forName(
                "net.jini.jeri.ssl.HandshakeWatchdog", false, loader);
            Method schedule = watchdog.getDeclaredMethod(
                "schedule", Runnable.class, long.class);
            schedule.setAccessible(true);
            Method handOff = watchdog.getDeclaredMethod("handOff", Runnable.class);
            handOff.setAccessible(true);
            /*
             * A check of the access control context uses the random numbers
             * of the JDK; they are started before a check can need them.
             */
            java.util.concurrent.ThreadLocalRandom.current().nextInt();
            AccessController.getContext();
            /* Installed last: nothing is checked before the scenario. */
            System.setSecurityManager(manager);
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                ARMED.set(true);
                try {
                    /* The first use initialises the scheduler. */
                    CountDownLatch done = new CountDownLatch(2);
                    schedule.invoke(null, (Runnable) done::countDown, 1_000_000L);
                    handOff.invoke(null, (Runnable) done::countDown);
                    if (!done.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("the tasks did not run");
                    }
                } finally {
                    ARMED.set(false);
                }
                return null;
            }, context);
        } catch (UnsupportedOperationException noSecurityManager) {
            result = "UNSUPPORTED " + noSecurityManager;
        } catch (Throwable t) {
            result = "FAILED " + t;
            for (Throwable c = t.getCause(); c != null; c = c.getCause()) {
                result += " <- " + c;
            }
        }
        synchronized (DEMANDED) {
            for (String p : new LinkedHashSet<>(DEMANDED)) {
                System.out.println("DEMANDED " + p);
            }
        }
        System.out.println(result);
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    /** Writes a permission as {@code class|name|actions}. */
    static String format(Permission p) {
        return p.getClass().getName() + "|" + p.getName() + "|" + p.getActions();
    }

    static Permission parse(String spec) throws Exception {
        String[] parts = spec.split("\\|", -1);
        return (Permission) Class.forName(parts[0])
            .getConstructor(String.class, String.class)
            .newInstance(parts[1], parts[2]);
    }
}
