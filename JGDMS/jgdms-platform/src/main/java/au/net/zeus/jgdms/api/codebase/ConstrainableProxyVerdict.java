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
     * The class is a concrete smart proxy that is <em>not</em> constrainable —
     * it does not reach {@code RemoteMethodControl}, so a client cannot impose
     * {@code Integrity}/{@code ServerAuthentication}/{@code Confidentiality} on
     * its calls and it cannot participate in proxy-trust verification.
     *
     * <p>The existence of a constrainable sibling or subclass does not clear this
     * verdict: which concrete proxy a factory returns is a runtime decision on
     * the server reference's type ({@code server instanceof RemoteMethodControl}),
     * which static analysis cannot resolve — this concrete class can still be the
     * instance on the wire, a live constraint-downgrade path.
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
     * The class is an in-scope smart proxy (it crosses the wire and holds a
     * remote reference) that implements {@code java.io.Serializable} <em>at
     * all</em> — whether or not it is also {@code @AtomicSerial}.
     *
     * <p>Plain Java serialization is the insecure deserialization path JGDMS
     * uncouples from, and it is flagged outright: if the class is {@code
     * @AtomicSerial} as well, the {@code java.io} path
     * ({@code readObject}/default deserialization) is an unvalidated backdoor
     * that bypasses the validating {@code (GetArg)} constructor; if it is not,
     * there is no validation at all.  Either way {@code @AtomicSerial} must be
     * the sole wire path, so {@code java.io.Serializable} is discouraged entirely
     * on smart proxies.
     */
    JAVA_SERIALIZATION,

    /**
     * The class bytes could not be parsed.  Reported fail-secure so the codebase
     * is refused rather than trusted by default.
     */
    UNREADABLE,

    /**
     * The constrainable-proxy contract does not apply to this class.  This
     * covers: an interface; an <em>abstract</em> class (never the runtime type
     * of a deserialized wire instance — its concrete leaves carry their own
     * verdicts); a class that does not cross the wire (neither
     * {@code @AtomicSerial}/{@code @Stateless} nor {@code Serializable}); and a
     * connection-less value object that crosses the wire but holds no remote
     * reference (no {@code Remote}-typed field, no {@code ProxyAccessor}, does
     * not extend {@code AbstractSmartProxy}) — just deserialized data, not a
     * smart proxy.
     */
    NA
}
