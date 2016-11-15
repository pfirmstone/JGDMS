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

package net.jini.jrmp;

import java.lang.ref.WeakReference;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationID;
import java.rmi.server.ExportException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.export.Exporter;


/**
 * A <code>JrmpExporter</code> contains the information necessary to export a
 * single remote object to the 
 * <a href="http://java.sun.com/j2se/1.4/docs/guide/rmi/">JRMP</a> runtime.  It
 * acts as an adapter between existing methods for (un)exporting remote objects
 * over JRMP (e.g, {@link UnicastRemoteObject#exportObject(Remote)}, 
 * {@link Activatable#exportObject(Remote, ActivationID, int)}) and the 
 * {@link Exporter} interface.
 *
 * <p>An object exported via a <code>JrmpExporter</code> can customize the
 * following properties that govern invocation behavior and other
 * characteristics of the exported remote object and its stub:
 * <ul>
 * 
 * <li><code>port</code>: the port number for the {@link java.net.ServerSocket}
 * on which the JRMP runtime will listen for incoming calls to the exported
 * object.
 * 
 * <li>{@link RMIClientSocketFactory}: the factory object used by client stubs
 * to create {@link java.net.Socket} objects over which to issue calls to the
 * exported object.  If <code>null</code>, then the global JRMP socket factory
 * will be used (i.e., the value returned by 
 * {@link java.rmi.server.RMISocketFactory#getSocketFactory}), or if there
 * isn't one set, then the default global JRMP socket factory will be used
 * (i.e., the value returned by 
 * {@link java.rmi.server.RMISocketFactory#getDefaultSocketFactory}).
 * 
 * <li>{@link RMIServerSocketFactory}: the factory object used by the JRMP
 * runtime to create <code>ServerSocket</code> objects over which to receive
 * calls to the exported object.  If <code>null</code>, then the global JRMP
 * socket factory will be used, or if there isn't one set, then the default
 * global JRMP socket factory will be used.  The default global JRMP socket
 * factory returns a <code>ServerSocket</code> for an anonymous port if passed
 * a port number of zero.
 * 
 * <li>{@link ActivationID}: the <code>ActivationID</code> identifying this
 * remote object to the activation system, or <code>null</code> if the remote
 * object is not to be exported as activatable.
 * </ul>
 * 
 * <p>This exporter is a front-end adapter on top of
 * <code>UnicastRemoteObject</code> and <code>Activatable</code>; exporting
 * remote objects through this exporter is equivalent to doing so directly via
 * the various <code>exportObject</code> methods defined by the aforementioned
 * classes.
 * 
 * @author	Sun Microsystems, Inc.
 * @since	2.0
 *
 * @org.apache.river.impl
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.jrmp.JrmpExporter</code> to log
 * information at the following levels:
 *
 * <table summary="Describes what is logged by JrmpExporter at various
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
public final class JrmpExporter implements Exporter {
    
    private static final Logger logger =
	Logger.getLogger("net.jini.jrmp.JrmpExporter");

    private final int port;
    private final RMIClientSocketFactory csf;
    private final RMIServerSocketFactory ssf;
    private final ActivationID id;
    private WeakReference ref;

    /**
     * Creates an exporter for a non-activatable JRMP "unicast" remote object
     * that exports on an anonymous port and does not use custom socket
     * factories.
     */
    public JrmpExporter() {
	this(0);
    }
    
    /**
     * Creates an exporter for a non-activatable JRMP "unicast" remote object
     * that exports on the specified TCP port and does not use custom socket
     * factories.
     * 
     * @param	port number of the port on which an exported object will
     * 		receive calls (if zero, an anonymous port will be chosen)
     */
    public JrmpExporter(int port) {
	this(port, null, null);
    }
    
    /**
     * Creates an exporter for a non-activatable JRMP "unicast" remote object
     * that exports on the specified TCP port and uses sockets created by the
     * given custom socket factories.
     * 
     * @param	port number of the port on which an exported object will
     * 		receive calls (if zero, an anonymous port will be chosen)
     * @param	csf client-side socket factory (if null, the global JRMP socket
     * 		factory or, if necessary, the default global JRMP socket
     * 		factory will be used to create client-side sockets)
     * @param	ssf server-side socket factory (if null, the global JRMP socket
     * 		factory or, if necessary, the default global JRMP socket
     * 		factory will be used to create server-side sockets)
     */
    public JrmpExporter(int port,
			RMIClientSocketFactory csf,
			RMIServerSocketFactory ssf)
    {
	this.port = port;
	this.csf = csf;
	this.ssf = ssf;
	id = null;
    }
    
    /**
     * Creates an exporter for an activatable JRMP remote object with the given
     * activation ID that exports on the specified TCP port and does not use
     * custom socket factories.
     * 
     * @param	id activation ID associated with the object to export
     * @param	port number of the port on which an exported object will
     * 		receive calls (if zero, an anonymous port will be chosen)
     * @throws	NullPointerException if <code>id</code> is <code>null</code>
     */
    public JrmpExporter(ActivationID id, int port) {
	this(id, port, null, null);
    }
    
    /**
     * Creates an exporter for an activatable JRMP remote object with the given
     * activation ID that exports on the specified TCP port and uses sockets
     * created by the given custom socket factories.
     * 
     * @param	id activation ID associated with the object to export
     * @param	port number of the port on which an exported object will
     * 		receive calls (if zero, an anonymous port will be chosen)
     * @param	csf client-side socket factory (if null, the global JRMP socket
     * 		factory or, if necessary, the default global JRMP socket
     * 		factory will be used to create client-side sockets)
     * @param	ssf server-side socket factory (if null, the global JRMP socket
     * 		factory or, if necessary, the default global JRMP socket
     * 		factory will be used to create server-side sockets)
     * @throws	NullPointerException if <code>id</code> is <code>null</code>
     */
    public JrmpExporter(ActivationID id, int port,
			RMIClientSocketFactory csf,
			RMIServerSocketFactory ssf)
    {
	if (id == null) {
	    throw new NullPointerException();
	}
	this.id = id;
	this.port = port;
	this.csf = csf;
	this.ssf = ssf;
    }
    
    /**
     * Returns the port used by this exporter, or zero if an anonymous port is
     * used.
     * 
     * @return	port number, or zero if anonymous
     */
    public int getPort() {
	return port;
    }
    
    /**
     * Returns the client socket factory for this exporter, or
     * <code>null</code> if none (in which case {@link java.net.Socket} objects
     * are created directly).
     * 
     * @return	client socket factory, or <code>null</code> if none
     */
    public RMIClientSocketFactory getClientSocketFactory() {
	return csf;
    }
    
    /**
     * Returns the server socket factory for this exporter, or
     * <code>null</code> if none (in which case
     * <code>java.net.ServerSocket</code> objects are created directly).
     * 
     * @return	server socket factory, or <code>null</code> if none
     */
    public RMIServerSocketFactory getServerSocketFactory() {
	return ssf;
    }
    
    /**
     * Returns the activation ID associated with the object exported by this
     * exporter, or <code>null</code> if activation is not being used with this
     * exporter.
     * 
     * @return	activation ID, or <code>null</code> if none
     */
    public ActivationID getActivationID() {
	return id;
    }
    
    /**
     * Exports a remote object, <code>impl</code>, to the JRMP runtime and
     * returns a proxy (stub) for the remote object.  This method cannot be
     * called more than once to export a remote object or an
     * <code>IllegalStateException</code> will be thrown.
     * 
     * <p>If this exporter was created with a constructor that accepted a
     * <code>java.rmi.activation.ActivationID</code>, then calling this method
     * is equivalent to invoking
     * <code>java.rmi.activation.Activatable.exportObject</code> with the
     * appropriate impl, <code>ActivationID</code>, port,
     * <code>RMIClientSocketFactory</code>, and
     * <code>RMIServerSocketFactory</code> values.  Otherwise, calling this
     * method is equivalent to invoking
     * <code>java.rmi.server.UnicastRemoteObject.exportObject</code> with the
     * appropriate impl, port, <code>RMIClientSocketFactory</code>, and
     * <code>RMIServerSocketFactory</code> values.
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
	}
	ref = new WeakReference(impl);

	try {
	    Remote proxy = (id != null) ? 
		Activatable.exportObject(impl, id, port, csf, ssf) :
		UnicastRemoteObject.exportObject(impl, port, csf, ssf);
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
     * <code>export</code> method such that the object can no longer
     * accept incoming remote calls that were possible as a result of
     * exporting via this exporter.
     *
     * <p>If <code>force</code> is <code>true</code>, the object is forcibly
     * unexported even if there are pending or in progress calls to the remote
     * object via this exporter.  If <code>force</code> is <code>false</code>,
     * the object is only unexported if there are no pending or in progress
     * calls to the remote object via this exporter.  This method is equivalent
     * to calling <code>java.rmi.activation.Activatable.unexportObject</code>
     * or <code>java.rmi.server.UnicastRemoteObject.unexportObject</code> and
     * passing the "impl" that was previously passed to this exporter's
     * <code>export</code> method, depending on whether or not this exporter
     * was created with a constructor that accepted a
     * <code>java.rmi.activation.ActivationID</code> value.
     * 
     * <p>The return value is <code>true</code> if the object is (or was
     * previously) unexported, and <code>false</code> if the object is still
     * exported.
     *
     * @throws	IllegalStateException {@inheritDoc}
     */
    public synchronized boolean unexport(boolean force) {
	if (ref == null) {
	    throw new IllegalStateException(
		"an object has not been exported via this exporter");
	}
	Remote impl = (Remote) ref.get();
	if (impl == null) {
	    return true;
	}
	try {
	    boolean result = (id != null) ?
		Activatable.unexportObject(impl, force) :
		UnicastRemoteObject.unexportObject(impl, force);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "unexport on {0} returns {1}",
		    new Object[]{ this, Boolean.valueOf(result) });
	    }
	    return result;
	} catch (NoSuchObjectException ex) {
	    return true;
	}
    }
    
    /**
     * Returns the string representation for this exporter.
     * 
     * @return the string representation for this exporter
     */
    public String toString() {
	return (id != null) ?
	    "JrmpExporter[" + id + "," + port + "," + csf + "," + ssf + "]" :
	    "JrmpExporter[" + port + "," + csf + "," + ssf + "]";
    }
}
