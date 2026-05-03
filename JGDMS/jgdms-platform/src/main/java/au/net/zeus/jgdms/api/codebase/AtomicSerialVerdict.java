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
     * The class is either not {@code Serializable}, or is annotated
     * {@code @Stateless}, so {@code @AtomicSerial} compliance analysis is
     * not applicable.
     */
    NA
}
