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

package com.sun.jini.jeri.internal.connection;

import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.connection.ServerConnection;
import net.jini.jeri.connection.ServerConnectionManager;

/**
 * Manages server-side connections by delegating directly to
 * {@link ServerConnectionManager}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class BasicServerConnManager implements ServerConnManager {

    private static final ServerConnectionManager manager =
	new ServerConnectionManager();

    /**
     * Creates new instance containing a <code>ServerConnectionManager</code>.
     */
    public BasicServerConnManager() {
    }

    /**
     * Delegates to the {@link ServerConnectionManager#handleConnection
     * handleConnection} method of the contained
     * <code>ServerConnectionManager</code>.
     *
     * @throws NullPointerException if either argument is <code>null</code>
     */
    public void handleConnection(ServerConnection conn,
				 RequestDispatcher dispatcher)
    {
	manager.handleConnection(conn, dispatcher);
    }
}
