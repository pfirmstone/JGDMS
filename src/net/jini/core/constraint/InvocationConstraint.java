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

/**
 * The marker interface used to identify constraints for method invocations.
 * Constraints are immutable and should be serializable.
 * <p>
 * An instance of this interface must implement {@link Object#equals
 * Object.equals} to return <code>true</code> when passed a constraint that is
 * equivalent in trust, content, and function, and to return <code>false</code>
 * otherwise. That is, the <code>equals</code> method must be a sufficient
 * substitute for
 * {@link net.jini.security.proxytrust.TrustEquivalence#checkTrustEquivalence
 * TrustEquivalence.checkTrustEquivalence}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface InvocationConstraint {
}
