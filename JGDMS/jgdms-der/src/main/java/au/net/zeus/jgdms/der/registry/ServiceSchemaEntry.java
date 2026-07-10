/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.registry;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Advertises a service's current schema via the DER codec (JGDMS-STD-006 S12.3).
 *
 * <p>In the full Jini integration (JGDMS-STD-005) this class would be a
 * {@code @SerialEntry} (an {@code Entry} in the service's attribute set). Here
 * it is implemented as an {@code @AtomicSerial} value class so that it round-trips
 * through the Phase-4 {@link au.net.zeus.jgdms.der.object.ObjectCodec DER codec}
 * without any Java-serialization dependency. The STD-005 {@code @SerialEntry} /
 * {@code Entry} integration is a follow-up task.
 *
 * <h2>Field type constraint (S12.1)</h2>
 * All fields are {@code primitive}, {@link String}, or {@code byte[]}. No
 * service-specific objects may appear. This constraint is enforced structurally
 * by the field declarations below and is verified in the unit tests (Task 7.1a).
 *
 * <h2>Semantics</h2>
 * <dl>
 *   <dt>{@code schemaDigest}</dt>
 *   <dd>SHA-256 digest of the leaf {@link au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord}
 *       for this service's current serial form. 32 bytes. Primary lookup key in the
 *       {@link SchemaRegistry}.</dd>
 *   <dt>{@code serviceInterface}</dt>
 *   <dd>Fully-qualified name of the service API interface; non-null, non-empty.</dd>
 *   <dt>{@code schemaVersion}</dt>
 *   <dd>Human-readable version label (optional; informational only; may be null).</dd>
 *   <dt>{@code schemaFormat}</dt>
 *   <dd>Format identifier; normally {@value #FORMAT_JGDMS_STD006_ATOMIC_DER}; non-null.</dd>
 * </dl>
 */
@AtomicSerial
public final class ServiceSchemaEntry {

    /** Canonical format identifier for JGDMS-STD-006 DER encoding. */
    public static final String FORMAT_JGDMS_STD006_ATOMIC_DER = "JGDMS-STD-006/ATOMIC-DER";

    // -------------------------------------------------------------------------
    // Serial form -- ALL fields are primitive, String, or byte[] (S12.1)
    // -------------------------------------------------------------------------

    /** Wire-ordered serial form. */
    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[]{
            new AtomicSerial.SerialForm("schemaDigest",    byte[].class),
            new AtomicSerial.SerialForm("serviceInterface", String.class),
            new AtomicSerial.SerialForm("schemaVersion",   String.class),
            new AtomicSerial.SerialForm("schemaFormat",    String.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): emit each serialForm() field by name. */
    public static void serialize(AtomicSerial.PutArg arg, ServiceSchemaEntry o) throws IOException {
        arg.put("schemaDigest",     o.schemaDigest);
        arg.put("serviceInterface", o.serviceInterface);
        arg.put("schemaVersion",    o.schemaVersion);
        arg.put("schemaFormat",     o.schemaFormat);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Fields (names MUST match serialForm wire names exactly)
    // -------------------------------------------------------------------------

    /** SHA-256 digest of the leaf AtomicSerialSchemaRecord; exactly 32 bytes. */
    private final byte[]  schemaDigest;

    /** Fully-qualified name of the service API interface. */
    private final String  serviceInterface;

    /**
     * Human-readable version label (optional; informational only; may be null).
     */
    private final String  schemaVersion;

    /** Format identifier; normally {@value #FORMAT_JGDMS_STD006_ATOMIC_DER}. */
    private final String  schemaFormat;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code ServiceSchemaEntry}.
     *
     * <p>{@code schemaVersion} is informational only. Because the DER codec cannot
     * encode null {@code String} values, a {@code null} argument is normalized to
     * the empty string {@code ""} (callers that want to omit a version label should
     * pass {@code null} or {@code ""}; both are stored as {@code ""}).
     *
     * @param schemaDigest     exactly 32-byte SHA-256 digest of the leaf schema record
     * @param serviceInterface fully-qualified name of the service API interface
     * @param schemaVersion    human-readable version label (may be null; stored as {@code ""})
     * @param schemaFormat     format identifier; normally {@value #FORMAT_JGDMS_STD006_ATOMIC_DER}
     * @throws NullPointerException     if {@code schemaDigest}, {@code serviceInterface},
     *                                  or {@code schemaFormat} is null
     * @throws IllegalArgumentException if {@code schemaDigest.length != 32} or
     *                                  {@code serviceInterface} is empty
     */
    public ServiceSchemaEntry(byte[] schemaDigest,
                               String serviceInterface,
                               String schemaVersion,
                               String schemaFormat) {
        Objects.requireNonNull(schemaDigest,     "schemaDigest");
        Objects.requireNonNull(serviceInterface, "serviceInterface");
        Objects.requireNonNull(schemaFormat,     "schemaFormat");
        if (schemaDigest.length != 32) {
            throw new IllegalArgumentException(
                    "schemaDigest must be exactly 32 bytes, got " + schemaDigest.length);
        }
        if (serviceInterface.isEmpty()) {
            throw new IllegalArgumentException("serviceInterface must not be empty");
        }
        this.schemaDigest     = schemaDigest.clone();
        this.serviceInterface = serviceInterface;
        // Normalize null to "" -- DER codec cannot encode null String values.
        this.schemaVersion    = (schemaVersion == null) ? "" : schemaVersion;
        this.schemaFormat     = schemaFormat;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor -- check FIRST, then assign
    // -------------------------------------------------------------------------

    /**
     * @AtomicSerial deserialization constructor.
     * Calls {@link #check(AtomicSerial.GetArg)} before any field assignment.
     */
    public ServiceSchemaEntry(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        byte[] d = (byte[]) arg.get("schemaDigest", null);
        this.schemaDigest     = (d == null) ? null : d.clone();
        this.serviceInterface = (String) arg.get("serviceInterface", null);
        String sv             = (String) arg.get("schemaVersion",   null);
        this.schemaVersion    = (sv == null) ? "" : sv;
        this.schemaFormat     = (String) arg.get("schemaFormat",    null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    /**
     * Validates invariants before any field assignment.
     *
     * <ul>
     *   <li>{@code schemaDigest} must be non-null and exactly 32 bytes.</li>
     *   <li>{@code serviceInterface} must be non-null and non-empty.</li>
     *   <li>{@code schemaFormat} must be non-null.</li>
     * </ul>
     *
     * @param arg the incoming {@code GetArg}
     * @return {@code arg} unchanged (fluent)
     * @throws InvalidObjectException if any constraint is violated
     */
    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        byte[] d = (byte[]) arg.get("schemaDigest", null);
        if (d == null) {
            throw new InvalidObjectException("ServiceSchemaEntry: schemaDigest must not be null");
        }
        if (d.length != 32) {
            throw new InvalidObjectException(
                    "ServiceSchemaEntry: schemaDigest must be exactly 32 bytes, got " + d.length);
        }
        String si = (String) arg.get("serviceInterface", null);
        if (si == null || si.isEmpty()) {
            throw new InvalidObjectException(
                    "ServiceSchemaEntry: serviceInterface must be non-null and non-empty");
        }
        String sf = (String) arg.get("schemaFormat", null);
        if (sf == null) {
            throw new InvalidObjectException("ServiceSchemaEntry: schemaFormat must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors (defensive copies of mutable state)
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the 32-byte schema digest. */
    public byte[] getSchemaDigest() {
        return (schemaDigest == null) ? null : schemaDigest.clone();
    }

    /** Returns the fully-qualified service interface name. */
    public String getServiceInterface() { return serviceInterface; }

    /** Returns the optional human-readable version label (may be null). */
    public String getSchemaVersion() { return schemaVersion; }

    /** Returns the format identifier. */
    public String getSchemaFormat() { return schemaFormat; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceSchemaEntry that)) return false;
        return Arrays.equals(schemaDigest, that.schemaDigest)
                && Objects.equals(serviceInterface, that.serviceInterface)
                && Objects.equals(schemaVersion, that.schemaVersion)
                && Objects.equals(schemaFormat, that.schemaFormat);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(schemaDigest);
        result = 31 * result + Objects.hashCode(serviceInterface);
        result = 31 * result + Objects.hashCode(schemaVersion);
        result = 31 * result + Objects.hashCode(schemaFormat);
        return result;
    }

    @Override
    public String toString() {
        return "ServiceSchemaEntry{"
                + "schemaDigest=[32 bytes], "
                + "serviceInterface='" + serviceInterface + "', "
                + "schemaVersion='" + schemaVersion + "', "
                + "schemaFormat='" + schemaFormat + "'}";
    }
}
