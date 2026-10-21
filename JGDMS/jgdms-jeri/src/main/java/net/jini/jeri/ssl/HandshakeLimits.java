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

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import net.jini.security.Security;
import org.apache.river.action.GetIntegerAction;
import org.apache.river.action.GetLongAction;

/**
 * How many TLS handshakes the server listeners of the JVM work on at once, how
 * many accepted connections may wait for their turn, and how many may compute
 * the cryptography of a first flight at once.
 *
 * <p>A connection that has been accepted but has not finished its handshake
 * costs a file descriptor, and one that is working on the handshake costs about
 * 60 KiB of heap as well, before the peer has proved anything.  So both
 * numbers come from the descriptors that the process may open: handshakes
 * working at once are at most an eighth of them (and 512), and the waiting
 * connections at most a quarter of them less the working ones (and 4096), which
 * leaves room for the established connections and for everything else.
 *
 * <p>The descriptors and the processors belong to the process, not to a
 * listener, so the permits are kept here and {@link #shared} returns the one
 * instance that every listener of the JVM uses: a process with several
 * listeners admits no more sockets, and lets handshakes compute on no more
 * processors, than one with a single listener.  An instance is made for each
 * distinct setting of the properties, so a process whose properties do not
 * change has one.
 */
final class HandshakeLimits {

    /** The system property that sets the number of handshakes at once. */
    static final String MAX_HANDSHAKES_PROPERTY =
        "org.apache.river.jeri.ssl.maxServerHandshakes";

    /** The system property that sets the number of connections waiting. */
    static final String MAX_QUEUED_PROPERTY =
        "org.apache.river.jeri.ssl.maxServerHandshakeQueue";

    /** The system property that sets the descriptor limit to assume. */
    static final String MAX_DESCRIPTORS_PROPERTY =
        "org.apache.river.jeri.ssl.maxFileDescriptors";

    /**
     * The descriptor limit assumed where the JVM cannot report one (it
     * can on Linux and macOS, not on Windows): 16384.
     */
    static final long FALLBACK_MAX_DESCRIPTORS = 16384L;

    /**
     * The system property that sets the number of server handshakes that may
     * compute the first flight of the server at once.
     */
    static final String CPU_PERMITS_PROPERTY =
        "org.apache.river.jeri.ssl.serverHandshakeCpuPermits";

    static final int MAX_HANDSHAKES_CEILING = 512;
    static final int MAX_QUEUED_CEILING = 4096;

    /** The handshakes that work at once. */
    final int maxHandshakes;

    /** The accepted connections that wait for a handshake to be free. */
    final int maxQueued;

    /** The handshakes that compute the first flight at once. */
    final int cpuPermits;

    /**
     * One permit for each accepted connection whose handshake has not
     * finished, working or waiting, and one for each accept loop while it
     * waits in accept.  An accept loop takes a permit before it accepts, so
     * when the waiting connections fill the queue it stops accepting, and the
     * connections that arrive wait in the operating system's backlog.  The
     * queue of the loops that wait is first in, first out, so that no listener
     * is passed over for ever.
     */
    final Semaphore admission;

    /**
     * One permit for each handshake that works.  A handshake gets one before
     * anything is allocated for it, in turn.
     */
    final Semaphore running;

    /**
     * One permit for each handshake that computes the first flight of its
     * server side.  The limit on the handshakes that work bounds the memory
     * and the descriptors; this bounds the CPU, which a peer that replays a
     * ClientHello and then closes can spend at a rate of thousands a second
     * although each of its connections lasts a few milliseconds.
     */
    final Semaphore cpu;

    /**
     * One permit for each handshake done with an SSLSocket, which holds it
     * for the whole of its {@code startHandshake}.  The JDK does the reads and
     * the writes of the handshake inside that call, so it cannot be split, as
     * the first flight of an SSLEngine handshake is, into the work for the
     * processor and the waits for the peer, and a peer that sends a
     * ClientHello and then waits holds its permit until its handshake times
     * out.  The permits are separate from those of {@link #cpu}, so that such
     * peers, which can only be on the endpoints that use SSLSockets (HTTPS and
     * those with a server socket factory), cannot hold up the handshakes of
     * the other endpoints.  There are as many as the others.
     */
    final Semaphore socketCpu;

