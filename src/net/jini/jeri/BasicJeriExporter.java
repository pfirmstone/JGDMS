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

package net.jini.jeri;

import org.apache.river.jeri.internal.runtime.BasicExportTable;
import org.apache.river.logging.Levels;
import java.lang.ref.WeakReference;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.rmi.server.Unreferenced;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.export.Exporter;
import net.jini.export.ServerContext;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalInputStream;
import net.jini.io.context.ClientHost;
import net.jini.io.context.ClientSubject;
import net.jini.security.Security;
import net.jini.security.SecurityContext;

/**
 * An <code>Exporter</code> implementation for exporting
 * a remote object to use Jini extensible remote invocation
 * (Jini ERI).  Typically, instances of this class should be
 * obtained from a {@link Configuration} rather than being explicitly
 * constructed.
 * Each instance of <code>BasicJeriExporter</code>
 * can export only a single remote object.
 *
 * <p>The following properties (defined during construction) govern
 * invocation behavior and other characteristics of the exported remote
 * object and its proxy:
 * <ul>
 * <li>{@link ServerEndpoint}: the <code>ServerEndpoint</code> over
 * which calls are accepted.
 *
 * <li><p>{@link InvocationLayerFactory}: a factory used to obtain the remote
 * object's proxy and invocation dispatcher.
 *
 * <li><p><i>enableDGC</i> flag: if <code>true</code>, distributed
 * garbage collection (DGC) is enabled for the exported remote object,
 * and the {@link BasicObjectEndpoint} produced by this exporter
 * participates in DGC and thus constitutes a strong remote reference
 * to the remote object; if <code>false</code>, DGC is not enabled for
 * the remote object, and the <code>BasicObjectEndpoint</code> does
 * not participate in DGC and thus is a weak remote reference, so it
 * does not prevent the remote object from being garbage collected.
 *
 * <li><p><i>keepAlive</i> flag: if <code>true</code>, the virtual
 * machine is kept alive (with a non-daemon thread) while the remote
 * object remains exported via this exporter.
 *
 * <li><p>{@link Uuid Uuid}: the object identifier to use for the
 * remote object; if <code>null</code>, a unique object identifier is
 * chosen for the remote object using {@link UuidFactory#generate
 * UuidFactory.generate}.
 * </ul>
 *
 * <p>If DGC is not enabled for a remote object, then the
 * implementation always only weakly references the remote object.  If
 * DGC is enabled for a remote object, then the implementation weakly
 * references the remote object when its referenced set is empty and
 * strongly references the remote object when its referenced set is
 * not empty (see below).  If the implementation weakly references the
 * remote object and the weak reference is cleared, the remote object
 * becomes effectively unexported.
 *
 * <p>Enabling DGC is not advisable in some circumstances.  DGC should
 * not be enabled for a remote object exported with a well known
 * object identifier.  Enabling DGC with a secure remote object is
 * generally discouraged, because DGC communication is always made in
 * a client-side context in which there are no client constraints and
 * no client subject, so it can leave the remote object open to denial
 * of service attacks.  Some transport providers may not support making
 * requests without a client subject, so even if DGC is enabled in the
 * case where such a transport provider is used, DGC will be effectively
 * disabled on the client side.
 *
 * <p>Multiple remote objects can be exported on the same server endpoint,
 * and the same remote object can be exported multiple times on different
 * server endpoints with the only restriction being that a given pair of
 * object identifier and listen endpoint (derived from the server endpoint)
 * can only have one active export at any given time.
 * 
 * <p>Two instances of this class are equal only if they are references to
 * the same (<code>==</code>) object.
 * 
 * <p>The server endpoint is not transmitted in the remote reference; only the
 * derived client endpoint is transmitted.
 *
 * <p>Remote objects exported with instances of this class can call {@link
 * ServerContext#getServerContextElement
 * ServerContext.getServerContextElement}, passing the class {@link
 * ClientSubject} to obtain the authenticated identity of the client (if
 * any) for an incoming remote call, or passing the class {@link
 * ClientHost} to obtain the address of the client host.
 * 
 * <p>For remote objects exported with instances of this class, there is no
 * automatic replacement of the proxy for the remote object during
 * marshalling; either the proxy must be passed explicitly, or the remote
 * object implementation class must be serializable and have a
 * <code>writeReplace</code> method that returns its proxy.
 *
 * <h4>Distributed Garbage Collection</h4>
 *
 * The <code>BasicJeriExporter</code> class acts as the server-side
 * DGC implementation for all remote objects exported with DGC enabled
 * using its instances.
 *
 * <p>An entity known as the <i>DGC client</i> tracks the existence
 * and reachability of live remote references
 * (<code>BasicObjectEndpoint</code> instances that participate in
 * DGC) for a <code>BasicObjectEndpoint</code> class in some
 * (potentially) remote virtual machine.  A DGC client is identified
 * by a universally unique identifier (a <code>Uuid</code>).  A DGC
 * client sends <i>dirty calls</i> and <i>clean calls</i> to the
 * {@link Endpoint} of a live remote reference to inform the
 * server-side DGC implementation when the number of live remote
 * references to a given remote object it is tracking increases from
 * zero to greater than zero and decreases from greater than zero to
 * zero, respectively.  A DGC client also sends dirty calls to the
 * <code>Endpoint</code> of live remote references it is tracking to
 * renew its lease.  The client-side behavior of dirty and clean calls
 * is specified by {@link BasicObjectEndpoint}.
 *
 * <p>On the server side, for every remote object exported with DGC
 * enabled, the implementation maintains a <i>referenced set</i>,
 * which contains the <code>Uuid</code>s of the DGC clients that are
 * known to have live remote references to the remote object.  The
 * contents of the referenced set are modified as a result of dirty
 * calls, clean calls, and expiration of leases (see below).  While
 * the referenced set is not empty, the implementation strongly
 * references the remote object, so that it will not be garbage
 * collected.  While the referenced set is empty, the implementation
 * only weakly references the remote object, so that it may be garbage
 * collected (if it is not otherwise strongly reachable locally).  If
 * a remote object is garbage collected, it becomes effectively
 * unexported.  If a remote object that is an instance of {@link
 * Unreferenced} is exported with DGC enabled, then whenever the size
 * of its referenced set transitions from greater than zero to zero,
 * its {@link Unreferenced#unreferenced unreferenced} method will be
 * invoked (before the strong reference is dropped).  Note that a
 * referenced set spans multiple exports of the same (identical)
 * remote object with <code>BasicJeriExporter</code>.
 *
 * <p>For every <code>RequestDispatcher</code> passed by
 * <code>BasicJeriExporter</code> to a <code>ListenEndpoint</code> as
 * part of exporting, whenever it has at least one remote object
 * exported with DGC enabled, it also has an implicitly exported
 * remote object that represents the server-side DGC implementation.
 * This remote object is effectively exported with an object
 * identifier of <code>d32cd1bc-273c-11b2-8841-080020c9e4a1</code> and
 * an <code>InvocationDispatcher</code> that behaves like a {@link
 * BasicInvocationDispatcher} with no server constraints, with a
 * {@link BasicInvocationDispatcher#createMarshalInputStream
 * createMarshalInputStream} implementation that returns a {@link
 * MarshalInputStream} that ignores codebase annotations, and with
 * support for at least the following remote methods:
 *
 * <pre>
 *     long dirty(Uuid clientID, long sequenceNum, Uuid[] ids)
 *         throws {@link RemoteException};
 *
 *     void clean(Uuid clientID, long sequenceNum, Uuid[] ids, boolean strong)
 *         throws RemoteException;
 * </pre>
 *
 * <code>clientID</code> identifies the DGC client that is making the
 * dirty or clean call, and <code>sequenceNum</code> identifies the
 * sequence number of the dirty or clean call with respect to other
 * dirty and clean calls from the same DGC client.  The sequence
 * numbers identify the correct order of semantic interpretation of
 * dirty and clean calls from the same DGC client, regardless of the
 * order in which they arrive.  The well-known object identifier for
 * the server-side DGC implementation is reserved; attempting to
 * export any other remote object with that object identifier always
 * throws an {@link ExportException}.
 *
 * <p>A dirty call is processed as follows:
 *
 * <ul>
 *
 * <li>It establishes or renews the DGC lease for the identified DGC
 * client.  The duration of the granted lease, which is chosen by the
 * implementation, is conveyed as the value returned by the dirty
 * call, in milliseconds starting from the some time during the
 * processing of the dirty call.  While the lease for a DGC client is
 * valid (not expired), the DGC client is preserved in referenced sets
 * of exported remote objects.
 *
 * <li><p>It adds the DGC client's <code>Uuid</code> to the referenced
 * sets of the exported remote objects identified by <code>ids</code>,
 * if any, so that they are prevented from being garbage collected.
 * For each <code>Uuid</code> in <code>ids</code>:
 *
 * <blockquote>
 *
 * The identified remote object is the remote object exported with
 * that <code>Uuid</code> on the <code>ListenEndpoint</code> (and thus
 * <code>RequestDispatcher</code>) that the dirty call was received
 * on.  If no such exported remote object exists (for example, if it
 * has been garbage collected), then that <code>Uuid</code> in
 * <code>ids</code> is ignored.  If the sequence number is less than
 * the last recorded sequence number of a dirty or clean call for the
 * identified remote object from the same DGC client, then the remote
 * object's referenced set is not modified.  Otherwise, the DGC
 * client's <code>Uuid</code> is added to the remote object's
 * referenced set (if not already present).  If this addition causes
 * the referenced set to transition from empty to non-empty, then the
 * implementation starts strongly referencing the remote object.
 *
 * </blockquote>
 *
 * </ul>
 *
 * <p>A clean call is processed as follows:
 *
 * <ul>
 *
 * <li>It removes the DGC client's <code>Uuid</code> from the
 * referenced sets of the exported remote objects identified by
 * <code>ids</code>, so that they are not prevented from being garbage
 * collected by the given DGC client.  For each <code>Uuid</code> in
 * <code>ids</code>:
 *
 * <blockquote>
 *
 * <p>The identified remote object is the remote object exported with
 * that <code>Uuid</code> on the <code>ListenEndpoint</code> (and thus
 * <code>RequestDispatcher</code>) that the dirty call was received
 * on.  If no such exported remote object exists (for example, if it
 * has been garbage collected), then that <code>Uuid</code> in
 * <code>ids</code> is ignored.  If the sequence number is less then
 * the last recorded sequence number of a dirty or clean call for the
 * identified remote object from the same DGC client, then the remote
 * object's referenced set is not modified.  Otherwise, the DGC
 * client's <code>Uuid</code> is removed from the remote object's
 * referenced set (if it is present).  If this removal causes the
 * referenced set to transition from non-empty to empty, then the
 * implementation starts only weakly referencing the remote object
 * (and before doing so, if the remote object is an instance of
 * <code>Unreferenced</code>, its <code>unreferenced</code> method is
 * invoked).  If <code>strong</code> is <code>true</code>, then a
 * record is kept of the specified sequence number from the DGC client
 * for some reasonable period of time, in the event of a dirty call
 * that might arrive later with a lower sequence number.
 *
 * </blockquote>
 *
 * </ul>
 *
 * <p>If the implementation detects that the most recently granted DGC
 * lease for a given DGC client has expired, then it assumes that the
 * DGC client has abnormally terminated, and the DGC client's
 * <code>Uuid</code> is removed from the referenced sets of all
 * exported remote objects.  If this removal causes a referenced set
 * to transition from non-empty to empty, then the implementation
 * starts only weakly referencing the corresponding remote object (and
 * before doing so, if the remote object is an instance of
 * <code>Unreferenced</code>, its <code>unreferenced</code> method is
 * invoked).
 *
 * <p>Unexporting a remote object with a
 * <code>BasicJeriExporter</code> causes the removal of DGC client
 * <code>Uuid</code>s from the remote object's referenced set that
 * were only present because of dirty calls that were received as a
 * result of exporting it with that <code>BasicJeriExporter</code>.
 * If the remote object remains exported with DGC enabled by another
 * <code>BasicJeriExporter</code> and this removal causes the
 * referenced set to transition from non-empty to empty, then the
 * implementation starts only weakly referencing the remote object
 * (and before doing so, if the remote object is an instance of
 * <code>Unreferenced</code>, its <code>unreferenced</code> method is
 * invoked).
 *
 * <p>When the implementation invokes a remote object's
 * <code>unreferenced</code> method, it does so with the security
 * context and context class loader in effect when the remote object
 * was exported.  If the remote object is currently exported more than
 * once, then the security context and context class loader in effect
 * for any one of those exports are used.
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.BasicJeriExporter</code> to log
 * information at the following levels:
 *
 * <table summary="Describes what is logged by BasicJeriExporter at various
 *        logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> incoming request for
 * unrecognized object identifier (no such object)
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> I/O exception
 * dispatching incoming request
 *
 * <tr> <td> {@link Level#FINE FINE} <td> successful export of object
 * 
 * <tr> <td> {@link Level#FINE FINE} <td> attempted unexport of object
 *
 * <tr> <td> {@link Level#FINE FINE} <td> garbage collection of
 * exported object
 *
 * <tr> <td> {@link Level#FINE FINEST} <td> detailed implementation
 * activity
 *
 * </table>
 **/
