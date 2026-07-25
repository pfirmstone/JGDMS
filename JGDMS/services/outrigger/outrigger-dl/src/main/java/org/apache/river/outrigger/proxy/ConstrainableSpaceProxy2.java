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
package org.apache.river.outrigger.proxy;

import org.apache.river.landlord.ConstrainableLandlordLease;
import org.apache.river.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.space.JavaSpace;
import net.jini.space.JavaSpace05;
import net.jini.space.TupleSpace;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Constrainable subclass of <code>SpaceProxy2</code>
 */
@AtomicSerial
public final class ConstrainableSpaceProxy2 extends SpaceProxy2
    implements RemoteMethodControl
{
    /**
     * Array containing element pairs in which each pair of elements
     * represents a mapping between two methods having the following
     * characteristics:
     * <ul>
     * <li> the first element in the pair is one of the public, remote
     *      method(s) that may be invoked by the client through 
     *      <code>SpaceProxy2</code>.
     * <li> the second element in the pair is the method, implemented
     *      in the backend server class, that is ultimately executed in
     *      the server's backend when the client invokes the corresponding
     *      method in this proxy.
     * </ul>
     */
    private static final Method[] methodMapArray =  {
	ProxyUtil.getMethod(Administrable.class, "getAdmin", new Class[] {}),
	ProxyUtil.getMethod(Administrable.class, "getAdmin", new Class[] {}),

	ProxyUtil.getMethod(JavaSpace.class, "write", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "write",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class}),


	ProxyUtil.getMethod(JavaSpace.class, "read", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "read",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "take", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "take",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "readIfExists", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "readIfExists",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	ProxyUtil.getMethod(JavaSpace.class, "takeIfExists", 
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "takeIfExists",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class}),


	/* DER-only (JGDMS 4.0.0): the client-side method mapped here is
	 * the live TupleSpace MarshalledInstance overload -- the
	 * deprecated JavaSpace MarshalledObject overload's behavior is
	 * withdrawn (non-null -> UnsupportedOperationException; null
	 * delegates to this MI path), so constraints are keyed against
	 * the MI method.
	 */
	ProxyUtil.getMethod(TupleSpace.class, "notify",
			    new Class[] {Entry.class,
					 Transaction.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "notify",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class}),

	/* U1a-review F2 rider (U1c): constraint pairs for the three
	 * client-reachable server methods the map previously omitted --
	 * registerForAvailabilityEvent (keyed, like notify, against the
	 * live TupleSpace MI overload; the deprecated MO overload's
	 * behavior is withdrawn), batch write, and batch take. Without a
	 * pair, a client's per-method constraints on these operations
	 * would silently fail to map onto the backend methods.
	 */
	ProxyUtil.getMethod(TupleSpace.class, "registerForAvailabilityEvent",
			    new Class[] {Collection.class,
					 Transaction.class,
					 boolean.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "registerForAvailabilityEvent",
			    new Class[] {EntryRep[].class,
					 Transaction.class,
					 boolean.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class}),

	ProxyUtil.getMethod(JavaSpace05.class, "write",
			    new Class[] {List.class,
					 Transaction.class,
					 List.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "write",
			    new Class[] {EntryRep[].class,
					 Transaction.class,
					 long[].class}),

	ProxyUtil.getMethod(JavaSpace05.class, "take",
			    new Class[] {Collection.class,
					 Transaction.class,
					 long.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "take",
			    new Class[] {EntryRep[].class,
					 Transaction.class,
					 long.class,
					 int.class,
					 OutriggerServer.QueryCookie.class}),

	ProxyUtil.getMethod(JavaSpace05.class, "contents",
			    new Class[] {Collection.class,
					 Transaction.class,
					 long.class,
					 long.class}), 
	ProxyUtil.getMethod(OutriggerServer.class, "contents",
			    new Class[] {EntryRep[].class,
					 Transaction.class,
					 long.class,
					 long.class}), 


	// Use the same constants for nextBatch as contents
	ProxyUtil.getMethod(JavaSpace05.class, "contents",
			    new Class[] {Collection.class,
					 Transaction.class,
					 long.class,
					 long.class}),
	ProxyUtil.getMethod(OutriggerServer.class, "nextBatch",
			    new Class[] {Uuid.class,
					 Uuid.class}),

	/* Filtered (CEL pushdown) operations — SOW Part B, unit B1. Each
	 * client-facing FilteredJavaSpace method is paired with its filtered
	 * OutriggerServer backend method so a client's per-method constraints
	 * (including the space's ATOMIC_DER MarshallingFormat requirement) map
	 * onto the filtered call exactly as they do for the unfiltered siblings.
	 */
	ProxyUtil.getMethod(FilteredJavaSpace.class, "read",
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class,
					 byte[].class}),
	ProxyUtil.getMethod(OutriggerServer.class, "read",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class,
					 byte[].class}),

	ProxyUtil.getMethod(FilteredJavaSpace.class, "take",
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class,
					 byte[].class}),
	ProxyUtil.getMethod(OutriggerServer.class, "take",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class,
					 byte[].class}),

	ProxyUtil.getMethod(FilteredJavaSpace.class, "readIfExists",
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class,
					 byte[].class}),
	ProxyUtil.getMethod(OutriggerServer.class, "readIfExists",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class,
					 byte[].class}),

	ProxyUtil.getMethod(FilteredJavaSpace.class, "takeIfExists",
			    new Class[] {Entry.class,
					 Transaction.class,
					 long.class,
					 byte[].class}),
	ProxyUtil.getMethod(OutriggerServer.class, "takeIfExists",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 long.class,
					 OutriggerServer.QueryCookie.class,
					 byte[].class}),

	ProxyUtil.getMethod(FilteredJavaSpace.class, "notify",
			    new Class[] {Entry.class,
					 Transaction.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class,
					 byte[].class}),
	ProxyUtil.getMethod(OutriggerServer.class, "notify",
			    new Class[] {EntryRep.class,
					 Transaction.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class,
					 byte[].class}),

	ProxyUtil.getMethod(FilteredJavaSpace.class, "registerForAvailabilityEvent",
			    new Class[] {Collection.class,
					 Transaction.class,
					 boolean.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class,
					 byte[].class}),
	ProxyUtil.getMethod(OutriggerServer.class, "registerForAvailabilityEvent",
			    new Class[] {EntryRep[].class,
					 Transaction.class,
					 boolean.class,
					 RemoteEventListener.class,
					 long.class,
					 MarshalledInstance.class,
					 byte[].class})
    };//end methodMapArray

    /** 
     * Client constraints placed on this proxy (may be <code>null</code> 
     * @serial
     */
    private final MethodConstraints methodConstraints;

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("methodConstraints", MethodConstraints.class)
        };
    }

    public static void serialize(PutArg arg, ConstrainableSpaceProxy2 o) throws IOException {
        arg.put("methodConstraints", o.methodConstraints);
        arg.writeArgs();
    }

    /**
     * Create a new <code>ConstrainableSpaceProxy2</code>.
     * @param space The <code>OutriggerServer</code> for the 
     *              space.
     * @param spaceUuid The universally unique ID for the
     *              space
     * @param serverMaxServerQueryTimeout The value this proxy
     *              should use for the <code>maxServerQueryTimeout</code>
     *              if no local value is provided.
     * @param entryFormat The marshalling format this space was born with
     *              (fixed at the space's instantiation, immutable for its
     *              life); every entry/template this proxy marshals uses
     *              this format. <em>Not</em> derived from
     *              <code>methodConstraints</code> -- see
     *              {@link #setConstraints}.
     * @param methodConstraints the client method constraints to place on
     *                          this proxy (may be <code>null</code>).
     * @throws NullPointerException if <code>space</code>,
     *         <code>spaceUuid</code> or <code>entryFormat</code> is
     *         <code>null</code>.
     * @throws IllegalArgumentException if 
     *         <code>serverMaxServerQueryTimeout</code> is not
     *         larger than zero.     
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    public ConstrainableSpaceProxy2(OutriggerServer space, Uuid spaceUuid, 
				    long serverMaxServerQueryTimeout, 
				    MarshallingFormat entryFormat,
				    MethodConstraints methodConstraints)
    {
	super(constrainServer(space, methodConstraints),
	      spaceUuid, serverMaxServerQueryTimeout, entryFormat);
	this.methodConstraints = methodConstraints;
    }

    ConstrainableSpaceProxy2(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg, check(arg));
    }
    
    ConstrainableSpaceProxy2(GetArg arg, MethodConstraints constraints) throws IOException, ClassNotFoundException {
	super(arg);
	methodConstraints = constraints;
    }
    
    private static MethodConstraints check(GetArg arg) throws IOException, ClassNotFoundException {
	// Read the superclass field directly rather than constructing a plain
	// SpaceProxy2 (now abstract); super(arg) still runs its validation.
	Object space = arg.get("space", null, OutriggerServer.class);
	MethodConstraints methodConstraints = arg.get("methodConstraints", null, MethodConstraints.class);
	MethodConstraints proxyCon = null;
	if (space instanceof RemoteMethodControl &&
	    (proxyCon = ((RemoteMethodControl)space).getConstraints()) != null) {
	    // Constraints set during proxy deserialization.
	    return ConstrainableProxyUtil.reverseTranslateConstraints(
		    proxyCon, methodMapArray);
	}
	/* Basic validation of space, spaceUuid, and
	 * serverMaxServerQueryTimeout was performed by
	 * SpaceProxy2.readObject(), we just need to verify than
	 * space implements RemoteMethodControl and that it has
	 * appropriate constraints.
	 */
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    methodConstraints, space, methodMapArray);
	return methodConstraints;
    }

    /**
     * Returns a copy of the given <code>OutriggerServer</code> proxy
     * having the client method constraints that result after
     * mapping defined by methodMapArray is applied.
     * @param server The proxy to attach constrains too.
     * @param constraints The source method constraints.
     * @throws NullPointerException if <code>server</code> is 
     *         <code>null</code>.
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    private static OutriggerServer constrainServer(OutriggerServer server,
        MethodConstraints constraints)
    {
	final MethodConstraints serverRefConstraints 
	    = ConstrainableProxyUtil.translateConstraints(constraints,
							  methodMapArray);
	final RemoteMethodControl constrainedServer = 
	    ((RemoteMethodControl)server).
	    setConstraints(serverRefConstraints);

	return (OutriggerServer)constrainedServer;
    }

    /**
     * Returns a copy of this proxy with the client's requested method
     * constraints. The born {@link #entryFormat} is carried over from
     * {@code this} unconditionally -- it is <em>not</em> one of the
     * constraints being replaced here, so a client cannot drop or change
     * the space's format via {@code setConstraints}; only the dedicated
     * immutable field set once at the space's instantiation ever
     * determines it (JGDMS-STD-006 sec.3 item 5).
     */
    public RemoteMethodControl setConstraints(MethodConstraints constraints)
    {
	return new ConstrainableSpaceProxy2(space, spaceUuid,
					   serverMaxServerQueryTimeout,
					   entryFormat,
					   constraints);
    }

    public MethodConstraints getConstraints() {
	return methodConstraints;
    }

    protected Lease constructLease(Uuid uuid, long expiration) {
	return new ConstrainableLandlordLease(uuid, space, spaceUuid,
					      expiration, null);
    }
}

