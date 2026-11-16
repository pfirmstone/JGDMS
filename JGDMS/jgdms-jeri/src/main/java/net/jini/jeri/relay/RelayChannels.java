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

package net.jini.jeri.relay;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The source of relay connections used by {@link RelayEndpoint}.
 *
 * <p>A {@link RelayChannelFactory} may be installed once with {@link
 * #setFactory}; it cannot be replaced.  If none is installed, each request
 * opens a Unix domain socket to the path named by the system property
 * {@value #SOCKET_PROPERTY}, read when the request is started.  If that
 * property is not set either, the request fails with an {@code IOException}
 * from {@link net.jini.jeri.OutboundRequestIterator#next}.
 *
 * <p>Whether this process may connect to the relay socket is decided by the
 * operating system; no permission check is made here beyond the one guarding
 * {@link #setFactory}.
 *
 * @since 4.0
 */
public final class RelayChannels {

    /** System property naming the relay's Unix domain socket path. */
    public static final String SOCKET_PROPERTY = "net.jini.jeri.relay.socket";

    /**
     * System property giving the time limit, in milliseconds, for the
     * opening exchange with the relay.  The default is 30000; values below 1
     * are taken as 1.
     */
    public static final String HANDSHAKE_TIMEOUT_PROPERTY =
        "net.jini.jeri.relay.handshakeTimeoutMs";

    static final long DEFAULT_HANDSHAKE_TIMEOUT = 30000L;

    private static final RuntimePermission SET_FACTORY_PERMISSION =
        new RuntimePermission("setRelayChannelFactory");

    /** The JVM-wide instance used by {@link RelayEndpoint#newRequest}. */
    static final RelayChannels SYSTEM =
        new RelayChannels(SOCKET_PROPERTY, HANDSHAKE_TIMEOUT_PROPERTY);

    private final String property;
    private final String timeoutProperty;
    private final AtomicReference<RelayChannelFactory> factory =
        new AtomicReference<RelayChannelFactory>();

    /**
     * Separate instances exist only so tests need not share the JVM-wide
     * one; they name their own properties.
     */
    RelayChannels(String property, String timeoutProperty) {
        this.property = Objects.requireNonNull(property, "property");
        this.timeoutProperty =
            Objects.requireNonNull(timeoutProperty, "timeoutProperty");
    }

    RelayChannels(String property) {
        this(property, HANDSHAKE_TIMEOUT_PROPERTY);
    }

    /**
     * Returns the opening exchange time limit in milliseconds, read from the
     * timeout property; the default if it is unset or not a number.
     */
    long handshakeTimeoutMillis() {
        String value = getProperty(timeoutProperty);
        if (value == null) {
            return DEFAULT_HANDSHAKE_TIMEOUT;
        }
        long t;
        try {
            t = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_HANDSHAKE_TIMEOUT;
        }
        return Math.max(1L, t);
    }

    private static String getProperty(final String name) {
        return AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty(name);
                }
            });
    }

    /**
     * Installs the JVM-wide relay channel factory.  It can be set only once.
     *
     * @param factory the factory
     * @throws NullPointerException if {@code factory} is {@code null}
     * @throws SecurityException if a security manager is present and
     *         {@code RuntimePermission("setRelayChannelFactory")} is not granted
     * @throws IllegalStateException if a factory has already been installed
     */
    public static void setFactory(RelayChannelFactory factory) {
        SYSTEM.install(factory);
    }

    void install(RelayChannelFactory f) {
        Objects.requireNonNull(f, "factory");
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_FACTORY_PERMISSION);
        }
        if (!factory.compareAndSet(null, f)) {
            throw new IllegalStateException(
                "relay channel factory already installed");
        }
    }

    /**
     * Returns a new connected, blocking channel to the relay.
     */
    SocketChannel open() throws IOException {
        RelayChannelFactory f = factory.get();
        if (f != null) {
            SocketChannel channel = f.openChannel();
            if (channel == null) {
                throw new IOException("relay channel factory returned null");
            }
            if (!channel.isConnected() || !channel.isBlocking()) {
                try {
                    channel.close();
                } catch (IOException e) {
                }
                throw new IOException(
                    "relay channel factory returned a channel that is not"
                    + " connected and blocking");
            }
            return channel;
        }
        final String name = property;
        String path = getProperty(name);
        /*
         * A null path would also fail below, where UnixDomainSocketAddress.of
         * is wrapped; this check is kept for the clearer message.
         */
        if (path == null || path.isEmpty()) {
            throw new IOException("no relay channel factory installed and "
                + name + " is not set");
        }
        UnixDomainSocketAddress address;
        try {
            address = UnixDomainSocketAddress.of(path);
        } catch (RuntimeException e) {
            throw new IOException("invalid relay socket path in " + name, e);
        }
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        boolean ok = false;
        try {
            channel.connect(address);
            ok = true;
            return channel;
        } finally {
            if (!ok) {
                try {
                    channel.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
