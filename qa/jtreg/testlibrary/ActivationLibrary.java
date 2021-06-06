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
/**
 * 
 */

import org.apache.river.jeri.internal.runtime.Util;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;
import net.jini.activation.Activatable;
import net.jini.activation.arg.ActivationDesc;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationID;
import java.rmi.server.ExportException;
import java.rmi.server.RMIClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.activation.ActivationExporter;
import net.jini.activation.ActivationGroup;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.jrmp.JrmpExporter;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * Class of test utility/library methods related to Activatable
 * objects.
 */
public class ActivationLibrary {
    /** time safeDestroy should wait before failing on shutdown rmid */
    final static int SAFE_WAIT_TIME = 60000;

    static void mesg(Object mesg) {
	System.err.println("ACTIVATION_LIBRARY: " + mesg.toString());
    }

    /**
     * Deactivate an activated Activatable
     */
    public static void deactivate(Remote remote, ActivationID id) {
	deactivate(remote, id, null);
    }

    /**
     * Deactivates an activated object.  If exporter is null, then
     * deactivate using pre 1.4-APIs; otherwise use the new 1.4
     * API.
     */
    public static void deactivate(Remote remote,
				  ActivationID id,
				  Exporter exporter)
    {
	int i = 0;
	do {
	    i++;
	    try {
		boolean success =
		    ((exporter == null) ?
		     Activatable.inactive(id) :
		     ActivationGroup.inactive(id, exporter));
		    
		if (success) {
		    mesg("inactive successful");
		    return;
		} else {
		    Thread.sleep(1000);
		}
	    } catch (InterruptedException e) {
		continue;
	    } catch (Exception e) {
		e.printStackTrace();
		break;
	    }
	} while (i < 5);
	
	mesg("giving up on deactivation after " + i + " attempts");
	mesg("unexporting object forcibly instead");

	forcedUnexport(remote, exporter);
    }

    /**
     * Forcibly unexports the object.
     */
    public static void forcedUnexport(Remote remote, Exporter exporter) {
	
	try {
	    // forcibly unexport the object
	    if (exporter == null) {
		Activatable.unexportObject(remote, true);
	    } else {
		exporter.unexport(true);
	    }
	} catch (NoSuchObjectException ex) {
	}
    }


    /**
     * Simple method call to see if rmid is running.
     * 
     * This method intentionally avoids performing a lookup on the
     * activation system.  
     */
    public static boolean rmidRunning(int port) {
	int allowedNotReady = 10;
	int connectionRefusedExceptions = 0;

	for (int i = 0; i < 15 ; i++) {

	    try {
		Thread.sleep(500);
		if (containsString(Naming.list("//:" + port ), 
		    "net.jini.activation.arg.ActivationSystem")) 
                {
		    return true;
		}

	    } catch (java.rmi.ConnectException ce) {
		// ignore connect exceptions until we decide rmid is not up
		if ((connectionRefusedExceptions ++) >= allowedNotReady) {
		    return false;
		}

	    } catch (Exception e) {
		// print out other types of exceptions as an FYI.
		// test should not fail as rmid is likely to be in an
		// undetermined state at this point.

		mesg("caught an exception trying to" + 
		     " start rmid, last exception was: " + 
		     e.getMessage());
		e.printStackTrace();
	    }
	}
	return false;
    }

    /**
     * Check to see if an arry of Strings contains a given string.
     */
    private static boolean 
	containsString(String[] strings, String contained) 
    {
	if (strings == null) {
	    if (contained == null) {
		return true;
	    }
	    return false;
	}
	
	for (int i = 0 ; i < strings.length ; i ++ ) {
	    if ((strings[i] != null) && 
		(strings[i].indexOf(contained) >= 0)) 
	    {
		return true;
	    }
	}
	return false;
    }

    /** cleanup after rmid */
    public static void rmidCleanup(RMID rmid) {
	if (rmid != null) {
	    if (!ActivationLibrary.safeDestroy(rmid, 
					       SAFE_WAIT_TIME)) 
	    {
		TestLibrary.bomb("rmid not destroyed in: " + 
				 SAFE_WAIT_TIME + 
				 " milliseconds");
	    }
	}
	RMID.removeLog();
    }

    /**
     * Invoke shutdown on rmid in a way that will not cause a test 
     * to hang.
     *
     * @return whether or not shutdown completed succesfully in the
     *         timeAllowed 
     */
    public static boolean safeDestroy(RMID rmid, long timeAllowed) {
	DestroyThread destroyThread = new DestroyThread(rmid);
	destroyThread.start();

	try {
	    destroyThread.join(timeAllowed);
	} catch (InterruptedException ie) {
	    Thread.currentThread().interrupt();
	}

	return destroyThread.shutdownSucceeded();
    }

    /**
     * Thread class to handle the destruction of rmid
     */
    static class DestroyThread extends Thread {
	RMID rmid = null; 
	boolean succeeded = false;

	DestroyThread(RMID rmid) {
	    this.rmid = rmid;
	    this.setDaemon(true);
	}

	public void run() {
	    if (ActivationLibrary.
		rmidRunning(TestLibrary.RMID_PORT)) 
            {
		rmid.destroy();

		synchronized (this) {
		    // flag that the test was able to shutdown rmid
		    succeeded = true;
		}
		mesg("finished destroying rmid");
	    } else {
		mesg("tried to shutdown when rmid was not running");
	    }
	}

	public synchronized boolean shutdownSucceeded() {
	    return succeeded;
	}
    }


