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

import java.rmi.RemoteException;
import java.util.NoSuchElementException;

/**
 * Defines an iterator that produces objects from which a
 * {@link net.jini.security.TrustVerifier} might be obtained.
 * {@link ProxyTrustVerifier} obtains such iterators from instances of
 * classes that have a non-<code>static</code> member method with signature:
 * <pre>ProxyTrustIterator getProxyTrustIterator();</pre>
 * The expectation is that each element produced by the iterator either
 * implements {@link ProxyTrust} or might have a
 * <code>getProxyTrustIterator</code> method that can be used recursively to
 * obtain further candidates.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
@Deprecated
public interface ProxyTrustIterator {
    /**
     * Returns <code>true</code> if the iteration has more elements, and
     * <code>false</code> otherwise.
     *
     * @return <code>true</code> if the iteration has more elements, and
     * <code>false</code> otherwise
     */
    boolean hasNext();

    /**
     * Returns the next element in the iteration. This method can throw
     * an exception (other than <code>NoSuchElementException</code>) without
     * terminating the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     * @throws RemoteException if a communication-related exception occurs
     * while producing the next element
     * @throws RuntimeException if a runtime exception occurs while producing
     * the next element
     */
    Object next() throws RemoteException;

    /**
     * Provides the iteration with a <code>RemoteException</code> thrown from
     * a remote call made while attempting to obtain a
     * {@link net.jini.security.TrustVerifier} from the object
     * returned by the most recent call to {@link #next next}. Setting an
     * exception may influence which (if any) elements are subsequently
     * produced by the iteration. (A <code>RemoteException</code> thrown
     * directly by <code>next</code> should not be passed to this method.)
     *
     * @param e <code>RemoteException</code> thrown from a remote call
     * @throws NullPointerException if the argument is <code>null</code>
     * @throws IllegalStateException if <code>next</code> has never been
     * called, or if this method has already been called since the most
     * recent call to <code>next</code>, or if <code>hasNext</code> has been
     * called since the most recent call to <code>next</code>, or if the most
     * recent call to <code>next</code> threw a <code>RemoteException</code>
     */
    void setException(RemoteException e);
}
