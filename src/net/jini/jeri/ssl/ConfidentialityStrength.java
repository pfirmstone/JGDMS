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

package net.jini.jeri.ssl;

import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.InvocationConstraint;
import java.io.Serializable;

/**
 * Represents a constraint that, if confidentiality of message contents is
 * ensured, the specified strength of confidentiality be used. <p>
 *
 * The use of an instance of this constraint does not directly imply a {@link
 * Confidentiality#YES} constraint; that must be specified separately to ensure
 * that confidentiality is actually ensured. <p>
 *
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>. <p>
 *
 * This constraint is supported by the endpoints defined in this package. <p>
 *
 * The {@link SslTrustVerifier} trust verifier may be used for establishing
 * trust in remote proxies that use instances of this class.
 *
 * 
 * @see SslEndpoint
 * @see SslServerEndpoint
 * @see HttpsEndpoint
 * @see HttpsServerEndpoint
 * @see SslTrustVerifier
 * @since 2.0
 */
public final class ConfidentialityStrength
    implements InvocationConstraint, Serializable
{
    /* -- Fields -- */

    private static final long serialVersionUID = -5413316999614306469L;

    /**
     * If confidentiality of message contents is ensured, then use strong
     * confidentiality for message contents. <p>
     *
     * For the endpoints in this package, this constraint is supported by
     * cipher suites with the following cipher algorithms:
     *
     * <ul>
     * <li> 3DES_EDE_CBC
     * <li> AES_128_CBC
     * <li> AES_256_CBC
     * <li> IDEA_CBC
     * <li> RC4_128
     * </ul>
     */
    public static final ConfidentialityStrength STRONG =
	new ConfidentialityStrength(true);

    /**
     * If confidentiality of message contents is ensured, then use weak
     * confidentiality for message contents. <p>
     *
     * For the endpoints in this package, this constraint is supported by
     * cipher suites with the following cipher algorithms:
     *
     * <ul>
     * <li> DES40_CBC
     * <li> DES_CBC
     * <li> RC2_CBC_40
     * <li> RC4_40
     * </ul>
     */
    public static final ConfidentialityStrength WEAK =
	new ConfidentialityStrength(false);

    /**
     * <code>true</code> for <code>STRONG</code>, <code>false</code> for
     * <code>WEAK</code>
     *
     * @serial
     */
    private final boolean value;

    /* -- Methods -- */

    /**
     * Simple constructor.
     *
     * @param value <code>true</code> for <code>STRONG</code>, <code>false</code>
     *	      for <code>WEAK</code>
     */
    private ConfidentialityStrength(boolean value) {
	this.value = value;
    }

    /** Returns a string representation of this object. */
    public String toString() {
	return value
	    ? "ConfidentialityStrength.STRONG"
	    : "ConfidentialityStrength.WEAK"; 
    }

    /** Canonicalize so that <code>==</code> can be used. */
    private Object readResolve() {
	return value ? STRONG : WEAK;
    }
}
