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
package org.apache.river.test.share;

import java.io.IOException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * RegistrarProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
final class TesterTransactionManagerConstrainableProxy
    extends TesterTransactionManagerProxy implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * Creates new ConstrainableRegistrarProxy with given server reference,
     * service ID and client constraints.
     */
    TesterTransactionManagerConstrainableProxy(
				      TransactionManager server,
				      MethodConstraints constraints,
				      int sid)
    {
	super((TransactionManager) ((RemoteMethodControl) server).setConstraints(constraints), sid);
	this.constraints = constraints;
    }

    TesterTransactionManagerConstrainableProxy(GetArg arg) throws IOException, ClassNotFoundException {
	super(check(arg));
	constraints = (MethodConstraints) arg.get("constraints", null);
    }
    
    private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException {
	arg.get("constraints", null, MethodConstraints.class);
	return arg;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new TesterTransactionManagerConstrainableProxy(server, constraints, sid);
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