    private static final Map<String, HandshakeLimits> SHARED = new HashMap<>();

    HandshakeLimits(int maxHandshakes, int maxQueued) {
        this(maxHandshakes, maxQueued, defaultCpuPermits());
    }

    HandshakeLimits(int maxHandshakes, int maxQueued, int cpuPermits) {
        this.maxHandshakes = maxHandshakes;
        this.maxQueued = maxQueued;
        this.cpuPermits = cpuPermits;
        this.admission = new Semaphore(maxHandshakes + maxQueued, true);
        this.running = new Semaphore(maxHandshakes, true);
        this.cpu = new Semaphore(cpuPermits, true);
        this.socketCpu = new Semaphore(cpuPermits, true);
    }

    /** Returns the larger of 2 and a quarter of the processors. */
    static int defaultCpuPermits() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
    }

    /**
     * Returns the instance that the listeners of the JVM share, for the
     * system properties as they are now.
     */
    static HandshakeLimits shared() {
        HandshakeLimits limits = get();
        String key = limits.maxHandshakes + "/" + limits.maxQueued
            + "/" + limits.cpuPermits;
        synchronized (SHARED) {
            HandshakeLimits shared = SHARED.get(key);
            if (shared == null) {
                SHARED.put(key, limits);
                shared = limits;
            }
            return shared;
        }
    }

    /** Returns the limits for a process that may open this many descriptors. */
    static HandshakeLimits forDescriptors(long maxDescriptors) {
        long handshakes = Math.max(1L,
            Math.min(MAX_HANDSHAKES_CEILING, maxDescriptors / 8));
        long queued = Math.max(0L,
            Math.min(MAX_QUEUED_CEILING, maxDescriptors / 4 - handshakes));
        return new HandshakeLimits((int) handshakes, (int) queued);
    }

    /**
     * Returns the limits to use: the defaults for the descriptors of this
     * process, unless the system properties say otherwise.  A handshake limit
     * below 1 and a queue limit below 0 are ignored.
     */
    static HandshakeLimits get() {
        HandshakeLimits limits = forDescriptors(maxDescriptors());
        int handshakes = ((Integer) Security.doPrivileged(
            new GetIntegerAction(MAX_HANDSHAKES_PROPERTY,
                                 limits.maxHandshakes))).intValue();
        int queued = ((Integer) Security.doPrivileged(
            new GetIntegerAction(MAX_QUEUED_PROPERTY,
                                 limits.maxQueued))).intValue();
        int permits = ((Integer) Security.doPrivileged(
            new GetIntegerAction(CPU_PERMITS_PROPERTY, -1))).intValue();
        return new HandshakeLimits(
            handshakes < 1 ? limits.maxHandshakes : handshakes,
            queued < 0 ? limits.maxQueued : Math.min(queued, Integer.MAX_VALUE - handshakes),
            permits < 1 ? limits.cpuPermits : permits);
    }

    /**
     * Returns the number of descriptors that this process may open, as the
     * property says, else as the operating system reports it, else the
     * assumed {@link #FALLBACK_MAX_DESCRIPTORS}.
     */
    static long maxDescriptors() {
        long property = ((Long) Security.doPrivileged(
            new GetLongAction(MAX_DESCRIPTORS_PROPERTY, 0L))).longValue();
        if (property > 0L) {
            return property;
        }
        try {
            java.lang.management.OperatingSystemMXBean os =
                ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.UnixOperatingSystemMXBean) {
                long max = ((com.sun.management.UnixOperatingSystemMXBean) os)
                    .getMaxFileDescriptorCount();
                if (max > 0L) {
                    return max;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // not available here: use the assumed limit
        }
        return FALLBACK_MAX_DESCRIPTORS;
    }
}
