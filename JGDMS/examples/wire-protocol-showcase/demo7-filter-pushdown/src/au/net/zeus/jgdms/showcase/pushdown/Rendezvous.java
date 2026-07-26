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
package au.net.zeus.jgdms.showcase.pushdown;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;

/**
 * Tiny filesystem rendezvous shared by the demo7 {@code Server} and {@code Client}
 * processes. Both processes run on the same host (this is a two-process,
 * split-classpath demo, not a distributed deployment), so a shared scratch directory
 * is the simplest robust coordination channel — no lookup service, no discovery.
 *
 * <p>Two jobs:
 * <ul>
 *   <li><b>Proxy hand-off</b> — the server exports the space and transaction-manager
 *       proxies and writes each as a {@link MarshalledInstance} blob; the client reads
 *       and unmarshals them. (Both processes have the proxy classes on their local
 *       classpath; only the entry class {@code WeatherReading} is withheld from the
 *       server. So no codebase HTTP server is needed.)</li>
 *   <li><b>Barriers</b> — named marker files let each side wait for the other to reach
 *       a labelled point, so the confused-deputy proof runs as a strict, single
 *       quiescent sequence (memo &sect;8.3): the server snapshots its operator counters
 *       and JFR stream at exactly the right moments relative to the client's writes.</li>
 * </ul>
 *
 * <p>All files live under one directory passed on the command line. Writes are made
 * atomic by writing to a temp file and moving into place, so a reader never observes a
 * half-written blob or marker.
 */
final class Rendezvous {

    private final Path dir;
    private final long timeoutMillis;

    Rendezvous(Path dir, long timeoutMillis) throws IOException {
        this.dir = dir;
        this.timeoutMillis = timeoutMillis;
        Files.createDirectories(dir);
    }

    Path dir() {
        return dir;
    }

    // --- proxy hand-off ------------------------------------------------------

    /** Marshal {@code proxy} and publish it under {@code name} (atomic). */
    void publishProxy(String name, Object proxy) throws IOException {
        // AtomicMarshalledInstance reduces the proxy (and its codebase
        // annotation) to bytes internally using ATOMIC serialisation -- the
        // JGDMS smart proxies are @AtomicSerial, not classic Serializable, so
        // the plain MarshalledInstance (JOSS) constructor cannot marshal them.
        // The resulting envelope is itself Serializable (it just holds byte
        // arrays), so we serialise it to a blob; the proxy classes are NOT
        // touched by this outer stream -- they are already bytes inside, and
        // get(false) below reconstructs them via the atomic input stream.
        final MarshalledInstance mi = new AtomicMarshalledInstance(proxy);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(mi);
        }
        writeAtomic(name, baos.toByteArray());
    }

    /** Wait for, then read and unmarshal, the proxy published under {@code name}. */
    Object readProxy(String name) throws IOException, ClassNotFoundException {
        final Path p = awaitFile(name);
        final byte[] blob = Files.readAllBytes(p);
        final MarshalledInstance mi;
        try (ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(blob))) {
            mi = (MarshalledInstance) ois.readObject();
        }
        // integrity=false / no verification: this is a same-host demo hand-off.
        return mi.get(false);
    }

    // --- barriers ------------------------------------------------------------

    /** Signal that this side has reached the labelled point {@code name}. */
    void signal(String name) throws IOException {
        writeAtomic(name + ".marker", new byte[0]);
    }

    /** Block until the other side has {@link #signal signalled} {@code name}. */
    void await(String name) {
        awaitFile(name + ".marker");
    }

    // --- internals -----------------------------------------------------------

    private Path awaitFile(String name) {
        final Path p = dir.resolve(name);
        final long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (!Files.exists(p)) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "timed out after " + timeoutMillis + "ms waiting for " + p);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted waiting for " + p, e);
            }
        }
        return p;
    }

    private void writeAtomic(String name, byte[] bytes) throws IOException {
        final Path target = dir.resolve(name);
        final Path tmp = dir.resolve(name + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Barrier label constants — the demo7 protocol (server ⇄ client).
    static final String SPACE_READY = "space-ready";      // server → client: proxies published
    static final String P_REGISTERED = "p-registered";    // client → server: P's standing query is up
    static final String PROBE_ARMED = "probe-armed";      // server → client: M0/E0 snapshot taken
    static final String Q_WROTE_UNCOMMITTED = "q-wrote";  // client → server: Q wrote under Tq (uncommitted)
    static final String UNCOMMITTED_CHECKED = "uncommitted-checked"; // server → client: §8.3 uncommitted asserts done
    static final String Q_COMMITTED = "q-committed";       // client → server: Tq committed
    static final String COMMITTED_CHECKED = "committed-checked";     // server → client: §8.3 commit asserts done
    static final String CLIENT_DONE = "client-done";       // client → server: all client-side asserts passed
    static final String CLIENT_FAILED = "client-failed";   // client → server: a client-side assert failed
}
