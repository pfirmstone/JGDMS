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
import net.jini.activation.arg.ActivationDesc;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationGroupDesc;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.arg.ActivationInstantiator;
import net.jini.activation.arg.ActivationMonitor;
import net.jini.activation.arg.ActivationSystem;
import net.jini.activation.arg.UnknownGroupException;
import java.rmi.server.ExportException;
import java.util.Map;
import net.jini.export.Exporter;
import org.apache.river.phoenix.common.LocalAccess;
import org.apache.river.activation.ActivationAdmin;

/**
 * Exporter that wraps an <code>ActivationSystem</code> instance so that it
 * only accepts calls from the local host and optionally enforces a
 * {@link GroupPolicy} on calls to <code>registerGroup</code> and
 * <code>setActivationGroupDesc</code>.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class SystemAccessExporter implements Exporter {
    /**
     * The underlying exporter.
     */
    private final Exporter exporter;
    /**
     * The group policy, if any.
     */
    private final GroupPolicy policy;
    /**
     * The wrapped impl.
     */
    private Remote wrapped;

    /**
     * Creates an exporter with the specified underlying exporter and a
     * {@link DefaultGroupPolicy} instance.
     *
     * @param exporter the underlying exporter
     */
    public SystemAccessExporter(Exporter exporter) {
	this(exporter, new DefaultGroupPolicy());
    }

    /**
     * Creates an exporter with the specified underlying exporter and the
     * specified group policy, if any.
     *
     * @param exporter the underlying exporter
     * @param policy the group policy, or <code>null</code>
     */
    public SystemAccessExporter(Exporter exporter, GroupPolicy policy) {
	this.exporter = exporter;
	this.policy = policy;
    }

    /**
     * Wraps the specified remote object in an <code>ActivationSystem</code>
     * implementation that only accepts calls from the local host and
     * enforces the group policy (if any) before delegating to the specified
     * remote object, exports the wrapper with the underlying exporter, and
     * returns the resulting proxy. The wrapper is strongly referenced by
     * this exporter. For all <code>ActivationSystem</code> and
     * <code>ActivationAdmin</code> methods, the wrapper throws an
     * <code>AccessControlException</code> if the client is not calling from
     * the local host. For the <code>registerGroup</code> and
     * <code>setActivationGroupDesc</code> methods, the policy (if any)
     * is checked by calling its {@link GroupPolicy#checkGroup checkGroup}
     * method with the <code>ActivationGroupDesc</code> argument.
     *
     * @throws IllegalArgumentException if <code>impl</code> does not
     * implement <code>ActivationSystem</code> or <code>ActivationAdmin</code>
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public Remote export(Remote impl) throws ExportException {
	if (!(impl instanceof ActivationSystem)) {
	    throw new IllegalArgumentException("not an ActivationSystem");
	}
	if (!(impl instanceof ActivationAdmin)) {
	    throw new IllegalArgumentException("not an ActivationAdmin");
	}
	Remote decorated = new SystemImpl((ActivationSystem) impl, policy);
	Remote proxy = exporter.export(decorated);
	this.wrapped = decorated;
	return proxy;
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    public boolean unexport(boolean force) {
	return exporter.unexport(force);
    }

    private static class SystemImpl extends AbstractSystem {
	private final ActivationSystem impl;
	private final GroupPolicy policy;

	SystemImpl(ActivationSystem impl, GroupPolicy policy) {
	    this.impl = impl;
	    this.policy = policy;
	}

	public ActivationID registerObject(ActivationDesc desc)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    return impl.registerObject(desc);
	}

	public void unregisterObject(ActivationID id)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    impl.unregisterObject(id);
	}
	
	public ActivationGroupID registerGroup(ActivationGroupDesc desc)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    if (policy != null) {
		policy.checkGroup(desc);
	    }
	    return impl.registerGroup(desc);
	}
	
	public ActivationMonitor activeGroup(ActivationGroupID id,
					     ActivationInstantiator group,
					     long incarnation)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    return impl.activeGroup(id, group, incarnation);
	}
	
	public void unregisterGroup(ActivationGroupID id)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    impl.unregisterGroup(id);
	}

	public ActivationDesc setActivationDesc(ActivationID id,
						ActivationDesc desc)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    return impl.setActivationDesc(id, desc);
	}

	public ActivationGroupDesc setActivationGroupDesc(
						      ActivationGroupID id,
						      ActivationGroupDesc desc)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    if (policy != null) {
		policy.checkGroup(desc);
	    }
	    return impl.setActivationGroupDesc(id, desc);
	}

	public ActivationDesc getActivationDesc(ActivationID id)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    return impl.getActivationDesc(id);
	}
	      
	public ActivationGroupDesc getActivationGroupDesc(ActivationGroupID id)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    return impl.getActivationGroupDesc(id);
	}
	
	public void shutdown() throws RemoteException {
	    LocalAccess.check();
	    impl.shutdown();
	}

	public Map getActivationGroups() throws RemoteException {
	    LocalAccess.check();
	    return ((ActivationAdmin) impl).getActivationGroups();
	}

	public Map getActivatableObjects(ActivationGroupID id)
	    throws UnknownGroupException, RemoteException
	{
	    LocalAccess.check();
	    return ((ActivationAdmin) impl).getActivatableObjects(id);
	}
    }
}
