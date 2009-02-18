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
/* @test
 * @summary Tests the jgss trust verifier.
 * @author Daniel Jiang
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm
 *	-Dcom.sun.jini.temp.davis.jeri.server.hostname=localhost
 *      TestVerifier
 */

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Collections;
import java.security.Principal;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.Subject;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.kerberos.KerberosTrustVerifier;
import net.jini.jeri.kerberos.KerberosEndpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.TrustVerifier;

public class TestVerifier extends UnitTestUtilities implements Test {

    static class Context implements TrustVerifier.Context {
	private static final Context context = new Context();
	private static final TrustVerifier verifier =
	    new KerberosTrustVerifier();

	public boolean isTrustedObject(Object obj)
	    throws RemoteException
	{
	    return verifier.isTrustedObject(obj, this);
	}

	public Collection getCallerContext() {
	    return Collections.EMPTY_SET;
	}

	public ClassLoader getClassLoader() {
	    return null;
	}

	static boolean verify(Object obj) throws RemoteException {
	    return verifier.isTrustedObject(obj, context);
	}
    }

    static KerberosPrincipal kp =
	new KerberosPrincipal("test@TEST.REALM");
    static Principal p = new Principal() {
	    public String getName() {
		return "test";
	    }
	};
    static Endpoint kep =  // port has to be > 0
        KerberosEndpoint.getInstance("localhost", 2048, kp);
    static Endpoint ep = new Endpoint() {
	    public OutboundRequestIterator newRequest(
		InvocationConstraints constraints)
	    {
		return new OutboundRequestIterator() {
			boolean done;
			public boolean hasNext() {
			    return !done;
			}
			public OutboundRequest next() throws IOException {
			    if (done) {
				throw new java.util.NoSuchElementException();
			    } else {
				done = true;
				throw new UnsupportedConstraintException(null);
			    }
			}
		    };
	    }
	};
    
    static Test[] tests = {
	new TestVerifier(kp, true),
	new TestVerifier(p, false),
	new TestVerifier(kep, true),
	new TestVerifier(ep, false),
    };

    private String name;
    private Object obj;
    private boolean ok;

    /** Run all Verifier tests */
    public static void main(String[] args) {
	test(tests);
    }

    TestVerifier(Object obj, boolean ok) {
	name = String.valueOf(obj);
	this.obj = obj;
	this.ok = ok;
    }

    public String name() {
	return name;
    }

    public Object run() throws RemoteException {
	return Boolean.valueOf(Context.verify(obj));
    }

    public void check(Object result) {
	if (!(result instanceof Boolean)
	    || ((Boolean) result).booleanValue() != ok)
	{
	    throw new FailedException("Expected: " + ok);
	}
    }
}
