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
package com.sun.jini.outrigger;

import java.rmi.RemoteException;

import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;

import net.jini.space.JavaSpace05;
import net.jini.space.MatchSet;

/**
 * Interface for the iterators returned by the <code>contents()</code>
 * method of <code>JavaSpaceAdmin</code>.  Note
 * <code>AdminIterator</code>s do not survive restarts of the
 * underlying space.
 * <p>
 *
 * @deprecated Use {@link MatchSet} instead. <code>MatchSet</code>s
 *             can be obtained using the 
 *             {@link JavaSpace05#contents JavaSpace05.contents}
 *             method.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpaceAdmin 
 */
public interface AdminIterator {
    /**
     * Return the next entry in the sequence.  Returns <code>null</code>
     * if there are no more matching entries in the space.
     * <p>
     * This method is idempotent in the face of <code>RemoteException</code>s.
     *
     * @throws UnusableEntryException if the field of next entry in
     * sequence can't be deserialized (usually this is because the class
     * in question could not be loaded).
     */
    public Entry next()	throws UnusableEntryException, RemoteException;

    /**
     * The effect of this call depends on the most recent call to
     * <code>next()</code>:
     * <ul>
     * <li> If the last call to <code>next()</code> returned an
     * <code>Entry</code> that entry will be removed from the space.
     * <p>
     * <li> If the last call to <code>next()</code> threw a
     * <code>UnusableEntryException</code> the <code>Entry</code> that
     * could not be deserialized will be removed from the space.
     * <p>
     * <li> If the last call to <code>next()</code> returned
     * <code>null</code>, threw a <code>RemoteException</code>, or
     * <code>next()</code> has not yet been called on this iterator a
     * <code>IllegalStateException</code> will be thrown and no entry will
     * be removed.
     * <p>
     * </ul>
     * This method is idempotent in the face of
     * <code>RemoteException</code>.
     * <p>
     * @throws IllegalStateException if <code>next()</code> has not be
     * called on this iterator, or the last invocation of
     * <code>next()</code> returned <code>null</code> or threw
     * <code>RemoteException</code>.     
     */
    public void delete() throws RemoteException;

    /**
     * Tell the server that this iterator is no longer in use.  All
     * operations on a closed iterator  have undefined results, except  
     * the <code>close()</code> method.
     * <p> 
     * This method is idempotent in the face of <code>RemoteException</code>.
     */
    public void close() throws RemoteException;
}
