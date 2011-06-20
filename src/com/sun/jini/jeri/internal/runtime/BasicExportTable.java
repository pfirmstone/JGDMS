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
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.jini.id.Uuid;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;

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

    /** underlying object table */
    private final ObjectTable objectTable = new ObjectTable();

    /**
     * pool of endpoints that we're listening on:
     * maps SameClassKey(ServerEndpoint.ListenEndpoint) to Binding.
     * A binding removes itself from the listen pool.
     **/
    private final ConcurrentMap<SameClassKey,Binding> listenPool = 
            new ConcurrentHashMap<SameClassKey,Binding>(128);// 128 to reduce map resizing

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
	Target target = null;
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
		    ((Binding) bindings.get(i)).getRequestDispatcher();
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
                    binding.decrementExportInProgress();
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
	private final Target target;
	private final Endpoint endpoint;

	Entry(List bindings, Target target, Endpoint endpoint) {
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
	SameClassKey key = new SameClassKey(listenEndpoint);
	Binding binding = null;
        // This while loop ensures that a binding has it's exportInProgress
        // field incremented and the binding was not closed prior.
        // Once the exportInProgress field is incremented, the binding will stay active.
        // It is still possible for activation to be unsuccessful, resulting
        // in an IOException.
        // The reason for this while loop, is Binding's remove themselves from
        // the listenPool if inactive, a binding may be removed from the
        // listenPool by another thread without the current threads knowledge.
        // This will only happen while the binding has no Exports in progress.
        // Thus the increment calls are checked to be active;
        while (binding == null){
            binding = listenPool.get(key);
            if ( binding == null){
                binding = new Binding(listenEndpoint,objectTable, listenPool);
                Binding existed = listenPool.putIfAbsent(key, binding);
                if (existed != null){
                    binding = existed;
                    boolean active = binding.incrementExportInProgress();
                    if (!active){
                        binding = null;
                    }
                    continue;
                } else {
                    boolean active = binding.activate();
                    if (!active){
                        binding = null;
                    }
                    continue;
                }
            } else {
                // Although unlikely the binding could become inactive 
                // after retrieval, since the operation of getting and checking is not atomic.
                // If inactive, the binding has removed itself from the listenPool.
                boolean active = binding.incrementExportInProgress();
                if (!active) {
                    binding = null;
                    // This binding will have removed itself from listenPool.
                }
            }
        }
        binding.activate(); //Prevent a thread returning normally with an inactive object
        return binding;
    }

    /**
     * Collects the ListenEndpoints associated with a ServerEndpoint
     * and gets the corresponding bindings using the listen pool.
     **/
    private class LC implements ServerEndpoint.ListenContext {
	private volatile boolean done = false;
	private final List<Binding> bindings = 
                Collections.synchronizedList(new ArrayList<Binding>());

	LC() { }

	public ListenCookie addListenEndpoint(
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
	    return binding.getListenHandle().getCookie();
	}

	private List getFinalBindings() {
	    done = true;
	    return bindings;
	}
    }
}
