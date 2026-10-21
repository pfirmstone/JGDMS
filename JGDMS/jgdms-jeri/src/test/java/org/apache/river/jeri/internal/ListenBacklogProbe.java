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

package org.apache.river.jeri.internal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;

/**
 * Test support for the accept backlog tests: sets the backlog property, opens a
 * burst of simultaneous non-blocking connections against a listener that is
 * not accepting, and counts how many of them the kernel completed.
 */
public final class ListenBacklogProbe {

    /** The number of simultaneous connectors in the burst. */
    public static final int CONNECTORS = 200;

    /**
     * How long a burst waits for the connects that can still complete.  This
     * is shorter than the one second after which a dropped SYN is retransmitted.
     */
    private static final long WINDOW_MILLIS = 700;

    private ListenBacklogProbe() {
    }

    /** A dispatcher that is never expected to see a request. */
    public static final class NoRequests implements RequestDispatcher {
        public void dispatch(InboundRequest request) {
            request.abort();
        }
    }

    /**
     * Sets the backlog property to {@code value} ({@code null} clears it) and
     * returns the previous value for {@link #restore}.
     */
    public static String set(String value) {
        String previous = System.getProperty(ListenBacklog.PROPERTY);
        restore(value);
        return previous;
    }

    /** Sets the backlog property to {@code value}, clearing it if null. */
    public static void restore(String value) {
        if (value == null) {
            System.clearProperty(ListenBacklog.PROPERTY);
        } else {
            System.setProperty(ListenBacklog.PROPERTY, value);
        }
    }

    /** Returns the named field of {@code target}, whatever its access. */
    public static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /**
     * Returns the kernel's cap on every backlog, or {@code Integer.MAX_VALUE}
     * where it cannot be read (not Linux).
     */
    public static int kernelCap() {
        try {
            Path p = Path.of("/proc/sys/net/core/somaxconn");
            if (Files.isReadable(p)) {
                return Integer.parseInt(Files.readAllLines(p).get(0).trim());
            }
        } catch (IOException | RuntimeException e) {
            // unknown
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Opens {@link #CONNECTORS} non-blocking connections to {@code address} as
     * fast as it can and returns how many completed within the window.  A
     * connect that fails, or is still pending when the window ends, is not
     * counted.
     *
     * @param unix true if {@code address} is a Unix domain socket address
     */
    public static int burst(SocketAddress address, boolean unix)
	throws IOException
    {
	ProtocolFamily family = unix ? StandardProtocolFamily.UNIX : null;
	List<SocketChannel> channels = new ArrayList<>();
	int completed = 0;
	try (Selector selector = Selector.open()) {
	    for (int i = 0; i < CONNECTORS; i++) {
		SocketChannel ch = null;
		try {
		    ch = family == null ? SocketChannel.open()
					: SocketChannel.open(family);
		    channels.add(ch);
		    ch.configureBlocking(false);
		    if (ch.connect(address)) {
			completed++;
		    } else {
			ch.register(selector, SelectionKey.OP_CONNECT);
		    }
		} catch (IOException e) {
		    // refused or would block: not completed
		}
	    }
	    long deadline = System.nanoTime() + WINDOW_MILLIS * 1_000_000L;
	    while (completed < CONNECTORS && selector.keys().size() > 0) {
		long left = (deadline - System.nanoTime()) / 1_000_000L;
		if (left <= 0) {
		    break;
		}
		selector.select(Math.min(left, 50));
		Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		while (it.hasNext()) {
		    SelectionKey key = it.next();
		    it.remove();
		    try {
			if (((SocketChannel) key.channel()).finishConnect()) {
			    completed++;
			    key.cancel();
			}
		    } catch (IOException e) {
			key.cancel();
		    }
		}
	    }
	} finally {
	    for (SocketChannel ch : channels) {
		try { ch.close(); } catch (IOException e) { }
	    }
	}
	return completed;
    }
}