public final class BasicJeriExporter implements Exporter {
    
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicJeriExporter");

    private final ServerEndpoint se;
    private final InvocationLayerFactory ilf;
    private final boolean enableDGC;
    private final boolean keepAlive;
    private final Uuid id;

    private boolean used = false;
    private BasicExportTable.Entry entry;
    private WeakReference weakImplContainer = null;

    private static final BasicExportTable table = new BasicExportTable();

    /**
     * Creates a new <code>BasicJeriExporter</code> with the given server
     * endpoint and invocation layer factory.  The other properties of the
     * exporter default as follows: the <code>enableDGC</code> flag is
     * <code>false</code>, the <code>keepAlive</code> flag is
     * <code>true</code>, and the object identifier is chosen by invoking
     * {@link UuidFactory#generate UuidFactory.generate}.
     *
     * @param	se the server endpoint over which calls may be accepted
     * @param	ilf the factory for creating the remote object's
     *		proxy and invocation dispatcher
     * @throws	NullPointerException if <code>se</code> or <code>ilf</code>
     *		is <code>null</code>
     **/
    public BasicJeriExporter(ServerEndpoint se, InvocationLayerFactory ilf) {
	this(se, ilf, false, true);
    }
    
    /**
     * Creates a new <code>BasicJeriExporter</code> with the given server
     * endpoint, invocation layer factory, <code>enableDGC</code> flag, and
     * <code>keepAlive</code> flag.  The object identifier is chosen by
     * invoking {@link UuidFactory#generate UuidFactory.generate}.
     *
     * @param	se the server endpoint over which calls may be accepted
     * @param	ilf the factory for creating the remote object's
     *		proxy and invocation dispatcher
     * @param	enableDGC if <code>true</code>, DGC is enabled to the object
     *		on this server endpoint
     * @param	keepAlive if <code>true</code>, the VM is kept alive
     *		while the object (exported via this exporter) remains
     *		exported
     * @throws	NullPointerException if <code>se</code> or <code>ilf</code>
     *		is <code>null</code>
     **/
    public BasicJeriExporter(ServerEndpoint se,
			     InvocationLayerFactory ilf,
			     boolean enableDGC,
			     boolean keepAlive)
    {
	this(se, ilf, enableDGC, keepAlive, null);
    }

