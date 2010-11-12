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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.ObjID;
import java.rmi.server.RemoteCall;
import sun.rmi.server.MarshalInputStream;
import sun.rmi.server.UnicastServerRef;
import sun.rmi.transport.LiveRef;

/**
 * JRMP exporter to export a <code>Registry</code> using the well-known
 * registry object identifier, and preventing remote code downloading for
 * incoming remote calls. This exporter implementation is only designed to
 * work with Java(TM) 2 Standard Edition implementations from Sun
 * Microsystems(TM), Inc.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class RegistrySunExporter extends SunJrmpExporter {
    /**
     * Creates a JRMP exporter that exports on the standard activation port
     * (1098).
     */
    public RegistrySunExporter() {
	super(ObjID.REGISTRY_ID, ActivationSystem.SYSTEM_PORT);
    }

    /**
     * Creates a JRMP exporter that exports on the specified port.
     *
     * @param port the port (if zero, an anonymous port will be chosen)
     */
    public RegistrySunExporter(int port) {
	super(ObjID.REGISTRY_ID, port);
    }

    UnicastServerRef getServerRef(LiveRef lref) {
	return new BootstrapServerRef(lref);
    }

    /**
     * Server-side ref to prevent remote code downloading when unmarshalling
     * arguments.
     */
    static class BootstrapServerRef extends UnicastServerRef {
	private static final long serialVersionUID = 3007040253722540025L;
	private static Method useCodebaseOnly;

	static {
	    try {
		useCodebaseOnly =
		    MarshalInputStream.class.getDeclaredMethod(
					     "useCodebaseOnly", null);
	    } catch (NoSuchMethodException e) {
		throw new InternalError("XXX");
	    }
	    useCodebaseOnly.setAccessible(true);
	}

	/**
	 * Construct an instance with the given live ref.
	 */
	public BootstrapServerRef(LiveRef lref) {
	    super(lref);
	}

	/**
	 * Disable remote code downloading on the input stream and then
	 * continue normal dispatching.
         * 
         * From the RemoteCall javadoc:
         * RemoteCall is an abstraction used solely by the RMI runtime 
         * (in conjunction with stubs and skeletons of remote objects) 
         * to carry out a call to a remote object. The RemoteCall interface 
         * is deprecated because it is only used by deprecated methods of 
         * java.rmi.server.RemoteRef.
         * 
         * This method is an overridden method from UnicastServerRef which is
         * a sun internal implementation class.
         * 
         * @deprecated no replacement
         * @see java.rmi.server.RemoteCall
	 */
	@Deprecated
	@Override
	public void dispatch(Remote obj, RemoteCall call) throws IOException {
	    try {
		useCodebaseOnly.invoke(call.getInputStream(), new Object[0]);
	    } catch (IllegalAccessException e) {
		throw new InternalError("XXX");
	    } catch (InvocationTargetException e) {
		throw (Error) e.getTargetException();
	    }
	    super.dispatch(obj, call);
	}
    }
}
