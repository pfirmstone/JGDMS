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
import net.jini.security.Security;
import org.apache.river.action.GetIntegerAction;
import org.apache.river.action.GetLongAction;

/**
 * How many TLS handshakes a server listener works on at once, and how many
 * accepted connections may wait for their turn.
 *
 * <p>A connection that has been accepted but has not finished its handshake
 * costs a file descriptor, and one that is working on the handshake costs about
 * 60 KiB of heap as well, before the peer has proved anything.  So both
 * numbers come from the descriptors that the process may open: handshakes
 * working at once are at most an eighth of them (and 512), and the waiting
 * connections at most a quarter of them less the working ones (and 4096), which
 * leaves room for the established connections and for everything else.
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

    static final int MAX_HANDSHAKES_CEILING = 512;
    static final int MAX_QUEUED_CEILING = 4096;

    /** The handshakes that work at once. */
    final int maxHandshakes;

    /** The accepted connections that wait for a handshake to be free. */
    final int maxQueued;

    HandshakeLimits(int maxHandshakes, int maxQueued) {
        this.maxHandshakes = maxHandshakes;
        this.maxQueued = maxQueued;
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
        return new HandshakeLimits(
            handshakes < 1 ? limits.maxHandshakes : handshakes,
            queued < 0 ? limits.maxQueued : Math.min(queued, Integer.MAX_VALUE - handshakes));
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
