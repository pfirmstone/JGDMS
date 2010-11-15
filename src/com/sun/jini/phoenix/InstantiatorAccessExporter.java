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

package com.sun.jini.phoenix;

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationInstantiator;
import java.rmi.server.ExportException;
import net.jini.export.Exporter;
import net.jini.jrmp.JrmpExporter;

/**
 * Exporter that wraps an <code>ActivationInstantiator</code> instance so that
 * it only accepts calls from the local host.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class InstantiatorAccessExporter implements Exporter {
    /**
     * The underlying exporter.
     */
    private final Exporter exporter;
    /**
     * The wrapped impl.
     */
    private Remote wrapped;

    /**
     * Creates an exporter with an underlying {@link JrmpExporter} that
     * exports on an anonymous port.
     */
    public InstantiatorAccessExporter() {
	this.exporter = new JrmpExporter();
    }

    /**
     * Creates an exporter with the specified underlying exporter.
     *
     * @param exporter the underlying exporter
     */
    public InstantiatorAccessExporter(Exporter exporter) {
	this.exporter = exporter;
    }

    /**
     * Wraps the specified remote object in an
     * <code>ActivationInstantiator</code> implementation that only accepts
     * calls from the local host before delegating to the specified remote
     * object, exports the wrapper with the underlying exporter, and returns
     * the resulting proxy. The wrapper is strongly referenced by this
     * exporter. For the <code>newInstance</code> method, the wrapper throws an
     * <code>AccessControlException</code> if the client is not calling from
     * the local host.
     *
     * @throws IllegalArgumentException if <code>impl</code> does not
     * implement <code>ActivationInstantiator</code>
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    public Remote export(Remote impl) throws ExportException {
	if (!(impl instanceof ActivationInstantiator)) {
	    throw new IllegalArgumentException(
					    "not an ActivationInstantiator");
	}
	Remote wrapped = new InstantiatorImpl((ActivationInstantiator) impl);
	Remote proxy = exporter.export(wrapped);
	this.wrapped = wrapped;
	return proxy;
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    public boolean unexport(boolean force) {
	return exporter.unexport(force);
    }

    private static class InstantiatorImpl extends AbstractInstantiator {
	private final ActivationInstantiator impl;

	InstantiatorImpl(ActivationInstantiator impl) {
	    this.impl = impl;
	}

	public MarshalledObject newInstance(ActivationID id,
					    ActivationDesc desc)
	    throws ActivationException, RemoteException
	{
	    LocalAccess.check();
	    return impl.newInstance(id, desc);
	}
    }
}
