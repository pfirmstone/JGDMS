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
 * @summary Tests non-public methods of the Endpoint class.
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @build TestUtilities
 * @run main/othervm/policy=policy TestEndpointInternal
 */

import java.lang.reflect.InvocationTargetException;
import java.security.*;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.x500.*;
import net.jini.core.constraint.*;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.*;

public class TestEndpointInternal extends TestUtilities {

    /** All tests for the Endpoint class */
    public static Collection tests = new ArrayList();

    /** Runs all internal Endpoint tests */
    public static void main(String[] args) {
	test(tests);
    }

    /* -- Test SslEndpointImpl.getCallContext() -- */

    static {
	tests.add(TestGetCallContext.localtests);
    }

    static class TestGetCallContext implements Test {
	static final boolean OK = true;
	static final boolean FAIL = false;
	static final Subject clientAllRSASubject = new WithSubject() { {
	    addX500Principal("clientRSA1", subject);
	    addX500Principal("clientRSA2", subject);
	} }.subject();

	static Test[] localtests = {
	    new TestGetCallContext(
		"Wrong server principal",
		null,
		requirements(ServerAuthentication.YES,
			     serverPrincipals(x500("CN=Wrong"))),
		OK),
	    new TestGetCallContext(
		"Notice removed principals",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    clientSubject.getPrincipals().clear();
		    try {
			getCallContext();
			throw new FailedException("Didn't decache");
		    } catch (UnsupportedConstraintException e) {
		    }
		}
	    },
	    new TestGetCallContext(
		"Notice removed public credentials",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    clientSubject.getPublicCredentials().clear();
		    try {
			getCallContext();
			throw new FailedException("Didn't decache");
		    } catch (UnsupportedConstraintException e) {
		    }
		}
	    },
	    new TestGetCallContext(
		"Notice removed private credentials",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    clientSubject.getPrivateCredentials().clear();
		    try {
			getCallContext();
			throw new FailedException("Didn't decache");
		    } catch (UnsupportedConstraintException e) {
		    }
		}
	    },
	    new TestGetCallContext(
		"Don't notice removed private credentials with no server " +
		"principal constraint",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    clientSubject.getPrivateCredentials().clear();
		    /* Shouldn't fail */
		    getCallContext();
		}
	    },
	    /* XXX: These tests require access to the connection objects
	     * chosen, since the call context objects themselves are not
	     * cached.

	    new TestGetCallContext(
		"Noticed adding principals, no filtering",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    addX500Principal("clientDSA", clientSubject);
		    if (getCallContext().equals(result)) {
			throw new FailedException("Didn't decache");
		    }
		}
	    },
	    new TestGetCallContext(
		"Don't notice adding principals, with filtering",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES,
			     minPrincipals(x500(clientRSA1))),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    addX500Principal("clientDSA", clientSubject);
		    if (!getCallContext().equals(result)) {
			throw new FailedException("Didn't reuse");
		    }
		}
	    },
	    */
	    new TestGetCallContext(
		"No authentication permission for client principal",
		new WithSubject() { {
		    addX500Principal("noPerm", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES),
		OK),
	    new TestGetCallContext(
		"With destroyed credentials",
		new WithSubject() { {
		    addX500Principal("clientRSA1", subject);
		    destroyPrivateCredentials(subject);
		} }.subject(),
		requirements(ClientAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		FAIL),
	    new TestGetCallContext(
		"With credentials destroyed later",
		newClientRSA1Subject(),
		requirements(ClientAuthentication.YES,
			     serverPrincipals(x500(serverRSA))),
		OK)
	    {
		public void check(Object result) throws Exception {
		    super.check(result);
		    destroyPrivateCredentials(clientSubject);
		    try {
			getCallContext();
			throw new FailedException("Didn't decache");
		    } catch (UnsupportedConstraintException e) {
		    }
		}
	    },
	    new TestGetCallContext(
		"With expired credentials -- doesn't check validity",
		new WithSubject() { {
		    addX500Principal("clientDSA2expired", subject);
		} }.subject(),
		requirements(ClientAuthentication.YES),
		OK)
	};

	private static final LazyField implField =
	    new LazyField(useHttps ? "HttpsEndpoint" : "SslEndpoint", "impl");
	private static final Object impl =
	    implField.get(createEndpoint("localhost", 1));
	private static final LazyMethod getCallContext =
	    new LazyMethod("SslEndpointImpl", "getCallContext",
			   new Class[] { InvocationConstraints.class });

	private final String name;
	private final boolean supported;
	final InvocationConstraints constraints;
	final Subject clientSubject;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestGetCallContext(String name,
			   InvocationConstraints constraints,
			   boolean supported)
	{
	    this(name, TestUtilities.clientSubject, constraints, supported);
	}

	TestGetCallContext(String name,
			   Subject clientSubject,
			   InvocationConstraints constraints,
			   boolean supported)
	{
	    this.name =
		(name == null ? "" : name + ", ") +
		"clientSubject " + clientSubject + ",\n" + constraints;
	    this.supported = supported;
	    this.clientSubject = clientSubject;
	    this.constraints = constraints;

	}

	public String name() {
	    return name;
	}

	public Object run() {
	    try {
		return getCallContext();
	    } catch (UnsupportedConstraintException e) {
		return e;
	    }
	}

	Object getCallContext() throws UnsupportedConstraintException {
	    try {
		return Subject.doAs(
		    clientSubject,
		    new PrivilegedExceptionAction() {
			public Object run()
			    throws UnsupportedConstraintException
			{
			    try {
				return getCallContext.invokeWithThrows(
				    impl, new Object[] { constraints });
			    } catch (InvocationTargetException e) {
				if (e.getCause()
				    instanceof UnsupportedConstraintException)
				{
				    throw (UnsupportedConstraintException)
					e.getCause();
				} else {
				    throw unexpectedException(e.getCause());
				}
			    }
			}
		    });
	    } catch (PrivilegedActionException e) {
		throw (UnsupportedConstraintException) e.getCause();
	    }
	}

	static Subject newClientRSA1Subject() {
	    Subject subject = new Subject();
	    addX500Principal("clientRSA1", subject);
	    return subject;
	}

	public void check(Object result) throws Exception {
	    if (supported ==
		(result instanceof UnsupportedConstraintException))
	    {
		throw new FailedException(
		    "Should " + (supported ? "not " : "") +
		    "be an instance of UnsupportedConstraintException");
	    }
	}
    }
}
