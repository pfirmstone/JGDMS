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
package org.apache.river.tool.preferred.fixtures;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Compiled bytecode fixtures, one per hazard kind and per share kind (SOW
 * &sect;8).  Each is a separate {@code .class} the tests load as a classpath
 * resource and feed to the analyzer.  Kept package-private and minimal so the
 * intended bytecode shape is unambiguous.
 */
final class Fixtures { private Fixtures() { } }

// ---- share kinds --------------------------------------------------------

/** Instance-only state — must SHARE, no review. */
class InstanceOnly {
    private int a;
    int f() { return a; }
}

/** Only a serialVersionUID constant — must SHARE (not a hazard). */
class SerialVersionUidOnly {
    static final long serialVersionUID = 1L;
    int f() { return 0; }
}

/** A Serializable wire value type — cross-boundary, must SHARE. */
class SerializableValue implements Serializable {
    static final long serialVersionUID = 1L;
    private int x;
    int f() { return x; }
}

/** A marker bootstrap-proxy interface (registered via test config). */
interface FakeBootstrap { }

/** Implements a bootstrap interface — cross-boundary, must SHARE. */
class ImplementsBootstrap implements FakeBootstrap { }

// ---- criterion (b): lock / blocking coupling ----------------------------

/** {@code static synchronized} method — weak (b): SHARE, surfaced for review. */
class StaticSyncMethod {
    static synchronized void f() { }
}

/** {@code synchronized(staticField)} block — weak (b): SHARE, surfaced for review. */
class SyncOnStaticField {
    static final Object LOCK = new Object();
    void f() {
        synchronized (LOCK) {
            System.out.println("x"); // non-blocking work under the lock
        }
    }
}

/** Static field of a contended type (SecureRandom) — strong (b): PREFER. */
class SecureRandomField {
    static final SecureRandom R = new SecureRandom();
    long next() { return R.nextLong(); }
}

/** Static field of a bounded pool (ExecutorService) — strong (b): PREFER. */
class ExecutorPoolField {
    static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    void run(Runnable r) { POOL.execute(r); }
}

/** A blocking call (Thread.sleep) made under a static lock — strong (b): PREFER. */
class BlockingUnderStaticLock {
    static final Object LOCK = new Object();
    void f() throws InterruptedException {
        synchronized (LOCK) {
            Thread.sleep(1L);
        }
    }
}

// ---- criterion (a): semantic co-mingling of mutable static state --------

/** A static-final container mutated at runtime — (a): SHARE, surfaced for review. */
class StaticFinalMutableMap {
    static final Map<String, String> M = new HashMap<String, String>();
    void put(String k, String v) { M.put(k, v); }
}

/** A non-final static reference reassigned at runtime — (a): SHARE, surfaced for review. */
class NonFinalStaticSingleton {
    static Object instance;
    void init() { instance = new Object(); }
}

/**
 * A strong-(b) hazard whose superclass ({@code java.util.EventObject}, a
 * Serializable wire base) is outside the analyzed set.  The cross-boundary status
 * cannot be confirmed, so the analyzer must NOT silently prefer it — it downgrades
 * to SHARE+review (finding #1 safety guard).
 */
class StrongBExternalSuper extends java.util.EventObject {
    static final long serialVersionUID = 1L;
    static final SecureRandom R = new SecureRandom();
    StrongBExternalSuper(Object source) { super(source); }
    long next() { return R.nextLong(); }
}

/** An in-set base (extends Object) for the resolved-supertype contrast case. */
class LocalBase { }

/**
 * A strong-(b) hazard whose entire superclass chain ({@code LocalBase} -&gt;
 * Object) is inside the analyzed set, so the safety guard must NOT fire and the
 * class is preferred as usual.
 */
class StrongBLocalSuper extends LocalBase {
    static final SecureRandom R = new SecureRandom();
    long next() { return R.nextLong(); }
}
