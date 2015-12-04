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

/**
 * Defines an interface for checking that an object (that is not yet known to
 * be trusted) is equivalent in trust, content, and function to a known
 * trusted object. This interface can be useful in implementing a
 * {@link net.jini.security.TrustVerifier} returned by
 * {@link ProxyTrust#getProxyVerifier ProxyTrust.getProxyVerifier}.
 * <p>
 * An implementation of this interface must always compare components of the
 * two objects by invoking comparison methods (such as <code>equals</code>
 * or {@link #checkTrustEquivalence checkTrustEquivalence}) on components of
 * the known trusted object, and those comparison methods must in turn
 * compare subcomponents in the same fashion, always invoking comparison
 * methods on components of <code>this</code>.
 * <p>
 * For example, in the case of a smart proxy wrapped around an inner proxy,
 * where the inner proxy results from exporting a remote object, the inner
 * proxy could be required to be an instance of a class that implements this
 * interface. The verifier could contain a canonical instance of the inner
 * proxy, and use its <code>checkTrustEquivalence</code> method to check
 * that a candidate smart proxy contains the correct inner proxy.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface TrustEquivalence {
    /**
     * Returns <code>true</code> if the specified object (that is not yet
     * known to be trusted) is equivalent in trust, content, and function to
     * this known trusted object, and returns <code>false</code> otherwise.
     * This method is permitted to delete content of the specified object
     * if that content is cached state that might be stale (relative to the
     * known trusted object's cached state), cannot otherwise be checked,
     * and will be recreated on demand (from content that has been checked)
     * during subsequent operations on the object.
     *
     * @param obj object to check that is not yet known to be trusted
     * @return <code>true</code> if the specified object (that is not yet
     * known to be trusted) is equivalent in trust, content, and function to
     * this known trusted object, and returns <code>false</code> otherwise
     */
    boolean checkTrustEquivalence(Object obj);
}