    public static Remote createProxy(ActivationID id, ActivationDesc desc)
    {
	try {
	    Class cl = RMIClassLoader.loadClass(desc.getLocation(),
						desc.getClassName());
	    Class[] remoteInterfaces = Util.getRemoteInterfaces(cl);
	    InvocationHandler handler =
		new ActivatableInvocationHandler(id, null);
	    return (Remote) newProxyInstance(null, remoteInterfaces, handler);
	} catch (Exception e) {
	    throw (RuntimeException)
		new RuntimeException("proxy creation failed").initCause(e);
	}
    }

    public static Remote createSecureProxy(ActivationID id,
					   ActivationDesc desc)
    {
	try {
	    Class cl = RMIClassLoader.loadClass(desc.getLocation(),
						desc.getClassName());
	    Class[] remoteInterfaces = Util.getRemoteInterfaces(cl);
	    List list = new ArrayList(Arrays.asList(remoteInterfaces));
	    if (list.remove(ProxyTrust.class)) {
		remoteInterfaces =
		    (Class[]) list.toArray(new Class[list.size()]);
	    }
	    InvocationHandler handler =
		new ActivatableInvocationHandler(id, null);
	    return (Remote) newProxyInstance(remoteInterfaces,
					     new Class[]{
						   RemoteMethodControl.class,
						   TrustEquivalence.class},
					     handler);
	} catch (Exception e) {
	    throw (RuntimeException)
		new RuntimeException("proxy creation failed").initCause(e);
	}
    }

    /**
     * Returns a proxy that implements the concatenation of the specified
     * interfaces, containing the specified invocation handler.
     */
    public static Remote newProxyInstance(Class[] ifs1,
				     Class[] ifs2,
				     InvocationHandler ih)
    {
	Class[] ifs = combineInterfaces(ifs1, ifs2);
	return (Remote) Proxy.newProxyInstance(pickClassLoader(ifs), ifs, ih);
    }

    /**
     * Returns a concatenated array of the interfaces in i1 and i2.
     */
    public static Class[] combineInterfaces(Class[] i1, Class[] i2) {

	if (i1 == null) {
	    return i2;
	} else if (i2 == null) {
	    return i1;
	} else {
	    Class[] interfaces = new Class[i1.length + i2.length];
	    System.arraycopy(i1, 0, interfaces, 0, i1.length);
	    System.arraycopy(i2, 0, interfaces, i1.length, i2.length);
	    return interfaces;
	}
    }

    /**
     * Returns the preferred classloader to use for defining a proxy class
     * that implements all of the specified interfaces.
     */
    private static ClassLoader pickClassLoader(Class[] ifs) {
    outer:
	for (int i = ifs.length; --i >= 0; ) {
	    ClassLoader l = ifs[i].getClassLoader();
	    for (int j = ifs.length; --j >= 0; ) {
		if (!covers(l, ifs[j].getClassLoader())) {
		    continue outer;
		}
	    }
	    return l;
	}
	throw new IllegalArgumentException(
				      "no classloader covers all interfaces");
    }

    /**
     * Returns true if the first argument is either equal to, or is a
     * descendant of, the second argument.  Null is treated as the root of
     * the tree.
     */
    public static boolean covers(ClassLoader sub, ClassLoader sup) {
	if (sup == null) {
	    return true;
	} else if (sub == null) {
	    return false;
	}
	do {
	    if (sub == sup) {
		return true;
	    }
	    sub = sub.getParent();
	} while (sub != null);
	return false;
    }

    public static Remote register(String exportType, ActivationDesc desc)
	throws ActivationException, RemoteException
    {
	if (exportType.equals("default")) {
	    return Activatable.register(desc);
	} else {
	    ActivationID id =
		ActivationGroup.getSystem().registerObject(desc);
	    if (exportType.equals("basic")) {
		return createSecureProxy(id, desc);
	    } else if (exportType.equals("jrmp")) {
		return createProxy(id, desc);
	    } else {
		throw new ActivationException("unknown exportType");
	    }
	}
    }
    
    public static class ExportHelper implements Runnable {

	private final String exportType;
	private final ActivationID id;
	private final Remote impl;
	private Remote stub;
	private Exporter exporter = null;
	
	public ExportHelper(MarshalledObject mobj,
			    Remote impl,
			    ActivationID id)
    	    throws ActivationException
	{
	    try {
		this.exportType = (String) mobj.get();
	    } catch (Exception e) {
		throw new ActivationException(
			"MarshalledObject.get failed", e);
	    }
	
	    this.impl = impl;
	    this.id = id;
	}

	public ExportHelper(String exportType,
			    Remote impl,
			    ActivationID id)
    	    throws ActivationException
	{
	    this.exportType = exportType;
	    this.impl = impl;
	    this.id = id;
	}
	
	public Remote export() throws RemoteException {
	    if (exportType.equals("default")) {
		stub = Activatable.exportObject(impl, id, 0);
	    } else {
		Exporter uexporter;
		if (exportType.equals("basic")) {
		    uexporter = new
			BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					  new AtomicILFactory(null, null, ActivationLibrary.class),
					  true, true);
		} else if (exportType.equals("jrmp")) {
		    uexporter = new JrmpExporter();
		} else {
		    throw new ExportException("unknown exportType");
		}
		exporter = new ActivationExporter(id, uexporter);
		stub = exporter.export(impl);
	    }
	    System.err.println("stub = " + stub.toString());
	    return stub;
	}

	public Remote getStub() {
	    return stub;
	}

	public ActivationID getActivationID() {
	    return id;
	}

	public void deactivate() {
	    (new Thread(this, "ExportHelper")).start();
	}

	public void run() {
	    ActivationLibrary.deactivate(impl, id, exporter);
	}
    }
}
