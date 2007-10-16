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
package net.jini.space;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.constraint.RemoteMethodControl;

/**
 * A collection of {@link Entry} instances to be incrementally
 * returned from a JavaSpaces service. Some operations on a
 * space must return more entries than can be conveniently
 * returned by a single call, generally because returning all the
 * entries in one result would consume too many resources in the
 * client or introduce too much latency before the first entry
 * could be processed. In these cases, match sets are returned to
 * incrementally return the necessary entries. A
 * <code>MatchSet</code> instance is a proxy object that can be
 * used to access a match set created by a space.<p>
 *
 * Typically match sets are created by some factory method on the
 * space (for example, {@link JavaSpace05#contents
 * JavaSpace05.contents}) which returns a <code>MatchSet</code>
 * instance that will serve as a proxy to the match set. The match
 * set will initially contain some population of entries specified
 * by the operation that created it. These entries can be
 * retrieved by calling {@link #next next}. A successful call to
 * <code>next</code> will remove the returned <code>Entry</code>
 * from the match set.  Match sets can end up in one of two
 * terminal states, <em>exhausted</em> or <em>invalidated</em>.
 * Under what conditions a match set enters one of these states is
 * specified by the operation that created it. An exhausted match
 * set is empty and will have no more entries added. Calling
 * <code>next</code> on an exhausted match set must always return
 * <code>null</code>. Calling <code>next</code> on an invalidated
 * match set may return a non-<code>null</code> value, or it may
 * throw one of the allowed exceptions.  In particular it may
 * throw {@link NoSuchObjectException} to indicate that the match
 * set has been invalidated. Once <code>next</code> throws a
 * <code>NoSuchObjectException</code>, all future
 * <code>next</code> calls on that instance must also throw
 * <code>NoSuchObjectException</code>. Calling <code>next</code>
 * on an invalidated match set must never return
 * <code>null</code>. Implementations must not add entries to an
 * invalidated match set. <p>
 *
 * Between the time a match set is created and the time it reaches a
 * terminal state, entries may be added by the space. However, an
 * <code>Entry</code> that is removed by a <code>next</code> call
 * must not be added back to a match set (though if there is a
 * distinct but equivalent entry in the space it may be
 * added). The space may also remove entries independent of
 * <code>next</code> calls. The conditions under which entries
 * will be removed independent of <code>next</code> calls or added
 * after the initial creation of the match set are specified by
 * the operation that created the match set. <p>
 *
 * If there is a possibility that a match set may become
 * invalidated, it must be leased and an appropriate proxy must be
 * returned by the {@link #getLease getLease} method. If there is
 * no possibility that the match set will become invalidated,
 * implementations should not lease the match set. If a match set
 * is not leased, then <code>getLease</code> must return
 * <code>null</code>. <p>
 *
 * An active lease on a match set serves as a hint to the space
 * that the client is still interested in the match set, and as a
 * hint to the client that the match set is still functioning.
 * However, implementations are allowed to invalidate match sets
 * associated with active leases and to unilaterally cancel leases
 * associated with functioning match sets. If a match set is
 * leased and the lease is active, implementations should, to the
 * best of their ability, maintain the match set and not invalidate
 * it. There are cases, however, where this may not be possible in
 * particular, it is not expected that implementations will
 * maintain match sets across crashes. If the lease expires or is
 * canceled, an implementation should invalidate the match
 * set. An implementation must unilaterally cancel a match set's
 * lease if the match set is invalidated. An implementation may
 * unilaterally cancel the lease at other times without necessarily
 * invalidating the match set. Clients should <em>not</em> assume
 * that the resources associated with a leased match set will be
 * freed if the match set reaches the exhausted state, and should
 * instead cancel the lease.
 *
 * This interface is not a remote interface; though in general a
 * <code>MatchSet</code> is a proxy for some remote match set,
 * only the <code>next</code> method is considered to be a remote
 * method, and as outlined in its {@linkplain #next description}
 * it deviates in a number of ways from normal Java(TM) Remote
 * Method Invocation remote method semantics.
 *
 * @since 2.1 */
public interface MatchSet {
    /**
     * Removes one <code>Entry</code> from the match set and
     * returns a copy to the caller. Returns <code>null</code> if
     * the match set is empty. The returned <code>Entry</code>
     * must be unmarshalled in accordance with the <a 
     * href=http://www.jini.org/standards/index.html>Jini Entry
     * Specification</a>.<p>
     *
     * A given invocation of this method may perform remote
     * communications, but generally the <code>next</code> method
     * is not expected to have {@linkplain
     * net.jini.core.constraint remote method constraints} that
     * can vary from invocation to invocation. Instead the set of
     * constraints associated with the <code>next</code> method
     * will be fixed at the time the match set was created, even
     * if this object implements an interface like {@link
     * RemoteMethodControl} that would otherwise allow the set of
     * constraints associated with the <code>next</code> method to
     * be altered.<p>
     *
     * @return an <code>Entry</code> from the match set, or
     *         <code>null</code> if the match set is empty
     * @throws UnusableEntryException if the entry removed from
     *         the match set could not be unmarshalled
     * @throws RemoteException if a communication error occurs. If a
     *         <code>RemoteException</code> is thrown, no
     *         <code>Entry</code> was removed from the match set
     *         because of this call 
     */
    public Entry next() throws RemoteException, UnusableEntryException;

    /**
     * Returns a proxy to the {@link Lease} associated with this
     * match set, or <code>null</code> if this match set is not
     * leased.
     * @return a proxy for the match set's lease, or
     * <code>null</code> if there is no lease associated with this
     * match set  
     */
    public Lease getLease();

    /**
     * Returns a <em>snapshot</em> of the {@link Entry} removed by
     * the last call to {@link #next next}. Snapshots are defined
     * in section JS.2.6 of the <a
     * href=http://www.jini.org/standards/index.html>
     * JavaSpaces Service Specification</a> and are an
     * alternative representation of a given <code>Entry</code>
     * produced by a particular space for use with that same
     * space. Passing a snapshot to a space is generally more
     * efficient than passing the original representation.<p>
     * 
     * Any snapshot returned by this method will meet the same
     * contract as the object returned by passing the result of
     * the last <code>next</code> invocation to {@link
     * JavaSpace#snapshot JavaSpace.snapshot}.<p>
     *
     * Generally there is a cost associated with calling the
     * <code>JavaSpace.snapshot</code> method. Thus creating a
     * snapshot using that method is usually only worthwhile if
     * the resulting snapshot is used more than once. The cost of
     * invoking this method should be low and should be worthwhile
     * even if the resulting snapshot is used only once. <p>
     *
     * @return a <em>snapshot</em> of the {@link Entry} removed
     *         from the match set by the last call to {@link #next next}
     * @throws IllegalStateException if the last call to
     *         <code>next</code> did not remove an
     *         <code>Entry</code> from the match set, or no call
     *         to <code>next</code> has been made
     */
    public Entry getSnapshot();
}
