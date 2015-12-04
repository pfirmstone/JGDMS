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

import java.io.Serializable;

/**
 * Represents a constraint on the integrity of message contents, covering not
 * only data transmitted in band as part of the remote call itself, but also
 * out-of-band downloaded code. If an integrity violation on in-band data is
 * detected during a remote call, a {@link java.rmi.RemoteException} will be
 * thrown (in the client or in the server, depending on which side detected
 * the violation). If an integrity violation on out-of-band data is detected,
 * an {@link java.io.IOException} will be thrown at the point where the data
 * is downloaded.
 * <p>
 * Although most of the data for a remote call is transmitted in band as part
 * of the call itself, code is downloaded out of band, based on codebase URLs
 * that are transmitted in band. For a remote call to have integrity, the
 * out-of-band code as well as the in-band data must have integrity. A proxy
 * implementation that provides for integrity must ensure the integrity of
 * both code and data.
 * <p>
 * Code signing is difficult to use for this purpose if the classes span more
 * than a single package (because individual files are signed rather than the
 * entire JAR file being signed, and the only automatic enforcement is that
 * classes in a single package all have the same signers), or if the code
 * references bundled resources (because there is no way to determine the
 * signers of a resource). A better technique is to use codebase URLs that
 * provide content integrity, such as
 * <a href=../../url/httpmd/package-summary.html#package_description">HTTPMD</a>
 * or HTTPS URLs. If integrity-protecting codebase URLs are used, and the URLs
 * themselves are sent as part of the integrity-protected in-band data, the
 * result is complete object integrity. Because out-of-band communication is
 * used, integrity-protecting URLs must either contain sufficient information
 * to independently verify integrity (as is the case with HTTPMD URLs), or
 * must contain sufficient information to authenticate the origin of the
 * content and use sufficient means to maintain content integrity in transit
 * (as is the case with HTTPS URLs).
 * <p>
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see net.jini.security.Security#verifyCodebaseIntegrity
 * Security.verifyCodebaseIntegrity
 * @since 2.0
 */
public final class Integrity implements InvocationConstraint, Serializable {
    private static final long serialVersionUID = 418483423937969897L;

    /**
     * Detect when message contents (both requests and replies) have been
     * altered by third parties, and if detected, refuse to process the
     * message and throw an exception. The mechanisms used to maintain
     * integrity are not specified by this constraint.
     */
    public static final Integrity YES = new Integrity(true);
    /**
     * Do not detect when message contents have been altered by third parties.
     * Normally this constraint should not be used, as many secure
     * communication mechanisms have integrity mechanisms that cannot be
     * disabled.
     */
    public static final Integrity NO = new Integrity(false);

    /**
     * <code>true</code> for <code>YES</code>, <code>false</code> for
     * <code>NO</code>
     *
     * @serial
     */
    private final boolean val;

    /**
     * Simple constructor.
     *
     * @param val <code>true</code> for <code>YES</code>, <code>false</code>
     * for <code>NO</code>
     */
    private Integrity(boolean val) {
	this.val = val;
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return val ? "Integrity.YES" : "Integrity.NO";
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     */
    private Object readResolve() {
	return val ? YES : NO;
    }
}
