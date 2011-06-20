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

package com.sun.jini.jeri.internal.runtime;

import java.io.IOException;
import java.rmi.server.Unreferenced;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ConcurrentMap;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.security.Security;

/**
 * A bound ListenEndpoint and the associated ListenHandle and
 * RequestDispatcher.
 * 
 * @since 2.2.0
 * @author Peter Firmstone.
 */
class Binding {

    private final ListenEndpoint listenEndpoint;
    private final ObjectTable table;
    private final ConcurrentMap listenPool;
    private RequestDispatcher requestDispatcher;
    private ListenHandle listenHandle;
    boolean activated;
    boolean closed;
    // Changed to start at 1, so export in progress starts with construction.
    private int exportsInProgress = 1;

    Binding(final ListenEndpoint listenEndpoint, ObjectTable table,
            ConcurrentMap listenPool) throws IOException {
        super();
        this.table = table;
        this.listenPool = listenPool;
        this.listenEndpoint = listenEndpoint;
        activated = false;
        closed = false;
    }

    synchronized boolean incrementExportInProgress() {
        if (closed) {
            return false;
        }
        exportsInProgress++;
        return true;
    }

    synchronized void decrementExportInProgress() {
        if (closed) {
            throw new IllegalStateException("Cannot decrement closed Binding");
        }
        exportsInProgress--;
    }

    synchronized boolean activate() throws IOException {
        if (closed) {
            return false;
        }
        if (activated) {
            return true;
        }
        requestDispatcher = table.createRequestDispatcher(new Unreferenced() {

            public void unreferenced() {
                checkReferenced();
            }
        });
        try {
            listenHandle = (ListenHandle) Security.doPrivileged(new PrivilegedExceptionAction() {

                public Object run() throws IOException {
                    return listenEndpoint.listen(requestDispatcher);
                }
            });
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getException();
        }
        activated = true;
        return true;
    }

    synchronized void checkReferenced() {
        if (exportsInProgress > 0 || table.isReferenced(requestDispatcher)) {
            return;
        }
        listenPool.remove(new SameClassKey(listenEndpoint), this);
        listenHandle.close();
        closed = true;
    }

    synchronized RequestDispatcher getRequestDispatcher() {
        return requestDispatcher;
    }

    synchronized ListenHandle getListenHandle() {
        return listenHandle;
    }
}