    /**
     * Creates a new <code>BasicJeriExporter</code> with the given server
     * endpoint, invocation layer factory, <code>enableDGC</code> flag,
     * <code>keepAlive</code> flag, and object identifier.  If
     * <code>id</code> is <code>null</code>, the object identifier is
     * chosen by invoking {@link UuidFactory#generate
     * UuidFactory.generate}.
     *
     * @param	se the server endpoint over which calls may be accepted
     * @param	ilf the factory for creating the remote object's proxy
     *		and invocation dispatcher
     * @param	enableDGC if <code>true</code>, DGC is enabled to the object
     *		on this server endpoint
     * @param	keepAlive if <code>true</code>, the VM is kept alive
     *		while the object (exported via this exporter) remains
     *		exported
     * @param	id an object identifier or <code>null</code>
     * @throws	NullPointerException if <code>se</code> or <code>ilf</code>
     *		is <code>null</code>
     **/
    public BasicJeriExporter(ServerEndpoint se,
			     InvocationLayerFactory ilf,
			     boolean enableDGC,
			     boolean keepAlive,
			     Uuid id)
    {
	if (se == null || ilf == null) {
	    throw new NullPointerException();
	}
	this.se = se;
	this.ilf = ilf;
	this.id = ((id == null) ? UuidFactory.generate() : id);
	this.enableDGC = enableDGC;
	this.keepAlive = keepAlive;
    }

