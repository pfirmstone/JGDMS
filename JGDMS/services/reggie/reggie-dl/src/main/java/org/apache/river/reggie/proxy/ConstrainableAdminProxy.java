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

import java.io.IOException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * AdminProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class ConstrainableAdminProxy
    extends AdminProxy implements RemoteMethodControl
{
    private static final String CONSTRAINTS = "constraints";
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(CONSTRAINTS, MethodConstraints.class)
        };
    }
    
    public static void serialize(PutArg arg, ConstrainableAdminProxy cap) throws IOException{
        arg.put(CONSTRAINTS, cap.constraints);
        arg.writeArgs();
    }

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    private static MethodConstraints check(GetArg arg, Registrar server)
	    throws IOException, ClassNotFoundException{
	// The server field is private to AdminProxy's serialForm namespace:
	// a subclass cannot read it from its own GetArg frame (that resolves
	// against this class's namespace, which holds only CONSTRAINTS), so it
	// is passed in from the inherited field after super(arg) has populated it.
	MethodConstraints constraints = arg.get(CONSTRAINTS, null, MethodConstraints.class);
	MethodConstraints proxyCon = null;
	if (server instanceof RemoteMethodControl &&
	    (proxyCon = ((RemoteMethodControl)server).getConstraints()) != null) {
	    // Constraints set during proxy deserialization.
	    return reverseTranslateConstraints(proxyCon);
	}
	verifyConsistentConstraints(constraints, server);
	return constraints;
    }

    public ConstrainableAdminProxy(GetArg arg) throws IOException, ClassNotFoundException{
	// super(arg) reads and validates the inherited server field in
	// AdminProxy's own namespace; only then can constraints be checked
	// against it.  Reading server pre-super (arg.get("server") in this
	// class's frame) yielded null -> verifyConsistentConstraints NPE.
	super(arg);
	this.constraints = check(arg, server);
    }

    /**
     * Creates new ConstrainableAdminProxy with given server reference, service
     * ID and client constraints.
     */
    ConstrainableAdminProxy(Registrar server,
			    ServiceID registrarID,
			    MethodConstraints constraints)
    {
	super((Registrar) ((RemoteMethodControl) server).setConstraints(
		  translateConstraints(constraints)),
	      registrarID);
	this.constraints = constraints;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableAdminProxy(server, registrarID, constraints);
    }

    // javadoc inherited from RemoteMethodControl.getConstraints
    public MethodConstraints getConstraints() {
	return constraints;
    }
}
