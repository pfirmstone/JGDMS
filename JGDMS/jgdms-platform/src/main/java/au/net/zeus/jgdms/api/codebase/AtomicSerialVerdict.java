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
 * Result of the {@link org.apache.river.api.io.AtomicSerial} compliance
 * analysis performed by a {@link BytecodeAnalysisEngine} on a single class.
 *
 * <p>{@code @AtomicSerial} is JGDMS's safe-serialization protocol.  The BAE
 * checks that every class in a JAR that implements {@code Serializable} (or is
 * annotated {@code @AtomicSerial}) conforms to the protocol.
 *
 * @see ClassAnalysisResult
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public enum AtomicSerialVerdict {

    /**
     * The class is fully compliant with the {@code @AtomicSerial} protocol:
     * the {@code @AtomicSerial} annotation is present, the
     * {@code (GetArg)} constructor exists, validation is performed before
     * the super-constructor call, {@code serialForm()} is present, and
     * {@code serialize(PutArg, T)} is present.
     *
     * <p><b>What this verdict does and does not establish.</b>
     * {@code COMPLIANT} means the class passed a bytecode-level shape and
     * data-dependency check — it does <em>not</em> mean the check/validation
     * logic was proven to actually validate anything.  The analysis is a
     * lightweight, single-pass, non-recursive scan (see
     * {@code au.net.zeus.jgdms.bae.AtomicSerialComplianceVisitor} in the
     * bytecode-analysis-engine module): it confirms that a value derived
     * from reading the deserialization argument reaches an observable
     * outcome (a return, a branch, a cast, or a call this codebase could
     * plausibly have authored) before construction completes, but it does
     * not recursively verify what a called method does with that value.  A
     * downloaded proxy that ships its own same-package, same-jar helper
     * method which never reads its own parameter (a no-op "validator" that
     * exists purely to satisfy this shape check) currently still earns
     * {@code COMPLIANT} — the same top-trust bucket as a class with genuine
     * validation logic — because distinguishing the two would require
     * recursively verifying the callee's own body, which this pass-level
     * analysis does not do.  Closing that gap (bounded recursive
     * callee-body verification) is tracked as a separate, not-yet-built
     * follow-on to this check; callers that need a stronger guarantee than
     * "shape and dependency checked" should not treat {@code COMPLIANT} as
     * a proof of correct validation.
     */
    COMPLIANT,

    /**
     * The class has a {@code (GetArg)} constructor but is <em>not</em>
     * annotated with {@code @AtomicSerial}.  This is unusual and may indicate
     * incomplete migration or a programming error.
     */
    NOT_ANNOTATED,

    /**
     * The class is annotated {@code @AtomicSerial} but lacks the required
     * {@code (GetArg)} deserialization constructor.
     */
    MISSING_CONSTRUCTOR,

    /**
     * The class has the {@code (GetArg)} constructor but the static
     * validation call does not precede the {@code super(...)} invocation.
     * This violates the protocol invariant that deserialized state is checked
     * before the superclass constructor can observe it.
     */
    VALIDATION_ORDER,

    /**
     * The class is annotated {@code @AtomicSerial} but lacks the required
     * {@code public static SerialForm[] serialForm()} method, and is not
     * annotated {@code @Stateless}.
     */
    MISSING_SERIAL_FORM,

    /**
     * The class is annotated {@code @AtomicSerial} but lacks the required
     * {@code public static void serialize(PutArg, T)} method (the encode-side
     * counterpart of the {@code (GetArg)} constructor), and is not annotated
     * {@code @Stateless}.  {@link #COMPLIANT} requires both {@code
     * serialForm()} <em>and</em> {@code serialize(PutArg, T)} to be present;
     * a class with a validated {@code (GetArg)} constructor but no {@code
     * serialize} method can still be constructed from an untrusted stream
     * without ever being safely re-encoded, which is not itself a
     * deserialization hazard but is a protocol-completeness gap this verdict
     * makes visible.
     */
    MISSING_SERIALIZE,

    /**
     * The class has the {@code (GetArg)} constructor and a static validation
     * method, but that validation method retrieves at least one object-type
     * field from {@code GetArg} using the 2-argument
     * {@code get(String, Object)} form and uses the result directly in a
     * null-check ({@code IFNULL}/{@code IFNONNULL}) <em>without</em> a
     * preceding {@code CHECKCAST}.
     *
     * <p>This means the type of the deserialized object is never verified
     * inside the static check method.  Any {@code ClassCastException} is
     * therefore deferred to the bridge constructor, which fires <em>during</em>
     * object construction rather than safely before it.  An adversary who can
     * supply a manipulated byte stream can trigger a CCE inside a partially
     * constructed object.
     *
     * <p>The correct pattern is to use the typed 3-argument form
     * {@code arg.get(name, null, MyType.class)} in the static check method,
     * or to immediately cast the result: {@code (MyType) arg.get(name, null)}.
     */
    UNTYPED_GET,

    /**
     * The class is either not {@code Serializable}, or is annotated
     * {@code @Stateless}, so {@code @AtomicSerial} compliance analysis is
     * not applicable.
     */
    NA
}
