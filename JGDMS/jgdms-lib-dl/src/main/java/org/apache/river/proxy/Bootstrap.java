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

package org.apache.river.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.core.constraint.RemoteMethodControl;

/**
 *
 * @author peter
 */
public final class Bootstrap {
    
    /**
     * The bootstrap proxy must be limited to the following interfaces, in case
     * additional interfaces implemented by the proxy aren't available remotely.
     */
    private static final Class[] bootStrapProxyInterfaces = 
	{
	    ServiceProxyAccessor.class, 
	    ServiceAttributesAccessor.class,
	    ServiceIDAccessor.class,
	    RemoteMethodControl.class,
	    TrustEquivalence.class
	};
    
    private Bootstrap(){}
    
    /**
     * 
     * @param svc Service instance
     * @return 
     */
    public static Proxy create(Object svc){
	Object proxy;
	if (svc instanceof ProxyAccessor){ // Obtain exported proxy
	    proxy = ((ProxyAccessor)svc).getProxy();
	} else {
	    proxy = svc;
	    // Proxy may not have been exported yet, it is legal for
	    // a Serializable object to use writeReplace to export and serialize a proxy.
	    // This occurs locally, so we don't require a codebase download.
	    // REMIND: Should we set the context ClassLoader?
	    if (!Proxy.isProxyClass(proxy.getClass()) && proxy instanceof Serializable){
		try {
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutput out = new ObjectOutputStream(baos);
		    out.writeObject(proxy);
		    ByteArrayInputStream bain = new ByteArrayInputStream(baos.toByteArray());
		    ObjectInput in = new ObjectInputStream(bain);
		    proxy = in.readObject();
		} catch (IOException ex) {
		    Logger.getLogger(Bootstrap.class.getName()).log(Level.FINE, null, ex);
		} catch (ClassNotFoundException ex) {
		    Logger.getLogger(Bootstrap.class.getName()).log(Level.FINE, null, ex);
		}
	    }
	}
	Class proxyClass = proxy.getClass();
	if (proxy instanceof RemoteMethodControl //JERI
	    && proxy instanceof TrustEquivalence //JERI
	    // REMIND: The next three interfaces may not be available locally.
	    // so must be found in jsk-dl.jar
	    && proxy instanceof ServiceIDAccessor
	    && proxy instanceof ServiceProxyAccessor
	    && proxy instanceof ServiceAttributesAccessor
	    && Proxy.isProxyClass(proxyClass)
	    ) 
	{
	    // REMIND: InvocationHandler must be available locally, for now
	    // it must be an instance of BasicInvocationHandler.
	    InvocationHandler h = Proxy.getInvocationHandler(proxy);
	    return (Proxy) 
		Proxy.newProxyInstance(
			getProxyLoader(proxyClass),
			bootStrapProxyInterfaces, 
			h
		);
	}
	return null;
    }
    
    /**
     * Returns the class loader for the specified proxy class.
     */
    private static ClassLoader getProxyLoader(final Class proxyClass) {
	return (ClassLoader)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    return proxyClass.getClassLoader();
		}
	    });
    }
    
}
