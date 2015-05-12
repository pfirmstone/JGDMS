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

package net.jini.iiop;

import java.lang.ref.WeakReference;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.rmi.PortableRemoteObject;
import javax.rmi.CORBA.Stub;
import javax.rmi.CORBA.Util;
import net.jini.export.Exporter;
import org.omg.CORBA.ORB;

/**
 * An <code>IiopExporter</code> can be used to export a single remote object to
 * the <a href="http://java.sun.com/j2se/1.4/docs/guide/rmi-iiop/">RMI-IIOP</a>
 * runtime.  It acts as an adapter between the {@link Exporter} interface and
 * existing RMI-IIOP (un)export/utility APIs provided by the {@link javax.rmi}
 * and {@link javax.rmi.CORBA} packages.
 * 
 * <p>Note: although this exporter internally makes use of {@link
 * javax.rmi.PortableRemoteObject}, it cannot be used to export remote objects
 * over JRMP (as <code>PortableRemoteObject</code> can).  For JRMP exports,
 * {@link net.jini.jrmp.JrmpExporter} should be used instead.
 * 
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.iiop.IiopExporter</code> to log
 * information at the following levels:
 *
 * <table summary="Describes what is logged by IiopExporter at various
 *        logging levels" border=1 cellpadding=5>
 *
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link Level#FINE FINE} <td> successful export of object
 * 
 * <tr> <td> {@link Level#FINE FINE} <td> attempted unexport of object
 *
 * </table>
 */
public final class IiopExporter implements Exporter {
    
    private static final Logger logger =
	Logger.getLogger("net.jini.iiop.IiopExporter");

    private ORB orb;
    private WeakReference ref;

    /**
     * Creates a new exporter which can be used to export a remote object over
     * IIOP.  The stub resulting from an export of a remote object with this
     * exporter will not be connected to any {@link ORB}.
     */
    public IiopExporter() {
    }
    
    /**
     * Creates a new exporter which can be used to export a remote object over
     * IIOP.  If the given {@link ORB} is non-<code>null</code>, then the stub
     * resulting from an export of a remote object with this exporter will be
     * connected to it; otherwise, the stub will be left unconnected.
     * 
     * @param	orb if non-<code>null</code>, ORB to which to connect stub of
     * 		exported object
     */
    public IiopExporter(ORB orb) {
	this.orb = orb;
    }

    /**
     * Exports a remote object, <code>impl</code>, to the RMI-IIOP runtime and
     * returns a proxy (stub) for the remote object.  If an {@link ORB} was
     * specified during construction of this exporter, then the returned
     * RMI-IIOP stub will be connected to it.  This method cannot be called
     * more than once to export a remote object or an 
     * {@link IllegalStateException} will be thrown.
     *
     * @throws 	NullPointerException {@inheritDoc}
     * @throws	IllegalStateException {@inheritDoc}
     */
    public synchronized Remote export(Remote impl)
	throws ExportException
    {
	if (impl == null) {
	    throw new NullPointerException();
	} else if (ref != null) {
	    throw new IllegalStateException(
		"object already exported via this exporter");
	} else if (getTieClass(impl.getClass()) == null) {
	    throw new ExportException("tie class unavailable");
	}
	ref = new WeakReference(impl);
	
	try {
	    PortableRemoteObject.exportObject(impl);
	    Remote proxy = PortableRemoteObject.toStub(impl);
	    if (orb != null) {
		((Stub) proxy).connect(orb);
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, 
		    "export of {0} via {1} returns proxy {2}",
		    new Object[]{ impl, this, proxy });
	    }
	    return proxy;
	} catch (ExportException ex) {
	    throw ex;
	} catch (RemoteException ex) {
	    throw new ExportException("export failed", ex);
	}
    }

    /**
     * Unexports the remote object exported via this exporter's
     * {@link #export} method such that the object can no longer
     * accept incoming remote calls that were possible as a result of
     * exporting via this exporter.
     *
     * <p>This method unexports the remote object via a call to 
     * {@link PortableRemoteObject#unexportObject}, which only supports the
     * equivalent of a "forced" unexport (i.e., one in which the object is
     * unexported regardless of the presence of pending or in-progress calls).
     * Hence, this method will not consult the value of <code>force</code>,
     * and will always attempt a "forced" unexport of the remote object,
     * returning <code>true</code> upon normal completion.
     * 
     * @param	force ignored value (normally indicates whether or not to
     * 		unexport the object in the presence of pending or in-progress
     * 		calls, but this exporter does not support "unforced" unexports)
     * @return	<code>true</code>
     * @throws	IllegalStateException {@inheritDoc}
     */
    public synchronized boolean unexport(boolean force) {
	if (ref == null) {
	    throw new IllegalStateException(
		"an object has not been exported via this exporter");
	}
	Remote impl = (Remote) ref.get();
	if (impl != null) {
	    try {
		PortableRemoteObject.unexportObject(impl);
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "unexport on {0} returns {1}",
			new Object[]{ this, Boolean.TRUE });
		}
	    } catch (NoSuchObjectException ex) {
	    }
	}
	return true;
    }
    
    /**
     * Returns the string representation for this exporter.
     * 
     * @return the string representation for this exporter
     */
    public String toString() {
	return (orb != null) ? "IiopExporter[" + orb + "]" : "IiopExporter[]";
    }

    /**
     * Returns tie class for the given remote object class, or null if none
     * available.
     */
    private static Class getTieClass(Class implClass) {
	// based on com.sun.corba.se.internal.util.Utility.loadTie()
	// REMIND: cache results?
	String implClassName = implClass.getName();
	int i = implClassName.indexOf('$');
	if (i < 0) {
	    i = implClassName.lastIndexOf('.');
	}
	String tieClassName = (i > 0) ?
	    implClassName.substring(0, i + 1) + "_" + 
	    implClassName.substring(i + 1) + "_Tie" :
	    "_" + implClassName + "_Tie";

	// workaround for 4632973
	ArrayList names = new ArrayList(2);
	names.add(tieClassName);
	if (tieClassName.startsWith("java.") || 
	    tieClassName.startsWith("com.sun.") ||
	    tieClassName.startsWith("net.jini.") ||
	    tieClassName.startsWith("jini.") || 
	    tieClassName.startsWith("javax."))
	{
	    names.add("org.omg.stub." + tieClassName);
	}
	
	ClassLoader loader = implClass.getClassLoader();
	String codebase = Util.getCodebase(implClass);
	for (Iterator iter = names.iterator(); iter.hasNext();) {
	    tieClassName = (String) iter.next();
	    try {
		return Util.loadClass(tieClassName, codebase, loader);
	    } catch (ClassNotFoundException ex) {
	    }

	    // second attempt futile, but try anyway to mimic Utility.loadTie()
	    if (loader != null) {
		try {
		    return loader.loadClass(tieClassName);
		} catch (ClassNotFoundException ex) {
		}
	    }
	}
	
	Class implSuper = implClass.getSuperclass();
	return (implSuper != null &&
		implSuper != PortableRemoteObject.class &&
		implSuper != Object.class) ? getTieClass(implSuper) : null;
    }
}
