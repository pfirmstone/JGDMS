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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.DeSerializationPermission;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.api.io.MarshalDelegates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.jini.io.context.DeserializationCompletion;

/**
 * DER object encoder and decoder for single {@code @AtomicSerial} classes
 * (Phase 4.1 / 4.2 -- single class, one private SEQUENCE), for
 * {@code @AtomicSerial} class hierarchies (Phase 4.3 -- one private SEQUENCE
 * per class, superclass-first on wire), and for hierarchies containing
 * non-{@code @AtomicSerial} classes (Phase 4.4 -- S3.10 wire-visibility rules).
 *
 * <h2>Single-class encoding (object -> DER)</h2>
 * <p>
 * {@link #encode(Object, Class, AtomicSerialSchemaRecord)} encodes an object's
 * state to the DER bytes of the class's private SEQUENCE. The field values are
 * obtained by invoking the class's own {@code public static serialize(PutArg, T)}
 * method (the {@code @AtomicSerial} WRITE contract) and reading the captured
 * {@code put(name, value)} entries; the codec does NOT reflect on private fields.
 * A class without {@code serialize(PutArg)} is not serialized -- {@code encode}
 * fails fast rather than imitate Java Object Serialization's field access.
 *
 * <h2>Single-class decoding (DER -> object)</h2>
 * <p>
 * {@link #decode(Class, AtomicSerialSchemaRecord, byte[])} decodes the DER bytes
 * of one class's private SEQUENCE, builds a {@link DerFieldStore}, assembles a
 * single-entry {@link DerGetArg}, and drives construction by reflectively invoking
 * the {@code public C(AtomicSerial.GetArg)} constructor.
 *
 * <h2>Hierarchy encoding (Phase 4.3)</h2>
 * <p>
 * {@link #encodeHierarchy(Object, SchemaChain.Result)} emits one private SEQUENCE
 * per {@code @AtomicSerial} class, assembled in a top-level wrapper SEQUENCE in
 * <b>superclass-first (root-first, leaf-last)</b> order. Each class's SEQUENCE
 * contains only the fields declared by that class (via its own {@code serialForm()});
 * no class can write another class's fields. Wire structure:
 * <pre>
 * SEQUENCE {          -- outer hierarchy SEQUENCE
 *   SEQUENCE { ... }  -- root-class private SEQUENCE (first on wire)
 *   SEQUENCE { ... }  -- mid-class private SEQUENCE
 *   SEQUENCE { ... }  -- leaf-class private SEQUENCE (last on wire)
 * }
 * </pre>
 *
 * <h2>Hierarchy decoding (Phase 4.3)</h2>
 * <p>
 * {@link #decodeHierarchy(Class, SchemaChain.Result, byte[])} reads each per-class
 * SEQUENCE from the outer wrapper in superclass-first order, builds one
 * {@link DerFieldStore} per class, populates a multi-entry {@link DerGetArg}
 * (insertion order: superclass-first), then reflectively invokes the LEAF class's
 * {@code (GetArg)} constructor. The leaf constructor chains up via
 * {@code super(check(arg))}, and StackWalker dispatch in {@link DerGetArg} ensures
 * each level reads exclusively from its own {@link DerFieldStore}.
 *
 * <h2>check-before-construction contract</h2>
 * <p>
 * The {@code (GetArg)} constructor is responsible for calling its static
 * {@code check(GetArg)} method before assigning any field. If {@code check}
 * throws {@link IOException} or {@link InvalidObjectException}, the
 * {@link InvocationTargetException} wrapper is unwrapped and the exception
 * propagated. No partially-constructed object is returned.
 *
 * <h2>Type mapping for encoding (Class -> wireType)</h2>
 * <ul>
 *   <li>{@code boolean} / {@link Boolean} -> DER BOOLEAN</li>
 *   <li>{@code byte} / {@link Byte} -> DER INTEGER (1-byte range)</li>
 *   <li>{@code short} / {@link Short} -> DER INTEGER (2-byte range)</li>
 *   <li>{@code int} / {@link Integer} -> DER INTEGER (4-byte range)</li>
 *   <li>{@code long} / {@link Long} -> DER INTEGER (8-byte range)</li>
 *   <li>{@link String} -> DER UTF8String</li>
 *   <li>{@code byte[]} -> DER OCTET STRING</li>
 *   <li>Any other type -> {@link DerException} naming the unsupported type</li>
 * </ul>
 */
public final class ObjectCodec {

    /**
     * Maximum nesting depth for nested {@code @AtomicSerial} object fields
     * (STD-008 sec.16.2 depth-bound DoS guard). Encode and decode both
     * throw {@link DerException} when this limit is exceeded.
     */
    public static final int MAX_NESTING = 16;

    /**
     * The {@link DeSerializationPermission} required for a protection domain to
     * participate in {@code @AtomicSerial} de-serialization via the DER path.
     */
    private static final Permission ATOMIC = new DeSerializationPermission("ATOMIC");

    /**
     * The {@link DeSerializationPermission} required to reconstruct a nested
     * {@code java.lang.reflect.Proxy} field value (the field-level counterpart of the
     * object-stream {@code [8]} gate in {@code DerObjectStreamCodec}; mirrors JOSS
     * {@code deSerializationPermitted(PROXY)}).
     */
    private static final Permission PROXY = new DeSerializationPermission("PROXY");

    /**
     * Maximum number of interfaces a nested {@code java.lang.reflect.Proxy} field value
     * may declare (matches {@code DerObjectStreamCodec.MAX_PROXY_INTERFACES}); bounds the
     * {@code [8]} interface-name list against a hostile stream.
     */
    private static final int MAX_PROXY_INTERFACES = 127;

    /**
     * {@code [8]} context-constructed tag: a nested {@code java.lang.reflect.Proxy} field
     * value (interface names + the {@code @AtomicSerial} InvocationHandler). Identical tag
     * to the object-stream {@code CTX_PROXY} so the two layers share one wire discriminator.
     */
    private static final Tag CTX_PROXY = new Tag(Tag.CLASS_CONTEXT, true, 8);

    /**
     * Per-class {@code DeSerializationPermission("ATOMIC")} gate (STD-008): before
     * any {@code @AtomicSerial (GetArg)} constructor runs, every class in the
     * hierarchy whose constructor will execute must have
     * {@code DeSerializationPermission("ATOMIC")} granted to its protection domain.
     * This is the DER-path counterpart of {@code AtomicMarshalInputStream}'s
     * per-class check ({@code ObjectStreamClassContainer.deSerializationPermitted(ATOMIC)}):
     * it lets a deployment restrict which classes may be reconstructed from an
     * untrusted stream, independent of the parameter objects (which are validated
     * separately by each class's {@code check(GetArg)}). No-op when no
     * {@link SecurityManager} is installed.
     *
     * @param classes the {@code @AtomicSerial} classes about to be constructed
     */
    private static void checkAtomicDeSerializationPermitted(Collection<Class<?>> classes) {
        checkAtomicDeSerializationPermitted(classes, System.getSecurityManager());
    }

    /**
     * Testable seam for {@link #checkAtomicDeSerializationPermitted(Collection)}:
     * the {@link SecurityManager} is passed in so the gate can be exercised with a
     * denying / permitting manager without installing one process-wide (which
     * Java&nbsp;21 forbids at runtime unless started with
     * {@code -Djava.security.manager=allow}). The permission is checked against an
     * {@link AccessControlContext} built from the protection domains of the classes
     * being decoded (so the grant must sit with the class's codebase, not the
     * caller's).
     *
     * @param classes the {@code @AtomicSerial} classes about to be constructed
     * @param sm      the active security manager, or {@code null}
     * @throws SecurityException if {@code sm} denies
     *         {@code DeSerializationPermission("ATOMIC")} for the classes' domains
     */
    @SuppressWarnings("removal")
    static void checkAtomicDeSerializationPermitted(Collection<Class<?>> classes,
                                                    SecurityManager sm) {
        if (sm == null) return;
        AccessControlContext ctx = AccessController.doPrivileged(
                (PrivilegedAction<AccessControlContext>) () -> {
                    Set<ProtectionDomain> domains = new LinkedHashSet<>();
                    for (Class<?> c : classes) {
                        if (c == null) continue;
                        ProtectionDomain pd = c.getProtectionDomain();
                        if (pd != null) domains.add(pd);
                    }
                    return new AccessControlContext(
                            domains.toArray(new ProtectionDomain[0]));
                });
        sm.checkPermission(ATOMIC, ctx);
    }

    /**
     * {@code DeSerializationPermission("PROXY")} gate for reconstructing a nested
     * {@code java.lang.reflect.Proxy} field value; no-op without a {@link SecurityManager}.
     * The field-level counterpart of {@code DerObjectStreamCodec.checkProxyDeSerializationPermitted}.
     */
    private static void checkProxyDeSerializationPermitted(Class<?>[] interfaces) {
        checkProxyDeSerializationPermitted(interfaces, System.getSecurityManager());
    }