    /**
     * Returns the server endpoint for this exporter.
     *
     * @return the server endpoint
     **/
    public ServerEndpoint getServerEndpoint() {
	return se;
    }

    /**
     * Returns the <code>InvocationLayerFactory</code> for this
     * exporter.
     *
     * @return the factory
     **/
    public InvocationLayerFactory getInvocationLayerFactory() {
	return ilf;
    }

    /**
     * Returns <code>true</code> if DGC is enabled on the server endpoint to
     * the object corresponding to this exporter; otherwise
     * returns <code>false</code>.
     *
     * @return  <code>true</code> if DGC is enabled;
     *		<code>false</code> otherwise
     **/
    public boolean getEnableDGC() {
	return enableDGC;
    }

    /**
     * Returns <code>true</code> if the virtual machine is kept alive while
     * the object corresponding to this exporter is exported; otherwise
     * returns <code>false</code>.
     *
     * @return  <code>true</code> if VM is kept alive while object is
     *          exported; <code>false</code> otherwise
     **/
    public boolean getKeepAlive() {
	return keepAlive;
    }
    
    /**
     * Returns the object identifier for this exporter.
     *
     * @return the object identifier
     **/
    public Uuid getObjectIdentifier() {
	return id;
    }
    
    /**
     * Exports the specified remote object and returns a proxy for the
     * remote object.  This method cannot be called more than once to
     * export a remote object or an <code>IllegalStateException</code> will
     * be thrown.
     *
     * <p>A {@link BasicObjectEndpoint} instance is created with the object
     * identifier of this exporter, the {@link Endpoint} obtained from
     * listening on the server endpoint (see below), and the
     * <code>enableDGC</code> flag of this exporter.
     *
     * <p>The client <code>Endpoint</code> for the
     * <code>BasicObjectEndpoint</code> is obtained by invoking {@link
     * ServerEndpoint#enumerateListenEndpoints enumerateListenEndpoints} on
     * the server endpoint with a {@link ServerEndpoint.ListenContext}
     * whose {@link ServerEndpoint.ListenContext#addListenEndpoint
     * addListenEndpoint} method is implemented as follows: <ul>
     *
     * <li>Invokes {@link ServerEndpoint.ListenEndpoint#checkPermissions
     * checkPermissions} on the supplied listen endpoint.
     *
     * <li>If the supplied listen endpoint has the same class and is equal
     * to another listen endpoint that has already been listened on,
     * returns the {@link ServerEndpoint.ListenCookie} corresponding to the
     * previous <code>listen</code> operation.  Otherwise, it creates a
     * {@link RequestDispatcher} to handle inbound requests dispatched by
     * the listen endpoint, invokes {@link
     * ServerEndpoint.ListenEndpoint#listen listen} on the listen endpoint
     * (passing the request dispatcher) within an action passed to the
     * {@link Security#doPrivileged Security.doPrivileged} method, and
     * returns the <code>ServerEndpoint.ListenCookie</code> obtained by
     * invoking {@link ServerEndpoint.ListenHandle#getCookie getCookie} on
     * the {@link ServerEndpoint.ListenHandle} returned from the
     * <code>listen</code> invocation.
     * </ul>
     *
     * <p>A <code>RequestDispatcher</code> for a listen endpoint handles a
     * dispatched inbound request (when its {@link
     * RequestDispatcher#dispatch dispatch} method is invoked) as follows.
     * The request dispatcher reads the object identifer of the target
     * object being invoked by invoking {@link UuidFactory#read
     * UuidFactory.read} on the request input stream of the inbound
     * request.  If no exported object corresponds to the object identifier
     * read, it closes the request input stream, writes <code>0x00</code>
     * to the response output stream, and closes the response output
     * stream.  Otherwise, it writes <code>0x01</code> to the response
     * output stream, and invokes the {@link InvocationDispatcher#dispatch
     * dispatch} method on the invocation dispatcher passing the target
     * object, the inbound request, and the server context collection (see
     * below).
     *
     * <p>A proxy and an invocation dispatcher are created by
     * calling the {@link InvocationLayerFactory#createInstances
     * createInstances} method of this exporter's invocation layer factory,
     * passing the remote object, the <code>BasicObjectEndpoint</code>, and
     * the server endpoint (as the {@link ServerCapabilities}). The proxy
     * is returned by this method. The invocation dispatcher is called for
     * each incoming remote call to this exporter's object identifier
     * received from this exporter's server endpoint, passing the remote
     * object and the {@link InboundRequest} received from the transport
     * layer.
     *
     * <p>Each call to the invocation dispatcher's {@link
     * InvocationDispatcher#dispatch dispatch} method is invoked with
     * the following thread context:
     * <ul>
     * <li><code>dispatch</code> is invoked in a {@link
     *     PrivilegedAction} wrapped by a {@link SecurityContext}
     *     obtained when this method was invoked, with the {@link
     *     AccessControlContext} of that <code>SecurityContext</code>
     *     in effect.
     * <li>The context class loader is the context class loader
     *     in effect when this method was invoked.
     * <li>Each call to the dispatcher is made using {@link
     *     ServerContext#doWithServerContext
     *     ServerContext.doWithServerContext} with a server context
     *     collection that is an unmodifiable view of the context
     *     collection populated by invoking the {@link
     *     InboundRequest#populateContext populateContext} method on the
     *     inbound request passing a modifiable collection.  The invocation
     *     dispatcher's {@link InvocationDispatcher#dispatch dispatch}
     *     method is invoked with the <code>impl</code>, the inbound
     *     request, and that modifiable server context collection.
     * </ul>
     *
     * <p>There is no replacement of the proxy for the implementation
     * object during marshalling; either the proxy must be passed
     * explicitly in a remote call, or the implementation class must be
     * serializable and have a <code>writeReplace</code> method that
     * returns the proxy.
     *
     * @throws	ExportException if an object is already exported
     *		with the same object identifier and server endpoint, or
     *		the invocation layer factory cannot create a proxy or
     *		invocation dispatcher, or some other problem occurs while
     *		exporting the object 
     * @throws	NullPointerException {@inheritDoc}
     * @throws	IllegalStateException {@inheritDoc}
     * @throws	SecurityException if invoking the
     *		<code>checkPermissions</code> method on any of the listen
     *		endpoints throws a <code>SecurityException</code>
     **/
    public synchronized Remote export(Remote impl)
	throws ExportException
    {
	/*
	 * Check if object is already exported; disallow exporting more
	 * than once via this exporter.
	 */
	if (used) {
	    throw new IllegalStateException(
		"object already exported via this exporter");
	}
	assert (entry == null);		// ()s to work around javadoc bug

	/*
	 * Export the remote object.
	 */
	entry = table.export(impl, se, enableDGC, keepAlive, id);
	used = true;
	
	/*
	 * Create proxy and invocation dispatcher for the remote object.
	 *
	 * (Use package-private BasicObjectEndpoint constructor to suppress
	 * DGC activity for this local live reference and to keep the impl
	 * strongly referenced through it.)
	 */
	Remote proxy;
	InvocationLayerFactory.Instances inst = null;
	try {
	    ImplContainer implContainer = new ImplContainer(impl);
	    weakImplContainer = new WeakReference(implContainer);
	    ObjectEndpoint oe =
		new BasicObjectEndpoint(entry.getEndpoint(), id,
					enableDGC, implContainer);
	    inst = ilf.createInstances(impl, oe, se);
	    entry.setInvocationDispatcher(inst.getInvocationDispatcher());
	    proxy = inst.getProxy();

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "export of {0} via {1} returns proxy {2}",
			   new Object[]{ impl, this, proxy });
	    }
	} finally {
	    if (inst == null) {
		unexport(true);
	    }
	} 
	
	return proxy;
    }

    /**
     * Unexports the remote object exported via the exporter's
     * {@link #export export} method such that incoming remote calls
     * to the object identifier in this exporter are no longer accepted
     * through the server endpoint in this exporter.
     *
     * <p>If <code>force</code> is <code>true</code>, the object
     * is forcibly unexported even if there are pending or in-progress remote
     * calls to the object identifier through the server endpoint.  If
     * <code>force</code> is <code>false</code>, the object is only
     * unexported if there are no pending or in-progress remote calls to the
     * object identifier through the server endpoint.
     *
     * <p>The return value is <code>true</code> if the object is (or was
     * previously) unexported, and <code>false</code> if the object is still
     * exported.
     *
     * @throws	IllegalStateException {@inheritDoc}
     **/
    public synchronized boolean unexport(boolean force) {
	
	if (!used) {
	    throw new IllegalStateException(
		"no object exported via this exporter");
	}

	if (entry != null && entry.unexport(force)) {
	    entry = null;
	    ImplContainer implContainer =
		(ImplContainer) weakImplContainer.get();
	    if (implContainer != null) {
		implContainer.clearImpl();
	    }
	}

	boolean result = entry == null;
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "unexport on {0} returns {1}",
		new Object[]{ this, Boolean.valueOf(result) });
	}
	return result;
    }
    
    /**
     * Returns the string representation for this exporter.
     * 
     * @return the string representation for this exporter
     **/
    public String toString() {
	return "BasicJeriExporter[" + se + "," + id + "]";
    }

    /**
     * Container for an impl object.
     *
     * BasicJeriExporter, when exporting an impl, passes an impl container
     * to the package-private BasicObjectEndpoint constructor so that the
     * BasicObjectEndpoint can reference the impl strongly (through the
     * container) while the object is exported.  The BasicJeriExporter
     * instance holds onto the impl container weakly so it won't prevent
     * the impl from being garbage collected; only the local stub that
     * references the BasicObjectEndpoint will prevent the impl from being
     * garbage collected.
     *
     * If the object is explicitly unexported via BasicJeriExporter, the
     * BasicJeriExporter instance clears the impl field (if the container
     * hasn't been garbage collected) so a reachable stub that references
     * the container (via the BasicObjectEndpoint in the stub) will not
     * prevent the impl from being garbage collected.
     **/
    private static class ImplContainer {

	private Object impl;

	ImplContainer(Object impl) {
	    this.impl = impl;
	}

	void clearImpl() {
	    impl = null;
	}
    }
}
