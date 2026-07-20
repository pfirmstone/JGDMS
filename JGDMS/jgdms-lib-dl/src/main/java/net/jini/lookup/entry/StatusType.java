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
package net.jini.lookup.entry;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Resolve;

/**
 * Information pertaining to the severity of a Status object.
 *
 * <p>{@code StatusType} is a pre-{@code enum} typesafe-enum value class with exactly
 * four canonical instances ({@link #ERROR}, {@link #WARNING}, {@link #NOTICE},
 * {@link #NORMAL}). It is retrofitted {@link AtomicSerial} so a {@code Status} entry's
 * {@code severity} field can travel on the {@code ATOMIC_DER} wire (a plain
 * non-{@code @AtomicSerial} {@code Serializable} field is rejected by
 * {@code SchemaGenerator.toWireType}). The retrofit is purely additive: the pre-existing
 * default-field JOSS serialization (governed by {@code serialVersionUID} below) is
 * untouched, so an already-serialized {@code StatusType}/{@code Status} still
 * deserializes unchanged. Canonical-instance ({@code ==}) identity is preserved on both
 * paths by {@link #readResolve()} -- called automatically by {@code java.io} for the
 * JOSS path, and by the DER codec (via {@link Resolve}) for the {@code @AtomicSerial}
 * path.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see Status
 */
@AtomicSerial
public class StatusType implements Serializable, Resolve {
    private static final long serialVersionUID = -8268735508512712203L;

    /** @serial */
    private final int type;

    private StatusType(int t) {
	type = t;
    }

    /**
     * An error has occurred that is preventing a service from
     * operating correctly.
     */
    public static final StatusType ERROR = new StatusType(1);

    /**
     * A problem merits attention, but is not impeding the operation
     * of the service.
     */
    public static final StatusType WARNING = new StatusType(2);

    /**
     * An unusual event is occurring, but the event is not necessarily
     * a problem.
     */
    public static final StatusType NOTICE = new StatusType(3);

    /**
     * A noteworthy event is occurring during the normal operation of
     * a service.
     */
    public static final StatusType NORMAL = new StatusType(4);

    /**
     * Defines the {@link AtomicSerial} serial form: a single {@code type} int field,
     * matching the pre-existing default JOSS field layout.
     *
     * @return the serial form of this class
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm("type", int.class)
        };
    }

    /**
     * Writes the serial form of the given {@code StatusType}.
     *
     * @param arg the AtomicSerial output argument
     * @param st  the StatusType to serialize
     * @throws IOException if there are I/O errors while writing to the stream
     */
    public static void serialize(PutArg arg, StatusType st) throws IOException {
        arg.put("type", st.type);
        arg.writeArgs();
    }

    /**
     * Validates the wire {@code type} value atomically, before construction, against
     * the closed set of canonical severity values -- so an invalid stream cannot yield
     * an instance (and hence a reference cannot be stolen).
     */
    private static int check(GetArg arg) throws IOException {
        int t = arg.get("type", 0);
        if (t == ERROR.type || t == WARNING.type || t == NOTICE.type || t == NORMAL.type) {
            return t;
        }
        throw new InvalidObjectException("StatusType: illegal type value " + t);
    }

    /**
     * {@link AtomicSerial} constructor, used by the {@code ATOMIC_DER} decode path.
     * The resulting instance is never observed by a caller: {@link #readResolve()} (via
     * {@link Resolve}) immediately substitutes the canonical instance for {@code type}.
     *
     * @param arg the AtomicSerial deserialization argument
     * @throws IOException if there are I/O errors while reading, or
     *                      {@link InvalidObjectException} if {@code type} is not one of
     *                      the four canonical severity values
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public StatusType(GetArg arg) throws IOException, ClassNotFoundException {
        this(check(arg));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	if (type == ERROR.type) {
	    return "StatusType.ERROR";
	} else if (type == WARNING.type) {
	    return "StatusType.WARNING";
	} else if (type == NOTICE.type) {
	    return "StatusType.NOTICE";
	} else if (type == NORMAL.type) {
	    return "StatusType.NORMAL";
	} else {
	    return "StatusType." + type; // not possible ...
	}
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     *
     * <p>Called automatically by {@code java.io} on the JOSS path, and by the DER codec
     * (via {@link Resolve}, since {@code StatusType} is {@link AtomicSerial}) on the
     * {@code ATOMIC_DER} path -- so canonical-instance identity survives both.
     */
    @Override
    public Object readResolve() {
	if (type == ERROR.type) {
	    return ERROR;
	} else if (type == WARNING.type) {
	    return WARNING;
	} else if (type == NOTICE.type) {
	    return NOTICE;
	} else if (type == NORMAL.type) {
	    return NORMAL;
	} else {
	    throw new IllegalArgumentException("illegal type");
	}
    }
}