    /**
     * Testable seam for the nested-proxy {@code DeSerializationPermission("PROXY")} gate. The
     * permission is checked against an {@link AccessControlContext} built from the proxy
     * interfaces' protection domains, so a deployment governs which interface codebases may be
     * reconstructed as a proxy from an untrusted stream.
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
                    return new AccessControlContext(
                            domains.toArray(new ProtectionDomain[0]));
                });
        sm.checkPermission(PROXY, ctx);
    }

    private ObjectCodec() {
        throw new AssertionError("no instances");
    }

    // =========================================================================
    // Decode (DER bytes -> object)
    // =========================================================================

    /**
     * Decodes the DER bytes of one {@code @AtomicSerial} class's private SEQUENCE
     * and drives construction of an instance via the {@code (GetArg)} constructor.
     *
     * @param <T>             the type to construct
     * @param clazz           the leaf (and only, for Phase 4.1) {@code @AtomicSerial}
     *                        class; must have a {@code public T(AtomicSerial.GetArg)}
     *                        constructor
     * @param schema          the at-marshal-time schema for {@code clazz}
     * @param payloadSequence the complete DER encoding of the class's private SEQUENCE
     *                        TLV (outer SEQUENCE tag + length + content)
     * @return the constructed instance
     * @throws DerException           if the DER encoding is malformed or a wire type
     *                                is unsupported
     * @throws InvalidObjectException if the class's {@code check(GetArg)} invariant
     *                                enforcement fails
     * @throws IOException            if construction fails with an {@link IOException}
     * @throws NullPointerException   if any argument is {@code null}
     */
    public static <T> T decode(Class<T> clazz,
                                AtomicSerialSchemaRecord schema,
                                byte[] payloadSequence)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(payloadSequence, "payloadSequence");

        // 1. Build the field store for this class's SEQUENCE
        DerFieldStore store = new DerFieldStore(schema, payloadSequence);

        // 2. Assemble the single-entry DerGetArg
        Map<Class<?>, DerFieldStore> map = new LinkedHashMap<>();
        map.put(clazz, store);
        DerGetArg arg = new DerGetArg(map);

        // 3. Per-class DeSerializationPermission("ATOMIC") gate, then construct
        checkAtomicDeSerializationPermitted(map.keySet());
        MarshalDelegate delegate = MarshalDelegates.delegateFor(clazz);
        if (delegate != null) {
            // In-package construction via the class's own (GetArg) constructor;
            // exceptions propagate with their natural type (no reflective wrapping).
            @SuppressWarnings("unchecked")
            T created = (T) delegate.create(clazz, arg);
            return created;
        }
        Constructor<T> ctor = findGetArgConstructor(clazz);
        try {
            return ctor.newInstance(arg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InvalidObjectException ioe) {
                throw ioe;
            }
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            if (cause instanceof ClassNotFoundException cnfe) {
                throw cnfe;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            DerException de = new DerException(
                    "Construction of " + clazz.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
            // setAccessible(true) should prevent IllegalAccessException;
            // abstract classes should not reach here
            throw new AssertionError("Unexpected reflective access failure", ex);
        }
    }

    // =========================================================================
    // Hierarchy encode (Phase 4.3)
    // =========================================================================

    /**
     * Encodes an object from a {@code @AtomicSerial} hierarchy to DER bytes.
     *
     * <p>The returned bytes are a complete SEQUENCE TLV wrapping one private SEQUENCE
     * per {@code @AtomicSerial} class in the hierarchy, in <b>superclass-first
     * (root-first, leaf-last)</b> order. Each per-class SEQUENCE contains only the
     * fields declared by that class via its own {@code serialForm()}.
     *
     * <p>The {@code chain} must have been produced by
     * {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain(Class)} for the
     * leaf class. The chain order from {@link SchemaChain.Result#chain()} is
     * <em>leaf-first</em>; this method reverses it to <em>superclass-first</em> before
     * encoding so that the wire order matches the DerGetArg insertion order expected by
     * {@link #decodeHierarchy}.
     *
     * @param instance the object to encode; must be an instance of the leaf class
     * @param chain    the linked schema chain for the hierarchy (leaf-first from
     *                 {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain})
     * @return the complete DER encoding: outer SEQUENCE { per-class SEQUENCE ... }
     * @throws DerException         if a field type is unsupported or reflection fails
     * @throws NullPointerException if any argument is {@code null}
     */
    public static byte[] encodeHierarchy(Object instance,
                                          SchemaChain.Result chain)
            throws DerException {
        return encodeHierarchy(instance, chain, 0);
    }

    /**
     * Depth-aware overload used internally for nested object encoding.
     * The {@code depth} parameter counts nested {@code @AtomicSerial} object
     * fields; it is incremented each time {@link #encodeNested} recurses.
     * Throws {@link DerException} when {@code depth} exceeds {@link #MAX_NESTING}.
     */
    static byte[] encodeHierarchy(Object instance,
                                   SchemaChain.Result chain,
                                   int depth)
            throws DerException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(chain, "chain");
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }

        // chain.chain() is leaf-first; we need superclass-first (root-first) for wire order.
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // The schema chain was generated from instance.getClass(); resolve each level's
        // Class from the live instance's OWN already-loaded hierarchy rather than a by-name
        // load against ResolutionContext.NONE.  NONE resolves only against the system loader,
        // so a codebase (child-loader) class -- a downloaded smart proxy or Entry, e.g.
        // EntryRep -- is "already loaded" (the comment on loadClass) yet NOT findable by name
        // there, and the encode fails.  The schema levels are exactly the ancestors of the
        // live instance, so a name->Class map over its hierarchy is correct, loader-assumption
        // -free, and deterministic (a class's superclass chain and FQNs are fixed); it changes
        // neither the schema nor the encoded bytes, only how the sender locates its own classes.
        Map<String, Class<?>> hierarchy = new HashMap<String, Class<?>>();
        for (Class<?> c = instance.getClass(); c != null; c = c.getSuperclass()) {
            hierarchy.put(c.getName(), c);
        }
        List<byte[]> perClassSequences = new ArrayList<>(rootFirst.size());
        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            Class<?> cls = hierarchy.get(schemaRecord.className());
            if (cls == null) {
                throw new DerException(
                        "ObjectCodec: schema class '" + schemaRecord.className()
                        + "' is not in the runtime hierarchy of "
                        + instance.getClass().getName());
            }
            byte[] classSeq = encode(instance, cls, schemaRecord, depth);
            perClassSequences.add(classSeq);
        }

        // Wrap all per-class SEQUENCEs in an outer SEQUENCE
        return DerWriter.writeSequence(perClassSequences);
    }

    // =========================================================================
    // Hierarchy decode (Phase 4.3)
    // =========================================================================

    /**
     * Decodes DER bytes produced by {@link #encodeHierarchy} and constructs an
     * instance of the lowest {@code @AtomicSerial} class in the chain, which is
     * assignability-checked against {@code expectedSupertype}.
     *
     * <h2>Phase 4.4 -- non-{@code @AtomicSerial} class handling (S3.10)</h2>
     *
     * <p>The {@code chain} is produced by
     * {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain}, which
     * walks the hierarchy skipping any class not annotated {@code @AtomicSerial}.
     * Consequently the chain's first record ({@code chain.chain().get(0)}) names
     * the <em>lowest {@code @AtomicSerial} class</em> -- which may differ from the
     * {@code expectedSupertype} argument when a non-{@code @AtomicSerial} subclass
     * is passed in.
     *
     * <ul>
     *   <li><b>Non-{@code @AtomicSerial} subclass dropped to its superclass</b>
     *       (e.g. {@code Bar extends Foo}, only {@code Foo} is {@code @AtomicSerial}):
     *       {@code generateChain(Bar.class)} yields a chain whose leaf record is {@code Foo}.
     *       This method constructs a {@code Foo}, not a {@code Bar}. The caller passes
     *       {@code expectedSupertype = Bar.class} (or any supertype of {@code Foo}) -- it
     *       merely constrains what the caller may assign the result to. The decoded
     *       object's runtime class is exactly {@code Foo}.</li>
     *   <li><b>All-{@code @AtomicSerial} hierarchy (Phase 4.3)</b>:
     *       the chain leaf IS the passed class, so behaviour is unchanged.</li>
     * </ul>
     *
     * <p>The method reads each per-class SEQUENCE from the outer wrapper in
     * <b>superclass-first</b> order, builds a {@link DerFieldStore} per class, and
     * populates a {@link DerGetArg} with all stores (insertion order: superclass-first,
     * leaf-last -- matching {@code DerGetArg}'s documented contract and
     * {@code serialClasses()} order).
     *
     * <p>The <em>construct class</em>'s {@code (GetArg)} constructor is then invoked
     * with the populated {@link DerGetArg}. It chains up via {@code super(check(arg))},
     * and each level in the chain reads only its own private namespace through
     * StackWalker dispatch.
     *
     * @param <T>              the expected return supertype (may be broader than the
     *                         actual construct class; the construct class must be
     *                         assignable to this type)
     * @param expectedSupertype the expected supertype of the decoded result; used for
     *                         the assignability check only -- the actual class constructed
     *                         is the chain's leaf {@code @AtomicSerial} record
     * @param chain            the linked schema chain (leaf-first from
     *                         {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain})
     * @param hierarchyPayload the DER bytes produced by {@link #encodeHierarchy}
     * @return the constructed instance; its runtime class equals the chain's leaf
     *         {@code @AtomicSerial} class, which is assignable to {@code expectedSupertype}
     * @throws DerException           if the DER encoding is malformed, or if the
     *                                chain's construct class is not assignable to
     *                                {@code expectedSupertype}
     * @throws InvalidObjectException if any class's {@code check(GetArg)} fails
     * @throws IOException            if construction fails with IOException
     * @throws NullPointerException   if any argument is {@code null}
     */
    public static <T> T decodeHierarchy(Class<T> expectedSupertype,
                                         SchemaChain.Result chain,
                                         byte[] hierarchyPayload)
            throws DerException, IOException, ClassNotFoundException {
        return decodeHierarchy(expectedSupertype, chain, hierarchyPayload,
                               (DeserializationCompletion) null, ResolutionContext.NONE);
    }

    /**
     * As {@link #decodeHierarchy(Class, SchemaChain.Result, byte[])}, threading a
     * decode-unit completion token into the top-level {@code DerGetArg} (and, via the
     * {@code DerGetArg}, into any nested decode) so that a decoded DGC live reference can
     * register its batched {@code dirty} on the per-decode-unit token. {@code decodeUnit}
     * may be {@code null} (no DGC context).
     *
     * @param <T>              the constructed type
     * @param expectedSupertype the supertype the chain's construct class must be assignable to
     * @param chain            the linked schema chain (leaf-first)
     * @param hierarchyPayload the DER bytes produced by {@link #encodeHierarchy}
     * @param decodeUnit       the per-decode-unit completion sink, or {@code null}
     * @return the constructed instance
     * @throws DerException           if the DER encoding is malformed or unassignable
     * @throws InvalidObjectException if any class's {@code check(GetArg)} fails
     * @throws IOException            if construction fails with IOException
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     * @throws NullPointerException   if any of the first three arguments is {@code null}
     */
    public static <T> T decodeHierarchy(Class<T> expectedSupertype,
                                         SchemaChain.Result chain,
                                         byte[] hierarchyPayload,
                                         DeserializationCompletion decodeUnit,
                                         ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(expectedSupertype, "expectedSupertype");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");

        // chain.chain() is leaf-first; the first entry is the lowest @AtomicSerial class.
        // This may differ from expectedSupertype when a non-@AtomicSerial subclass was
        // passed to generateChain (S3.10, first rule: non-@AtomicSerial subclass is dropped).
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        String constructClassName = leafFirst.get(0).className();
        Class<?> constructClass = loadClass(constructClassName, resolution);

        // Assignability check: the constructed type must be a subtype of expectedSupertype.
        // When Bar extends Foo (Bar plain, Foo @AtomicSerial), constructClass = Foo,
        // expectedSupertype = Bar.class -> Foo IS a supertype of Bar, but Bar is NOT a
        // supertype of Foo. The correct check is: constructClass is assignable TO
        // expectedSupertype, meaning expectedSupertype.isAssignableFrom(constructClass).
        if (!expectedSupertype.isAssignableFrom(constructClass)) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: the chain's construct class '"
                    + constructClassName + "' is not assignable to the expected supertype '"
                    + expectedSupertype.getName() + "'. "
                    + "This chain was not generated for a class related to "
                    + expectedSupertype.getName() + ".");
        }

        // Reverse for superclass-first wire order
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // Read the outer SEQUENCE; it contains one child SEQUENCE per class (root-first)
        DerReader outer = new DerReader(hierarchyPayload);
        DerReader outerSeq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes after outer SEQUENCE");
        }

        // Build a DerFieldStore for each class, inserting superclass-first into the map
        Map<Class<?>, DerFieldStore> storeMap = new LinkedHashMap<>();
        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            Class<?> cls = loadClass(schemaRecord.className(), resolution);
            DerFieldStore store = new DerFieldStore(schemaRecord, outerSeq, resolution);
            storeMap.put(cls, store);
        }
        if (outerSeq.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes in outer SEQUENCE "
                    + "(more SEQUENCEs than schema records)");
        }

        // S3.9 / S11.6 namespace-isolation fix: every @AtomicSerial class in the
        // construct class's hierarchy whose (GetArg) constructor will run MUST have a
        // store entry, so DerGetArg.callerClass() resolves EACH level to its OWN
        // namespace. A class present in the receiver's hierarchy but ABSENT from the
        // embedded data (e.g. an @AtomicSerial class inserted AFTER the data was
        // written -- S11.6) gets an EMPTY store, so its arg.get(name, default) calls
        // return defaults. Without this, callerClass() would skip the absent class's
        // frame and resolve to the nearest neighbouring class's store -- leaking that
        // neighbour's namespace (proved by NamespaceLeakRegressionTest).
        for (Class<?> c = constructClass; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(AtomicSerial.class) && !storeMap.containsKey(c)) {
                storeMap.put(c, emptyFieldStore(c));
            }
        }

        // Assemble the multi-entry DerGetArg (superclass-first insertion order)
        DerGetArg arg = new DerGetArg(storeMap, 0, decodeUnit, resolution);

        // Per-class DeSerializationPermission("ATOMIC") gate: every @AtomicSerial
        // class in the hierarchy whose (GetArg) constructor will run must be permitted.
        checkAtomicDeSerializationPermitted(storeMap.keySet());

        // Invoke the CONSTRUCT CLASS's (GetArg) constructor -- it chains up via super(check(arg)).
        // For all-@AtomicSerial hierarchies (Phase 4.3) this is the same as the old leafClass.
        // For non-@AtomicSerial subclass dropped to its @AtomicSerial superclass, this is the
        // @AtomicSerial superclass (e.g. Foo, not Bar).
        MarshalDelegate delegate = MarshalDelegates.delegateFor(constructClass);
        if (delegate != null) {
            // In-package construction; the (GetArg) ctor chains up via super(check(arg)).
            @SuppressWarnings("unchecked")
            T created = (T) delegate.create(constructClass, arg);
            return created;
        }
        @SuppressWarnings("unchecked")
        Constructor<? extends T> ctor = (Constructor<? extends T>)
                findGetArgConstructor(constructClass);
        try {
            return ctor.newInstance(arg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InvalidObjectException ioe) throw ioe;
            if (cause instanceof IOException ioe) throw ioe;
            if (cause instanceof ClassNotFoundException cnfe) throw cnfe;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            DerException de = new DerException(
                    "Construction of " + constructClass.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
            throw new AssertionError("Unexpected reflective access failure", ex);
        }
    }

    // =========================================================================
    // Encode (object -> DER bytes)
    // =========================================================================

    /**
     * Encodes one {@code @AtomicSerial} class's serialisable state to the DER
     * bytes of its private SEQUENCE.
     *
     * <p>The method invokes the class's {@code serialForm()} to discover the
     * ordered list of fields, then reads each field's value by reflecting the
     * declared field with the same name in {@code declaringClass}. The assumption
     * that the wire name equals the Java field name holds for standard
     * {@code @AtomicSerial} implementations; it is documented here explicitly
     * because it is NOT a DER constraint but a coding convention.
     *
     * <p>The encoded bytes are a complete SEQUENCE TLV: tag {@code 0x30} + length
     * + ordered field TLVs. This is exactly what {@link DerFieldStore} expects as
     * {@code payloadSequence} in its byte-array constructor.
     *
     * @param instance       the object whose state is to be encoded; must be an
     *                       instance of {@code declaringClass}
     * @param declaringClass the {@code @AtomicSerial} class whose private SEQUENCE
     *                       is being encoded; must have a {@code public static
     *                       SerialForm[] serialForm()} method
     * @param schema         the schema for {@code declaringClass} (used to obtain
     *                       the ordered field definitions and wire types)
     * @return the complete DER encoding of the private SEQUENCE TLV
     * @throws DerException         if a field type is unsupported or reflection fails
     * @throws NullPointerException if any argument is {@code null}
     */
    public static byte[] encode(Object instance,
                                 Class<?> declaringClass,
                                 AtomicSerialSchemaRecord schema)
            throws DerException {
        return encode(instance, declaringClass, schema, 0);
    }

    /** Depth-aware encode -- called from the depth-aware encodeHierarchy. */
    private static byte[] encode(Object instance,
                                  Class<?> declaringClass,
                                  AtomicSerialSchemaRecord schema,
                                  int depth)
            throws DerException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(schema, "schema");

        // A @Stateless @AtomicSerial class has no serial fields and (per the annotation
        // contract) implements no serialize(PutArg); it contributes an empty private
        // SEQUENCE. Its schema record likewise has no fields (see SchemaGenerator).
        if (declaringClass.isAnnotationPresent(AtomicSerial.Stateless.class)) {
            return DerWriter.writeSequence(Collections.emptyList());
        }

        // @AtomicSerial WRITE contract: the class declares its serial form via its own
        // serialize(PutArg) method. The codec NEVER reflects on private fields (that would
        // imitate Java Object Serialization and reintroduce its security problems); a class
        // without serialize(PutArg) is not serialized -- the encoder fails fast.
        Map<String, Object> values = invokeSerialize(instance, declaringClass);

        List<byte[]> fieldTlvs = new ArrayList<>();
        for (AtomicSerialFieldDef fieldDef : schema.fields()) {
            String wireName = fieldDef.wireName();
            String wireType = fieldDef.wireType();
            if (!values.containsKey(wireName)) {
                throw new DerException(
                        "Class " + declaringClass.getName()
                        + ".serialize(PutArg) did not put serial field '" + wireName
                        + "' declared by serialForm(); serialize must put every serial field");
            }
            Object value = values.get(wireName);
            byte[] tlv = encodeValue(value, wireType, wireName, depth);
            fieldTlvs.add(tlv);
        }
        return DerWriter.writeSequence(fieldTlvs);
    }

    /**
     * Invokes {@code declaringClass}'s {@code public static void serialize(PutArg, T)} --
     * the {@code @AtomicSerial} WRITE contract -- and returns the captured field values.
     *
     * <p>Each class in the hierarchy contributes ONLY its own namespace (its own
     * {@code serialForm()} fields) through its own {@code serialize}; a class that does
     * not implement {@code @AtomicSerial} has no namespace and is not serialized.
     *
     * @throws DerException if the class has no conforming {@code serialize(PutArg, T)}
     *                      method (fail-fast: the codec does NOT fall back to field
     *                      reflection), or if {@code serialize} throws.
     */
    private static Map<String, Object> invokeSerialize(Object instance, Class<?> declaringClass)
            throws DerException {
        MarshalDelegate delegate = MarshalDelegates.delegateFor(declaringClass);
        if (delegate != null) {
            // In-package dispatch: invoke the class's own serialize(PutArg, T) with
            // no reflection into its package-private members; reflection is the fallback.
            DerPutArg putArg = new DerPutArg();
            try {
                delegate.serialize(declaringClass, putArg, instance);
            } catch (DerException de) {
                throw de;
            } catch (IOException ex) {
                DerException de = new DerException(
                        "serialize(PutArg) of " + declaringClass.getName() + " failed: " + ex);
                de.initCause(ex);
                throw de;
            }
            return putArg.captured();
        }
        Method serialize = serializeMethod(declaringClass); // cached + validated reflective fallback
        DerPutArg putArg = new DerPutArg();
        try {
            serialize.invoke(null, putArg, instance);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof DerException de) {
                throw de;
            }
            DerException de = new DerException(
                    "serialize(PutArg) of " + declaringClass.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (ReflectiveOperationException ex) {
            throw new DerException(
                    "Unable to invoke serialize(PutArg) of " + declaringClass.getName(), ex);
        }
        return putArg.captured();
    }

    // =========================================================================
    // S3.11 Data Independence: decode to named field map WITHOUT loading classes
    // =========================================================================

    /**
     * Decodes a DER hierarchy payload into a named field map given ONLY the DER
     * bytes and the {@link SchemaChain.Result} -- <b>without loading any class from
     * the originating codebase</b>. This satisfies the S3.11 normative requirement
     * for data independence.
     *
     * <p>The returned map is keyed by class name (as declared in each
     * {@link AtomicSerialSchemaRecord}). Each value is an ordered map from field
     * name to decoded Java value, corresponding exactly to the state that
     * {@link DerGetArg} would hold during construction: all fields present in the
     * schema, each bound to its decoded value. Fields absent from the payload (case
     * (b): schema newer than data) appear as absent in
     * {@link DerFieldStore#presentFields()} and are not included in the inner map.
     *
     * <p>No class is loaded; no constructor is invoked; no {@code check(GetArg)} is
     * run. The decode is purely structural: schema -> DER -> named values. This is
     * the S3.11 claim in executable form.
     *
     * <pre>
     * SEQUENCE {          -- outer hierarchy SEQUENCE (produced by encodeHierarchy)
     *   SEQUENCE { ... }  -- root-class private SEQUENCE (first on wire)
     *   ...
     *   SEQUENCE { ... }  -- leaf-class private SEQUENCE (last on wire)
     * }
     * </pre>
     *
     * @param chain            the schema chain for the hierarchy (leaf-first from
     *                         {@link SchemaGenerator#generateChain}); class names
     *                         must match the names used at encode time
     * @param hierarchyPayload the DER bytes produced by {@link #encodeHierarchy}
     * @return an ordered map: className -> (fieldName -> decoded value), in
     *         superclass-first hierarchy order; each inner map preserves schema
     *         field order
     * @throws DerException         if the DER encoding is malformed or a wire type
     *                              is unsupported
     * @throws NullPointerException if any argument is {@code null}
     */
    public static java.util.LinkedHashMap<String, java.util.Map<String, Object>>
            decodeToFieldMap(SchemaChain.Result chain,
                             byte[] hierarchyPayload) throws DerException {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");

        // chain.chain() is leaf-first; the wire is superclass-first (root-first).
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // Read the outer SEQUENCE; it contains one child SEQUENCE per class (root-first).
        DerReader outer = new DerReader(hierarchyPayload);
        DerReader outerSeq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("decodeToFieldMap: trailing bytes after outer SEQUENCE");
        }

        // Result map: class name -> field name -> value, in superclass-first order.
        java.util.LinkedHashMap<String, java.util.Map<String, Object>> result =
                new java.util.LinkedHashMap<>(rootFirst.size() * 2);

        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            // Build a DerFieldStore for this class's SEQUENCE from the outer reader.
            // This advances outerSeq past the per-class SEQUENCE TLV.
            DerFieldStore store = new DerFieldStore(schemaRecord, outerSeq);
            // Extract the present fields as an ordered map.
            result.put(schemaRecord.className(), store.presentFields());
        }

        if (outerSeq.hasMore()) {
            throw new DerException("decodeToFieldMap: trailing bytes in outer SEQUENCE "
                    + "(more SEQUENCEs than schema records)");
        }

        return result;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Finds and returns the {@code C(AtomicSerial.GetArg)} deserialization constructor,
     * making it accessible.
     *
     * <p>The constructor need NOT be public: an {@code @AtomicSerial} deserialization
     * constructor is conventionally non-public (package-private or protected) because it
     * is invoked only by the deserialization framework, never by user code (e.g.
     * {@code net.jini.jeri.BasicObjectEndpoint}'s is package-private). This mirrors the
     * JOSS/atomic path, which likewise reaches the declared constructor via
     * {@code setAccessible}. The security boundary is unchanged: only {@code @AtomicSerial}
     * classes are ever constructed, and {@code check(GetArg)} still runs first.
     */
    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> findGetArgConstructor(Class<T> clazz)
            throws DerException {
        Object v = GETARG_CTOR.get(clazz);
        if (v instanceof Constructor<?> ctor) {
            return (Constructor<T>) ctor;
        }
        throw new DerException((String) v);
    }

    // -------------------------------------------------------------------------
    // Per-class reflection caches for the delegate-less fallback path (the common case uses a
    // MarshalDelegate -- no reflection). Memoised in ClassValue: the resolved (validated) member on
    // success, the failure message otherwise. ClassValue is keyed by the defining class and GC-tied
    // to its lifetime, so there is no leak and no per-object getDeclared* lookup on the hot path.
    // (Accessibility is unchanged from the prior inline lookups -- no setAccessible is added here.)
    // -------------------------------------------------------------------------

    /** Cached {@code (AtomicSerial.GetArg)} constructor (or a failure message) per class. */
    private static final ClassValue<Object> GETARG_CTOR = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> clazz) {
            try {
                Constructor<?> ctor = clazz.getDeclaredConstructor(AtomicSerial.GetArg.class);
                String sv = MarshalDelegates.strictBlockCtor(clazz, ctor.getModifiers());
                if (sv != null) return sv;
                return ctor;
            } catch (NoSuchMethodException ex) {
                return "Class " + clazz.getName()
                        + " has no (AtomicSerial.GetArg) deserialization constructor";
            }
        }
    };

    /** Cached {@code static serialize(PutArg, T)} method (or a failure message) per class. */
    private static final ClassValue<Object> SERIALIZE_METHOD = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> clazz) {
            Method m;
            try {
                m = clazz.getDeclaredMethod("serialize", AtomicSerial.PutArg.class, clazz);
            } catch (NoSuchMethodException ex) {
                return "Class " + clazz.getName()
                        + " has no 'public static void serialize(AtomicSerial.PutArg, "
                        + clazz.getSimpleName() + ")' method. Every @AtomicSerial class"
                        + " MUST implement the serialize(PutArg) write contract; the DER codec"
                        + " does not read fields by reflection.";
            }
            if (!Modifier.isStatic(m.getModifiers())) {
                return "Class " + clazz.getName()
                        + " serialize(PutArg, " + clazz.getSimpleName() + ") must be static";
            }
            String sv = MarshalDelegates.strictBlockClass(clazz, "serialize(PutArg, T)");
            if (sv != null) return sv;
            return m;
        }
    };

    private static Method serializeMethod(Class<?> declaringClass) throws DerException {
        Object v = SERIALIZE_METHOD.get(declaringClass);
        if (v instanceof Method m) {
            return m;
        }
        throw new DerException((String) v);
    }

    /**
     * Encodes a Java value to its DER TLV according to the declared wire type.
     *
     * <p>Type mapping (as per STD-006 and {@code SchemaGenerator.toWireType}):
     * <ul>
     *   <li>{@code "boolean"} / {@code "java.lang.Boolean"} -> BOOLEAN</li>
     *   <li>{@code "byte"} / {@code "java.lang.Byte"} -> INTEGER</li>
     *   <li>{@code "short"} / {@code "java.lang.Short"} -> INTEGER</li>
     *   <li>{@code "int"} / {@code "java.lang.Integer"} -> INTEGER</li>
     *   <li>{@code "long"} / {@code "java.lang.Long"} -> INTEGER</li>
     *   <li>{@code "java.lang.String"} -> UTF8String</li>
     *   <li>{@code "byte[]"} / {@code "[B"} -> OCTET STRING</li>
     *   <li>{@code "@AtomicSerial"} -> SEQUENCE{schemaBytes, payloadBytes} or NULL</li>
     * </ul>
     */
    private static byte[] encodeValue(Object value, String wireType,
                                       String fieldName) throws DerException {
        return encodeValue(value, wireType, fieldName, 0);
    }

    private static byte[] encodeValue(Object value, String wireType,
                                       String fieldName, int depth) throws DerException {
        // Enum fields (STD-008 sec.17.1): "enum:<className>"
        if (wireType.startsWith("enum:")) {
            return encodeEnum(value, wireType, fieldName);
        }
        // Array fields (STD-008 sec.17.2): "array:<componentWireType>"
        // (includes "array:@AtomicSerial:<class>")
        if (wireType.startsWith("array:")) {
            return encodeArray(value, wireType, fieldName, depth);
        }

        // A nullable scalar reference field (boxed primitive, String, byte[], or a nested
        // @AtomicSerial value) whose value is null travels as DER NULL. A primitive field is
        // never null at encode (its captured value is autoboxed), so this only fires for a
        // nullable reference field. (Enum/array null is handled by encodeEnum/encodeArray above;
        // a null @AtomicSerial nested value yields the same DER NULL as encodeNested.)
        if (value == null) {
            return new byte[]{0x05, 0x00};
        }

        return switch (wireType) {
            case "boolean", "java.lang.Boolean" -> {
                if (!(value instanceof Boolean b)) {
                    throw new DerException("Expected Boolean for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeBoolean(b);
            }
            case "byte", "java.lang.Byte" -> {
                if (!(value instanceof Byte b)) {
                    throw new DerException("Expected Byte for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(b));
            }
            case "short", "java.lang.Short" -> {
                if (!(value instanceof Short s)) {
                    throw new DerException("Expected Short for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(s));
            }
            case "int", "java.lang.Integer" -> {
                if (!(value instanceof Integer iv)) {
                    throw new DerException("Expected Integer for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(iv));
            }
            case "long", "java.lang.Long" -> {
                if (!(value instanceof Long l)) {
                    throw new DerException("Expected Long for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(l));
            }
            case "java.lang.String" -> {
                if (!(value instanceof String s)) {
                    throw new DerException("Expected String for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeUtf8String(s);
            }
            case "byte[]", "[B" -> {
                if (!(value instanceof byte[] b)) {
                    throw new DerException("Expected byte[] for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeOctetString(b);
            }

            // STD-008 sec.17.3 -- float/double/char with STRICT canonicalization
            case "float" -> encodeFloat(value, fieldName);
            case "double" -> encodeDouble(value, fieldName);
            case "char" -> encodeChar(value, fieldName);

            // Nested @AtomicSerial object field (STD-008 sec.16)
            case "@AtomicSerial" -> encodeNested(value, fieldName, depth);
            default -> throw new DerException(
                    "ObjectCodec: unsupported wire type '" + wireType
                    + "' for field '" + fieldName + "'");
        };
    }

    /**
     * IEEE-754 canonical NaN bit patterns (STD-008 sec.17.3.1). Every encoder MUST emit
     * these for NaN; every decoder MUST reject any other NaN bit pattern. This is the
     * quiet-NaN bit pattern every mainstream language produces by default (Java
     * {@code Float.NaN}, Rust {@code f32::NAN.to_bits()}, C {@code NAN}).
     */
    static final int  CANONICAL_FLOAT_NAN_BITS  = 0x7FC00000;
    static final long CANONICAL_DOUBLE_NAN_BITS = 0x7FF8000000000000L;

    /** {@code +0.0} bit pattern -- the canonical zero. {@code -0.0} is rejected on the wire. */
    static final int  POSITIVE_ZERO_FLOAT_BITS  = 0x00000000;
    static final long POSITIVE_ZERO_DOUBLE_BITS = 0x0000000000000000L;
    /** {@code -0.0} bit pattern -- rejected on decode; canonicalized to {@code +0.0} on encode. */
    static final int  NEGATIVE_ZERO_FLOAT_BITS  = 0x80000000;
    static final long NEGATIVE_ZERO_DOUBLE_BITS = 0x8000000000000000L;

    private static byte[] encodeFloat(Object value, String fieldName) throws DerException {
        if (!(value instanceof Float f)) {
            throw new DerException("Expected Float for field '" + fieldName
                    + "' (wireType float) but got "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        int bits;
        if (Float.isNaN(f)) {
            bits = CANONICAL_FLOAT_NAN_BITS;            // canonicalize ANY NaN to canonical
        } else if (Float.floatToRawIntBits(f) == NEGATIVE_ZERO_FLOAT_BITS) {
            bits = POSITIVE_ZERO_FLOAT_BITS;            // canonicalize -0.0 to +0.0
        } else {
            bits = Float.floatToRawIntBits(f);          // raw bits preserve all finite values
        }
        byte[] content = new byte[] {
                (byte)(bits >>> 24), (byte)(bits >>> 16),
                (byte)(bits >>>  8), (byte) bits
        };
        return DerWriter.writeOctetString(content);
    }

    private static byte[] encodeDouble(Object value, String fieldName) throws DerException {
        if (!(value instanceof Double d)) {
            throw new DerException("Expected Double for field '" + fieldName
                    + "' (wireType double) but got "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        long bits;
        if (Double.isNaN(d)) {
            bits = CANONICAL_DOUBLE_NAN_BITS;
        } else if (Double.doubleToRawLongBits(d) == NEGATIVE_ZERO_DOUBLE_BITS) {
            bits = POSITIVE_ZERO_DOUBLE_BITS;
        } else {
            bits = Double.doubleToRawLongBits(d);
        }
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) {
            content[i] = (byte)(bits & 0xFF);
            bits >>>= 8;
        }
        return DerWriter.writeOctetString(content);
    }

    private static byte[] encodeChar(Object value, String fieldName) throws DerException {
        if (!(value instanceof Character c)) {
            throw new DerException("Expected Character for field '" + fieldName
                    + "' (wireType char) but got "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        int cp = c.charValue();
        // Surrogate code units are legal Java char values but NOT valid Unicode codepoints.
        // The wire is clean -- leaky abstraction stays on the Java side. STD-008 sec.17.3.2.
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            throw new DerException("Field '" + fieldName
                    + "' (wireType char): unpaired surrogate code unit 0x"
                    + Integer.toHexString(cp).toUpperCase()
                    + " is not a valid Unicode codepoint");
        }
        return DerWriter.writeInteger(BigInteger.valueOf(cp));
    }

    /**
     * Encodes an enum field value to its DER TLV (STD-008 sec.17.1).
     *
     * <p>Wire format: DER NULL for {@code null}, or DER UTF8String containing the
     * constant name (e.g. {@code "RED"} for {@code Color.RED}).
     *
     * @param value     the enum value (may be null)
     * @param wireType  the wireType string, e.g. {@code "enum:com.example.Color"}
     * @param fieldName used in error messages
     * @return the DER TLV bytes
     * @throws DerException if the value is non-null but is not an instance of the
     *                      declared enum class
     */
    private static byte[] encodeEnum(Object value, String wireType,
                                      String fieldName) throws DerException {
        if (value == null) {
            return new byte[]{0x05, 0x00}; // DER NULL
        }
        if (!(value instanceof Enum<?> ev)) {
            throw new DerException(
                    "ObjectCodec: expected Enum for field '" + fieldName
                    + "' (wireType " + wireType + ") but got "
                    + value.getClass().getName());
        }
        // Type-check: runtime class must match the declared enum class in the wireType.
        String className = wireType.substring(5); // strip "enum:"
        Class<?> declaredClass = loadClass(className, ResolutionContext.NONE);
        if (!declaredClass.isInstance(value)) {
            throw new DerException(
                    "ObjectCodec: enum field '" + fieldName
                    + "' has declared class '" + className
                    + "' but got runtime class '" + value.getClass().getName() + "'");
        }
        return DerWriter.writeUtf8String(ev.name());
    }

    /**
     * Encodes an array field value to its DER TLV (STD-008 sec.17.2).
     *
     * <p>Wire format: DER NULL for {@code null}, or a DER SEQUENCE of N element TLVs
     * (one per array element, in index order). Empty array encodes as a SEQUENCE of
     * length 0. Nullable element types (String, enum, @AtomicSerial) encode each
     * null element as DER NULL.
     *
     * @param value     the array value (may be null)
     * @param wireType  the wireType string, e.g. {@code "array:int"},
     *                  {@code "array:java.lang.String"},
     *                  {@code "array:@AtomicSerial:com.example.Foo"}
     * @param fieldName used in error messages
     * @param depth     current nesting depth (threaded into per-element nested encode)
     * @return the DER TLV bytes
     * @throws DerException if the value is not an array, or if encoding any element fails
     */
    private static byte[] encodeArray(Object value, String wireType,
                                       String fieldName, int depth) throws DerException {
        if (value == null) {
            return new byte[]{0x05, 0x00}; // DER NULL
        }
        if (!value.getClass().isArray()) {
            throw new DerException(
                    "ObjectCodec: expected array for field '" + fieldName
                    + "' (wireType " + wireType + ") but got "
                    + value.getClass().getName());
        }

        // Strip "array:" prefix to get the component wireType.
        // Note: "array:@AtomicSerial:<class>" strips to "@AtomicSerial:<class>",
        // which we match with startsWith("@AtomicSerial").
        String componentWT = wireType.substring(6); // strip "array:"
        int n = Array.getLength(value);
        List<byte[]> elementTlvs = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Object elem = Array.get(value, i);
            String elemFieldName = fieldName + "[" + i + "]";
            byte[] elemTlv;
            if (componentWT.startsWith("@AtomicSerial")) {
                // @AtomicSerial element: use encodeNested (handles null and depth-bound)
                elemTlv = encodeNested(elem, elemFieldName, depth);
            } else if (componentWT.equals("java.lang.String")) {
                // String element: DER NULL if null, else UTF8String
                elemTlv = (elem == null)
                        ? new byte[]{0x05, 0x00}
                        : DerWriter.writeUtf8String((String) elem);
            } else {
                // Primitive or enum element: delegate to encodeValue.
                // For enum elements the componentWT is "enum:<class>", which encodeEnum handles.
                // For primitive elements, elem is always non-null (Java arrays of primitives
                // contain their boxed form when retrieved via Array.get).
                elemTlv = encodeValue(elem, componentWT, elemFieldName, depth);
            }
            elementTlvs.add(elemTlv);
        }

        return DerWriter.writeSequence(elementTlvs);
    }

    /**
     * Encodes a top-level (non-{@code byte[]}) array as the self-describing content of a
     * {@code [9] CTX_ARRAY} stream TLV: {@code UTF8String(arrayWireType) ++ SEQUENCE(elements)}.
     * The component wire-type travels so the decoder reconstructs the typed array without the
     * method signature -- the value-array parallel of {@code byte[]} (CTX_BYTES) and {@code enum}
     * (CTX_ENUM). The element {@code SEQUENCE} is byte-identical to an {@code @AtomicSerial} array
     * <em>field</em> ({@link #encodeArray}), so a {@code long[]} return value and a {@code long[]}
     * field encode the same way. Primitive, {@code String}, enum and {@code @AtomicSerial} component
     * types are supported (one dimension; STD-006 sec.17.2). {@code byte[]} is NOT routed here
     * (it stays OCTET STRING via CTX_BYTES).
     *
     * @param array a non-null array whose component is not {@code byte}
     * @return the CTX_ARRAY content bytes
     * @throws DerException if {@code array} is not an array, is a {@code byte[]}, or has an
     *                      unsupported component type
     */
    public static byte[] encodeTopLevelArray(Object array) throws DerException {
        if (array == null || !array.getClass().isArray()) {
            throw new DerException("ObjectCodec.encodeTopLevelArray: not an array: "
                    + (array == null ? "null" : array.getClass().getName()));
        }
        String arrayWireType = SchemaGenerator.toWireType(array.getClass(), array.getClass());
        if (!arrayWireType.startsWith("array:")) {
            // byte[] maps to "byte[]" (CTX_BYTES handles it); anything else is a caller bug.
            throw new DerException("ObjectCodec.encodeTopLevelArray: unsupported top-level array '"
                    + array.getClass().getName() + "' (wireType " + arrayWireType + ")");
        }
        byte[] wtTlv   = DerWriter.writeUtf8String(arrayWireType);
        byte[] elemSeq = encodeArray(array, arrayWireType, "<top-level array>", 0);
        byte[] content = new byte[wtTlv.length + elemSeq.length];
        System.arraycopy(wtTlv,   0, content, 0,            wtTlv.length);
        System.arraycopy(elemSeq, 0, content, wtTlv.length, elemSeq.length);
        return content;
    }

    /**
     * Loads a class by name using the ResolutionContext.
     * Used by hierarchy encode/decode to resolve class
     * names from schema records.
     *
     * @param className the fully-qualified class name
     * @return the loaded class
     * @throws DerException if the class cannot be found
     */
    private static Class<?> loadClass(String className, ResolutionContext res) throws DerException {
        try {
            // Endpoint-assigned resolution via ClassLoading -- NEVER the thread-context loader
            // (the Warres ambient-resolution failure: wrong local copy, same-name type conflicts,
            // broken under OSGi). DER carries no codebase, so the name resolves against the
            // endpoint's defaultLoader through the preferred/OSGi-aware SPI. The ENCODE path,
            // whose classes are already loaded (ancestors of the live instance), passes NONE.
            return res.loadClass(className);
        } catch (ClassNotFoundException ex) {
            throw new DerException(
                    "ObjectCodec: cannot load class '" + className + "'", ex);
        }
    }

    /**
     * Builds an EMPTY {@link DerFieldStore} for {@code cls} -- a store over a schema
     * with no fields and an empty SEQUENCE payload. All {@code get(name, default)}
     * calls against it return the default; {@code defaulted(name)} is always true.
     *
     * <p>Used by {@link #decodeHierarchy} to register an absent class's namespace
     * (e.g. an @AtomicSerial class inserted after the data was written, S11.6) so
     * that {@code DerGetArg} dispatch resolves that class to its own (empty) store
     * rather than leaking a neighbour's namespace (S3.9).
     */
    private static DerFieldStore emptyFieldStore(Class<?> cls) throws DerException {
        AtomicSerialSchemaRecord emptySchema =
                new AtomicSerialSchemaRecord(cls.getName(), (byte[]) null, List.of());
        return new DerFieldStore(emptySchema, DerWriter.writeSequence(List.<byte[]>of()));
    }

    // =========================================================================
    // Nested @AtomicSerial field encode/decode (STD-008 sec.16)
    // =========================================================================

    /**
     * Wire format for a nested {@code @AtomicSerial} field:
     * <pre>
     * SEQUENCE {
     *   OCTET STRING -- schemaChainBytes (leaf-first concatenated AtomicSerialSchemaRecord DERs)
     *   OCTET STRING -- payloadBytes (ObjectCodec.encodeHierarchy output)
     * }
     * </pre>
     * A null value encodes as DER NULL (0x05 0x00).
     *
     * <p>The schema chain is encoded WITHOUT using {@code der.marshal} (which would
     * create a package cycle), by directly concatenating each record's
     * {@link AtomicSerialSchemaRecord#encode()} in leaf-first order -- the same
     * logic as {@code MarshalledInstanceRecord.encodeChainBytes}.
     *
     * @param value     the nested object value (may be null)
     * @param fieldName used in error messages only
     * @param depth     current nesting depth; incremented before the recursive call
     * @return the TLV bytes for this nested field
     */
    private static byte[] encodeNested(Object value, String fieldName, int depth)
            throws DerException {
        // null -> DER NULL
        if (value == null) {
            return new byte[]{0x05, 0x00};
        }

        // DER replacement: a registered non-@AtomicSerial value (e.g. an
        // AccessControlContext) is substituted with its @AtomicSerial serializer
        // before encoding; unregistered/already-@AtomicSerial values pass through.
        try {
            value = au.net.zeus.jgdms.der.serial.DerReplacer.replace(value);
        } catch (java.io.IOException e) {
            throw new DerException("DER replacement failed for nested field '"
                    + fieldName + "': " + e.getMessage(), e);
        }

        // Depth check BEFORE recursing (depth + 1 will be the child's depth)
        if (depth + 1 > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec: nesting depth " + (depth + 1)
                    + " exceeds MAX_NESTING (" + MAX_NESTING
                    + ") for nested field '" + fieldName + "'");
        }

        Class<?> cls = value.getClass();

        // A nested java.lang.reflect.Proxy field value (e.g. AdminProxy.admin, declared as the
        // OutriggerAdmin remote interface but holding a dynamic Proxy over an @AtomicSerial
        // InvocationHandler) is encoded as a [8] CTX_PROXY field record: interface names + the
        // @AtomicSerial handler, reconstructed at decode via Proxy.newProxyInstance. This mirrors
        // the object-stream top-level [8] in DerObjectStreamCodec; the handler rides as a nested
        // @AtomicSerial value, so it reuses the nested path's depth bound, ResolutionContext and
        // DGC decode-unit threading. (Resolved BEFORE nearestAtomicSerial below, since a Proxy's
        // own hierarchy -- Proxy -> Object -- carries no @AtomicSerial class.)
        if (Proxy.isProxyClass(cls)) {
            return encodeProxy(value, fieldName, depth);
        }

        // S3.10 wire-visibility: a value whose runtime class is not itself @AtomicSerial but
        // which extends an @AtomicSerial class is encoded as that @AtomicSerial superclass (its
        // subclass-only state is not wire-visible) -- exactly as the top-level decodeHierarchy
        // drops a non-@AtomicSerial subclass to its @AtomicSerial superclass. This is what lets
        // a final DerMarshalledInstance value travel as its @AtomicSerial MarshalledInstance
        // superclass and decode as a base MarshalledInstance via ServiceLoader dispatch
        // (@AtomicSerial is NOT @Inherited, so isAnnotationPresent on the subclass is false).
        // Require SOME @AtomicSerial class in the hierarchy, else fail clearly (a non-proxy value
        // with no @AtomicSerial ancestor is rejected here; a dynamic Proxy is handled above).
        if (nearestAtomicSerial(cls) == null) {
            throw new DerException(
                    "ObjectCodec: nested field '" + fieldName
                    + "' has wireType @AtomicSerial but runtime type "
                    + cls.getName() + " has no @AtomicSerial class in its hierarchy");
        }

        // Generate chain and encode payload (generateChain walks to the @AtomicSerial leaf)
        SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
        byte[] payloadBytes = encodeHierarchy(value, chain, depth + 1);

        // Encode the schema chain: leaf-first concatenated AtomicSerialSchemaRecord DERs
        byte[] schemaChainBytes = encodeSchemaChainBytes(chain.chain());

        // Wrap as SEQUENCE { OCTET STRING(schemaChainBytes), OCTET STRING(payloadBytes) }
        List<byte[]> children = new ArrayList<>(2);
        children.add(DerWriter.writeOctetString(schemaChainBytes));
        children.add(DerWriter.writeOctetString(payloadBytes));
        return DerWriter.writeSequence(children);
    }

    /**
     * Encodes a nested {@code java.lang.reflect.Proxy} field value as a {@code [8]} CTX_PROXY
     * record: {@code INTEGER(interfaceCount) ++ UTF8String(interfaceName)* ++ <nested handler>},
     * where the handler is its {@code @AtomicSerial} {@link InvocationHandler} carried via
     * {@link #encodeNested} (so the proxy occupies one nesting level and the handler the next).
     * The interface list travels because a proxy may implement more interfaces than the declared
     * field type names. At decode {@link #decodeProxy} resolves the interfaces and rebuilds the
     * proxy via {@code Proxy.newProxyInstance}.
     *
     * @param proxy     the dynamic proxy value (caller has verified {@code Proxy.isProxyClass})
     * @param fieldName the field name (diagnostics)
     * @param depth     the proxy's nesting depth; the handler is encoded at {@code depth + 1}
     */
    private static byte[] encodeProxy(Object proxy, String fieldName, int depth)
            throws DerException {
        Class<?>[] ifaces = proxy.getClass().getInterfaces();
        if (ifaces.length == 0 || ifaces.length > MAX_PROXY_INTERFACES) {
            throw new DerException("ObjectCodec: nested proxy field '" + fieldName
                    + "' interface count " + ifaces.length
                    + " out of range (1.." + MAX_PROXY_INTERFACES + ")");
        }
        InvocationHandler h = Proxy.getInvocationHandler(proxy);
        if (nearestAtomicSerial(h.getClass()) == null) {
            throw new DerException("ObjectCodec: nested proxy field '" + fieldName
                    + "' InvocationHandler " + h.getClass().getName()
                    + " is not @AtomicSerial");
        }
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(DerWriter.writeInteger(BigInteger.valueOf(ifaces.length)));
        for (Class<?> i : ifaces) {
            content.writeBytes(DerWriter.writeUtf8String(i.getName()));
        }
        // Handler as a nested @AtomicSerial value (depth + 1): reuses the depth bound,
        // ResolutionContext and DGC decode-unit threading of the nested-field path.
        content.writeBytes(encodeNested(h, fieldName + ".proxyHandler", depth + 1));
        return DerWriter.writeTlv(CTX_PROXY, content.toByteArray());
    }

    /** The nearest class in {@code c}'s hierarchy annotated {@code @AtomicSerial}, or null if none. */
    private static Class<?> nearestAtomicSerial(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (k.isAnnotationPresent(AtomicSerial.class)) return k;
        }
        return null;
    }

    /**
     * Decodes a nested {@code @AtomicSerial} field record as produced by
     * {@link #encodeNested}.
     *
     * <p>Called from {@link DerGetArg#get(String, Object)} when the field store
     * reports the field as nested (wireType {@code "@AtomicSerial"}).
     *
     * @param nestedRecordBytes the raw bytes of the nested record TLV (SEQUENCE or NULL)
     * @param depth             current nesting depth; used for the DoS guard
     * @return the decoded object, or {@code null} for a DER NULL encoding
     * @throws DerException if the encoding is malformed or depth exceeded
     * @throws IOException  if construction fails
     */
    public static Object decodeNested(byte[] nestedRecordBytes, int depth)
            throws DerException, IOException, ClassNotFoundException {
        return decodeNested(nestedRecordBytes, depth, null);
    }

    /** As {@link #decodeNested(byte[], int, DeserializationCompletion, ResolutionContext)} with no endpoint resolution context ({@link ResolutionContext#NONE}). */
    public static Object decodeNested(byte[] nestedRecordBytes, int depth,
                                      DeserializationCompletion decodeUnit)
            throws DerException, IOException, ClassNotFoundException {
        return decodeNested(nestedRecordBytes, depth, decodeUnit, ResolutionContext.NONE);
    }

    /**
     * Token-threading variant of {@link #decodeNested(byte[], int)}: the decode-unit
     * completion token is propagated to the nested object's {@code DerGetArg} so that a
     * nested DGC live reference batches with the outer refs of the same decode unit.
     *
     * @param nestedRecordBytes the raw bytes of the nested record TLV (SEQUENCE or NULL)
     * @param depth             current nesting depth (for the DoS guard)
     * @param decodeUnit        the per-decode-unit completion sink, or {@code null}
     * @return the decoded object, or {@code null} for a DER NULL encoding
     * @throws DerException if the encoding is malformed or depth exceeded
     * @throws IOException  if construction fails
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     */
    public static Object decodeNested(byte[] nestedRecordBytes, int depth,
                                      DeserializationCompletion decodeUnit,
                                      ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(nestedRecordBytes, "nestedRecordBytes");
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec.decodeNested: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }
        if (nestedRecordBytes.length == 0) {
            throw new DerException("ObjectCodec.decodeNested: empty bytes");
        }
        // DER NULL (0x05 0x00) -> null
        if (nestedRecordBytes[0] == 0x05) {
            if (nestedRecordBytes.length != 2 || nestedRecordBytes[1] != 0x00) {
                throw new DerException(
                        "ObjectCodec.decodeNested: malformed NULL TLV (expected 05 00)");
            }
            return null;
        }
        // [8] CTX_PROXY -> a nested java.lang.reflect.Proxy field value (see encodeProxy).
        if (CTX_PROXY.equals(new DerReader(nestedRecordBytes).peekTag())) {
            return decodeProxy(nestedRecordBytes, depth, decodeUnit, resolution);
        }
        // SEQUENCE { OCTET STRING(schemaChainBytes), OCTET STRING(payloadBytes) }
        DerReader outer = new DerReader(nestedRecordBytes);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeNested: trailing bytes after nested SEQUENCE");
        }
        byte[] schemaChainBytes = seq.readOctetString();
        byte[] payloadBytes = seq.readOctetString();
        if (seq.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeNested: unexpected trailing bytes in nested SEQUENCE");
        }

        // Decode schema chain (same format as MarshalledInstanceRecord.decodeSchemaChain)
        List<AtomicSerialSchemaRecord> records = new ArrayList<>();
        DerReader schemaReader = new DerReader(schemaChainBytes);
        while (schemaReader.hasMore()) {
            records.add(AtomicSerialSchemaRecord.decode(schemaReader));
        }
        if (records.isEmpty()) {
            throw new DerException("ObjectCodec.decodeNested: empty schema chain");
        }
        byte[] leafDigest = records.get(0).schemaDigest();
        SchemaChain.Result chain = new SchemaChain.Result(records, leafDigest);

        // The declared field type is Object (checked by caller via cast); the chain drives
        // the actual runtime class. decodeHierarchy does assignability checking.
        Object decoded = decodeHierarchy(Object.class, chain, payloadBytes, depth + 1, decodeUnit, resolution);
        // DER replacement: if the decoded value is a serializer (implements Resolve),
        // rebuild the original object via readResolve(); otherwise pass it through.
        return au.net.zeus.jgdms.der.serial.DerReplacer.resolve(decoded);
    }

    /**
     * Decodes a nested {@code [8]} CTX_PROXY field record produced by {@link #encodeProxy} and
     * reconstructs the dynamic {@code java.lang.reflect.Proxy}. Interfaces are resolved through
     * the {@link ResolutionContext} (the endpoint-assigned loader -- never the thread-context
     * loader, per Warres); a {@code DeSerializationPermission("PROXY")} gate runs before
     * reconstruction, mirroring the object-stream {@code [8]} path.
     *
     * @param proxyTlv   the raw {@code [8]} CTX_PROXY TLV bytes
     * @param depth      the proxy's nesting depth (the handler decodes at {@code depth + 1})
     * @param decodeUnit the per-decode-unit completion sink (DGC batching), or {@code null}
     * @param resolution the endpoint resolution context for class/proxy loading
     */
    private static Object decodeProxy(byte[] proxyTlv, int depth,
                                      DeserializationCompletion decodeUnit,
                                      ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        DerReader r = new DerReader(proxyTlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        if (!CTX_PROXY.equals(hdr.tag())) {
            throw new DerException("ObjectCodec.decodeProxy: expected [8] CTX_PROXY, got " + hdr.tag());
        }
        byte[] content = r.readRawContent(hdr.contentLength());
        if (r.hasMore()) {
            throw new DerException("ObjectCodec.decodeProxy: trailing bytes after [8] proxy TLV");
        }
        DerReader pr = new DerReader(content);
        int count;
        try {
            count = pr.readInteger().intValueExact();
        } catch (ArithmeticException e) {
            throw new DerException("ObjectCodec.decodeProxy: interface count overflow", e);
        }
        if (count <= 0 || count > MAX_PROXY_INTERFACES) {
            throw new DerException("ObjectCodec.decodeProxy: interface count " + count
                    + " out of range (1.." + MAX_PROXY_INTERFACES + ")");
        }
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = pr.readUtf8String();
        }
        // The handler is the single remaining TLV (a nested @AtomicSerial record).
        int start = pr.position();
        DerReader.TlvHeader hh = pr.readTlvHeader();
        pr.readRawContent(hh.contentLength());
        int end = pr.position();
        if (pr.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeProxy: trailing bytes after handler in [8] proxy content");
        }
        byte[] handlerTlv = Arrays.copyOfRange(content, start, end);
        Object handler = decodeNested(handlerTlv, depth + 1, decodeUnit, resolution);
        if (!(handler instanceof InvocationHandler)) {
            throw new DerException("ObjectCodec.decodeProxy: handler is not an InvocationHandler ("
                    + (handler == null ? "null" : handler.getClass().getName()) + ")");
        }
        // Endpoint-assigned resolution of the proxy class (the raw loader stays inside the
        // ResolutionContext); the DeSerializationPermission("PROXY") gate runs on its interfaces.
        Class<?> proxyClass = resolution.loadProxyClass(names);
        Class<?>[] ifaces = proxyClass.getInterfaces();
        checkProxyDeSerializationPermitted(ifaces);
        try {
            return Proxy.newProxyInstance(
                    proxyClass.getClassLoader(), ifaces, (InvocationHandler) handler);
        } catch (IllegalArgumentException e) {
            throw new DerException("ObjectCodec.decodeProxy: proxy reconstruction failed", e);
        }
    }

    /**
     * Depth-aware variant of {@link #decodeHierarchy(Class, SchemaChain.Result, byte[])}
     * used for nested decode. The {@code expectedSupertype} is {@code Object.class};
     * the outer constructor's field assignment will perform the actual cast.
     */
    private static <T> T decodeHierarchy(Class<T> expectedSupertype,
                                          SchemaChain.Result chain,
                                          byte[] hierarchyPayload,
                                          int depth,
                                          DeserializationCompletion decodeUnit,
                                          ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(expectedSupertype, "expectedSupertype");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }

        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        String constructClassName = leafFirst.get(0).className();
        Class<?> constructClass = loadClass(constructClassName, resolution);

        if (!expectedSupertype.isAssignableFrom(constructClass)) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: the chain's construct class '"
                    + constructClassName + "' is not assignable to the expected supertype '"
                    + expectedSupertype.getName() + "'. "
                    + "This chain was not generated for a class related to "
                    + expectedSupertype.getName() + ".");
        }

        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        DerReader outer = new DerReader(hierarchyPayload);
        DerReader outerSeq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes after outer SEQUENCE");
        }

        Map<Class<?>, DerFieldStore> storeMap = new LinkedHashMap<>();
        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            Class<?> cls = loadClass(schemaRecord.className(), resolution);
            DerFieldStore store = new DerFieldStore(schemaRecord, outerSeq, resolution);
            storeMap.put(cls, store);
        }
        if (outerSeq.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes in outer SEQUENCE "
                    + "(more SEQUENCEs than schema records)");
        }

        for (Class<?> c = constructClass; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(AtomicSerial.class) && !storeMap.containsKey(c)) {
                storeMap.put(c, emptyFieldStore(c));
            }
        }

        DerGetArg arg = new DerGetArg(storeMap, depth, decodeUnit, resolution);

        // Per-class DeSerializationPermission("ATOMIC") gate (nested decode path too).
        checkAtomicDeSerializationPermitted(storeMap.keySet());

        MarshalDelegate delegate = MarshalDelegates.delegateFor(constructClass);
        if (delegate != null) {
            // In-package construction; the (GetArg) ctor chains up via super(check(arg)).
            @SuppressWarnings("unchecked")
            T created = (T) delegate.create(constructClass, arg);
            return created;
        }
        @SuppressWarnings("unchecked")
        Constructor<? extends T> ctor = (Constructor<? extends T>)
                findGetArgConstructor(constructClass);
        try {
            return ctor.newInstance(arg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InvalidObjectException ioe) throw ioe;
            if (cause instanceof IOException ioe) throw ioe;
            if (cause instanceof ClassNotFoundException cnfe) throw cnfe;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            DerException de = new DerException(
                    "Construction of " + constructClass.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
            throw new AssertionError("Unexpected reflective access failure", ex);
        }
    }

    /**
     * Decodes a nested {@code @AtomicSerial[]} array field as produced by
     * {@link #encodeArray} for the {@code "array:@AtomicSerial:<class>"} wireType.
     *
     * <p>Called from {@link DerGetArg#get(String, Object)} when the field store
     * reports the field as a nested array (wireType starting with
     * {@code "array:@AtomicSerial:"}). The depth is threaded through so the
     * cumulative {@code MAX_NESTING} guard applies per element -- the same guard that
     * was fixed in inc-2 for the single-element case.
     *
     * <p>Wire format:
     * <ul>
     *   <li>DER NULL ({@code 05 00}) -- null array (returns {@code null})</li>
     *   <li>SEQUENCE of N element TLVs -- each element is a nested record SEQUENCE
     *       or DER NULL (null element)</li>
     * </ul>
     *
     * @param rawBytes           the raw TLV bytes (DER NULL or SEQUENCE)
     * @param componentClassName fully-qualified name of the component class (used to
     *                           allocate the result array of the correct type)
     * @param depth              current nesting depth (from the calling {@link DerGetArg})
     * @return the decoded array (of type {@code componentClass[]}) or {@code null}
     * @throws DerException if the encoding is malformed or depth exceeded
     * @throws IOException  if element construction fails
     */
    public static Object decodeNestedArray(byte[] rawBytes,
                                            String componentClassName,
                                            int depth)
            throws DerException, IOException, ClassNotFoundException {
        return decodeNestedArray(rawBytes, componentClassName, depth, null);
    }

    /** As {@link #decodeNestedArray(byte[], String, int, DeserializationCompletion, ResolutionContext)} with no endpoint resolution context ({@link ResolutionContext#NONE}). */
    public static Object decodeNestedArray(byte[] rawBytes,
                                            String componentClassName,
                                            int depth,
                                            DeserializationCompletion decodeUnit)
            throws DerException, IOException, ClassNotFoundException {
        return decodeNestedArray(rawBytes, componentClassName, depth, decodeUnit, ResolutionContext.NONE);
    }

    /**
     * Token-threading variant of {@link #decodeNestedArray(byte[], String, int)}: the
     * decode-unit completion token is propagated to each element's nested decode so a
     * DGC live reference inside an {@code @AtomicSerial[]} field batches with the outer
     * refs of the same decode unit.
     *
     * @param rawBytes           the raw TLV bytes (DER NULL or SEQUENCE)
     * @param componentClassName fully-qualified name of the component class
     * @param depth              current nesting depth
     * @param decodeUnit         the per-decode-unit completion sink, or {@code null}
     * @return the decoded array, or {@code null}
     * @throws DerException if the encoding is malformed or depth exceeded
     * @throws IOException  if element construction fails
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     */
    public static Object decodeNestedArray(byte[] rawBytes,
                                            String componentClassName,
                                            int depth,
                                            DeserializationCompletion decodeUnit,
                                            ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(rawBytes, "rawBytes");
        Objects.requireNonNull(componentClassName, "componentClassName");

        if (rawBytes.length == 0) {
            throw new DerException("ObjectCodec.decodeNestedArray: empty bytes");
        }
        // DER NULL (0x05 0x00) -> null array
        if (rawBytes[0] == 0x05) {
            if (rawBytes.length != 2 || rawBytes[1] != 0x00) {
                throw new DerException(
                        "ObjectCodec.decodeNestedArray: malformed NULL TLV (expected 05 00)");
            }
            return null;
        }

        // SEQUENCE of N element TLVs (each is a nested record SEQUENCE or NULL)
        DerReader outer = new DerReader(rawBytes);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeNestedArray: trailing bytes after array SEQUENCE");
        }

        // Collect raw element TLVs first (to know N before allocating the array)
        List<byte[]> elementRaws = new ArrayList<>();
        while (seq.hasMore()) {
            DerReader.TlvHeader hdr = seq.readTlvHeader();
            byte[] content = seq.readRawContent(hdr.contentLength());
            // Reconstruct full TLV for decodeNested
            byte[] tagBytes    = hdr.tag().encode();
            byte[] lengthBytes = DerWriter.encodeLength(hdr.contentLength());
            byte[] elementTlv  = new byte[tagBytes.length + lengthBytes.length + content.length];
            int pos = 0;
            System.arraycopy(tagBytes,    0, elementTlv, pos, tagBytes.length);
            pos += tagBytes.length;
            System.arraycopy(lengthBytes, 0, elementTlv, pos, lengthBytes.length);
            pos += lengthBytes.length;
            System.arraycopy(content,     0, elementTlv, pos, content.length);
            elementRaws.add(elementTlv);
        }

        // Load component class and allocate a typed array
        Class<?> componentClass = loadClass(componentClassName, resolution);
        Object result = Array.newInstance(componentClass, elementRaws.size());

        for (int i = 0; i < elementRaws.size(); i++) {
            // Each element is decoded with the THREADED depth (not 0!).
            // This is the critical invariant for the cumulative depth guard.
            Object element = decodeNested(elementRaws.get(i), depth, decodeUnit, resolution);
            Array.set(result, i, element); // null element is fine (nullable elements)
        }

        return result;
    }

    /**
     * Encodes a schema chain as a concatenation of each record's DER SEQUENCE bytes,
     * in leaf-first order (same as {@code MarshalledInstanceRecord.encodeChainBytes},
     * but kept in {@code der.object} to avoid a {@code der.marshal} cycle).
     */
    private static byte[] encodeSchemaChainBytes(List<AtomicSerialSchemaRecord> chain) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord record : chain) {
            byte[] encoded = record.encode();
            buf.write(encoded, 0, encoded.length);
        }
        return buf.toByteArray();
    }
}
