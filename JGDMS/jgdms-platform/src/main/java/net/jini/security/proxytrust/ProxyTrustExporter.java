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

package net.jini.security.proxytrust;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.Permission;
import java.util.LinkedList;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;

/**
 * Contains the information necessary to export a remote object that has a
 * proxy that will not directly be considered trusted by clients, such that
 * the proxy can be trusted by clients using {@link ProxyTrustVerifier}. The
 * remote object to be exported (called the main remote object) must be an
 * instance of {@link ServerProxyTrust}. In addition to exporting the main
 * remote object, this exporter also exports a second remote object
 * (called the bootstrap remote object, which is created internally by this
 * exporter) that implements the {@link ProxyTrust} interface by delegating
 * the {@link ProxyTrust#getProxyVerifier getProxyVerifier} method to the
 * corresponding {@link ServerProxyTrust#getProxyVerifier getProxyVerifier}
 * method of the main remote object.  Refer to <code>
 * net.jini.jeri.ProxyTrustILFactory</code>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@Deprecated
public class ProxyTrustExporter implements Exporter {
    /** Permission required to use class loader of main proxy's class */
    private static final Permission loaderPermission =
				      new RuntimePermission("getClassLoader");
    /**
     * Cleaner that releases the bootstrap remote object once the main remote
     * object has been garbage collected, replacing the former static
     * WeakReference / ReferenceQueue / reaper-thread machinery. A {@code
     * Cleaner} is thread-safe and owns its own daemon thread, so no static lock
     * or shared thread pool is required.
     */
    private static final Cleaner cleaner = Cleaner.create();

    /** The main exporter, for the main remote object.*/
    private final Exporter mainExporter;
    /** The bootstrap exporter, for the ProxyTrust remote object. */
    private final Exporter bootExporter;
    /** The class loader to define the proxy class in, or null */
    private final ClassLoader loader;
    /** Cleanup registration for the current export, or null. */
    private Cleaner.Cleanable cleanable = null;

    /**
     * Creates an instance with the specified main exporter (which will be
     * used to export the main remote object) and the specified bootstrap
     * exporter (which will be used to export the bootstrap remote object).
     * The main exporter must produce a proxy (called the main proxy) that is
     * an instance of both {@link RemoteMethodControl} and
     * {@link TrustEquivalence}, and if the main proxy's class is not
     * <code>public</code>, the direct superinterfaces of that class and all
     * of its superclasses must be <code>public</code>. The bootstrap
     * exporter, when used to export a remote object that is an instance of
     * {@link ProxyTrust}, must produce a proxy (called the bootstrap proxy)
     * that is an instance of <code>ProxyTrust</code>,
     * <code>RemoteMethodControl</code>, and <code>TrustEquivalence</code>,
     * and should satisfy the bootstrap proxy requirements of
     * <code>ProxyTrustVerifier</code>.
     * The dynamic proxy class generated at export will be defined
     * by the same class loader as the main proxy's class.
     *
     * @param mainExporter the main exporter, for the main remote object
     * @param bootExporter the bootstrap exporter, for the bootstrap remote
     * object
     * @throws NullPointerException if any argument is <code>null</code>
     */
    public ProxyTrustExporter(Exporter mainExporter, Exporter bootExporter) {
	this(mainExporter, bootExporter, null);
    }

    /**
     * Creates an instance with the specified main exporter (which will be
     * used to export the main remote object), the specified bootstrap
     * exporter (which will be used to export the bootstrap remote object),
     * and the specified class loader (in which the generated dynamic proxy
     * class will be defined). The main exporter must produce a proxy (called
     * the main proxy) that is an instance of both {@link RemoteMethodControl}
     * and {@link TrustEquivalence}, and if the main proxy's class is not
     * <code>public</code>, the direct superinterfaces of that class and all
     * of its superclasses must be <code>public</code>. The bootstrap
     * exporter, when used to export a remote object that is an instance of
     * {@link ProxyTrust}, must produce a proxy (called the bootstrap proxy)
     * that is an instance of <code>ProxyTrust</code>,
     * <code>RemoteMethodControl</code>, and <code>TrustEquivalence</code>,
     * and should satisfy the bootstrap proxy requirements of
     * <code>ProxyTrustVerifier</code>.
     * If the specified class loader is <code>null</code>, the
     * dynamic proxy class generated at export will be defined by the same
     * class loader as the main proxy's class.
     *
     * @param mainExporter the main exporter, for the main remote object
     * @param bootExporter the bootstrap exporter, for the bootstrap remote
     * object
     * @param loader the class loader to define the proxy class in, or
     * <code>null</code>
     * @throws NullPointerException if either exporter argument is
     * <code>null</code>
     */
    public ProxyTrustExporter(Exporter mainExporter,
			      Exporter bootExporter,
			      ClassLoader loader)
    {
	if (mainExporter == null || bootExporter == null) {
	    throw new NullPointerException("exporter is null");
	}
	this.mainExporter = mainExporter;
	this.bootExporter = bootExporter;
	this.loader = loader;
    }

    /**
     * Exports the specified main remote object and returns a dynamic proxy
     * for the object. The main remote object must be an instance of
     * {@link ServerProxyTrust}. The main remote object is exported using the
     * main exporter (specified at the construction of this exporter),
     * returning a proxy called the main proxy. The main proxy must be an
     * instance of both {@link RemoteMethodControl} and
     * {@link TrustEquivalence}, and if the main proxy's class is not
     * <code>public</code>, the direct superinterfaces of that class and all
     * of its superclasses must be <code>public</code>. A bootstrap remote
     * object that is an instance of {@link ProxyTrust} is created and
     * exported using the bootstrap exporter (also specified at the
     * construction of this exporter), returning a proxy called the bootstrap
     * proxy. The bootstrap proxy must be an instance of
     * <code>ProxyTrust</code>, <code>RemoteMethodControl</code>, and
     * <code>TrustEquivalence</code>. The bootstrap remote object will remain
     * reachable as long as the main remote object is reachable and the
     * {@link #unexport unexport} method of this exporter has not returned
     * <code>true</code>. A {@link Proxy} class is generated that implements
     * the direct superinterfaces of the main proxy's class and all of its
     * superclasses, in the following order: the direct superinterfaces of a
     * class immediately follow the direct superinterfaces of its direct
     * superclass; the direct superinterfaces of a class are in declaration
     * order (the order in which they are declared in the class's
     * <code>implements</code> clause); and if an interface appears more than
     * once, only the first instance is retained. If a non-<code>null</code>
     * class loader was specified at the construction of this exporter, the
     * generated class is defined by that class loader, otherwise it is
     * defined by the class loader of the main proxy's class. The dynamic
     * proxy returned by this method is an instance of that generated class,
     * containing a {@link ProxyTrustInvocationHandler} instance created with
     * the main proxy and the bootstrap proxy.
     *
     * @throws ExportException if the export of either remote object throws
     * <code>ExportException</code>, or if the export the bootstrap remote
     * object throws <code>IllegalArgumentException</code>, or if the main
     * proxy is not an instance of both <code>RemoteMethodControl</code> and
     * <code>TrustEquivalence</code>, or if the main proxy's class is not
     * <code>public</code> and it or a superclass has a
     * non-<code>public</code> direct superinterface, or if the bootstrap
     * proxy is not an instance of <code>ProxyTrust</code>,
     * <code>RemoteMethodControl</code>, and <code>TrustEquivalence</code>,
     * or if any of the superinterfaces of the main proxy's class are not
     * visible through the class loader specified at the construction of this
     * exporter
     * @throws IllegalArgumentException if the specified remote object is not
     * an instance of <code>ServerProxyTrust</code>, or if the export of the
     * main remote object throws <code>IllegalArgumentException</code>
     * @throws IllegalStateException if the export of either remote object
     * throws <code>IllegalStateException</code>
     * @throws SecurityException if a non-<code>null</code> class loader was
     * not specified at the construction of this exporter and the calling
     * context does not have
     * {@link RuntimePermission}<code>("getClassLoader")</code> permission
     */
    public synchronized Remote export(Remote impl) throws ExportException {
	if (impl != null && !(impl instanceof ServerProxyTrust)) {
	    throw new IllegalArgumentException(
					   "must implement ServerProxyTrust");
	}
	if (loader == null) {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkPermission(loaderPermission);
	    }
	}
	Object main = mainExporter.export(impl);
	Class c = main.getClass();
	LinkedList ifaces = new LinkedList();
	Object boot = null;
	boolean ok = false;
	try {
	    if (!(main instanceof RemoteMethodControl)) {
		throw new ExportException(
			  "main proxy must implement RemoteMethodControl");
	    } else if (!(main instanceof TrustEquivalence)) {
		throw new ExportException(
			     "main proxy must implement TrustEquivalence");
	    }
	    boolean needPub = !Modifier.isPublic(c.getModifiers());
	    for (Class sup = c; sup != null; sup = sup.getSuperclass()) {
		Class[] ifs = sup.getInterfaces();
		for (int i = ifs.length; --i >= 0; ) {
		    if (needPub && !Modifier.isPublic(ifs[i].getModifiers())) {
			throw new ExportException(
				"main proxy implements non-public interface");
		    }
		    ifaces.remove(ifs[i]);
		    ifaces.addFirst(ifs[i]);
		}
	    }
	    ProxyTrustImpl bootImpl = new ProxyTrustImpl(impl);
	    // Keep the bootstrap impl reachable while the main object is; the
	    // cleaner releases it (so the bootstrap can be unexported and
	    // collected) once the main object is GC'd. The action must not
	    // reference the main object, so it holds the bootstrap impl, whose
	    // reference back to the main object is weak.
	    if (impl != null) {
		cleanable = cleaner.register(impl, new BootHolder(bootImpl));
	    }
	    boot = bootExporter.export(bootImpl);
	    if (!(boot instanceof ProxyTrust)) {
		throw new ExportException(
			     "bootstrap proxy must implement ProxyTrust");
	    } else if (!(boot instanceof RemoteMethodControl)) {
		throw new ExportException(
			 "bootstrap proxy must implement RemoteMethodControl");
	    } else if (!(boot instanceof TrustEquivalence)) {
		throw new ExportException(
			    "bootstrap proxy must implement TrustEquivalence");
	    }
	    Remote proxy = (Remote) Proxy.newProxyInstance(
				loader != null ? loader : c.getClassLoader(),
				(Class[]) ifaces.toArray(
						    new Class[ifaces.size()]),
				new ProxyTrustInvocationHandler(
						 (RemoteMethodControl) main,
						 (ProxyTrust) boot));
	    ok = true;
	    return proxy;
	} catch (IllegalArgumentException e) {
	    throw new ExportException("export failed", e);
	} finally {
	    if (!ok) {
		if (cleanable != null) {
		    cleanable.clean();
		}
		if (boot != null) {
		    bootExporter.unexport(true);
		}
		mainExporter.unexport(true);
	    }
	}
    }

    /**
     * Unexports the remote objects that were previously exported via this
     * exporter. The <code>unexport</code> method of the main exporter is
     * called with the specified argument and if that returns
     * <code>true</code>, the <code>unexport</code> method of the bootstrap
     * exporter is called with <code>true</code>. The result of the main
     * <code>unexport</code> call is returned by this method. Any exception
     * thrown by either <code>unexport</code> call is rethrown by this
     * method.
     *
     * @throws IllegalStateException if the unexport of either remote object
     * throws <code>IllegalStateException</code>
     */
    public synchronized boolean unexport(boolean force) {
	if (!mainExporter.unexport(force)) {
	    return false;
	}
	bootExporter.unexport(true);
	if (cleanable != null) {
	    cleanable.clean();
	}
	return true;
    }

    /**
     * Holds the bootstrap remote object strongly while the main remote object
     * is reachable, releasing it when the {@link Cleaner} runs (the main object
     * has been collected) or on an explicit {@link #unexport}. Registered with
     * the cleaner against the main remote object, so it must not reference that
     * object; it holds the {@link ProxyTrustImpl}, whose reference back to the
     * main object is weak.
     */
    private static final class BootHolder implements Runnable {
	private ProxyTrustImpl bootImpl;

	BootHolder(ProxyTrustImpl bootImpl) {
	    this.bootImpl = bootImpl;
	}

	public void run() {
	    bootImpl = null;
	}
    }

    /** ProxyTrust impl class */
    private static class ProxyTrustImpl implements ProxyTrust {
	/** Weak reference to the main remote object */
	private final Reference ref;

	ProxyTrustImpl(Remote impl) {
	    this.ref = new WeakReference(impl);
	}

	/** Delegate to the main remote object */
	public TrustVerifier getProxyVerifier() throws RemoteException {
	    ServerProxyTrust impl = (ServerProxyTrust) ref.get();
	    if (impl == null) {
		throw new UnsupportedOperationException("impl is gone");
	    }
	    return impl.getProxyVerifier();
	}
    }
}
