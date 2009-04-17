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
 * @bug 4404702
 * @summary When the JERI runtime (lazily) spawns system threads that could
 * outlive the application context in which they were (happened to be)
 * created, such threads should not inherit (thread local) data specific to
 * such an application context for various isolation reasons (see 4219095).
 * While there is not yet a practical means for a general solution to this
 * problem, the particular problem documented in 4404702-- the inheritance
 * of the parent thread's context class loader, preventing that loader from
 * being garbage collected in the future-- can be easily fixed.  This test
 * verifies that the context class loader in effect when the first remote
 * object is exported (and thus when some long-lived JERI daemon threads are
 * created) can be garbage collected after the remote object has been
 * unexported.  [Note that this test is somewhat at the mercy of other J2SE
 * subsystems also not holding on to the loader in their daemon threads.]
 * @author Peter Jones
 *
 * @build RuntimeThreadInheritanceLeak
 * @run main/othervm RuntimeThreadInheritanceLeak
 */

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.Map;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class RuntimeThreadInheritanceLeak implements Remote {

    private static final int TIMEOUT = 20000;

    public static void main(String[] args) {

	System.err.println("\nRegression test for bug 4404702\n");

	/*
	 * HACK: Work around the fact that java.util.logging.LogManager's
	 * (singleton) construction also has this bug-- it will register a
	 * "shutdown hook", i.e. a thread, which will inherit and pin the
	 * current thread's context class loader for the lifetime of the VM--
	 * by causing the LogManager to be initialized now, instead of by
	 * RMI when our special context class loader is set.
	 */
	java.util.logging.LogManager.getLogManager();

	/*
         * HACK: Work around the fact that the non-native, thread-based
	 * SecureRandom seed generator (ThreadedSeedGenerator) seems to
	 * have this bug too (which had been causing this test to fail
	 * when run with jtreg on Windows XP-- see 4910382).
	 */
	(new java.security.SecureRandom()).nextInt();

	RuntimeThreadInheritanceLeak obj = new RuntimeThreadInheritanceLeak();
	Exporter exporter = null;

	try {
	    ClassLoader loader = URLClassLoader.newInstance(new URL[0]);
	    ReferenceQueue refQueue = new ReferenceQueue();
	    Reference loaderRef = new WeakReference(loader, refQueue);
	    System.err.println("created loader: " + loader);

	    Thread.currentThread().setContextClassLoader(loader);
	    exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					     new BasicILFactory(), true, true);
	    exporter.export(obj);
	    Thread.currentThread().setContextClassLoader(
		ClassLoader.getSystemClassLoader());
	    System.err.println(
		"exported remote object with loader as context class loader");

	    loader = null;
	    System.err.println("nulled strong reference to loader");

	    exporter.unexport(true);
	    System.err.println("unexported remote object");
	    exporter = null;		// required to work around 4403470

	    /*
	     * HACK: Work around the fact that the sun.misc.GC daemon thread
	     * also has this bug-- it will have inherited our loader as its
	     * context class loader-- by giving it a chance to pass away.
	     */
	    Thread.sleep(1000);
	    System.gc();

	    System.err.println(
		"waiting to be notified of loader being weakly reachable...");
	    Reference dequeued = refQueue.remove(TIMEOUT);
	    if (dequeued == null) {
		System.err.println(
                    "TEST FAILED: loader not deteced weakly reachable");
		throw new RuntimeException(
                    "TEST FAILED: loader not detected weakly reachable");
	    }

	    System.err.println(
		"TEST PASSED: loader detected weakly reachable");

	} catch (RuntimeException e) {
	    throw e;
	} catch (Exception e) {
	    throw new RuntimeException("TEST FAILED: unexpected exception", e);
	} finally {
	    try {
		exporter.unexport(true);
	    } catch (Exception e) {
	    }
	}
    }
}
