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

package net.jini.activation;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import au.net.zeus.rmi.tls.TlsRMIClientSocketFactory;
import net.jini.export.Exporter;
import net.jini.loader.ClassLoading;
import net.jini.security.Security;
import org.apache.river.action.GetBooleanAction;
import org.apache.river.action.GetIntegerAction;
import org.apache.river.action.GetPropertyAction;

/**
 * Subclass of {@link java.rmi.activation.ActivationGroup
 * java.rmi.activation.ActivationGroup} to allow activatable objects that
 * are exported using an {@link Exporter} to go inactive.
 * 
 * <p>This class can be used only productively in conjunction with an
 * activation system daemon implementation that will cause the {@link
 * #createGroup createGroup} method of this class to be called.  The
 * standard <code>rmid</code> does not do this.
 *
 * <!-- Implementation Specifics -->
 *
 * Unlike <code>rmid</code>, <a
 * href="../../../org/apache/river/phoenix/package-summary.html#package_description">phoenix</a>
 * is a configurable Java(TM) Remote Method Invocation (Java RMI)
 * activation system daemon implementation that uses
 * the {@link #createGroup createGroup} method of this class.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
public abstract class ActivationGroup
    extends java.rmi.activation.ActivationGroup
{
    private static final long serialVersionUID = 1011518575632276884L;
    
    /** current activation group for this virtual machine */
    private static ActivationGroup currGroup;

    /**
     * Constructs an activation group with the specified activation group
     * identifier. This constructor exports the group as a
     * {@link java.rmi.server.UnicastRemoteObject}. A subclass constructor
     * can, if desired, unexport the group and then re-export it a different
     * way.
     *
     * @param id the activation group identifier
     * @throws RemoteException if this group could not be exported
     */
    protected ActivationGroup(ActivationGroupID id)
	throws RemoteException 
    {
	super(id);
    }
    
    /**
     * Creates and sets the activation group for the current virtual machine.
     * This method calls
     * {@link java.rmi.activation.ActivationGroup#createGroup
     * java.rmi.activation.ActivationGroup.createGroup} with the same
     * arguments, and returns the result. This method must be called in
     * order to use the {@link #inactive inactive} method of this class.
     * 
     * @param id the activation group identifier
     * @param desc the activation group descriptor
     * @param incarnation the group incarnation number (zero on initial
     * creation)
     * @return the created activation group
     * @throws ActivationException if a group already exists, if the
     * group's class is not a subclass of this class, or if an
     * exception occurs creating the group
     * @throws SecurityException if a security manager exists and invoking
     * its {@link SecurityManager#checkSetFactory checkSetFactory} method
     * throws a <code>SecurityException</code> 
     **/
    public static synchronized
	java.rmi.activation.ActivationGroup createGroup(
				    ActivationGroupID id,
				    final ActivationGroupDesc desc,
				    long incarnation)
        throws ActivationException
    {
	String className = desc.getClassName();
	if (className == null ||
	    !isAssignableFrom(className, desc.getLocation()))
	{
	    throw new ActivationException("group class not subclass");
	}
	currGroup = (ActivationGroup)
	    java.rmi.activation.ActivationGroup.createGroup(id, desc,
							    incarnation);
	return currGroup;
    }

    /**
     * Returns <code>true</code> if the specified class (loaded from the
     * specified location) is a subclass of <code>ActivationGroup</code>
     * and returns <code>false</code> otherwise.
     *
     * @return <code>true</code> if the specified class is a subclass
     * @throws ActivationException if the specified class could not be
     * loaded 
     **/
    private static boolean isAssignableFrom(final String className,
					    final String location)
	throws ActivationException
    {
	try {
	    Class cl = (Class) Security.doPrivileged(
		new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			return ClassLoading.loadClass(location, className, null, false, null);
		    }
	    });
	    return ActivationGroup.class.isAssignableFrom(cl);

	} catch (PrivilegedActionException e) {
	    throw new ActivationException("unable to load group class",
					  e.getException());
	}
    }
	
    /**
     * Attempts to make the remote object that is associated with the
     * specified activation identifier, and that was exported through the
     * specified exporter, inactive. This method calls the
     * {@link #inactiveObject inactiveObject} method of the current group
     * with the same arguments, and returns the result. The overall effect
     * is as follows. The {@link Exporter#unexport unexport} method of the
     * specified exporter is called with <code>false</code> as an argument.
     * If that call returns <code>false</code>, this method returns
     * <code>false</code>. If that call returns <code>true</code>, the
     * object is marked inactive in this virtual machine, the {@link
     * java.rmi.activation.ActivationMonitor#inactiveObject
     * ActivationMonitor.inactiveObject} method of the group's monitor is
     * called to inform the activation system daemon that the object is
     * inactive, and this method returns <code>true</code>.
     *
     * @param id the object's activation identifier
     * @param exporter the exporter to use to unexport the object
     * @return <code>true</code> if the object was successfully made
     * inactive; <code>false</code> otherwise
     * @throws java.rmi.activation.UnknownObjectException if the object is
     * not known to be active (it may already be inactive)
     * @throws ActivationException if the group is not active
     * @throws RemoteException if the remote call to the activation
     * monitor fails
     */
    public static boolean inactive(ActivationID id, Exporter exporter)
	throws ActivationException, RemoteException
    {
	ActivationGroup group;
	synchronized (ActivationGroup.class) {
	    group = currGroup;
	}
	if (group == null) {
	    throw new ActivationException("group is not active");
	}
	return group.inactiveObject(id, exporter);
    }
    
    /**
     * Returns the activation system for the VM. 
     * 
     * The <code>getSystem</code> method attempts to
     * obtain a reference to the <code>ActivationSystem</code> by using
     * an {@link au.net.zeus.rmi.tls.TlsRMIClientSocketFactory} to contact the
     * {@link java.rmi.registry.Registry} using {@link java.rmi.registry.LocateRegistry}
     * looking up the name "java.rmi.activation.ActivationSystem" in
     * the Activator's registry. By default, the port number used to
     * look up the activation system is defined by
     * <code>ActivationSystem.SYSTEM_PORT</code>. This port can be
     * overridden by setting the property
     * <code>java.rmi.activation.port</code>.  The default host name used to
     * lookup the Activator's registry is "localhost" and can be overridden
     * by setting the property <code>java.rmi.server.hostname</code>.
     * 
     * If a Registry is not located, the result of
     * {@link java.rmi.activation.ActivationGroup#getSystem() }
     * is returned.
     * 
     * This method has been provided to support the use of Secure Sockets
     * for the Registry.  Clients should call this from their logged in context.
     * 
     * @return the activation system for the VM/group
     * @exception ActivationException if activation system cannot be
     *  obtained or is not bound
     * (means that it is not running)
     * @exception UnsupportedOperationException if and only if activation is
     * not supported by the jvm and java.rmi.activation.ActivationGroup.getSystem()
     * is called.
     * @since 3.1
     */
    public static ActivationSystem getSystem() throws ActivationException
    {
	int port = AccessController.doPrivileged(
	    new GetIntegerAction(
		    "java.rmi.activation.port",
		    ActivationSystem.SYSTEM_PORT
	    )
	);
	String host = AccessController.doPrivileged(
	    new GetPropertyAction(
		    "java.rmi.server.hostname",
		    "" //localhost
	    )
	);
	Registry registry;
	try {
	    registry = LocateRegistry.getRegistry(
		    host, port, new TlsRMIClientSocketFactory()
	    );
	} catch (RemoteException ex) {
	    throw new ActivationException("Unable to obtain Registry stub", ex);
	}
	if (registry == null) throw new ActivationException("Registry not runing");
	try {
	    return (ActivationSystem) 
		    registry.lookup("java.rmi.activation.ActivationSystem");
	} catch (RemoteException ex) {
	    boolean insecure = false;
	    try {
		insecure = AccessController.doPrivileged(
		    new GetBooleanAction("net.jini.security.allowInsecureConnections"));
	    } catch (SecurityException e){} // Ignore
	    if (insecure){
		return java.rmi.activation.ActivationGroup.getSystem();
	    } else {
		throw new ActivationException("Unable to establish connection with secure Registry", ex);
	    }
	} catch (NotBoundException ex) {
	    throw new ActivationException("ActivationSystem not bound",ex);
	}
    }

    /**
     * Attempts to make the remote object that is associated with the
     * specified activation identifier, and that was exported through the
     * specified exporter, inactive. The {@link Exporter#unexport unexport}
     * method of the specified exporter is called with <code>false</code>
     * as an argument. If that call returns <code>false</code>, this method
     * returns <code>false</code>. If that call returns <code>true</code>,
     * the object is marked inactive in this virtual machine, the {@link
     * java.rmi.activation.ActivationMonitor#inactiveObject
     * ActivationMonitor.inactiveObject} method of the group's monitor is
     * called to inform the activation system daemon that the object is
     * inactive, and this method returns <code>true</code>.
     *
     * @param id the object's activation identifier
     * @param exporter the exporter to use to unexport the object
     * @return <code>true</code> if the object was successfully made
     * inactive; <code>false</code> otherwise
     * @throws java.rmi.activation.UnknownObjectException if the object is
     * not known to be active (it may already be inactive)
     * @throws ActivationException if the group is not active
     * @throws RemoteException if the remote call to the activation
     * monitor fails
     */
    public abstract boolean inactiveObject(ActivationID id, Exporter exporter)
	throws ActivationException, RemoteException;
}
