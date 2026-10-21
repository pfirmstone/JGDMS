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

import net.jini.security.Security;
import org.apache.river.action.GetIntegerAction;

/**
 * The accept backlog that the JERI server endpoints request when they bind a
 * listening socket.
 * <p>
 * The JDK default backlog is 50, so a burst of more than about 50 simultaneous
 * connectors overflows the kernel queue of a listener that is busy: Linux
 * drops the SYN and the client retries after 1, 3 and 7 seconds, Windows
 * answers with a reset ("Connection refused").  The endpoints therefore ask for
 * {@link #DEFAULT_BACKLOG}, which every operating system clamps to its own
 * maximum, unless the {@value #PROPERTY} system property says otherwise.
 *
 * @see #get
 */
public final class ListenBacklog {

    /** The system property that sets the backlog. */
    public static final String PROPERTY = "org.apache.river.jeri.listenBacklog";

    /**
     * The backlog requested when the property is not set (or is not an
     * integer): the operating system clamps it to its own maximum.
     */
    public static final int DEFAULT_BACKLOG = Integer.MAX_VALUE;

    private ListenBacklog() {
    }

    /**
     * Returns the backlog to pass to the {@code ServerSocket},
     * {@code ServerSocketFactory} or {@code ServerSocketChannel} bind methods.
     * The property is read on each call.  A value below 1 yields 0, which those
     * methods treat as "use the JDK default" (50).
     *
     * @return the requested backlog, or 0 for the JDK default
     */
    public static int get() {
        int backlog = Security.doPrivileged(
            new GetIntegerAction(PROPERTY, DEFAULT_BACKLOG)).intValue();
        return backlog < 1 ? 0 : backlog;
    }
}
