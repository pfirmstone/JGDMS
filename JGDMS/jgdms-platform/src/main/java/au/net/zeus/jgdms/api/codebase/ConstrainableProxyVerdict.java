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
package au.net.zeus.jgdms.api.codebase;

/**
 * Result of the constrainable-smart-proxy contract analysis performed by a
 * {@link BytecodeAnalysisEngine} on a single class.
 *
 * <p>A JGDMS service is always exported over JERI, whose invocation-layer
 * factory always makes the exported stub a
 * {@link net.jini.core.constraint.RemoteMethodControl}.  A downloaded smart
 * proxy (a concrete subclass of
 * {@code au.net.zeus.jgdms.proxy.AbstractSmartProxy}, or any class implementing
 * {@code RemoteMethodControl}) is therefore expected to be <em>constrainable</em>
 * and to honour the {@code RemoteMethodControl} contract: {@code setConstraints}
 * must return a <em>new</em> instance of the proxy with the requested constraints
 * applied, never {@code this} and never {@code null}.  A proxy that silently
 * ignores {@code setConstraints} lets a client believe it has hardened a call
 * (e.g. required {@code Confidentiality}) while the proxy quietly drops the
 * requirement — a downgrade attack the compiler cannot catch in third-party
 * bytecode.
 *
 * @see ClassAnalysisResult
 * @see AtomicSerialVerdict
 * @since 3.1.1
 * @author Peter Firmstone
 */
public enum ConstrainableProxyVerdict {

    /**
     * The class is a constrainable smart proxy that honours the contract: it
     * reaches {@code RemoteMethodControl}, declares {@code setConstraints}, and
     * that method constructs and returns a new instance with the constraints
     * applied.
     */
    COMPLIANT,

    /**
     * The class is a concrete smart proxy (a subclass of
     * {@code AbstractSmartProxy}) that is <em>not</em> constrainable — it does
     * not reach {@code RemoteMethodControl}, so a client cannot impose
     * {@code Integrity}/{@code ServerAuthentication}/{@code Confidentiality} on
     * its calls and it cannot participate in proxy-trust verification.
     */
    NON_CONSTRAINABLE,

    /**
     * The class is constrainable (implements {@code RemoteMethodControl}) but
     * neither it nor any of its in-scope superclasses declares a concrete
     * {@code setConstraints(MethodConstraints)} method.
     */
    SETCONSTRAINTS_MISSING,

    /**
     * The class is constrainable and declares {@code setConstraints}, but that
     * method does not construct and return a new proxy instance — it returns
     * {@code this} or {@code null}, or otherwise fails to apply the requested
     * constraints.  This is a silent-downgrade hazard.
     */
    CONSTRAINTS_NOT_APPLIED,

    /**
     * The class bytes could not be parsed.  Reported fail-secure so the codebase
     * is refused rather than trusted by default.
     */
    UNREADABLE,

    /**
     * The class is not a smart proxy (it neither subclasses
     * {@code AbstractSmartProxy} nor implements {@code RemoteMethodControl}), so
     * the constrainable-proxy contract does not apply.
     */
    NA
}
