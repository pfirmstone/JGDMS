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

import org.apache.river.jeri.internal.runtime.DgcClient;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.ObjectStreamContext;
import net.jini.io.context.AcknowledgmentSource;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * References a remote object with an {@link Endpoint Endpoint} for
 * sending requests to the object and a {@link Uuid Uuid} to identify
 * the object at that <code>Endpoint</code>.
 *
 * <p>In addition to the <code>Endpoint</code> and the
 * <code>Uuid</code>, <code>BasicObjectEndpoint</code> instances also
 * contain a flag indicating whether or not the instance participates
 * in distributed garbage collection (DGC).
 *
 * <p>The {@link #newCall newCall} method can be used to send a
 * request to the remote object that this object references.
 *
 * <h4>Distributed Garbage Collection</h4>
 *
 * The <code>BasicObjectEndpoint</code> class acts as the <i>DGC
 * client</i> for all of its instances that participate in DGC (which
 * are called live remote references).  That is, it tracks the
 * existence and reachability of live remote references and makes
 * <i>dirty calls</i> and <i>clean calls</i> to the associated
 * server-side DGC implementations, as described below.
 *
 * <p>The server-side behavior of dirty and clean calls is specified
 * by {@link BasicJeriExporter}.  When the DGC client makes a dirty or
 * clean call to a given <code>Endpoint</code>, the behavior is
 * effectively that of using a <code>BasicObjectEndpoint</code>
 * containing that <code>Endpoint</code> and the object identifier
 * <code>d32cd1bc-273c-11b2-8841-080020c9e4a1</code> (and that doesn't
 * itself participate in DGC), wrapped in a {@link
 * net.jini.jeri.BasicInvocationHandler} with no client or server
 * constraints, wrapped in an instance of a dynamic proxy class that
 * implements an interface with the following remote methods:
 *
 * <pre>
 *     long dirty(Uuid clientID, long sequenceNum, Uuid[] ids)
 *         throws {@link RemoteException};
 *
 *     void clean(Uuid clientID, long sequenceNum, Uuid[] ids, boolean strong)
 *         throws RemoteException;
 * </pre>
 *
 * <code>clientID</code> is the DGC client's universally unique
 * identifier, which is generated using {@link UuidFactory#generate
 * UuidFactory.generate}.  <code>sequenceNum</code> identifies the
 * sequence number of the dirty or clean call with respect to all
 * other dirty and clean calls made by the same DGC client (regardless
 * of the <code>Endpoint</code> that the calls are made to).  All
 * dirty and clean calls made by a DGC client must have a unique
 * sequence number that monotonically increases with the temporal
 * order of the states (of reachable live remote references) that the
 * calls assert.  A dirty call asserts that live remote references
 * with the called <code>Endpoint</code> and each of the object
 * identifiers in <code>ids</code> exist for the identified DGC
 * client.  A clean call asserts that there are no (longer) live
 * remote references with the called <code>Endpoint</code> and each of
 * the object identifiers in <code>ids</code> for the identified DGC
 * client.
 *
 * <p>The tracked live remote references are categorized by their
 * <code>Endpoint</code> and further categorized by their object
 * identifier (with the <code>Endpoint</code> and object identifier
 * pair identifying a remote object).  When a new live remote
 * reference is created, either by construction or deserialization, it
 * is remembered among the live remote references with the same
 * <code>Endpoint</code> and object identifier, and its reachability
 * is tracked with a phantom reference.  If there is not already a
 * live remote reference with the same <code>Endpoint</code> and
 * object identifier, the DGC client makes a dirty call to the
 * server-side DGC implementation at that <code>Endpoint</code>, with
 * that object identifier in the <code>ids</code> argument.  Dirty
 * calls for multiple newly created live remote references with the
 * same <code>Endpoint</code> may be batched as one dirty call (such
 * as for multiple live remote references deserialized from the same
 * stream).
 *
 * <p>Each successful dirty call establishes or renews a lease for the
 * DGC client with the server-side DGC implementation at the
 * <code>Endpoint</code> that the dirty call was made to.  The
 * duration of the lease granted by the server is conveyed as the
 * return value of the dirty call, in milliseconds starting from some
 * time during the processing of the dirty call.  While there are live
 * remote references with a given <code>Endpoint</code>, the DGC
 * client attempts to maintain a valid lease with that
 * <code>Endpoint</code> by renewing its lease with successive dirty
 * calls.  The DGC client should take into consideration network and
 * processing latencies of the previous dirty call and the next
 * required dirty call in choosing when to renew a lease.  If the DGC
 * client has reason to assume that its lease with a given
 * <code>Endpoint</code> might have expired, then in subsequent dirty
 * calls to that <code>Endpoint</code>, it should include the object
 * identifiers of all currently reachable live remote references with
 * that <code>Endpoint</code>.  If a dirty call returns a negative
 * lease duration or throws a {@link NoSuchObjectException}, the DGC
 * client should refrain from making further dirty calls to the same
 * <code>Endpoint</code> until a new live remote reference with that
 * <code>Endpoint</code> is created.
 *
 * <p>If a dirty call fails with a communication exception other than
 * a <code>NoSuchObjectException</code>, the DGC client implementation
 * should make a reasonable effort to retry the dirty call (in a
 * network-friendly manner).  Also, after such a failed dirty call for
 * a given <code>Endpoint</code> and object identifier, any clean call
 * that is made for that same <code>Endpoint</code> and object
 * identifier within a reasonable amount of time should pass
 * <code>true</code> for the <code>strong</code> argument, in case the
 * failed dirty call does eventually get delivered to the server after
 * such a clean call has been processed.
 *
 * <p>When the last remaining live remote reference with a given
 * <code>Endpoint</code> and object identifier is detected to be
 * phantom reachable, the DGC client makes a clean call to the
 * server-side DGC implementation at that <code>Endpoint</code>, with
 * that object identifier in the <code>ids</code> argument.  Clean
 * calls for several object identifiers at the same
 * <code>Endpoint</code> may be batched as one clean call (such as
 * when multiple live remote references with the same
 * <code>Endpoint</code> and different object identifiers are detected
 * to be phantom reachable at the same time).
 *
 * <p>If a clean call fails with a communication exception other than
 * a <code>NoSuchObjectException</code>, the DGC client implementation
 * should make a reasonable effort to retry the clean call, in a
 * network-friendly manner, especially while the DGC client's lease
 * for the <code>Endpoint</code> remains valid (or while dirty calls
 * for the same <code>Endpoint</code> succeed).
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.BasicObjectEndpoint</code> to log information
 * at the following levels:
 *
 * <table summary="Describes what is logged by BasicObjectEndpoint at
 *        various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> failure of DGC dirty
 * or clean call
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> detailed implementation
 * activity
 *
 * </table>
 **/
public final class BasicObjectEndpoint
    implements ObjectEndpoint, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = 3235008605817758127L;

    /** local client-side DGC implementation */
    private static final DgcClient dgcClient = new DgcClient();

    /**
     * maps ObjectInputStream to DgcBatchContext
     * REMIND: We'd really like to use a weak *identity* hash map here--
     * does the lack of equals() security here create a risk?
     */
    private static final Map<ObjectInputStream,DgcBatchContext> streamBatches 
            = new WeakHashMap<ObjectInputStream,DgcBatchContext>(11);

    /**
     * The endpoint to send remote call requests to.
     *
     * @serial
     **/
    private final Endpoint ep;

    /**
     * The object identifier for the remote object.
     *
     * @serial
     **/
    private final Uuid id;

    /**
     * Flag indicating whether or not this
     * <code>BasicObjectEndpoint</code> participates in DGC.
     *
     * @serial
     **/
    private final boolean dgc;

    /** optional local reference to remote object (to maintain reachability) */
    private transient Object impl;

    /**
     * Creates a new <code>BasicObjectEndpoint</code> to reference a
     * remote object at the specified <code>Endpoint</code> with the
     * specified <code>Uuid</code>.
     *
     * @param ep the endpoint to send remote call requests for the
     * remote object to
     *
     * @param id the object identifier for the remote object
     *
     * @param enableDGC flag indicating whether or not the
     * <code>BasicObjectEndpoint</code> participates in DGC
     *
     * @throws NullPointerException if <code>e</code> or
     * <code>id</code> is <code>null</code>
     **/
    public BasicObjectEndpoint(Endpoint ep, Uuid id, boolean enableDGC) {
	if (ep == null) {
	    throw new NullPointerException("null endpoint");
	}
	if (id == null) {
	    throw new NullPointerException("null object identifier");
	}
	this.ep = ep;
	this.id = id;
	this.dgc = enableDGC;

	/*
	 * If DGC-enabled, register this live reference instance to be
	 * tracked by the local client-side DGC system.
	 */
	if (dgc) {
	    dgcClient.registerRefs(ep, Collections.singleton(this));
	}
    }

    /**
     * Creates a new BasicObjectEndpoint to reference the supplied
     * remote object in the current virtual machine with the specified
     * Endpoint, Uuid, and enableDGC status.
     **/
    BasicObjectEndpoint(Endpoint ep, Uuid id, boolean enableDGC, Object impl) {
	this.ep = ep;
	this.id = id;
	this.dgc = enableDGC;

	if (dgc) {
	    /*
	     * When this constructor is used, this live reference refers to a
	     * remote object in the current VM, so instead of registering for
	     * participation in the DGC protocol, we just keep a direct strong
	     * reference to the impl to maintain its reachability.
	     */
	    this.impl = impl;
	}
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method first invokes {@link Endpoint#newRequest
     * newRequest} on this <code>BasicObjectEndpoint</code>'s
     * contained <code>Endpoint</code> with the specified constraints
     * to obtain an <code>OutboundRequestIterator</code>.  It then
     * wraps the obtained iterator in another
     * <code>OutboundRequestIterator</code> and returns the wrapped
     * iterator.
     *
     * <p>The methods of the returned
     * <code>OutboundRequestIterator</code> behave as follows:
     *
     * <p>{@link OutboundRequestIterator#hasNext boolean hasNext()}:
     *
     * <blockquote>
     *
     * <p>Returns <code>true</code> if this iterator supports making
     * at least one more attempt to communicate the remote call, and
     * <code>false</code> otherwise.
     *
     * <p>This method invokes <code>hasNext</code> on the underlying
     * iterator and returns the result.
     *
     * <p>The security context in which this method is invoked may be
     * used for subsequent verification of security permissions; see
     * the {@link OutboundRequestIterator#next next} method
     * specification for more details.
     *
     * </blockquote>
     *
     * <p>{@link OutboundRequestIterator#next OutboundRequest next()}:
     *
     * <blockquote>
     *
     * <p>Initiates an attempt to communicate the remote call to the
     * referenced remote object.
     *
     * <p>This method invokes <code>next</code> on the underlying
     * iterator to obtain an <code>OutboundRequest</code>.  Then it
     * writes the object identifier of the
     * <code>BasicObjectEndpoint</code> that produced this iterator to
     * the request's output stream by invoking {@link
     * Uuid#write(OutputStream) Uuid.write(OutputStream)} with the
     * request output stream, and then it returns the request.
     *
     * <p>Throws {@link NoSuchElementException} if this iterator does
     * not support making another attempt to communicate the remote
     * call (that is, if <code>hasNext</code> would return
     * <code>false</code>).
     *
     * <p>Throws {@link IOException} if an <code>IOException</code> is
     * thrown by the invocation of <code>next</code> on the underlying
     * iterator or by the subsequent I/O operations.
     *
     * <p>Throws {@link SecurityException} if a
     * <code>SecurityException</code> is thrown by the invocation of
     * <code>next</code> on the underlying iterator or by the
     * subsequent I/O operations.
     *
     * </blockquote>
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public OutboundRequestIterator newCall(InvocationConstraints constraints) {
	final OutboundRequestIterator iter = ep.newRequest(constraints);
	return new OutboundRequestIterator() {

	    public boolean hasNext() {
		return iter.hasNext();
	    }

	    public OutboundRequest next() throws IOException {
		OutboundRequest call = iter.next();
		boolean ok = false;
		try {
		    id.write(call.getRequestOutputStream());
		    ok = true;
		} finally {
		    if (!ok) {
			call.abort();	// because caller cannot
		    }
		}
		return call;
	    }
	};
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method reads a byte from the response input stream of
     * <code>call</code>.  If an {@link IOException} is thrown reading
     * the byte, that exception is thrown to the caller.  If reading
     * the byte otherwise indicates EOF, an {@link EOFException} is
     * thrown to the caller.  If the byte is <code>0x00</code>, then
     * this method returns a {@link NoSuchObjectException} indicating
     * that there is no remote object exported with the object
     * identifier at the remote endpoint.  If the byte is
     * <code>0x01</code>, then this method returns <code>null</code>,
     * indicating that a remote object corresponding to the object
     * identifier and endpoint is exported, and thus the caller may
     * proceed to read the response of the remote call.  If the byte
     * is any other value, this method returns an {@link
     * UnmarshalException} indicating that a protocol error occurred.
     *
     * @throws NullPointerException {@inheritDoc}
     **/
    public RemoteException executeCall(OutboundRequest call)
	throws IOException
    {
	// assume that the request output stream has been closed

	int status = call.getResponseInputStream().read();
	switch (status) {

	case -1:
	    throw new EOFException();

	case 0x00:
	    // REMIND: close the response input stream?
            Exception ex = null;
            try {
                call.getResponseInputStream().close();
            } catch (IOException e){
                ex = e;
            }
	    // REMIND: Do we want to read a server-supplied reason string?
            if (ex != null){
                return new NoSuchObjectException("no such object in table, input stream close threw IOException: " + ex);
            }
	    return new NoSuchObjectException("no such object in table");

	case 0x01:
	    return null;

	default:
	    // REMIND: close the response input stream?
            Exception exc = null;
            try {
                call.getResponseInputStream().close();
            } catch (IOException e){
                exc = e;
            }
	    // REMIND: Do we really want this failure mode here?
            if (exc != null){
                return new UnmarshalException("unexpected invocation status: " +
					  Integer.toHexString(status), exc);
            }
	    return new UnmarshalException("unexpected invocation status: " +
					  Integer.toHexString(status));
	}
    }

    /**
     * Returns the <code>Endpoint</code> for the referenced remote
     * object.
     *
     * @return the <code>Endpoint</code> for the referenced remote
     * object
     **/
    public Endpoint getEndpoint() {
	return ep;
    }

    /**
     * Returns the object identifier for the referenced remote object.
     *
     * @return the object identifier for the referenced remote object
     **/
    public Uuid getObjectIdentifier() {
 	return id;
    }

    /**
     * Returns <code>true</code> if this
     * <code>BasicObjectEndpoint</code> participates in DGC and
     * <code>false</code> otherwise.
     *
     * @return <code>true</code> if this
     * <code>BasicObjectEndpoint</code> participates in DGC and
     * <code>false</code> otherwise
     **/
    public boolean getEnableDGC() {
	return dgc;
    }

    /**
     * Returns the hash code value for this
     * <code>BasicObjectEndpoint</code>.
     *
     * @return the hash code value for this
     * <code>BasicObjectEndpoint</code>
     **/
    public int hashCode() {
	return id.hashCode();
    }

    /**
     * Compares the specified object with this
     * <code>BasicObjectEndpoint</code> for equality.
     *
     * <p>This method returns <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object is also a
     * <code>BasicObjectEndpoint</code>,
     *
     * <li>the object identifier and <code>enableDGC</code> flag in
     * the specified object are equal to the ones in this object, and
     *
     * <li>the <code>Endpoint</code> in the specified object has
     * the same class and is equal to the one in this object.
     *
     * </ul>
     *
     * @param obj the object to compare with
     *
     * @return <code>true</code> if <code>obj</code> is equivalent to
     * this object; <code>false</code> otherwise
     **/
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof BasicObjectEndpoint)) {
	    return false;
	}
	BasicObjectEndpoint other = (BasicObjectEndpoint) obj;
	return
	    id.equals(other.id) &&
	    dgc == other.dgc &&
	    Util.sameClassAndEquals(ep, other.ep);
    }

    /**
     * Returns <code>true</code> if the specified object (which is not
     * yet known to be trusted) is equivalent in trust, content, and
     * function to this known trusted object, and <code>false</code>
     * otherwise.
     *
     * <p>This method returns <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object is also a
     * <code>BasicObjectEndpoint</code>,
     *
     * <li>the object identifier and <code>enableDGC</code> flag in
     * the specified object are equal to the ones in this object, and
     *
     * <li>this object's <code>Endpoint</code> is an instance of
     * {@link TrustEquivalence} and invoking its
     * <code>checkTrustEquivalence</code> method with the specified
     * object's <code>Endpoint</code> returns <code>true</code>.
     *
     * </ul>
     **/
    public boolean checkTrustEquivalence(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof BasicObjectEndpoint)) {
	    return false;
	}
	BasicObjectEndpoint other = (BasicObjectEndpoint) obj;
	return
	    id.equals(other.id) &&
	    dgc == other.dgc &&
	    Util.checkTrustEquivalence(ep, other.ep);
    }

    /**
     * Returns a string representation of this
     * <code>BasicObjectEndpoint</code>.
     *
     * @return a string representation of this
     * <code>BasicObjectEndpoint</code>
     **/
    public String toString() {
	return
	    "BasicObjectEndpoint[" + (dgc ? "DGC," : "") + id + "," + ep + "]";
    }

    /**
     * If this <code>BasicObjectEndpoint</code> participates in DGC
     * and if <code>out</code> is an instance of {@link
     * ObjectStreamContext} and its context collection contains an
     * {@link AcknowledgmentSource}, ensures that an {@link
     * net.jini.io.context.AcknowledgmentSource.Listener} is
     * registered (with the <code>AcknowledgmentSource</code>) that
     * will hold a strong reference to this
     * <code>BasicObjectEndpoint</code> until the listener's {@link
     * net.jini.io.context.AcknowledgmentSource.Listener#acknowledgmentReceived
     * acknowledgmentReceived} method is invoked (or some other
     * implementation-specific event occurs, such as a timeout
     * expiration).
     **/
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();

	if (dgc && out instanceof ObjectStreamContext) {
	    Collection context =
		((ObjectStreamContext) out).getObjectStreamContext();
	    for (Iterator i = context.iterator(); i.hasNext();) {
		Object e = i.next();
		if (e instanceof AcknowledgmentSource) {
		    AcknowledgmentSource ackSource = (AcknowledgmentSource) e;
		    ackSource.addAcknowledgmentListener(new AckListener(this));
		    break;
		}
	    }
	}
    }

    /**
     * Holds a strong reference to a BasicObjectEndpoint until an
     * acknowledgment has been received.
     *
     * REMIND: Clear reference after a certain timeout regardless?
     **/
    private static class AckListener implements AcknowledgmentSource.Listener {
	private volatile BasicObjectEndpoint ref;
	AckListener(BasicObjectEndpoint ref) { this.ref = ref; }
	public void acknowledgmentReceived(boolean received) { ref = null; }
    }

    /**
     * If this <code>BasicObjectEndpoint</code> participates in DGC,
     * initiates asynchronous DGC activity for it.
     *
     * @throws InvalidObjectException if the <code>Endpoint</code> or
     * the object identifier is <code>null</code>
     **/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (ep == null) {
	    throw new InvalidObjectException("null endpoint");
	}
	if (id == null) {
	    throw new InvalidObjectException("null object identifier");
	}
	    
	/*
	 * If DGC-enabled, register this live reference instance to be
	 * tracked by the local client-side DGC implementation.  Use
	 * an ObjectInputValidation for delayed registration so that
	 * multiple live references in the object graph being
	 * deserialized can be registered in one batch.
	 */
	if (dgc) {
	    /*
	     * REMIND: short circuit lookup with thread local,
	     * to avoid synchronization overhead in the common case?
	     */
	    DgcBatchContext batchContext;
	    synchronized (streamBatches) {
		batchContext = (DgcBatchContext) streamBatches.get(in);
		if (batchContext == null) {
		    batchContext = new DgcBatchContext();
		    try {				// REMIND: priority??
			in.registerValidation(batchContext, 0);
		    } catch (InvalidObjectException e) { // should be NPE
			throw new AssertionError();
		    }
		    streamBatches.put(in, batchContext);
		}
	    }
	    batchContext.addLiveRef(this);
	}
    }

    /**
     * Collects live references to be registered with the local
     * client-side DGC implementation and registers them in
     * Endpoint-specific batches.
     *
     * REMIND: lack of thread safety OK?
     **/
    private static class DgcBatchContext implements ObjectInputValidation {

	/** maps Endpoint to List<BasicObjectEndpoint> */
	private final Map endpointTable = new HashMap(3);

	DgcBatchContext() { }

	void addLiveRef(BasicObjectEndpoint ref) {
	    /*
	     * Organize collected live references into separate lists
	     * for each distinct endpoint.
	     */
	    Endpoint endpoint = ref.getEndpoint();
	    Collection refList = (Collection) endpointTable.get(endpoint);
	    if (refList == null) {
		refList = new ArrayList();
		endpointTable.put(endpoint, refList);
	    }
	    refList.add(ref);
	}

	public void validateObject() {	// doesn't throw InvalidObjectException
	    /*
	     * Perform a batch DGC registration for each list of
	     * live references to the same endpoint.
	     */
	    Iterator iter = endpointTable.entrySet().iterator();
	    while (iter.hasNext()) {
		Map.Entry entry = (Map.Entry) iter.next();
		Endpoint endpoint = (Endpoint) entry.getKey();
		Collection refList = (Collection) entry.getValue();
		dgcClient.registerRefs(endpoint, refList);
	    }
	    endpointTable.clear();
	}
    }
}
