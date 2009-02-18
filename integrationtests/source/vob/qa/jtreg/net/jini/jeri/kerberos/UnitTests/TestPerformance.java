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

import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import net.jini.security.AccessPermission;
import net.jini.security.AuthenticationPermission;
import net.jini.security.BasicProxyPreparer;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collections;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.Subject;

import java.security.AccessController;

public class TestPerformance extends TestUtilities {

    private Test[] tests;

    private final String clientLoginEntry = "testClient";
    private final Subject clientSubject;
    private static KerberosPrincipal[] cps = new KerberosPrincipal[] {
	new KerberosPrincipal("testClient1"),
	new KerberosPrincipal("testClient2"),
	new KerberosPrincipal("testClient3")
    };

    private final String serverLoginEntry = "testServer";
    private final Subject serverSubject;
    private static KerberosPrincipal[] sps = new KerberosPrincipal[] {
	new KerberosPrincipal("testServer1"),
	new KerberosPrincipal("testServer2"),
	new KerberosPrincipal("testServer3")
    };
    private Exporter serverExporter;
    private Server server;
    private Hello[] helloProxies;

    public TestPerformance(int initRuns, int repeatRuns) {
	boolean initOk = false;
	try {
	    clientSubject = getLoginSubject(clientLoginEntry);
	    serverSubject = getLoginSubject(serverLoginEntry);

	    InvocationConstraints[] clientConstraintsArr =
		new InvocationConstraints[] {
		    new InvocationConstraints(
			new InvocationConstraint[] {
			    new ClientMinPrincipal(cps[1]),
			    new ClientMaxPrincipal(
				new Principal[] {cps[0], cps[1], cps[2]})},
			null),
		    new InvocationConstraints(
			new InvocationConstraint[] {
			    new ClientMinPrincipal(cps[1]),
			    new ClientMaxPrincipal(
				new Principal[] {cps[0], cps[1]}),
			    Confidentiality.YES},
			null),
		    new InvocationConstraints(
			new InvocationConstraint[] {
			    new ClientMinPrincipal(cps[1]),
			    new ClientMaxPrincipal(
				new Principal[] {cps[1], cps[2]}),
			    Delegation.YES},
			null),
		    new InvocationConstraints(
			new InvocationConstraint[] {
			    new ClientMinPrincipal(cps[1]),
			    new ClientMaxPrincipal(
				new Principal[] {cps[1], cps[2]}),
			    Delegation.YES},
			new InvocationConstraint[] {Confidentiality.YES})
	    };

	    InvocationConstraints serverConstraints =
		new InvocationConstraints(
		    new InvocationConstraint[] {Integrity.YES},
		    null);

	    KerberosServerEndpoint serverEndpoint =
		KerberosServerEndpoint.getInstance(
		serverSubject, null, null, 0, null, null);
	    
	    serverExporter = new BasicJeriExporter(
		serverEndpoint,
		new BasicILFactory(
		    new BasicMethodConstraints(serverConstraints),
		    ServerPermission.class));
	    server = new Server();
	    Hello serverProxy = (Hello) serverExporter.export(server);

	    KerberosPrincipal cp = cps[1];
	    helloProxies = new Hello[clientConstraintsArr.length];
	    for (int i = 0; i < clientConstraintsArr.length; i++) {
		BasicProxyPreparer preparer = new BasicProxyPreparer(
		    true,
		    new BasicMethodConstraints(clientConstraintsArr[i]),
		    null);
		helloProxies[i] = (Hello) preparer.prepareProxy(serverProxy);
	    }
	    initTests(initRuns, repeatRuns);
	    initOk = true;
	} catch (Exception e) {
	    throw new RuntimeException("endpoints init failure", e);
	} finally {
	    if (!initOk)
		cleanup();
	}
    }

    public Test[] getTests() {
	return tests;
    }

    public void cleanup() {
	if (serverExporter != null) {
	    debugPrint(30, "Unexporting: " + serverExporter);
	    serverExporter.unexport(true);
	}
    }

    /** Runs all Endpoint tests */
    public static void main(String[] args) {
	if (args.length != 2) {
	    System.out.println("uasge: java TestPerformance initRuns " +
			       "repeatRuns");
	}
	int initRuns = Integer.decode(args[0]).intValue();
	int repeatRuns = Integer.decode(args[1]).intValue();
	TestPerformance testPerformance =
	    new TestPerformance(initRuns, repeatRuns);
	try {
	    test(testPerformance.getTests());
	} finally {
	    testPerformance.cleanup();
	}
    }

    //-----------------------------------
    //          private methods
    //-----------------------------------

    /* Add in all endpoint tests */
    private void initTests(int initRuns, int repeatRuns) {
	tests = new Test[] {
	    new PerformanceTest("CallRoundtripTimeTest", initRuns, repeatRuns)
	};
    };

    //-----------------------------------
    //          private classes
    //-----------------------------------

    private interface Hello extends Remote {
	String sayHello() throws RemoteException;
	String sayHello2() throws RemoteException;
    }

    private class Server implements Hello {
	public String sayHello() {
	    return "Hello, world!";
	}
	public String sayHello2() {
	    return "Hello, world2!";
	}
    }

    private class PerformanceTest extends BasicTest {

	int initRuns;
	int repeatRuns;
	    
	PerformanceTest(String name, int initRuns, int repeatRuns) {
	    super(name);
	    this.initRuns = initRuns;
	    this.repeatRuns = repeatRuns;
	}

	public Object run() throws Exception {
	    Object value = Subject.doAs(clientSubject, new PrivilegedAction() {
		    public Object run() {
			try {
			    // run a few times warm up
			    for (int i = 0; i < initRuns; i++) {
				for (int j = 0; j < helloProxies.length; j++) {
				    helloProxies[j].sayHello();
				    helloProxies[j].sayHello2();
				}
			    }
			    
			    // here comes the real run
			    long start = System.currentTimeMillis();
			    for (int i = 0; i < repeatRuns; i++) {
				for (int j = 0; j < helloProxies.length; j++) {
				    helloProxies[j].sayHello();
				    helloProxies[j].sayHello2();
				}
			    }
			    return new Long(System.currentTimeMillis() -
					    start);
			} catch (Exception e) {
			    return e;
			}
		    }
		});

	    if (value instanceof Exception) {
		throw (Exception)value;
	    } else {
		long time = ((Long)value).longValue();
		int numCalls = repeatRuns * helloProxies.length * 2;
		System.out.println("average call time: " +
				   (float)time/numCalls +
				   "msecs (averaged over " + numCalls + 
				   " invocations)\n");
	    }

	    return null;
	}

	public void check(Object result) throws Exception {
	    if (result != null)
		throw new FailedException(name() + " failed!");
	}
    }
}
