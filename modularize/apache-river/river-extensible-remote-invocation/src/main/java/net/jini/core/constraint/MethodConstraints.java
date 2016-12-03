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

package net.jini.core.constraint;

import java.lang.reflect.Method;
import java.util.Iterator;

/**
 * Defines an immutable mapping from {@link Method} to
 * {@link InvocationConstraints}, used to specify per-method constraints.
 * <p>
 * An instance of this interface must implement {@link Object#equals
 * Object.equals} to return <code>true</code> when passed a mapping that is
 * equivalent in trust, content, and function, and to return <code>false</code>
 * otherwise. That is, the <code>equals</code> method must be a sufficient
 * substitute for
 * {@link net.jini.security.proxytrust.TrustEquivalence#checkTrustEquivalence
 * TrustEquivalence.checkTrustEquivalence}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface MethodConstraints {
    /**
     * Returns the constraints for the specified method as a
     * non-<code>null</code> value. The same constraints are always returned
     * for any given <code>Method</code> instance and for any equivalent
     * <code>Method</code> instance. Note that no exception is thrown for
     * "unknown" methods; a constraints instance is always returned.
     *
     * @param method the method
     * @return the constraints for the specified method as a
     * non-<code>null</code> value
     * @throws NullPointerException if the argument is <code>null</code>
     */
    InvocationConstraints getConstraints(Method method);

    /**
     * Returns an iterator that yields all of the possible distinct
     * constraints that can be returned by {@link #getConstraints
     * getConstraints}, in arbitrary order and with duplicates permitted. The
     * iterator throws an {@link UnsupportedOperationException} on any
     * attempt to remove an element.
     *
     * @return an iterator that yields all of the possible distinct
     * constraints that can be returned by <code>getConstraints</code>,
     * in arbitrary order and with duplicates permitted
     */
    Iterator possibleConstraints();
}
