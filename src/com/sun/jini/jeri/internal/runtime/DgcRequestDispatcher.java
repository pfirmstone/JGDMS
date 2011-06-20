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

import com.sun.jini.jeri.internal.runtime.ObjectTable.NoSuchObject;
import com.sun.jini.logging.Levels;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.rmi.server.ExportException;
import java.rmi.server.Unreferenced;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.ServerContext;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalInputStream;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerCapabilities;

/**
 *
 * @author peter
 */
public class DgcRequestDispatcher implements RequestDispatcher {
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicJeriExporter");

    private static final Collection<Method> dgcDispatcherMethods =
            new ArrayList<Method>(2);
    static {
	Method[] methods = DgcServer.class.getMethods();
	for (int i = 0; i < methods.length; i++) {
	    final Method m = methods[i];
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    m.setAccessible(true);
		    return null;
		}
	    });
	    dgcDispatcherMethods.add(m);
	}
    }

    private static final ServerCapabilities dgcServerCapabilities =
	new ServerCapabilities() {
	    public InvocationConstraints checkConstraints(
		InvocationConstraints constraints)
		throws UnsupportedConstraintException
	    {
		assert constraints.equals(InvocationConstraints.EMPTY);
		return InvocationConstraints.EMPTY;
	    }
	};
        
    private final Unreferenced unrefCallback;
    private final ObjectTable table;
    private final ConcurrentMap<Uuid,Target> idTable =
            new ConcurrentHashMap<Uuid,Target>();
    private final AtomicInteger dgcEnabledCount =  new AtomicInteger();	// guarded by idTable lock

    private final InvocationDispatcher dgcDispatcher;
    private final DgcServer dgcServer;

    DgcRequestDispatcher(Unreferenced unrefCallback, ObjectTable table ) {
        this.unrefCallback = unrefCallback;
        this.table = table;
        try {
            dgcDispatcher =
                new BasicInvocationDispatcher(
                    dgcDispatcherMethods, dgcServerCapabilities,
                    null, null, this.getClass().getClassLoader())
                {
                    protected ObjectInputStream createMarshalInputStream(
                        Object impl,
                        InboundRequest request,
                        boolean integrity,
                        Collection context)
                        throws IOException
                    {
                        ClassLoader loader = getClassLoader();
                        return new MarshalInputStream(
                            request.getRequestInputStream(),
                            loader, integrity, loader,
                            Collections.unmodifiableCollection(context));
                        // useStreamCodebases() not invoked
                    }
                };
        } catch (ExportException e) {
            throw new AssertionError();
        }
        this.dgcServer = table.getDgcServer(this);
    }

    boolean forTable(ObjectTable table) {
        return this.table == table;
    }

    boolean isReferenced() {
            return !idTable.isEmpty();
    }

    Target get(Uuid id) {
            return idTable.get(id);
    }

    void put(Target target) throws ExportException {
        Uuid id = target.getObjectIdentifier();
        if (id.equals(Jeri.DGC_ID)) {
            throw new ExportException(
                "object identifier reserved for DGC");
        }
        Target exists = idTable.putIfAbsent(id, target);
        if (exists != null){
            throw new ExportException(
                "object identifier already in use");
        }
        if (target.getEnableDGC()) {
            dgcEnabledCount.incrementAndGet();
        }
    }

    void remove(Target target, boolean gc) {
            Uuid id = target.getObjectIdentifier();
            boolean removed = idTable.remove(id, target);
            if (target.getEnableDGC() && removed) {
                int count = dgcEnabledCount.decrementAndGet();
                assert count >= 0;
            }

        if (gc && idTable.isEmpty()) {
            /*
             * We have to be careful to make this callback without holding
             * the lock for idTable, because the callback implementation
             * will likely be code that calls this object's isReferenced
             * method in its own synchronized block.
             * 
             * This also means it is possible (although unlikely) for the 
             * idtable to become non empty before making this call.
             */
            unrefCallback.unreferenced();
        }
    }

    private boolean hasDgcEnabledTargets() {
            return dgcEnabledCount.get() > 0;
    }

    public void dispatch(InboundRequest request) {
        try {
            InputStream in = request.getRequestInputStream();
            Uuid id = UuidFactory.read(in);
            Target target = null;
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "id={0}", id);
            }

            try {
                /*
                 * The DGC object identifier is hardwired here,
                 * rather than install it in idTable; this
                 * eliminates the need to worry about not counting
                 * the DGC server as an exported object in the
                 * table, and it doesn't need all of the machinery
                 * that Target provides.
                 */
                if (id.equals(Jeri.DGC_ID)) {
                    dispatchDgcRequest(request);
                    return;
                }

                target = get(id);
                if (target == null) {
                    logger.log(Level.FINEST, "id not in table");
                    throw new NoSuchObject();
                }
                target.dispatch(request);
            } catch (NoSuchObject e) {
                in.close();
                OutputStream out = request.getResponseOutputStream();
                out.write(Jeri.NO_SUCH_OBJECT);
                out.close();

                if (logger.isLoggable(Levels.FAILED)) {
                    logger.log(Levels.FAILED, "no such object: {0}", id);
                }
            }
        } catch (IOException e) {
            request.abort();

            if (logger.isLoggable(Levels.FAILED)) {
                logger.log(Levels.FAILED,
                           "I/O exception dispatching request", e);
            }
        }
    }

    private void dispatchDgcRequest(final InboundRequest request)
        throws IOException, NoSuchObject
    {
        if (!hasDgcEnabledTargets()) {
            logger.log(Level.FINEST, "no DGC-enabled targets");
            throw new NoSuchObject();
        }

        OutputStream out = request.getResponseOutputStream();
        out.write(Jeri.OBJECT_HERE);

        final Collection context = new ArrayList(5);
        request.populateContext(context);

        ServerContext.doWithServerContext(new Runnable() {
            public void run() {
                dgcDispatcher.dispatch(dgcServer, request, context);
            }
        }, Collections.unmodifiableCollection(context));

    }
}
