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

import net.jini.activation.arg.UnknownGroupException;
import net.jini.activation.arg.ActivationMonitor;
import net.jini.activation.arg.ActivationInstantiator;
import net.jini.activation.arg.ActivationGroupDesc;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationSystem;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import au.net.zeus.rmi.tls.TlsRMIClientSocketFactory;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.rmi.Remote;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.arg.MarshalledObject;
import net.jini.activation.arg.UnknownObjectException;
import net.jini.export.Exporter;
import net.jini.io.MarshalledInstance;
import net.jini.loader.ClassLoading;
import net.jini.security.Security;
import org.apache.river.action.GetBooleanAction;
import org.apache.river.action.GetIntegerAction;
import org.apache.river.action.GetPropertyAction;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;
import org.apache.river.impl.Messages;

/**
 * Allows activatable objects that
 * are exported using an {@link Exporter} to go inactive.
 * 
 * <p>This class can be used only productively in conjunction with an
 * activation system daemon implementation that will cause the {@link
 * #createGroup createGroup} method of this class to be called.
 *
 * <!-- Implementation Specifics -->
 *
 * Unlike <code>rmid</code>, <a
 * href="../../../org/apache/river/phoenix/package-summary.html#package_description">phoenix</a>
 * is a configurable
 * activation system daemon implementation that uses
 * the {@link #createGroup createGroup} method of this class.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
@AtomicSerial
public abstract class ActivationGroup implements ActivationInstantiator, Serializable
{
    // REMIND: Do we need to implement Serializable?
    private static final long serialVersionUID = 1011518575632276884L;
    private static final Logger LOGGER = Logger.getLogger("net.jini.activation.ActivationGroup");
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm("groupID", ActivationGroupID.class),
            new SerialForm("monitor", ActivationMonitor.class),
            new SerialForm("incarnation", Long.TYPE)
        };
    }
    
    public static void serialize(PutArg arg, ActivationGroup group) throws IOException{
        arg.put("groupID", group.groupID);
        arg.put("monitor", group.monitor);
        arg.put("incarnation", group.incarnation);
        arg.writeArgs();
    }
    
    /**
     * The ActivationSystem for this VM.
     */
    private static ActivationSystem current_AS;

    /**
     * Current ActivationGroupID
     */
    private static ActivationGroupID current_AGID;

    /**
     * Current ActivationGroup for this virtual machine.
     */
    private static ActivationGroup current_AG;

    /**
     * Creates and sets the activation group for the current virtual machine.
     * This method must be called in
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
    public static synchronized ActivationGroup createGroup(ActivationGroupID id,
            ActivationGroupDesc desc, long incarnation) throws ActivationException {
        if (desc == null) throw new NullPointerException("ActivationGroupDesc cannot be null");
        String className = desc.getClassName();
	if (className == null ||
	    !isAssignableFrom(className, desc.getLocation()))
	{
	    throw new ActivationException("group class not subclass");
	}
        // rmi.log.17=ActivationGroup.createGroup [id={0}, desc={1},
        // incarnation={2}
        LOGGER.log(Level.FINER, Messages.getString("rmi.log.17", //$NON-NLS-1$ 
                new Object[] { id, desc, incarnation }));
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkSetFactory();
        }
        /*
         * Classname of the ActivationGroup implementation. If the group class
         * name was given in 'desc' assign it to group_CN, use default
         * otherwise.
         */
        String group_CN =  desc.getClassName();
        // rmi.log.18=group_CN = {0}
        LOGGER.log(Level.FINER, Messages.getString("rmi.log.18", group_CN)); //$NON-NLS-1$
        if (current_AG != null) {
            // rmi.11=The ActivationGroup for this VM already exists.
            throw new ActivationException(Messages.getString("rmi.11")); //$NON-NLS-1$
        }
        try {
            // rmi.log.19=Ready to load ActivationGroupImpl class
            LOGGER.log(Level.FINER, Messages.getString("rmi.log.19")); //$NON-NLS-1$
            Class<?> cl = ClassLoading.loadClass(desc.getLocation(), group_CN, null, false, null);
            // rmi.log.1A=ag class = {0}
            LOGGER.log(Level.FINER, Messages.getString("rmi.log.1A", cl)); //$NON-NLS-1$
            Class[] special_constructor_parameter_classes = { ActivationGroupID.class,
                    MarshalledObject.class };
            
            Constructor<?> constructor = cl.getConstructor(special_constructor_parameter_classes);
            Object[] constructor_parameters = { id, desc.getData() };
            ActivationGroup ag = (ActivationGroup) constructor
                    .newInstance(constructor_parameters);
            // rmi.log.1B=ag = {0}
            LOGGER.log(Level.FINER, Messages.getString("rmi.log.1B", ag)); //$NON-NLS-1$
            current_AS = id.getSystem();
            // rmi.log.1C=current_AS = {0}
            LOGGER.log(Level.FINER, Messages.getString("rmi.log.1C", current_AS)); //$NON-NLS-1$
            ag.incarnation = incarnation;
            // rmi.log.1D=ag.incarnation = {0}
            LOGGER.log(Level.FINER,  Messages.getString("rmi.log.1D", ag.incarnation)); //$NON-NLS-1$
            ag.monitor = current_AS.activeGroup(id, ag, incarnation);
            // rmi.log.1E=ag.monitor = {0}
            LOGGER.log(Level.FINER, Messages.getString("rmi.log.1E", ag.monitor)); //$NON-NLS-1$
            current_AG = ag;
            current_AGID = id;
        } catch (Throwable t) {
            // rmi.log.1F=Exception in createGroup: {0}
            LOGGER.log(Level.FINER, Messages.getString("rmi.log.1F", t)); //$NON-NLS-1$
            // rmi.12=Unable to create group.
            throw new ActivationException(Messages.getString("rmi.12"), t); //$NON-NLS-1$
        }
        // rmi.log.20=Group created: {0}
        LOGGER.log(Level.FINER, Messages.getString("rmi.log.20", current_AG)); //$NON-NLS-1$
        return current_AG;
    }

    /**
     * Returns the current activation group's identifier. 
     * Returns null if no group is currently active for this VM.
     * @return the current activation group's identifier. 
     */
    public static synchronized ActivationGroupID currentGroupID() {
        return current_AGID;
    }

    /**
     * Set the activation system for the VM. The activation system can only
     * be set it if no group is currently active. If the activation system
     * is not set via this call, then the getSystem method attempts to
     * obtain a reference to the ActivationSystem by looking up the name 
     * "net.jini.activation.arg.ActivationSystem" in the Activator's registry.
     * By default, the port number used to look up the activation system
     * is defined by ActivationSystem.SYSTEM_PORT. This port can be 
     * overridden by setting the property net.jini.activation.port. 
     * 
     * @param system
     * @throws ActivationException 
     */
    public static synchronized void setSystem(ActivationSystem system)
            throws ActivationException {
        if (current_AS != null) {
            // rmi.14=The ActivationSystem for this ActivationGroup was already
            // defined.
            throw new ActivationException(Messages.getString("rmi.14")); //$NON-NLS-1$
        }
        current_AS = system;
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
                    @Override
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
     * net.jini.activation.arg.ActivationMonitor#inactiveObject
     * ActivationMonitor.inactiveObject} method of the group's monitor is
     * called to inform the activation system daemon that the object is
     * inactive, and this method returns <code>true</code>.
     *
     * @param id the object's activation identifier
     * @param exporter the exporter to use to unexport the object
     * @return <code>true</code> if the object was successfully made
     * inactive; <code>false</code> otherwise
     * @throws net.jini.activation.arg.UnknownObjectException if the object is
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
	    group = current_AG;
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
     * looking up the name "net.jini.activation.arg.ActivationSystem" in
     * the Activator's registry. By default, the port number used to
     * look up the activation system is defined by
     * <code>ActivationSystem.SYSTEM_PORT</code>. This port can be
     * overridden by setting the property
     * <code>net.jini.activation.port</code>.  The default host name used to
     * lookup the Activator's registry is "localhost" and can be overridden
     * by setting the property <code>java.rmi.server.hostname</code>.
     * 
     * This method has been provided to support the use of Secure Sockets
     * for the Registry.  Clients should call this from their logged in context.
     * 
     * @return the activation system for the VM/group
     * @exception ActivationException if activation system cannot be
     *  obtained or is not bound
     * (means that it is not running)
     * @exception UnsupportedOperationException if and only if activation is
     * not supported by the jvm.
     * @since 3.1
     */
    public static ActivationSystem getSystem() throws ActivationException
    {
	int port = AccessController.doPrivileged(
	    new GetIntegerAction(
		    "net.jini.activation.port",
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
		    registry.lookup("net.jini.activation.arg.ActivationSystem");
	} catch (RemoteException ex) {
	    boolean insecure = false;
	    try {
		insecure = AccessController.doPrivileged(
		    new GetBooleanAction("net.jini.security.allowInsecureConnections"));
	    } catch (SecurityException e){
                ex.addSuppressed(e);
            }
	    if (insecure){
                try {
                    registry = LocateRegistry.getRegistry(host, port);
                    if (registry == null) throw new ActivationException("Registry not runing");
                    return (ActivationSystem) 
                            registry.lookup("net.jini.activation.arg.ActivationSystem");
                } catch (RemoteException e) {
                    e.addSuppressed(ex);
                    throw new ActivationException("ActivationSystem not bound",e);
                } catch (NotBoundException ex1) {
                    ex1.addSuppressed(ex);
                    throw new ActivationException("ActivationSystem not bound",ex1);
                }
	    } else {
		throw new ActivationException("Unable to establish connection with secure Registry", ex);
	    }
	} catch (NotBoundException ex) {
	    throw new ActivationException("ActivationSystem not bound",ex);
	}
    }
    
    

    private final ActivationGroupID groupID;

    private ActivationMonitor monitor;

    private long incarnation;
    
    private ActivationGroup(ActivationGroupID groupID,
            ActivationMonitor monitor,
            long incarnation)
    {
        this.groupID = groupID;
        this.monitor = monitor;
        this.incarnation = incarnation;
    }
    
    protected ActivationGroup(GetArg arg) throws IOException, ClassNotFoundException {
        this(Valid.notNull(arg.get("groupID", null, ActivationGroupID.class), "groupID cannot be null"),
             Valid.notNull(arg.get("monitor", null, ActivationMonitor.class), "monitor cannot be null"),
             arg.get("incarnation", 0L)
                );
    }

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
    protected ActivationGroup(ActivationGroupID id) throws RemoteException {
        /**
         * We need to export this group, so we call the constructor of the
         * superclass.
         */
        this.groupID = id;
    }

    protected void activeObject(ActivationID id, MarshalledInstance mobj)
            throws ActivationException, UnknownObjectException, RemoteException {        
        // rmi.log.14=ActivationGroup.activeObject: {0}; {1}
        LOGGER.log(Level.FINER, Messages.getString("rmi.log.14", id, mobj)); //$NON-NLS-1$
        // rmi.log.15=monitor: {0}
        LOGGER.log(Level.FINER, Messages.getString("rmi.log.15", monitor)); //$NON-NLS-1$
        monitor.activeObject(id, mobj);
        // rmi.log.16=ActivationGroup.activeObject finished.
        LOGGER.log(Level.FINER, Messages.getString("rmi.log.16")); //$NON-NLS-1$
    }

    public abstract void activeObject(ActivationID id, Remote obj)
            throws ActivationException, UnknownObjectException, RemoteException;

    protected void inactiveGroup() throws UnknownGroupException,
            RemoteException {
        monitor.inactiveGroup(groupID, incarnation);
    }

    /**
     * Attempts to make the remote object that is associated with the
     * specified activation identifier, and that was exported through the
     * specified exporter, inactive. The {@link Exporter#unexport unexport}
     * method of the specified exporter is called with <code>false</code>
     * as an argument. If that call returns <code>false</code>, this method
     * returns <code>false</code>. If that call returns <code>true</code>,
     * the object is marked inactive in this virtual machine, the {@link
     * net.jini.activation.arg.ActivationMonitor#inactiveObject
     * ActivationMonitor.inactiveObject} method of the group's monitor is
     * called to inform the activation system daemon that the object is
     * inactive, and this method returns <code>true</code>.
     *
     * @param id the object's activation identifier
     * @param exporter the exporter to use to unexport the object
     * @return <code>true</code> if the object was successfully made
     * inactive; <code>false</code> otherwise
     * @throws net.jini.activation.arg.UnknownObjectException if the object is
     * not known to be active (it may already be inactive)
     * @throws ActivationException if the group is not active
     * @throws RemoteException if the remote call to the activation
     * monitor fails
     */
    public abstract boolean inactiveObject(ActivationID id, Exporter exporter)
	throws ActivationException, RemoteException;
}
