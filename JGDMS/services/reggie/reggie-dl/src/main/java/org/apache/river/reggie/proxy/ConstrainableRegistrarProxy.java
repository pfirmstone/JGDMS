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
package org.apache.river.reggie.proxy;

import org.apache.river.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * RegistrarProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class ConstrainableRegistrarProxy
    extends RegistrarProxy implements RemoteMethodControl
{
    /** Mappings between ServiceRegistrar and Registrar methods */
    static final Method[] methodMappings = {
	Util.getMethod(ServiceRegistrar.class, "getEntryClasses",
		       new Class[]{ ServiceTemplate.class }),
	Util.getMethod(Registrar.class, "getEntryClasses",
		       new Class[]{ Template.class }),

	Util.getMethod(ServiceRegistrar.class, "getFieldValues",
		       new Class[]{ ServiceTemplate.class, int.class,
				    String.class }),
	Util.getMethod(Registrar.class, "getFieldValues",
		       new Class[]{ Template.class, int.class, int.class }),

	Util.getMethod(ServiceRegistrar.class, "getGroups", new Class[0]),
	Util.getMethod(Registrar.class, "getMemberGroups", new Class[0]),

	Util.getMethod(ServiceRegistrar.class, "getLocator", new Class[0]),
	Util.getMethod(Registrar.class, "getLocator", new Class[0]),

	Util.getMethod(ServiceRegistrar.class, "getServiceTypes",
		       new Class[]{ ServiceTemplate.class, String.class }),
	Util.getMethod(Registrar.class, "getServiceTypes",
		       new Class[]{ Template.class, String.class }),

	Util.getMethod(ServiceRegistrar.class, "lookup",
		       new Class[]{ ServiceTemplate.class }),
	Util.getMethod(Registrar.class, "lookup",
		       new Class[]{ Template.class }),

	Util.getMethod(ServiceRegistrar.class, "lookup",
		       new Class[]{ ServiceTemplate.class, int.class }),
	Util.getMethod(Registrar.class, "lookup",
		       new Class[]{ Template.class, int.class }),

	Util.getMethod(ServiceRegistrar.class, "notify",
		       new Class[]{ ServiceTemplate.class, int.class,
				    RemoteEventListener.class,
				    MarshalledObject.class, long.class }),
	Util.getMethod(Registrar.class, "notify",
		       new Class[]{ Template.class, int.class,
				    RemoteEventListener.class,
				    MarshalledObject.class, long.class }),

	Util.getMethod(ServiceRegistrar.class, "register",
		       new Class[]{ ServiceItem.class, long.class }),
	Util.getMethod(Registrar.class, "register",
		       new Class[]{ Item.class, long.class }),

	Util.getMethod(Administrable.class, "getAdmin", new Class[0]),
	Util.getMethod(Administrable.class, "getAdmin", new Class[0])
    };
    
    private static final String CONSTRAINTS = "constraints";
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(CONSTRAINTS, MethodConstraints.class)
        };
    }
    
    public static void serialize(PutArg arg, ConstrainableRegistrarProxy crp) throws IOException{
        arg.put(CONSTRAINTS, crp.constraints);
        arg.writeArgs();
    }

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    private static MethodConstraints check(GetArg arg, Registrar server)
	    throws IOException, ClassNotFoundException{
	// The server field is private to RegistrarProxy's serialForm namespace:
	// a subclass cannot read it from its own GetArg frame (that resolves
	// against this class's namespace, which holds only CONSTRAINTS), so it
	// is passed in from the inherited field after super(arg) has populated it.
	MethodConstraints constraints = arg.get(CONSTRAINTS, null, MethodConstraints.class);
	MethodConstraints proxyCon = null;
	if (server instanceof RemoteMethodControl &&
	    (proxyCon = ((RemoteMethodControl)server).getConstraints()) != null) {
	    // Constraints set during proxy deserialization.
	    return ConstrainableProxyUtil.reverseTranslateConstraints(
		    proxyCon, methodMappings);
	}
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, server, methodMappings);
	return constraints;
    }

    public ConstrainableRegistrarProxy(GetArg arg)
	    throws IOException, ClassNotFoundException{
	// super(arg) reads and validates the inherited server field in
	// RegistrarProxy's own namespace; only then can constraints be checked
	// against it (reading server pre-super yielded null -> NPE).
	super(arg);
	this.constraints = check(arg, server);
    }

    /**
     * Creates new ConstrainableRegistrarProxy with given server reference,
     * service ID, born entry format and client constraints.
     *
     * @param entryFormat the marshalling format this registrar was born
     *              with (fixed at the registrar's instantiation, immutable
     *              for its life); <em>not</em> derived from
     *              {@code constraints} -- see {@link #setConstraints}.
     */
    ConstrainableRegistrarProxy(Registrar server,
				ServiceID registrarID,
				MarshallingFormat entryFormat,
				MethodConstraints constraints)
    {
	super((Registrar) ((RemoteMethodControl) server).setConstraints(
		  ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings)),
	      registrarID, entryFormat);
	this.constraints = constraints;
    }

    /**
     * Returns a copy of this proxy with the client's requested method
     * constraints. The born {@link RegistrarProxy#entryFormat entryFormat}
     * is carried over from {@code this} unconditionally -- it is
     * <em>not</em> one of the constraints being replaced here, so a client
     * cannot drop or change the registrar's format via
     * {@code setConstraints}; only the dedicated immutable field set once
     * at the registrar's instantiation ever determines it
     * (JGDMS-STD-006 sec.3 item 5).
     */
    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableRegistrarProxy(
	    server, registrarID, entryFormat, constraints);
    }

    // javadoc inherited from RemoteMethodControl.getConstraints
    public MethodConstraints getConstraints() {
	return constraints;
    }

    /**
     * Returns iterator used by ProxyTrustVerifier to retrieve a trust verifier
     * for this object.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(server);
    }
}
