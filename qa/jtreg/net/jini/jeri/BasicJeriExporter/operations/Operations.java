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
 * @bug 4403495 4403470 4302502
 * 
 * @summary test BasicJeriExporter basic operations
 * 
 * @run main/othervm/policy=security.policy Operations
 */
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.rmi.server.Unreferenced;
import java.security.AccessControlException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javax.security.auth.AuthPermission;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.*;
import net.jini.jeri.ssl.SslServerEndpoint;

public class Operations {

    public interface Test extends Remote {
	void test() throws RemoteException;
    }

    public static class Tester implements Test, Unreferenced {
	public static boolean unreferenced = false;

	private BasicJeriExporter exp;
	private ClassLoader ccl;

	public Tester(BasicJeriExporter exp, ClassLoader ccl) {
	    this.exp = exp;
	    this.ccl = ccl;
	}

	public void test() {
	    // check the context classloader
	    if (Thread.currentThread().getContextClassLoader() != ccl) {
		throw new RuntimeException("bad context classloader");
	    }
	    // check that unforced unexport does nothing
	    if (exp.unexport(false)) {
		throw new RuntimeException("export in call succeeded");
	    }
	}

	public void unreferenced() {
	    unreferenced = true;
	}
    }

    public static class MyILFactory extends AbstractILFactory {
	private MethodConstraints serverConstraints;

	public MyILFactory(MethodConstraints serverConstraints) {
	    this.serverConstraints = serverConstraints;
	}

	protected InvocationDispatcher createInvocationDispatcher(
						Collection methods,
						Remote impl,
						ServerCapabilities caps)
	    throws ExportException
	{
	    return new MyDispatcher(methods, caps, serverConstraints);
	}

	protected InvocationHandler createInvocationHandler(
						    Class[] interfaces,
						    Remote impl,
						    ObjectEndpoint oe)
	    throws ExportException
	{
	    return new MyHandler(oe, serverConstraints);
	}
    }

    public static class MyHandler extends BasicInvocationHandler {
	public static WeakReference wr;

	public MyHandler(ObjectEndpoint ep, MethodConstraints mc) {
	    super(ep, mc);
	    wr = new WeakReference(this);
	}
    }

    public static class MyDispatcher extends BasicInvocationDispatcher {
	public static WeakReference wr;

	public MyDispatcher(Collection methods,
			    ServerCapabilities serverCaps,
			    MethodConstraints serverConstraints)
	    throws ExportException
	{
	    super(methods, serverCaps, serverConstraints, null, null);
	    wr = new WeakReference(this);
	}
    }

    public interface BadInterface extends Remote {
	public void foo() throws ExportException;
    }

