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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Represents a constraint on the wire marshalling format used to encode objects
 * transmitted as part of a remote call, and to encode
 * {@link net.jini.io.MarshalledInstance} hand-back objects. The format is named
 * by the same self-describing {@code payloadFormat} identifier that
 * {@code MarshalledInstance} carries as first-class state (JGDMS-STD-008 sec.13):
 * for example {@link #ATOMIC_DER} selects the JGDMS-STD-006/ATOMIC-DER canonical wire format and
 * {@link #JOSS} selects legacy Java Object Serialization.
 *
 * <p>Requiring {@link #ATOMIC_DER} forces use of the schema-bearing, data-independent DER
 * codec and thereby excludes Java Object Serialization, removing the
 * deserialization attack surface that each Java-Serialization permission grant
 * represents -- the motivation for JGDMS 4.0.0. A requirement for one format and
 * a requirement for a different format on the same call cannot both be satisfied
 * (the requirements conflict), exactly as for any other pair of mutually exclusive
 * {@link InvocationConstraint}s.
 *
 * <p>Unlike {@link AtomicInputValidation} (a binary YES/NO property), a marshalling
 * format is an open value -- further formats (e.g. CBOR) may be defined -- so this
 * constraint carries the format identifier rather than being an enum. Two instances
 * with the same {@linkplain #getFormat() format} are {@linkplain #equals(Object)
 * equal}; well-known instances are provided as constants.
 *
 * <p>This constraint is serializable; serialization uses {@link AtomicSerial} and
 * preserves the format identifier.
 *
 * @since 4.0
 */
@AtomicSerial
public final class MarshallingFormat implements InvocationConstraint, Serializable {

    private static final long serialVersionUID = 5839852455936216357L;

    /**
     * Requires the JGDMS-STD-006/ATOMIC-DER canonical wire format. The identifier matches
     * {@code MarshalledInstanceRecord.PAYLOAD_FORMAT} and the {@code payloadFormat()}
     * of the DER {@code MarshalFactoryProvider} (in the {@code jgdms-der} module);
     * it is a stable wire constant and is therefore inlined here to avoid a module
     * dependency from the platform on the codec.
     */
    public static final MarshallingFormat ATOMIC_DER =
	    new MarshallingFormat("JGDMS-STD-006/ATOMIC-DER");

    /**
     * Requires legacy Java Object Serialization. The identifier is the stable wire
     * constant {@code "JOSS"}; it matches {@link net.jini.io.MarshalledInstance#FORMAT_JOSS}
     * (inlined here so the constraint package does not depend on {@code net.jini.io};
     * {@code MarshallingFormatTest} asserts the two agree).
     */
    public static final MarshallingFormat JOSS =
	    new MarshallingFormat("JOSS");

    /**
     * Argument names and types for {@link AtomicSerial}.
     * @return serial arguments.
     */
    public static SerialForm[] serialForm(){
	return new SerialForm[]{
	    new SerialForm("format", String.class)
	};
    }

    /**
     * Provides access to internal state for {@link AtomicSerial}.
     * @param arg arguments to populate with serial arguments.
     * @param c the object to serialize.
     * @throws IOException if a problem occurs writing the arguments.
     */
    public static void serialize(PutArg arg, MarshallingFormat c) throws IOException {
	arg.put("format", c.format);
	arg.writeArgs();
    }

    /**
     * The {@code payloadFormat} identifier of the required marshalling format.
     *
     * @serial
     */
    private final String format;

    /**
     * Creates a constraint requiring the marshalling format with the given
     * {@code payloadFormat} identifier.
     *
     * @param format the format identifier (e.g. {@code "JGDMS-STD-006/ATOMIC-DER"}).
     * @throws NullPointerException if {@code format} is {@code null}.
     * @throws IllegalArgumentException if {@code format} is empty.
     */
    public MarshallingFormat(String format){
	if (format == null) throw new NullPointerException("format cannot be null");
	if (format.isEmpty()) throw new IllegalArgumentException("format cannot be empty");
	this.format = format;
    }

    /**
     * {@link AtomicSerial} constructor. Validates the format identifier before the
     * superclass (Object) constructor completes, so an invalid instance can never
     * be created from a hostile stream.
     *
     * @param arg the deserialized arguments.
     * @throws IOException including {@link InvalidObjectException} if the format is
     *         absent or empty.
     * @throws ClassNotFoundException if a class cannot be resolved.
     */
    public MarshallingFormat(GetArg arg) throws IOException, ClassNotFoundException {
	this(validate(arg.get("format", null, String.class)));
    }

    private static String validate(String format) throws InvalidObjectException {
	if (format == null) throw new InvalidObjectException("format cannot be null");
	if (format.isEmpty()) throw new InvalidObjectException("format cannot be empty");
	return format;
    }

    /**
     * Returns the {@code payloadFormat} identifier of the required marshalling format.
     *
     * @return the format identifier; never {@code null}.
     */
    public String getFormat(){
	return format;
    }

    @Override
    public int hashCode(){
	return MarshallingFormat.class.hashCode() + format.hashCode();
    }

    /**
     * Two instances of this class are equal if both require the same format.
     */
    @Override
    public boolean equals(Object obj){
	if (this == obj) return true;
	if (!(obj instanceof MarshallingFormat)) return false;
	return format.equals(((MarshallingFormat) obj).format);
    }

    @Override
    public String toString(){
	return "MarshallingFormat[" + format + "]";
    }
}
