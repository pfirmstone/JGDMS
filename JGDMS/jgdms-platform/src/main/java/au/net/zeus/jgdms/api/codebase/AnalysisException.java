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

import java.io.NotSerializableException;

/**
 * Thrown by {@link BytecodeAnalysisEngine#analyzeJar} when a non-transient
 * analysis failure occurs.
 *
 * <p>Examples of conditions that cause this exception:
 * <ul>
 *   <li>The {@link AnalysisRequest#getJarBytes()} content cannot be read as a
 *       valid ZIP/JAR stream.</li>
 *   <li>A mandatory analysis component (e.g. the ASM bytecode parser) reports
 *       a fatal internal error.</li>
 *   <li>The engine's private key cannot be used to sign the resulting
 *       {@link JarAnalysisReport}.</li>
 * </ul>
 *
 * <p>This exception is distinct from {@link java.rmi.RemoteException}, which
 * covers transport-level failures.  If an {@code AnalysisException} is thrown,
 * the caller should treat the JAR as {@link VerdictType#INCONCLUSIVE} and
 * log the error.  It should <em>not</em> be retried without first investigating
 * the root cause, because the same bytes will likely fail on every attempt.
 *
 * @see BytecodeAnalysisEngine#analyzeJar
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class AnalysisException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an {@code AnalysisException} with the specified detail message.
     *
     * @param message the detail message; may be {@code null}
     */
    public AnalysisException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code AnalysisException} with the specified detail message
     * and cause.
     *
     * @param message the detail message; may be {@code null}
     * @param cause   the underlying cause; may be {@code null}
     */
    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * java.io deserialization is disabled; reconstruct via {@code @AtomicSerial}
     * (the {@link #RemoteEvent(GetArg)} constructor) instead. Poisoning this
     * superclass hook also blocks JOSS reconstruction of every subclass, since
     * java.io invokes the superclass {@code readObject} first.
     *
     * @throws NotSerializableException always
     */
    private void readObject(java.io.ObjectInputStream in)
	throws java.io.IOException, ClassNotFoundException
    {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (GetArg)");
    }

    /**
     * @throws NotSerializableException always
     */
    private void readObjectNoData() throws java.io.ObjectStreamException {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (GetArg)");
    }

    /**
     * java.io serialization is disabled; this class is marshalled via its
     * {@code @AtomicSerial} form ({@link #serialize}) instead.
     *
     * @throws NotSerializableException always
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException
    {
	throw new NotSerializableException(
	    "java.io serialization is disabled for " + getClass().getName()
	    + "; use @AtomicSerial (PutArg)");
    }
}
