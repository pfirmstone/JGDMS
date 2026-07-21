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
package net.jini.space;

import java.io.*;

/**
 * This exception denotes a problem with the local implementation of the
 * <code>JavaSpace</code> interface.  The <code>detail</code> field
 * will give a description that can be reported to the space developer
 * (and may be documented in that space's external documentation).
 *
 * <h2>Serialization (JGDMS 4.0.0)</h2>
 * <p>
 * java.io (JOSS) serialization of this exception is <b>disabled</b>
 * ({@code writeObject}/{@code readObject}/{@code readObjectNoData} throw
 * {@link NotSerializableException}) &mdash; in <em>any</em> object graph,
 * including when this exception appears as a nested <em>cause</em> or
 * suppressed exception of another {@code Throwable} being java.io-serialized.
 * The {@code Serializable} signature and the public {@link #nestedException}
 * field are retained for API compatibility only.
 * <p>
 * Over the atomic marshalling layers the exception still travels exactly as
 * before: {@code org.apache.river.api.io.AtomicMarshalOutputStream} substitutes
 * every non-{@code @AtomicSerial} {@code Throwable} with
 * {@code org.apache.river.api.io.ThrowableSerializer} <em>before</em> the
 * object itself is serialized, so the fence methods below are never invoked on
 * that path and the wire form is unchanged. This class is deliberately
 * <b>not</b> annotated {@code @AtomicSerial}: the runtime class's annotation
 * takes precedence over the {@code ThrowableSerializer} substitution in both
 * atomic layers ({@code AtomicMarshalOutputStream.defaultReplaceObject} and the
 * DER codec's {@code DerReplacer.replace} check the annotation first), so
 * annotating it would silently change the existing wire form
 * (SOW-Outrigger-DER-Only-JOSS-Rejection &sect;2.2 board constraint).
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpace
 */
public class InternalSpaceException extends RuntimeException {
    static final long serialVersionUID = -4167507833172939849L;


    /**
     * The exception (if any) that triggered the internal exception.  This
     * may be <code>null</code>.
     *
     * @serial
     */
    public final Throwable nestedException;

    /**
     * Create an exception, forwarding a string to the superclass constructor.
     * @param str  a detail message
     */
    public InternalSpaceException(String str) {
	super(str);
	nestedException = null;
    }

    /**
     * Create an exception, forwarding a string and exception to the
     * superclass constructor.
     * 
     * @param str  a detail message
     * @param ex a nested exception
     */
    public InternalSpaceException(String str, Throwable ex) {
	super(str, ex);
	nestedException = ex;
    }

    /**
     * Print the stack trace of this exception, plus that of the nested
     * exception, if any.
     */
    public void printStackTrace() {
	printStackTrace(System.err);
    }

    /**
     * Print the stack trace of this exception, plus that of the nested
     * exception, if any.
     */
    @Override
    public void printStackTrace(PrintStream out) {
	super.printStackTrace(out);
	if (nestedException != null) {
	    out.println("nested exception:");
	    nestedException.printStackTrace(out);
	}
    }

    /**
     * Print the stack trace of this exception, plus that of the nested
     * exception, if any.
     */
    @Override
    public void printStackTrace(PrintWriter out) {
	super.printStackTrace(out);
	if (nestedException != null) {
	    out.println("nested exception:");
	    nestedException.printStackTrace(out);
	}
    }

    /**
     * @throws NotSerializableException always -- java.io serialization is
     * disabled; over the atomic marshalling layers this exception is
     * substituted with {@code ThrowableSerializer} before serialization, so
     * this method is never reached on those paths.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	throw new NotSerializableException(
	    "java.io serialization is disabled for " + getClass().getName()
	    + "; atomic marshalling substitutes ThrowableSerializer");
    }

    /**
     * java.io deserialization is disabled; inbound atomic streams reconstruct
     * this exception via {@code ThrowableSerializer}'s constructor-matching
     * reconstruction ({@code InternalSpaceException(String, Throwable)}).
     *
     * @throws NotSerializableException always
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; atomic marshalling substitutes ThrowableSerializer");
    }

    private void readObjectNoData() throws ObjectStreamException {
	throw new NotSerializableException(
	    "java.io deserialization is disabled for " + getClass().getName()
	    + "; atomic marshalling substitutes ThrowableSerializer");
    }
}
