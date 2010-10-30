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
import java.io.InterruptedIOException;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.rmi.server.Unreferenced;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jini.id.Uuid;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import net.jini.security.Security;

/**
 * An ObjectTable front end for exporting remote objects with a
 * ServerEndpoint as the producer of InboundRequests.
 *
 * A BasicExportTable manages a pool of ServerEndpoint.ListenEndpoints
 * being listened on.
 *
 * @author Sun Microsystems, Inc.
 **/
public final class BasicExportTable {

    /**
     * listen pool marker value to signal that a listen operation on a
     * ListenEndpoint is currently being started by another thread
     **/
    private static final Object PENDING = new Object();

    /** underlying object table */
    private final ObjectTable objectTable = new ObjectTable();

    /** guards listenPool and all Binding.exportsInProgress fields */
    private final Object lock = new Object();

    /**
     * pool of endpoints that we're listening on:
     * maps SameClassKey(ServerEndpoint.ListenEndpoint) to Binding
     **/
    private final Map listenPool = new HashMap();

    /**
     * Creates a new instance.
     **/
    public BasicExportTable() {
    }

    /**
     * Exports a remote object to this BasicExportTable.
     **/
    public Entry export(Remote impl,
			ServerEndpoint serverEndpoint,
			boolean allowDGC,
			boolean keepAlive,
			Uuid id)
        throws ExportException
    {
	List bindings = null;
	ObjectTable.Target target = null;
	Endpoint endpoint;
	try {
	    LC listenContext = new LC();
	    try {
		endpoint =
		    serverEndpoint.enumerateListenEndpoints(listenContext);
	    } catch (IOException e) {
		throw new ExportException("listen failed", e);
	    } finally {
		bindings = listenContext.getFinalBindings();
	    }

	    RequestDispatcher[] requestDispatchers =
		new RequestDispatcher[bindings.size()];
	    for (int i = 0; i < requestDispatchers.length; i++) {
		requestDispatchers[i] =
		    ((Binding) bindings.get(i)).requestDispatcher;
	    }
	    target = objectTable.export(
		impl, requestDispatchers, allowDGC, keepAlive, id);

	} finally {
	    if (bindings != null) {
		/*
		 * All bindings in the listen context have had their
		 * exportsInProgress fields incremented, so they must
		 * all be decremented here.
		 */
		for (int i = 0; i < bindings.size(); i++) {
		    Binding binding = (Binding) bindings.get(i);
		    synchronized (lock) {
			binding.exportsInProgress--;
		    }
		    /*
		     * If export wasn't successful, check to see if
		     * binding can be released.
		     */
		    if (target == null) {
			binding.checkReferenced();
		    }
		}
	    }
	}

	return new Entry(bindings, target, endpoint);
    }

    /**
     * Represents a remote object exported to this BasicExportTable.
     *
     * An Entry can be used to get the client-side Endpoint to use to
     * communicate with the exported object, to set the invocation
     * dispatcher for the exported object, and to unexport the
     * exported object.
     **/
    public static final class Entry {
	private final List bindings;
	private final ObjectTable.Target target;
	private final Endpoint endpoint;

	Entry(List bindings, ObjectTable.Target target, Endpoint endpoint) {
	    this.bindings = bindings;
	    this.target = target;
	    this.endpoint = endpoint;
	}

	/**
	 * Returns the client-side Endpoint to use to communicate
	 * with the exported object.
	 **/
	public Endpoint getEndpoint() {
	    return endpoint;
	}

	/**
	 * Sets the invocation dispatcher for the exported object.
	 **/
	public void setInvocationDispatcher(InvocationDispatcher id) {
	    target.setInvocationDispatcher(id);
	}

	/**
	 * Unexports the exported object.
	 **/
	public boolean unexport(boolean force) {
	    if (!target.unexport(force)) {
		return false;
	    }
	    for (int i = 0; i < bindings.size(); i++) {
		((Binding) bindings.get(i)).checkReferenced();
	    }
	    return true;
	}
    }

