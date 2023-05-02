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

package org.apache.river.phoenix;

import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.arg.ActivationMonitor;
import net.jini.activation.arg.UnknownGroupException;
import net.jini.activation.arg.UnknownObjectException;
import java.rmi.server.ExportException;
import net.jini.activation.arg.MarshalledObject;
import net.jini.export.Exporter;
import org.apache.river.phoenix.common.LocalAccess;

/**
 * Exporter that wraps an <code>ActivationMonitor</code> instance so that it
 * only accepts calls from the local host.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class MonitorAccessExporter implements Exporter {
    /**
     * The underlying exporter.
     */
    private final Exporter exporter;
    /**
     * The wrapped impl.
     */
    private Remote wrapped;

    /**
     * Creates an exporter with the specified underlying exporter.
     *
     * @param exporter the underlying exporter
     */
    public MonitorAccessExporter(Exporter exporter) {
	this.exporter = exporter;
    }

    /**
     * Wraps the specified remote object in an <code>ActivationMonitor</code>
     * implementation that only accepts calls from the local host before
     * delegating to the specified remote object, exports the wrapper with
     * the underlying exporter, and returns the resulting proxy. The wrapper
     * is strongly referenced by this exporter. For all
     * <code>ActivationMonitor</code> methods, the wrapper throws an
     * <code>AccessControlException</code> if the client is not calling from
     * the local host.
     *
     * @throws IllegalArgumentException if <code>impl</code> does not
     * implement <code>ActivationMonitor</code>
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public Remote export(Remote impl) throws ExportException {
	if (!(impl instanceof ActivationMonitor)) {
	    throw new IllegalArgumentException("not an ActivationMonitor");
	}
	Remote wrapped = new MonitorImpl((ActivationMonitor) impl);
	Remote proxy = exporter.export(wrapped);
	this.wrapped = wrapped;
	return proxy;
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public boolean unexport(boolean force) {
	return exporter.unexport(force);
    }

    private static class MonitorImpl extends AbstractMonitor {
	private final ActivationMonitor impl;

	MonitorImpl(ActivationMonitor impl) {
	    this.impl = impl;
	}

        @Override
	public void inactiveObject(ActivationID id)
	    throws UnknownObjectException, RemoteException
	{
	    LocalAccess.check();
	    impl.inactiveObject(id);
	}
    
        @Override
	public void activeObject(ActivationID id, MarshalledObject mobj)
    	    throws UnknownObjectException, RemoteException
	{
	    LocalAccess.check();
	    impl.activeObject(id, mobj);
	}
	
        @Override
	public void inactiveGroup(ActivationGroupID id,
				  long incarnation)
	    throws UnknownGroupException, RemoteException
	{
	    LocalAccess.check();
	    impl.inactiveGroup(id, incarnation);
	}
    }
}
