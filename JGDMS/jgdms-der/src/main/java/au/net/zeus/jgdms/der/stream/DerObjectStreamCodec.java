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

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.DerProxySerializer;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.ProxyWireSupport;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import java.util.Arrays;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.io.context.DeserializationCompletion;
import net.jini.security.Security;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.DeSerializationPermission;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Engine for DER object-stream encoding and decoding (JGDMS-STD-008 sec.15.5,
 * Increment 1).
 *
 * <h2>Context-tag scheme for self-describing object items</h2>
 * <p>
 * Each object written through {@link #writeObject} / read through
 * {@link #readObject} is a context-specific TLV whose tag number encodes the
 * item kind:
 * <pre>
 *   [0] PRIMITIVE   -- NULL reference (length 0)
 *   [1] CONSTRUCTED -- @AtomicSerial object; content = MarshalledInstanceRecord DER SEQUENCE
 *   [3] PRIMITIVE   -- java.lang.String; content = UTF8String value bytes
 *   [5] PRIMITIVE   -- byte[]; content = OCTET STRING value bytes
 *   [7] CONSTRUCTED -- enum constant; content = UTF8String(declaringClassName) ++ UTF8String(constantName)
 *   [8] CONSTRUCTED -- bare java.lang.reflect.Proxy; content = INTEGER ifaceCount ++ ifaceName(UTF8String)* ++ [1] @AtomicSerial InvocationHandler
 * </pre>
 * Context class = 0x80. Constructed bit = 0x20 set for [1], [7], [8]. Single-byte tags:
 * [0]->0x80, [1]->0xa1, [3]->0x83, [5]->0x85, [7]->0xa7, [8]->0xa8.
 * (A {@code java.lang.reflect.Proxy} whose interfaces + {@code @AtomicSerial} handler are
 * locally resolvable is transmitted bare as [8]; one that needs a codebase download is
 * instead substituted by a {@code ProxySerializer} and rides the [1] path, per STD-008 sec.15.2.
 * Interface names in a [8] item are resolved TOLERANTLY: a name that fails to resolve locally is
 * dropped rather than failing the whole item, provided at least one interface still resolves --
 * see {@link au.net.zeus.jgdms.der.object.ProxyWireSupport} and
 * {@link au.net.zeus.jgdms.der.object.BoomerangProxyHandler}, which also preserve the full
 * original wire bytes for a byte-for-byte-faithful later re-forward of a narrowed proxy.)
 *
 * <h2>No handle table -- pure value-tree, deterministic (STD-008 sec.15.3)</h2>
 * <p>
 * There is NO handle table and NO back-reference: every object occurrence is encoded in
 * full, by VALUE. This keeps the stream a deterministic (canonical-DER) function of the
 * argument values rather than of object identity or write order, and it carries no
 * aliasing. It loses nothing real -- {@code @AtomicSerial} deserialization defensively
 * copies and re-checks invariants per object, so shared identity is never preserved across
 * the boundary anyway (a deliberate security property). Reference cycles are consequently
 * impossible to express. A back-reference-style context tag (e.g. [2]) is not part of the
 * grammar and is rejected fail-secure.
 */
final class DerObjectStreamCodec {

    // =========================================================================
    // Context tags (single byte, pre-computed)
    // =========================================================================

    /** [0] primitive context tag: NULL reference. */
    private static final Tag CTX_NULL        = new Tag(Tag.CLASS_CONTEXT, false, 0);
    /** [1] constructed context tag: @AtomicSerial object (MarshalledInstanceRecord). */
    private static final Tag CTX_ATOMIC      = new Tag(Tag.CLASS_CONTEXT, true,  1);
    /** [3] primitive context tag: java.lang.String (UTF8String content). */
    private static final Tag CTX_STRING      = new Tag(Tag.CLASS_CONTEXT, false, 3);
    /** [5] primitive context tag: byte[] (OCTET STRING content). */
    private static final Tag CTX_BYTES       = new Tag(Tag.CLASS_CONTEXT, false, 5);
    /** [7] constructed context tag: enum; content = UTF8String(declaringClass) ++ UTF8String(name). */
    private static final Tag CTX_ENUM        = new Tag(Tag.CLASS_CONTEXT, true,  7);
    /** [8] constructed context tag: bare java.lang.reflect.Proxy (interface names + @AtomicSerial handler). */
    private static final Tag CTX_PROXY       = new Tag(Tag.CLASS_CONTEXT, true,  8);
    /** [9] constructed context tag: top-level value array (UTF8 componentWireType + element SEQUENCE). */
    private static final Tag CTX_ARRAY       = new Tag(Tag.CLASS_CONTEXT, true,  9);

    /** {@link DeSerializationPermission}("PROXY") required to reconstruct a [8] proxy (mirrors JOSS). */
    private static final Permission PROXY_PERM = new DeSerializationPermission("PROXY");

    // =========================================================================
    // Write side state
    // =========================================================================

    /** Output accumulator for the write side. */
    private final List<byte[]> writeBuffer = new ArrayList<>();

    /**
     * Write-side proxy-substitution config. When {@code substituteProxies} is set (via
     * {@link #initWriter}), a downloadable proxy ({@link DynamicProxyCodebaseAccessor} /
     * {@link ProxyAccessor}) written through {@link #writeObject} is substituted with a
     * {@code DerProxySerializer} carrier (the object-stream counterpart of
     * {@code AtomicMarshalOutputStream.defaultReplaceObject}, used on the JERI invocation
     * arg/return path). Default OFF -- a plain object stream (e.g. the bare serviceProxy inside
     * a carrier) must NOT re-substitute. {@code writeStreamLoader} gates {@code create()}'s
     * {@code ProxyCodebaseSpi.substitute()} check; it is held only here on the trusted write side.
     */
    private boolean substituteProxies = false;
    private ClassLoader writeStreamLoader = null;
    private Collection<?> writeContext = Collections.emptyList();

    // =========================================================================
    // Read side state
    // =========================================================================

    /** DER reader over the input bytes (read side). */
    private DerReader reader;

    /**
     * Completion sink for the current decode unit, threaded into the constructed
     * {@code DerGetArg}s so that a decoded DGC live reference can register its batched
     * {@code dirty} on the per-decode-unit token. May be {@code null} (e.g. a standalone
     * MarshalledInstance decode with no DGC context).
     */
    private DeserializationCompletion decodeUnit;

    /**
     * Endpoint-assigned resolution context for class names in the stream ([7] enum, [8] proxy,
     * [1] {@code @AtomicSerial}). Defaults to {@link ResolutionContext#NONE}; the
     * MarshalledInstance/JERI entry points set the real endpoint loaders via
     * {@link #initReader(byte[], DeserializationCompletion, ResolutionContext)}. Holds a
     * capability (the loader) and is therefore never exposed beyond this trusted codec.
     */
    private ResolutionContext resolution = ResolutionContext.NONE;

    // =========================================================================
    // Construction
    // =========================================================================

    /** Creates a fresh codec ready for writing; initialise read side later via {@link #initReader}. */
    DerObjectStreamCodec() {}

    /**
     * Enables write-side substitution of a downloadable top-level proxy ({@link
     * DynamicProxyCodebaseAccessor} / {@link ProxyAccessor}) with a {@code DerProxySerializer}
     * carrier (the JERI invocation arg/return path). Without this, a proxy travels bare ([8]) and
     * must be locally resolvable.
     *
     * @param context     the stream context collection (must not be {@code null})
     * @param streamLoader the stream loader gating the {@code ProxyCodebaseSpi.substitute()} check
     *                     (the client proxy loader, or the dispatcher's stream loader); may be {@code null}
     */
    void initWriter(Collection<?> context, ClassLoader streamLoader) {
        this.writeContext = Objects.requireNonNull(context, "context");
        this.writeStreamLoader = streamLoader;
        this.substituteProxies = true;
    }

    /** Initialises the read side over a complete DER byte array (no decode-unit token). */
    void initReader(byte[] buf) {
        initReader(buf, null);
    }

    /**
     * Initialises the read side over a complete DER byte array, threading the given
     * decode-unit completion token into every {@code DerGetArg} constructed during decode.
     *
     * @param buf        the complete DER byte array (must not be {@code null})
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     */
    void initReader(byte[] buf, DeserializationCompletion decodeUnit) {
        initReader(buf, decodeUnit, ResolutionContext.NONE);
    }

    /**
     * Initialises the read side, additionally carrying the endpoint-assigned
     * {@link ResolutionContext} used to resolve class names against the endpoint's loader (NOT
     * the thread-context loader -- the Warres ambient-resolution failure).
     *
     * @param buf        the complete DER byte array (must not be {@code null})
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @param resolution the endpoint-assigned resolution context (must not be {@code null})
     */
    void initReader(byte[] buf, DeserializationCompletion decodeUnit, ResolutionContext resolution) {
        Objects.requireNonNull(buf, "buf");
        this.reader = new DerReader(buf);
        this.decodeUnit = decodeUnit;
        this.resolution = Objects.requireNonNull(resolution, "resolution");
    }

    // =========================================================================
    // Write side: typed primitives
    // =========================================================================

    void writeBoolean(boolean v) {
        writeBuffer.add(DerWriter.writeBoolean(v));
    }

    void writeByte(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf((byte) v)));
    }

    void writeShort(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf((short) v)));
    }

    void writeInt(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(v)));
    }

    void writeLong(long v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(v)));
    }

    /**
     * STD-008 sec.17.3.1: IEEE-754 in 4-byte OCTET STRING with canonical NaN and
     * canonical {@code +0.0}. {@code -0.0} is mapped to {@code +0.0} on encode.
     */
    void writeFloat(float v) {
        int bits;
        if (Float.isNaN(v))                                          bits = 0x7FC00000;
        else if (Float.floatToRawIntBits(v) == 0x80000000)           bits = 0x00000000;
        else                                                         bits = Float.floatToRawIntBits(v);
        byte[] content = new byte[] {
                (byte)(bits >>> 24), (byte)(bits >>> 16),
                (byte)(bits >>>  8), (byte) bits
        };
        writeBuffer.add(DerWriter.writeOctetString(content));
    }

    /**
     * STD-008 sec.17.3.1: IEEE-754 in 8-byte OCTET STRING with canonical NaN and
     * canonical {@code +0.0}. {@code -0.0} is mapped to {@code +0.0} on encode.
     */
    void writeDouble(double v) {
        long bits;
        if (Double.isNaN(v))                                                  bits = 0x7FF8000000000000L;
        else if (Double.doubleToRawLongBits(v) == 0x8000000000000000L)        bits = 0x0000000000000000L;
        else                                                                  bits = Double.doubleToRawLongBits(v);
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) { content[i] = (byte)(bits & 0xFF); bits >>>= 8; }
        writeBuffer.add(DerWriter.writeOctetString(content));
    }

    /**
     * STD-008 sec.17.3.2: Unicode codepoint INTEGER. Surrogate code units rejected.
     * (Note: {@link java.io.ObjectOutput#writeChar(int)} takes an {@code int}; we treat
     * the low 16 bits as the char value, matching {@link DataOutput#writeChar}.)
     */
    void writeChar(int v) {
        int cp = v & 0xFFFF;
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            throw new IllegalArgumentException(
                    "DER stream: unpaired surrogate code unit 0x"
                    + Integer.toHexString(cp).toUpperCase()
                    + " is not a valid Unicode codepoint (STD-008 sec.17.3.2)");
        }
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(cp)));
    }

    void writeUTF(String s) {
        Objects.requireNonNull(s, "s");
        writeBuffer.add(DerWriter.writeUtf8String(s));
    }

    void writeBytes(byte[] b, int off, int len) {
        byte[] content = new byte[len];
        System.arraycopy(b, off, content, 0, len);
        writeBuffer.add(DerWriter.writeOctetString(content));
    }

    void writeSingleByte(int b) {
        // write(int) from ObjectOutputStream -- writes a single byte as an INTEGER
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf((byte)(b & 0xFF))));
    }

    // =========================================================================
    // Write side: objects
    // =========================================================================

    /**
     * Encodes an object item and appends it to the write buffer.
     *
     * <ul>
     *   <li>null -> [0] NULL</li>
     *   <li>@AtomicSerial instance -> [1] MarshalledInstanceRecord (full, every occurrence)</li>
     *   <li>String -> [3]</li>
     *   <li>byte[] -> [5]</li>
     *   <li>anything else -> UnsupportedOperationException (fail-secure)</li>
     * </ul>
     */
    void writeObject(Object obj) throws IOException {
        // Write seam (JERI invocation arg/return path): substitute a downloadable proxy
        // (DynamicProxyCodebaseAccessor / ProxyAccessor) with a DerProxySerializer carrier so it
        // carries its bootstrap + codebase for download, riding the [1] @AtomicSerial path -- the
        // object-stream counterpart of AtomicMarshalOutputStream.defaultReplaceObject. With no
        // registered ProxyCodebaseSpi, create() returns the proxy unchanged and it travels bare
        // ([8]) below. OFF by default (a bare serviceProxy inside a carrier must NOT re-substitute).
        if (substituteProxies && obj != null) {
            if (obj instanceof DynamicProxyCodebaseAccessor dpca) {
                obj = DerProxySerializer.create(dpca, writeStreamLoader, writeContext);
            } else if (obj instanceof ProxyAccessor pa) {
                obj = DerProxySerializer.create(pa, writeStreamLoader, writeContext);
            }
        }
        if (obj == null) {
            writeBuffer.add(DerWriter.writeTlv(CTX_NULL, new byte[0]));
            return;
        }

        // String: value semantics, no handle table
        if (obj instanceof String s) {
            byte[] strBytes = s.getBytes(StandardCharsets.UTF_8);
            writeBuffer.add(DerWriter.writeTlv(CTX_STRING, strBytes));
            return;
        }

        // byte[]: value semantics, no handle table
        if (obj instanceof byte[] bytes) {
            writeBuffer.add(DerWriter.writeTlv(CTX_BYTES, bytes));
            return;
        }

        // Non-byte[] value array (primitive/String/enum/@AtomicSerial component) -> [9] CTX_ARRAY.
        // Arrays are VALUES, not @AtomicSerial objects (byte[] handled above as OCTET STRING).
        if (obj.getClass().isArray()) {
            try {
                writeBuffer.add(DerWriter.writeTlv(CTX_ARRAY, ObjectCodec.encodeTopLevelArray(obj)));
            } catch (DerException e) {
                throw new IOException("DER stream [9] array encode failed for "
                        + obj.getClass().getName(), e);
            }
            return;
        }

        // enum: STD-008 sec.15.2 [7] / sec.17.1 -- declaring class + constant name, by value.
        // Use getDeclaringClass(), NOT getClass() (which is the constant-body subclass for
        // constants with bodies, e.g. Op.ADD -> Op$1).
        if (obj instanceof Enum<?> e) {
            byte[] clsName = DerWriter.writeUtf8String(e.getDeclaringClass().getName());
            byte[] name    = DerWriter.writeUtf8String(e.name());
            byte[] content = new byte[clsName.length + name.length];
            System.arraycopy(clsName, 0, content, 0, clsName.length);
            System.arraycopy(name, 0, content, clsName.length, name.length);
            writeBuffer.add(DerWriter.writeTlv(CTX_ENUM, content));
            return;
        }

        // bare java.lang.reflect.Proxy -> [8]: interface names + the @AtomicSerial InvocationHandler,
        // reconstructed via Proxy.newProxyInstance. No ProxySerializer/bootstrap/codebase -- the
        // interfaces + handler must be locally resolvable on the receiver (sec.15.2).
        //
        // If obj's live handler is a BoomerangProxyHandler (this node itself decoded obj from a
        // [8] item that dropped >=1 interface it couldn't resolve locally -- see readObject
        // below), re-emit the RETAINED ORIGINAL wire bytes verbatim, byte-for-byte, rather than
        // re-deriving fresh bytes from obj.getClass().getInterfaces() (which only shows the
        // narrowed runtime set this node built, and would also forfeit the sender's
        // @AtomicSerial-validated integrity guarantee -- see BoomerangProxyHandler). Otherwise --
        // the common case, nothing was ever dropped -- behaviour is unchanged: encode fresh.
        if (Proxy.isProxyClass(obj.getClass())) {
            byte[] retained = ProxyWireSupport.wireContentForBoomerang(obj);
            if (retained != null) {
                writeBuffer.add(DerWriter.writeTlv(CTX_PROXY, retained));
                return;
            }
            Class<?>[] ifaces = obj.getClass().getInterfaces();
            if (ifaces.length == 0 || ifaces.length > ProxyWireSupport.MAX_PROXY_INTERFACES) {
                throw new IOException("DER stream [8] proxy: interface count " + ifaces.length
                        + " out of range (1.." + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
            }
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (!h.getClass().isAnnotationPresent(AtomicSerial.class)) {
                throw new UnsupportedOperationException(
                        "DER stream [8] proxy: InvocationHandler "
                        + h.getClass().getName() + " is not @AtomicSerial");
            }
            java.io.ByteArrayOutputStream content = new java.io.ByteArrayOutputStream();
            content.writeBytes(DerWriter.writeInteger(BigInteger.valueOf(ifaces.length)));
            for (Class<?> iface : ifaces) {
                content.writeBytes(DerWriter.writeUtf8String(iface.getName()));
            }
            try {
                content.writeBytes(DerWriter.writeTlv(CTX_ATOMIC, encodeAtomicRecord(h)));
            } catch (DerException e) {
                throw new IOException("DER stream [8] proxy: handler encode failed", e);
            }
            writeBuffer.add(DerWriter.writeTlv(CTX_PROXY, content.toByteArray()));
            return;
        }

        // @AtomicSerial object -> a full record, EVERY occurrence (no handle table; sec.15.3).
        // Encoded by VALUE so the stream is a deterministic (canonical-DER) function of values,
        // not object identity/order; @AtomicSerial deserialization copies + re-checks invariants
        // per object, so shared identity is not preserved across the boundary anyway.
        Class<?> cls = obj.getClass();
        if (!cls.isAnnotationPresent(AtomicSerial.class)) {
            throw new UnsupportedOperationException(
                    "DER stream inc-1: unsupported object type "
                    + cls.getName()
                    + "; @AtomicSerial-restricted");
        }

        try {
            writeBuffer.add(DerWriter.writeTlv(CTX_ATOMIC, encodeAtomicRecord(obj)));
        } catch (DerException e) {
            throw new IOException("DER encode failed for " + cls.getName(), e);
        }
    }

    // =========================================================================
    // Read side: typed primitives
    // =========================================================================

    boolean readBoolean() throws IOException {
        try {
            return reader.readBoolean();
        } catch (DerException e) {
            throw new IOException("readBoolean: " + e.getMessage(), e);
        }
    }

    byte readByte() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            long lv = v.longValueExact();
            if (lv < Byte.MIN_VALUE || lv > Byte.MAX_VALUE) {
                throw new IOException("readByte: value " + lv + " out of byte range");
            }
            return (byte) lv;
        } catch (ArithmeticException e) {
            throw new IOException("readByte: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readByte: " + e.getMessage(), e);
        }
    }

    short readShort() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            long lv = v.longValueExact();
            if (lv < Short.MIN_VALUE || lv > Short.MAX_VALUE) {
                throw new IOException("readShort: value " + lv + " out of short range");
            }
            return (short) lv;
        } catch (ArithmeticException e) {
            throw new IOException("readShort: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readShort: " + e.getMessage(), e);
        }
    }

    int readInt() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            return v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IOException("readInt: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readInt: " + e.getMessage(), e);
        }
    }

    long readLong() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            return v.longValueExact();
        } catch (ArithmeticException e) {
            throw new IOException("readLong: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readLong: " + e.getMessage(), e);
        }
    }

    /**
     * STD-008 sec.17.3.1: strict canonical IEEE-754 decode. 4-byte OCTET STRING required;
     * non-canonical NaN bit patterns and {@code -0.0} bits rejected fail-secure.
     */
    float readFloat() throws IOException {
        try {
            byte[] content = reader.readOctetString();
            if (content.length != 4) {
                throw new IOException("readFloat: OCTET STRING must be 4 bytes, got " + content.length);
            }
            int bits =  ((content[0] & 0xFF) << 24)
                      | ((content[1] & 0xFF) << 16)
                      | ((content[2] & 0xFF) <<  8)
                      |  (content[3] & 0xFF);
            if (bits == 0x80000000) {
                throw new IOException("readFloat: -0.0 bits are not canonical (STD-008 sec.17.3.1)");
            }
            boolean isNaN = (bits & 0x7F800000) == 0x7F800000 && (bits & 0x007FFFFF) != 0;
            if (isNaN && bits != 0x7FC00000) {
                throw new IOException("readFloat: non-canonical NaN 0x"
                        + String.format("%08X", bits) + " (canonical is 0x7FC00000)");
            }
            return Float.intBitsToFloat(bits);
        } catch (DerException e) {
            throw new IOException("readFloat: " + e.getMessage(), e);
        }
    }

    /** STD-008 sec.17.3.1: strict canonical IEEE-754 decode (8 bytes; rejects non-canonical NaN / {@code -0.0}). */
    double readDouble() throws IOException {
        try {
            byte[] content = reader.readOctetString();
            if (content.length != 8) {
                throw new IOException("readDouble: OCTET STRING must be 8 bytes, got " + content.length);
            }
            long bits = 0L;
            for (int i = 0; i < 8; i++) bits = (bits << 8) | (content[i] & 0xFF);
            if (bits == 0x8000000000000000L) {
                throw new IOException("readDouble: -0.0 bits are not canonical (STD-008 sec.17.3.1)");
            }
            boolean isNaN = (bits & 0x7FF0000000000000L) == 0x7FF0000000000000L
                         && (bits & 0x000FFFFFFFFFFFFFL) != 0L;
            if (isNaN && bits != 0x7FF8000000000000L) {
                throw new IOException("readDouble: non-canonical NaN 0x"
                        + String.format("%016X", bits) + " (canonical is 0x7FF8000000000000)");
            }
            return Double.longBitsToDouble(bits);
        } catch (DerException e) {
            throw new IOException("readDouble: " + e.getMessage(), e);
        }
    }

    /** STD-008 sec.17.3.2: Unicode codepoint INTEGER, BMP non-surrogate. */
    char readChar() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            int cp = v.intValueExact();
            if (cp < 0 || cp > 0xFFFF) {
                throw new IOException("readChar: codepoint " + cp
                        + " out of BMP range [0, 0xFFFF] (STD-008 sec.17.3.2)");
            }
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                throw new IOException("readChar: surrogate codepoint 0x"
                        + Integer.toHexString(cp).toUpperCase()
                        + " is not a valid Unicode codepoint");
            }
            return (char) cp;
        } catch (ArithmeticException e) {
            throw new IOException("readChar: codepoint INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readChar: " + e.getMessage(), e);
        }
    }

    String readUTF() throws IOException {
        try {
            return reader.readUtf8String();
        } catch (DerException e) {
            throw new IOException("readUTF: " + e.getMessage(), e);
        }
    }

    byte[] readOctetString() throws IOException {
        try {
            return reader.readOctetString();
        } catch (DerException e) {
            throw new IOException("readOctetString: " + e.getMessage(), e);
        }
    }

    int readSingleByte() throws IOException {
        // inverse of writeSingleByte
        return readByte() & 0xFF;
    }

    int available() {
        // Simple: 1 if there are more bytes to read, 0 otherwise
        return reader != null && reader.hasMore() ? 1 : 0;
    }

    // =========================================================================
    // Read side: objects
    // =========================================================================

    /**
     * Decodes the next context-tagged item from the stream and returns the object.
     */
    Object readObject() throws IOException, ClassNotFoundException {
        Tag tag;
        DerReader.TlvHeader hdr;
        try {
            hdr = reader.readTlvHeader();
        } catch (DerException e) {
            throw new IOException("readObject: failed to read TLV header: " + e.getMessage(), e);
        }
        tag = hdr.tag();

        if (CTX_NULL.equals(tag)) {
            // [0] NULL -- no content
            if (hdr.contentLength() != 0) {
                throw new IOException("readObject: [0] NULL TLV must have length 0, got "
                        + hdr.contentLength());
            }
            return null;
        }

        if (CTX_STRING.equals(tag)) {
            // [3] String: content is UTF-8 bytes
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read string content", e);
            }
            return new String(content, StandardCharsets.UTF_8);
        }

        if (CTX_BYTES.equals(tag)) {
            // [5] byte[]: content is raw bytes
            try {
                return reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read byte[] content", e);
            }
        }

        if (CTX_ARRAY.equals(tag)) {
            // [9] top-level value array: content = UTF8String(arrayWireType) ++ element SEQUENCE.
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read [9] array content", e);
            }
            DerReader ar = new DerReader(content);
            String awt;
            try {
                awt = ar.readUtf8String();
            } catch (DerException e) {
                throw new IOException("readObject: malformed [9] array wireType", e);
            }
            if (!awt.startsWith("array:")) {
                throw new IOException("readObject: [9] array wireType must start with 'array:', got '"
                        + awt + "'");
            }
            byte[] seqBytes = Arrays.copyOfRange(content, ar.position(), content.length);
            String componentWT = awt.substring("array:".length());
            try {
                if (componentWT.startsWith("@AtomicSerial:")) {
                    String cls = componentWT.substring("@AtomicSerial:".length());
                    return ObjectCodec.decodeNestedArray(seqBytes, cls, 0, decodeUnit, resolution);
                }
                return DerFieldStore.decodePrimitiveArray(seqBytes, awt, resolution);
            } catch (DerException | ClassNotFoundException e) {
                throw new IOException("readObject: [9] array decode failed (" + awt + ")", e);
            }
        }

        if (CTX_ATOMIC.equals(tag)) {
            // [1] @AtomicSerial object: content is a MarshalledInstanceRecord SEQUENCE
            byte[] recBytes;
            try {
                recBytes = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read @AtomicSerial record bytes", e);
            }
            return decodeAtomicRecord(recBytes);
        }

        if (CTX_ENUM.equals(tag)) {
            // [7] enum: content = UTF8String(declaringClassName) ++ UTF8String(constantName)
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read [7] enum content", e);
            }
            DerReader er = new DerReader(content);
            String className;
            String constant;
            try {
                className = er.readUtf8String();
                constant  = er.readUtf8String();
            } catch (DerException e) {
                throw new IOException("readObject: malformed [7] enum item", e);
            }
            if (er.hasMore()) {
                throw new IOException("readObject: trailing bytes in [7] enum item");
            }
            // Endpoint-assigned resolution (NEVER the thread-context loader -- Warres).
            Class<?> enumClass = resolution.loadClass(className);
            if (!enumClass.isEnum()) {
                throw new IOException("readObject: [7] enum class '" + className
                        + "' is not an enum");
            }
            try {
                return enumValueOf(enumClass, constant);
            } catch (IllegalArgumentException e) {
                throw new IOException("readObject: unknown enum constant '" + constant
                        + "' in " + className, e);
            }
        }

        if (CTX_PROXY.equals(tag)) {
            // [8] bare java.lang.reflect.Proxy: INTEGER count, count x UTF8String interface name,
            // then a [1] @AtomicSerial InvocationHandler. Reconstruct via Proxy.newProxyInstance.
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read [8] proxy content", e);
            }
            DerReader pr = new DerReader(content);
            int count;
            String[] names;
            Object handler;
            try {
                count = pr.readInteger().intValueExact();
                if (count <= 0 || count > ProxyWireSupport.MAX_PROXY_INTERFACES) {
                    throw new IOException("readObject: [8] proxy interface count " + count
                            + " out of range (1.." + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
                }
                names = new String[count];
                for (int i = 0; i < count; i++) {
                    names[i] = pr.readUtf8String();
                }
                DerReader.TlvHeader hh = pr.readTlvHeader();
                if (!CTX_ATOMIC.equals(hh.tag())) {
                    throw new IOException("readObject: [8] proxy handler must be a [1] @AtomicSerial item");
                }
                handler = decodeAtomicRecord(pr.readRawContent(hh.contentLength()));
                if (pr.hasMore()) {
                    throw new IOException("readObject: trailing bytes in [8] proxy item");
                }
            } catch (ArithmeticException e) {
                throw new IOException("readObject: [8] proxy interface count overflow", e);
            } catch (DerException e) {
                throw new IOException("readObject: malformed [8] proxy item", e);
            }
            if (!(handler instanceof InvocationHandler)) {
                throw new IOException("readObject: [8] proxy handler is not an InvocationHandler ("
                        + (handler == null ? "null" : handler.getClass().getName()) + ")");
            }
            // Endpoint-assigned TOLERANT resolution of the proxy class (NEVER the thread-context
            // loader -- Warres): resolves each interface name independently rather than failing
            // the whole item when a single name doesn't resolve locally. Names that don't resolve
            // are dropped (and logged); the proxy still builds over the resolvable subset, wrapped
            // in a BoomerangProxyHandler that retains the full original wire bytes so a later
            // re-forward of this proxy doesn't silently lose the dropped interfaces -- and re-emits
            // those bytes byte-for-byte rather than re-deriving them (see ProxyWireSupport /
            // BoomerangProxyHandler and the write side above).
            ProxyWireSupport.Resolved resolved = ProxyWireSupport.resolveTolerant(names, resolution);
            // DeSerializationPermission("PROXY") gate before reconstruction -- DER counterpart of
            // AtomicMarshalInputStream.instantiateProxy's deSerializationPermitted(PROXY). No-op w/o SM.
            // Runs against the RESOLVED (narrowed) interface set actually being instantiated, not
            // the full original names -- that's what's actually being granted a live proxy.
            checkProxyDeSerializationPermitted(resolved.interfaces);
            InvocationHandler realHandler = (InvocationHandler) handler;
            InvocationHandler toUse = resolved.droppedNames.length == 0
                    ? realHandler
                    : ProxyWireSupport.wrapForDrop(realHandler, content);
            try {
                return Proxy.newProxyInstance(resolved.proxyClass.getClassLoader(), resolved.interfaces, toUse);
            } catch (IllegalArgumentException e) {
                throw new IOException("readObject: [8] proxy reconstruction failed", e);
            }
        }

        throw new IOException("readObject: unexpected context tag " + tag
                + " (expected [0],[1],[3],[5],[7],[8]); back-references are not supported (sec.15.3)");
    }

    /** Resolves an enum constant by declaring-class + name (raw-type bridge for {@link Enum#valueOf}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValueOf(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }

    /** Encodes an {@code @AtomicSerial} object to its MarshalledInstanceRecord DER bytes (the [1] content). */
    private static byte[] encodeAtomicRecord(Object obj) throws DerException {
        SchemaChain.Result chain = SchemaGenerator.generateChain(obj.getClass());
        byte[] payload = ObjectCodec.encodeHierarchy(obj, chain);
        return MarshalledInstanceRecord.fromChain(chain, payload).encode();
    }

    /** Decodes the bytes inside a [1] {@code CTX_ATOMIC} TLV (a MarshalledInstanceRecord) into its object. */
    private Object decodeAtomicRecord(byte[] recBytes) throws IOException, ClassNotFoundException {
        MarshalledInstanceRecord rec;
        try {
            rec = MarshalledInstanceRecord.decode(recBytes);
        } catch (DerException e) {
            throw new IOException("readObject: malformed MarshalledInstanceRecord", e);
        }
        String leafClassName;
        try {
            leafClassName = rec.decodeSchemaChain().get(0).className();
        } catch (DerException e) {
            throw new IOException("readObject: failed to decode embedded schema chain", e);
        }
        // Endpoint-assigned resolution (NEVER the thread-context loader -- Warres).
        Class<?> leafClass = resolution.loadClass(leafClassName);
        try {
            return MarshalledInstanceCodec.decodeMarshalledInstance(rec, leafClass, decodeUnit, resolution).object();
        } catch (DerException e) {
            throw new IOException("readObject: decode failed for " + leafClassName, e);
        }
    }

    /** {@code DeSerializationPermission("PROXY")} gate for [8] reconstruction; no-op without a {@link SecurityManager}. */
    private static void checkProxyDeSerializationPermitted(Class<?>[] interfaces) {
        checkProxyDeSerializationPermitted(interfaces, System.getSecurityManager());
    }

    /**
     * Testable seam for the [8] {@code DeSerializationPermission("PROXY")} gate (mirrors
     * {@code ObjectCodec.checkAtomicDeSerializationPermitted}). The permission is checked against an
     * {@link AccessControlContext} built from the proxy interfaces' protection domains -- the
     * DER-path counterpart of JOSS {@code deSerializationPermitted(PROXY)} -- so a deployment can
     * govern which interface codebases may be reconstructed as a proxy from an untrusted stream.
     *
     * @param interfaces the resolved proxy interfaces about to be reconstructed
     * @param sm         the active security manager, or {@code null}
     * @throws SecurityException if {@code sm} denies {@code DeSerializationPermission("PROXY")}
     */
    @SuppressWarnings("removal")
    static void checkProxyDeSerializationPermitted(Class<?>[] interfaces, SecurityManager sm) {
        if (sm == null) return;
        AccessControlContext ctx = AccessController.doPrivileged(
                (PrivilegedAction<AccessControlContext>) () -> {
                    Set<ProtectionDomain> domains = new LinkedHashSet<>();
                    for (Class<?> i : interfaces) {
                        if (i == null) continue;
                        ProtectionDomain pd = i.getProtectionDomain();
                        if (pd != null) domains.add(pd);
                    }
                    return Security.create(domains.toArray(new ProtectionDomain[0]));
                });
        sm.checkPermission(PROXY_PERM, ctx);
    }

    // =========================================================================
    // Write-side drain
    // =========================================================================

    /**
     * Concatenates all accumulated write-buffer chunks and writes them to the
     * given output stream. Clears the buffer.
     */
    void drainTo(OutputStream out) throws IOException {
        for (byte[] chunk : writeBuffer) {
            out.write(chunk);
        }
        writeBuffer.clear();
    }
}