    /**
     * Returns the binding for the specified ListenEndpoint, by
     * returning the one already in the listen pool, if any, or else
     * by creating a new one.  If a Binding is returned, its
     * exportsInProgress field will have been incremented.
     **/
    private Binding getBinding(ListenEndpoint listenEndpoint)
	throws IOException
    {
	Object key = new SameClassKey(listenEndpoint);
	Binding binding = null;
	synchronized (lock) {
	    do {
		Object value = listenPool.get(key);
		if (value instanceof Binding) {
		    binding = (Binding) value;
		    binding.exportsInProgress++;
		    return binding;
		} else if (value == PENDING) {
		    try {
			lock.wait();
		    } catch (InterruptedException e) {
			throw new InterruptedIOException();
		    }
		    continue;
		} else {
		    assert value == null;
		    listenPool.put(key, PENDING);
		    break;
		}
	    } while (true);
	}
	try {
	    // start listen operation without holding global lock
	    binding = new Binding(listenEndpoint);
	} finally {
	    synchronized (lock) {
		assert listenPool.get(key) == PENDING;
		if (binding != null) {
		    listenPool.put(key, binding);
		    binding.exportsInProgress++;
		} else {
		    listenPool.remove(key);
		}
		lock.notifyAll();
	    }
	}
	return binding;
    }

    /**
     * Collects the ListenEndpoints associated with a ServerEndpoint
     * and gets the corresponding bindings using the listen pool.
     **/
    private class LC implements ServerEndpoint.ListenContext {
	private boolean done = false;
	private final List bindings = new ArrayList();

	LC() { }

	public synchronized ListenCookie addListenEndpoint(
	    ListenEndpoint listenEndpoint)
	    throws IOException
	{
	    if (done) {
		throw new IllegalStateException();
	    }

	    // must always check permissions before exposing pool
	    listenEndpoint.checkPermissions();

	    Binding binding = getBinding(listenEndpoint);
	    bindings.add(binding);
	    return binding.listenHandle.getCookie();
	}

	synchronized List getFinalBindings() {
	    done = true;
	    return bindings;
	}
    }

    /**
     * A bound ListenEndpoint and the associated ListenHandle and
     * RequestDispatcher.
     **/
    private class Binding {
	private final ListenEndpoint listenEndpoint;
	final RequestDispatcher requestDispatcher;
	final ListenHandle listenHandle;

	int exportsInProgress = 0;	// guarded by outer "lock"

	/**
	 * Creates a binding for the specified ListenEndpoint by
	 * attempting to listen on it.
	 **/
	Binding(final ListenEndpoint listenEndpoint) throws IOException {
	    this.listenEndpoint = listenEndpoint;
	    requestDispatcher =
		objectTable.createRequestDispatcher(new Unreferenced() {
		    public void unreferenced() { checkReferenced(); }
		});
	    try {
		/*
		 * We don't want this (potentially) shared listen
		 * operation to inherit the access control context of
		 * the current callers arbitrarily (their permissions
		 * were already checked by the ListenContext, and the
		 * ObjectTable will take care of checking permissions
		 * per requests against the appropriate callers'
		 * access control context).
		 */
		listenHandle = (ListenHandle)
		    Security.doPrivileged(new PrivilegedExceptionAction() {
			public Object run() throws IOException {
			    return listenEndpoint.listen(requestDispatcher);
			}
		    });
	    } catch (java.security.PrivilegedActionException e) {
		throw (IOException) e.getException();
	    }
	}

	/**
	 * Checks whether there are any objects currently exported to
	 * this binding's RequestDispatcher or if there are any
	 * exports in progress for this binding; if there are neither,
	 * this binding is removed from the listen pool and its listen
	 * operation is closed.
	 **/
	void checkReferenced() {
	    synchronized (lock) {
		if (exportsInProgress > 0 ||
		    objectTable.isReferenced(requestDispatcher))
		{
		    return;
		}
		listenPool.remove(new SameClassKey(listenEndpoint));
	    }
	    listenHandle.close();
	}
    }
}
