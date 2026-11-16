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
import java.security.AccessController;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
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
 * <p>The handshakes that work at once are also at most a small multiple of the
 * processors, four times as many and at least 16.  On a local network a
 * handshake is bound by the processor (the key exchange, the signatures, the
 * verification of the certificates), so more of them at once only share the
 * same processor time among more of them: each finishes later, and at the
 * deadline many are cut off after they have done their work and their peers
 * try again.  Measured on a machine of 12 logical processors, with 4096
 * connectors opening connections as fast as they could: a limit of 512 gave
 * 314 and 620 connections a second with 809 and 70 failures and a 99th
 * percentile of 12 s, with windows of seconds in which nothing completed
 * although hundreds of handshakes were working; a limit of 48 gave 658 and 517
 * a second, no failures and a 99th percentile of 8 s.  On a link with a long
 * round trip a handshake mostly waits for the peer, and the rate is about the
 * limit divided by the time a handshake takes: 48 handshakes at a round trip
 * of 300 ms allow about 80 new connections a second, so the limit should be
 * raised for such links (see {@link #MAX_HANDSHAKES_PROPERTY}).
 *
 * <p>The descriptors and the processors belong to the process, not to a
 * listener, so the permits are kept here and {@link #shared} returns the one
 * instance that every listener that uses this copy of the classes shares: a
 * process with several listeners admits no more sockets, and lets handshakes
 * compute on no more processors, than one with a single listener.  An
 * instance is made for each distinct setting of the properties, so a process
 * whose properties do not change has one.
 *
 * <p>The instance is a static of the class, so it is shared by the classes of
 * one class loader, not of one JVM: a second copy of jgdms-jeri in the same
 * JVM (a bundle beside a copy on the class path, two bundle revisions after
 * a refresh, two web applications) has permits, and a timer thread, of its
 * own, and the limits then hold for each copy, not for the process.
 */
final class HandshakeLimits {

    /**
     * The system property that sets the number of handshakes at once.  The
     * default is the least of 512, an eighth of the descriptors and four for
     * each processor (at least 16).
     */
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

    /** The handshakes at once that a machine of any size may work on. */
    static final int MIN_HANDSHAKES_FOR_PROCESSORS = 16;

    /** The handshakes at once that each processor adds to that. */
    static final int HANDSHAKES_PER_PROCESSOR = 4;
    static final int MAX_QUEUED_CEILING = 4096;

    /** The handshakes that work at once. */
    final int maxHandshakes;

    /** The accepted connections that wait for a handshake to be free. */
    final int maxQueued;

    /** The handshakes that compute the first flight at once. */
    final int cpuPermits;

    /**
     * One permit for each accepted connection whose handshake has not
     * finished, working or waiting.  An accept loop waits for a permit to be
     * free before it accepts, and takes it when it has accepted a connection,
     * so when the waiting connections fill the queue it stops accepting, and
     * the connections that arrive wait in the operating system's backlog; and
     * a loop that is idle in accept holds none, so listeners that have no
     * peers take nothing from those that have.  The queue of the loops that
     * wait is first in, first out, so that no listener is passed over for
     * ever; the semaphore must be fair for that.  Another listener may take the
     * last permit between a loop's wait and its accept, and then the accepted
     * connection waits for a permit: at most one connection for each listener
     * is accepted beyond the permits.
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
     * One permit for each handshake done with an SSLSocket, which is taken
     * when the peer has sent the whole of a ClientHello, never before,
     * and given back when the server has written its first flight and reads
     * from the peer.  The JDK does the reads and the writes of the handshake
     * inside {@code startHandshake}, so the work for the processor cannot be
     * told from the waits for the peer, as it can with the first flight of an
     * SSLEngine handshake; the streams of the socket that the SSLSocket is
     * layered over show it ({@link HandshakeSocket}).  A peer that replays a
     * ClientHello and then stalls holds a permit for that work, and one that
     * does not read the flight, so that the write blocks, until its handshake
     * times out.  The permits are separate from those of {@link #cpu}, so that
     * such peers, which can only be on the endpoints that use SSLSockets (HTTPS
     * and those with a server socket factory), cannot hold up the handshakes of
     * the other endpoints.  There are as many as the others.  A handshake
     * that the server answers with a HelloRetryRequest gives its permit
     * back with that flight, and takes one again when the second
     * ClientHello has been read whole (the wrapper reads it, as the queue
     * phase reads the first), for the work of the flight that follows it.
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
     * Returns the instance that the listeners that use this copy of the
     * classes share (see the class comment), for the system properties as
     * they are now.
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

    /**
     * Returns the limits for a process that may open this many descriptors,
     * on the processors of this machine.
     */
    static HandshakeLimits forDescriptors(long maxDescriptors) {
        return forDescriptors(maxDescriptors,
                              Runtime.getRuntime().availableProcessors());
    }

    /**
     * Returns the limits for a process that may open this many descriptors
     * and has this many processors: the handshakes at once are the least of
     * 512, an eighth of the descriptors, and four for each processor (and at
     * least 16).
     */
    static HandshakeLimits forDescriptors(long maxDescriptors, int processors) {
        long forProcessors = Math.max(MIN_HANDSHAKES_FOR_PROCESSORS,
            (long) HANDSHAKES_PER_PROCESSOR * Math.max(1, processors));
        long handshakes = Math.max(1L,
            Math.min(Math.min(MAX_HANDSHAKES_CEILING, maxDescriptors / 8),
                     forProcessors));
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
        int handshakes = ((Integer) AccessController.doPrivileged(
            new GetIntegerAction(MAX_HANDSHAKES_PROPERTY,
                                 limits.maxHandshakes))).intValue();
        int queued = ((Integer) AccessController.doPrivileged(
            new GetIntegerAction(MAX_QUEUED_PROPERTY,
                                 limits.maxQueued))).intValue();
        int permits = ((Integer) AccessController.doPrivileged(
            new GetIntegerAction(CPU_PERMITS_PROPERTY, -1))).intValue();
        /*
         * The sockets that are admitted are the working and the waiting ones
         * together, and the permits for them are an int: whichever of the two
         * is the default, the sum must not overflow.
         */
        long working = handshakes < 1 ? limits.maxHandshakes : handshakes;
        long waiting = Math.min(queued < 0 ? limits.maxQueued : queued,
                                Integer.MAX_VALUE - working);
        return new HandshakeLimits((int) working, (int) waiting,
            permits < 1 ? limits.cpuPermits : permits);
    }

    /**
     * Returns the number of descriptors that this process may open, as the
     * property says, else as the operating system reports it, else the
     * assumed {@link #FALLBACK_MAX_DESCRIPTORS}.
     */
    static long maxDescriptors() {
        long property = ((Long) AccessController.doPrivileged(
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