    public static class BadImpl implements BadInterface {
	public void foo() {
	}
    }

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	Subject s =
	    new Subject(true,
			Collections.singleton(new X500Principal("CN=bob")),
			Collections.EMPTY_SET, Collections.EMPTY_SET);
	final SslServerEndpoint se =
	    SslServerEndpoint.getInstance(s, null, null, 0);
	final MethodConstraints nomc =
	    new BasicMethodConstraints(InvocationConstraints.EMPTY);
	final InvocationLayerFactory uilf =
	    new BasicILFactory(nomc, null);
	// check that null server endpoint throws NPE
	try {
	    new BasicJeriExporter(null, uilf);
	    throw new RuntimeException("null endpoint did not cause NPE");
	} catch (NullPointerException e) {
	}
	// check that null il factory throws NPE
	try {
	    new BasicJeriExporter(se, null);
	    throw new RuntimeException("null il factory did not cause NPE");
	} catch (NullPointerException e) {
	}
	InvocationConstraints sauth =
	    new InvocationConstraints(ServerAuthentication.YES, null);
	MethodConstraints samc = new BasicMethodConstraints(sauth);
	final InvocationLayerFactory ailf =
	    new BasicILFactory(samc, null);
	// check that subject cannot satisfy server authentication
	BasicJeriExporter exp = new BasicJeriExporter(se, ailf);
	try {
	    exp.export(new Tester(exp, null));
	    throw new RuntimeException("server auth req did not cause EE");
	} catch (ExportException e) {
	}
	final Uuid oid = UuidFactory.generate();
	exp = new BasicJeriExporter(se, uilf, false, false, oid);
	compare(exp, se, uilf, false, false, oid);
	compare(new BasicJeriExporter(se, uilf, false, true, oid),
		se, uilf, false, true, oid);
	compare(new BasicJeriExporter(se, uilf, true, false, oid),
		se, uilf, true, false, oid);
	compare(new BasicJeriExporter(se, uilf, true, true, oid),
		se, uilf, true, true, oid);
	// check that null object ID is OK
	new BasicJeriExporter(se, uilf, false, false, null);
	Subject.doAsPrivileged(s, new PrivilegedExceptionAction() {
	    public Object run() throws Exception {
		compare(new BasicJeriExporter(se, uilf, true, true, oid),
			se, uilf, true, true, oid);
		return null;
	    }
	}, null);
	// check that subject cannot satisfy server authentication
	exp = new BasicJeriExporter(se, ailf, true, true, null);
	try {
	    exp.export(new Tester(exp, null));
	    throw new RuntimeException("server auth req did not cause EE");
	} catch (ExportException e) {
	}
	InvocationLayerFactory myilf = new MyILFactory(nomc);
	// check that null object ID is OK
	exp = new BasicJeriExporter(se, myilf, false, false, null);
	// check that export of null impl causes NPE
	try {
	    exp.export(null);
	    throw new RuntimeException("export of null impl succeeded");
	} catch (NullPointerException e) {
	}
	// check that remote methods are checked for RemoteException
	exp = new BasicJeriExporter(se, myilf, false, false, null);
	try {
	    exp.export(new BadImpl());
	    throw new RuntimeException("export of bad impl succeeded");
	} catch (ExportException e) {
	}
	// check that unexport fails before export
	exp = new BasicJeriExporter(se, myilf, false, false, null);
	try {
	    exp.unexport(true);
	    throw new RuntimeException("unexport succeeded prior to export");
	} catch (IllegalStateException e) {
	}
	ClassLoader ccl = Thread.currentThread().getContextClassLoader();
	ClassLoader cl = new URLClassLoader(new URL[]{});
	Test obj = new Tester(exp, cl);
	WeakReference wrobj = new WeakReference(obj);
	Thread.currentThread().setContextClassLoader(cl);
	Test stub = (Test) exp.export(obj);
	// check that second export fails
	try {
	    exp.export(new Tester(exp, cl));
	    throw new RuntimeException("second export succeeded");
	} catch (IllegalStateException e) {
	}
	Thread.currentThread().setContextClassLoader(ccl);
	stub.test();
	// give server side a chance to say the call is done
	Thread.sleep(5000);
	// check that unforced unexport works
	if (!exp.unexport(false)) {
	    throw new RuntimeException("unexport failed");
	}
	// check that redundant unexport works
	if (!exp.unexport(false)) {
	    throw new RuntimeException("second unexport failed");
	}
	// check that subsequent export fails
	try {
	    exp.export(obj);
	    throw new RuntimeException("reexport succeeded");
	} catch (IllegalStateException e) {
	}
	obj = null;
	cl = null;
	flushRefs();
	// check that nothing holds onto the server impl
	if (wrobj.get() != null) {
	    throw new RuntimeException("server impl held onto");
	}
	// check that nothing holds onto the dispatcher
	if (MyDispatcher.wr.get() != null) {
	    throw new RuntimeException("dispatcher held onto");
	}
	stub = null;
	WeakReference wrstub = new WeakReference(stub);
	exp = null;
	WeakReference wrexp = new WeakReference(exp);
	flushRefs();
	// check that nothing holds onto the stub
	if (wrstub.get() != null) {
	    throw new RuntimeException("stub held onto");
	}
	// check that nothing holds onto the invocation handler
	if (MyHandler.wr.get() != null) {
	    throw new RuntimeException("invocation handler held onto");
	}
	// check that nothing holds onto the exporter
	if (wrexp.get() != null) {
	    throw new RuntimeException("exporter held onto");
	}
	// check that unreferenced was not called
	if (Tester.unreferenced) {
	    throw new RuntimeException("unreferenced called");
	}
	exp = new BasicJeriExporter(se, uilf, true, false, null);
	obj = new Tester(exp, ccl);
	wrobj = new WeakReference(obj);
	stub = (Test) exp.export(obj);
	stub.test();
	obj = null;
	exp.unexport(true);
	flushRefs();
	// check that nothing holds onto the server impl
	if (wrobj.get() != null) {
	    throw new RuntimeException("server impl held onto");
	}
	exp = new BasicJeriExporter(se, uilf, true, false, null);
	obj = new Tester(exp, ccl);
	wrobj = new WeakReference(obj);
	stub = (Test) exp.export(obj);
	stub.test();
	obj = null;
	flushRefs();
	// check that server impl is still around
	if (wrobj.get() == null) {
	    throw new RuntimeException("server impl not held onto");
	}
	// check that unreferenced was not called
	if (Tester.unreferenced) {
	    throw new RuntimeException("unreferenced called");
	}
	stub.test();
	// give server side a chance to really finish the call
	Thread.sleep(5000);
	wrstub = new WeakReference(stub);
	// cause stub to be marshalled and unmarshalled to force dirty call
	(new MarshalledInstance(stub)).get(false);
	stub = null;
	flushRefs();
	// check that nothing holds onto the stub
	if (wrstub.get() != null) {
	    throw new RuntimeException("stub held onto");
	}
	Thread.sleep(5000);
	// check that unreferenced was called
	if (!Tester.unreferenced) {
	    throw new RuntimeException("unreferenced not called");
	}
	flushRefs();
	// check that nothing holds onto the server impl
	if (wrobj.get() != null) {
	    throw new RuntimeException("server impl held onto");
	}
	if (!exp.unexport(false)) {
	    throw new RuntimeException("unexport failed");
	}
    }

    /**
     * Check that the exporter contents match the specified values.
     */
    static void compare(BasicJeriExporter exp,
			ServerEndpoint ep,
			InvocationLayerFactory ilf,
			boolean enableDGC,
			boolean keepAlive,
			Uuid oid)
    {
	ServerEndpoint nep = exp.getServerEndpoint();
	if (!ep.equals(nep)) {
	    throw new RuntimeException("endpoints not equal");
	}
	if (exp.getInvocationLayerFactory() != ilf) {
	    throw new RuntimeException("il factory not equal");
	}
	if (exp.getEnableDGC() != enableDGC) {
	    throw new RuntimeException("enableDGC not equal");
	}
	if (exp.getKeepAlive() != keepAlive) {
	    throw new RuntimeException("keepAlive not equal");
	}
	if (exp.getObjectIdentifier() != oid) {
	    throw new RuntimeException("obj id not equal");
	}
    }

    /**
     * Force desparate garbage collection so that all WeakReference instances
     * will be cleared.
     */
    private static void flushRefs() {
	ArrayList chain = new ArrayList();
	try {
	    while (true) {
		chain.add(new int[65536]);
	    }
	} catch (OutOfMemoryError e) {
	}
    }
}
