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
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.immutable.ImmutableList;
import au.net.zeus.jgdms.der.object.immutable.ImmutableMap;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSet;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSortedMap;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSortedSet;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.DeSerializationPermission;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.api.io.MarshalDelegates;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;

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
import java.util.AbstractMap;
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
import net.jini.security.Security;

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
     * Maximum element count for any collection-valued field (STD-006 §4.5
     * {@code maxCollection}: {@code CollectionField}/{@code MapField} are
     * {@code SEQUENCE SIZE(0..maxCollection) OF ...}). Enforced during decode on
     * every collection token (set/bag/orderedset/list/map/orderedmap) as a
     * structural resource ceiling against a CPU/memory DoS, BEFORE the collection is
     * built and before any constructor runs. The {@code (65536 + 1)}-th element
     * throws {@link DerException}. This is a wire/structural bound, not a semantic
     * size invariant (which belongs in {@code check(GetArg)}).
     */
    public static final int MAX_COLLECTION = 65536;

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
     * {@code [8]} context-constructed tag: a nested {@code java.lang.reflect.Proxy} field
     * value (interface names + the {@code @AtomicSerial} InvocationHandler). Identical tag
     * to the object-stream {@code CTX_PROXY} so the two layers share one wire discriminator.
     */
    private static final Tag CTX_PROXY = new Tag(Tag.CLASS_CONTEXT, true, 8);

    /**
     * {@code [7]} context-constructed tag: an {@code Enum} constant sitting in a polymorphic
     * ({@code @AtomicSerial} / interface / abstract) slot, encoded self-describingly as
     * {@code UTF8String(declaringClassName) ++ UTF8String(constantName)} -- the per-value
     * discriminator that tells {@code decodeNested} "this polymorphic element is an enum leaf,
     * not an {@code @AtomicSerial} hierarchy leaf." Identical tag and content layout to the
     * object-stream {@code CTX_ENUM} (STD-008 sec.15.2 [7]) so the two layers share one wire
     * discriminator. See {@link #encodeNested} / {@link #decodeEnumLeaf}, STD-008 sec.17.1.
     */
    private static final Tag CTX_ENUM = new Tag(Tag.CLASS_CONTEXT, true, 7);

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
                    return Security.create(
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
                    return Security.create(
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
     * Pre-construction decode-admission gate (security review R2 / STD-008 §16).
     *
     * <p>Decides whether the wire-named {@code constructClass} (the leaf resolved from the
     * transmitted schema chain via the endpoint {@link ResolutionContext} -- never the
     * thread-context loader) may be reconstructed into a slot whose <b>declared</b> type is
     * {@code expectedSupertype}. This is evaluated <b>before</b> any {@code (GetArg)}
     * constructor or {@code check(GetArg)} runs, so a hostile peer that names an arbitrary
     * {@code @AtomicSerial} class in a concrete/narrowly-typed slot is failed closed
     * <em>without</em> its constructor firing. The {@code DeSerializationPermission("ATOMIC")}
     * gate is a no-op under SM-less / DirtyChai deployments, so this type check -- not that
     * permission -- is what closes concrete-typed fields.
     *
     * <p>Admission (in order):
     * <ol>
     *   <li><b>Ordinary polymorphism</b> -- {@code expectedSupertype.isAssignableFrom(
     *       constructClass)}: the wire leaf IS the declared type or a subtype. This also
     *       admits every genuinely {@code Object}-typed or broad-interface-typed slot (where
     *       {@code expectedSupertype} is {@code Object}/an interface the leaf implements),
     *       which is the acknowledged <b>residual</b>: such slots are NOT closed by this gate
     *       and still rely on the ATOMIC gate + {@code check(GetArg)} + decode bounds.</li>
     *   <li><b>{@link Serializer @Serializer} substitution</b> -- the leaf is a serializer
     *       standing in for its {@code replaceObType} {@code R} on the wire (the DER analogue
     *       of {@code writeReplace}; e.g. declared {@code X500Principal} &larr; wire
     *       {@code X500PrincipalSerializer}, {@code replaceObType = X500Principal}). Because
     *       {@code X500Principal.isAssignableFrom(X500PrincipalSerializer)} is {@code false},
     *       clause 1 alone would wrongly reject the legitimate substitution. Accept iff the
     *       declared slot could legitimately hold an {@code R}, i.e.
     *       {@code expectedSupertype.isAssignableFrom(R)} -- decided by the leaf's
     *       <em>statically declared</em> {@code replaceObType}. This is what REJECTS the
     *       attack: declared {@code X500Principal}, wire {@code ThrowableSerializer}
     *       ({@code replaceObType = Throwable}) -&gt; {@code X500Principal.isAssignableFrom(
     *       Throwable)} is {@code false} -&gt; rejected before {@code ThrowableSerializer}'s
     *       reflective-construction ctor runs. A {@code @Serializer} leaf is decided SOLELY by
     *       clauses 1/2 (it never falls through to clause 3), since its {@code replaceObType}
     *       is exactly the type it is permitted to stand in for.</li>
     *   <li><b>java.io {@link Resolve}/Replace serialization-proxy substitution</b> -- a leaf
     *       that is NOT a {@code @Serializer} but implements {@link Resolve} (e.g.
     *       {@code ConstrainableAID$State}, whose {@code readResolve()} rebuilds the original
     *       {@code ActivationID}). Unlike a {@code @Serializer} there is no statically declared
     *       target type, so the resolved runtime type is <b>not knowable before construction</b>;
     *       the proxy is admitted here and the resolved value's type is enforced
     *       <em>after</em> {@code readResolve()} by the caller's typed {@code get()/cast}.
     *       <b>Residual:</b> any {@code Resolve}-implementing {@code @AtomicSerial} proxy
     *       remains constructible in a narrowly-typed slot (its ctor runs); this is narrower
     *       than the prior behaviour (every {@code @AtomicSerial} class was constructible in
     *       any nested slot) and is bounded by the ATOMIC gate + {@code check(GetArg)}, AND by
     *       the post-{@code readResolve()} typed cast (a clause-3 value that resolves to the
     *       wrong type cannot populate the slot). The clause-3 set reachable on the platform is
     *       small and reviewed (a tripwire pins it -- {@code ClauseThreeResolveProxyTest}):
     *       immutable constraint/UUID constants, and {@link DerProxySerializer} -- the one with
     *       an ACTIVE {@code readResolve()} (it rebuilds a bootstrap {@code CodebaseAccessor}
     *       proxy), itself bounded by the codebase-download grant + integrity check and not
     *       newly reachable (STD-008 §16.2).</li>
     * </ol>
     *
     * @param expectedSupertype the slot's declared type (never {@code null}; {@code Object.class}
     *                          when no declared-type information is available -- clause 1 then
     *                          always admits, exactly the pre-existing behaviour)
     * @param constructClass    the wire-named leaf class resolved from the schema chain
     * @return {@code true} iff {@code constructClass} may be reconstructed into the slot
     */
    private static boolean admissibleConstructClass(Class<?> expectedSupertype,
                                                    Class<?> constructClass) {
        // (1) Ordinary polymorphism / Object / broad-interface slot (residual).
        if (expectedSupertype.isAssignableFrom(constructClass)) {
            return true;
        }
        // (2) @Serializer substitution: decided by the STATICALLY declared replaceObType.
        Serializer ser = constructClass.getAnnotation(Serializer.class);
        if (ser != null) {
            return expectedSupertype.isAssignableFrom(ser.replaceObType());
        }
        // (3) java.io Replace/Resolve serialization proxy: resolved type known only
        //     post-construction; admitted, final type enforced by the caller's typed cast.
        return Resolve.class.isAssignableFrom(constructClass);
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

        // Pre-construction admission gate (security review R2): the wire-named leaf must be
        // admissible into a slot declared as expectedSupertype -- ordinary polymorphism, a
        // @Serializer substitution for a compatible replaceObType, or a java.io Resolve proxy
        // (see admissibleConstructClass). Fail closed BEFORE any (GetArg) ctor / check runs.
        if (!admissibleConstructClass(expectedSupertype, constructClass)) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: the chain's construct class '"
                    + constructClassName + "' is not admissible into a slot declared '"
                    + expectedSupertype.getName() + "' (not a subtype, not a @Serializer for it, "
                    + "and not a Resolve serialization proxy) -- fail-closed before construction.");
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
        // Collection fields (STD-006 sec.3.8): set:/orderedset:/list:/map:/orderedmap:
        // The declared type's discipline (preserve vs canonicalise) is baked into the
        // token; the encoder honours it here so signed / Entry-matched fields are
        // byte-deterministic.
        if (CollectionWireTypes.isCollection(wireType)) {
            return encodeCollection(value, wireType, fieldName, depth);
        }
        // The self-describing Any element (STD-006 memo §4): the rule selected "any" as the
        // element/key/value wire-type because the declared type was unresolvable. The runtime
        // value's category (scalar / @AtomicSerial object / collection) selects the context tag.
        if (AnyCodec.ANY.equals(wireType)) {
            return AnyCodec.encode(value, fieldName, depth);
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
            case "java.lang.Class" -> {
                // A Class travels as its name (UTF8String); the decode side resolves it
                // through the endpoint-assigned ResolutionContext loader (never ambient).
                if (!(value instanceof Class<?> c)) {
                    throw new DerException("Expected Class for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeUtf8String(c.getName());
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

    static byte[] encodeFloat(Object value, String fieldName) throws DerException {
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

    static byte[] encodeDouble(Object value, String fieldName) throws DerException {
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

    static byte[] encodeChar(Object value, String fieldName) throws DerException {
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
            } else if (componentWT.equals("java.lang.Class")) {
                // Class element: DER NULL if null, else UTF8String of the class name
                elemTlv = (elem == null)
                        ? new byte[]{0x05, 0x00}
                        : DerWriter.writeUtf8String(((Class<?>) elem).getName());
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

    // =========================================================================
    // Collection field encode (STD-006 sec.3.8 -- set:/orderedset:/list:/map:/orderedmap:)
    // =========================================================================

    /**
     * Encodes a {@code Collection} or {@code Map} field value to its DER TLV per the
     * STD-006 §3.8 encounter-order rule. The discipline (preserve vs canonicalise) is
     * fixed by the token, which {@code SchemaGenerator} derived from the DECLARED type
     * at schema-generation time -- it is NOT re-derived from the runtime instance here.
     *
     * <p>Wire form (both disciplines share the SEQUENCE OF tag; they differ only in the
     * encoder's ordering obligation, §7.6 / memo §6.3):
     * <ul>
     *   <li>DER NULL ({@code 05 00}) for a null field.</li>
     *   <li>{@code set:}/{@code orderedset:}/{@code list:} -- {@code SEQUENCE OF Element}.</li>
     *   <li>{@code map:}/{@code orderedmap:} -- {@code SEQUENCE OF SEQUENCE { key, value }}.</li>
     * </ul>
     *
     * <p>For the CANONICALISE disciplines ({@code set:}/{@code map:}) the element (or
     * {@code {key,value}} entry) encodings are octet-sorted per X.690 §11.6
     * ({@link CollectionWireTypes#OCTET_SORT}) -- applied bottom-up, since each element
     * is fully encoded (recursively canonical) before the outer sort. For the PRESERVE
     * disciplines the iterator's order is emitted unchanged.
     *
     * @param value     the {@code Collection} or {@code Map} value (may be null)
     * @param wireType  the collection token
     * @param fieldName diagnostics
     * @param depth     current nesting depth (threaded into per-element nested encode)
     */
    static byte[] encodeCollection(Object value, String wireType,
                                           String fieldName, int depth) throws DerException {
        // Depth-bound DoS guard, symmetric with the decode side (decodeCollection) and with
        // encodeNested: a nested-collection element recurses at depth + 1, so an over-deep
        // nested-collection value is rejected fail-secure before StackOverflowError.
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec.encodeCollection: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }
        if (value == null) {
            return new byte[]{0x05, 0x00}; // DER NULL
        }
        boolean canonicalise = CollectionWireTypes.isCanonicalise(wireType);

        if (CollectionWireTypes.isMap(wireType)) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new DerException("ObjectCodec: expected Map for field '" + fieldName
                        + "' (wireType " + wireType + ") but got " + value.getClass().getName());
            }
            String[] kv = CollectionWireTypes.mapKeyValueWireTypes(wireType);
            String keyWT = kv[0];
            String valWT = kv[1];
            // Collect (keyEncoding, entryEncoding) pairs so a canonicalise map can octet-sort by
            // the KEY encoding, not the whole entry. Sorting the whole entry is NOT equivalent to
            // sorting by key: the entry SEQUENCE's length octet encodes key-size + value-size, so a
            // larger value can flip two entries whose keys would sort the other way key-only. §2 /
            // §11.6 fix the canonical order on the KEY (keys unique -> total order), so a non-JVM
            // peer implementing "sort by encoded key" agrees byte-for-byte. Determinism held either
            // way, but cross-implementation canonical-form agreement requires key-only ordering.
            List<byte[]> keyTlvs   = new ArrayList<>(map.size());
            List<byte[]> entryTlvs = new ArrayList<>(map.size());
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                byte[] keyTlv = encodeElementValue(e.getKey(),   keyWT, fieldName + ".key[" + i + "]",   depth);
                byte[] valTlv = encodeElementValue(e.getValue(), valWT, fieldName + ".value[" + i + "]", depth);
                // entry := SEQUENCE { key, value }
                List<byte[]> pair = new ArrayList<>(2);
                pair.add(keyTlv);
                pair.add(valTlv);
                keyTlvs.add(keyTlv);
                entryTlvs.add(DerWriter.writeSequence(pair));
                i++;
            }
            if (canonicalise) {
                // Stable sort the entry list by the parallel key-encoding list (X.690 §11.6 over
                // the key encodings). Keys are unique -> the key comparison is total, so no two
                // entries compare equal and stability is immaterial; value bytes never participate.
                Integer[] order = new Integer[entryTlvs.size()];
                for (int j = 0; j < order.length; j++) order[j] = j;
                java.util.Arrays.sort(order,
                        (p, q) -> CollectionWireTypes.compareOctets(keyTlvs.get(p), keyTlvs.get(q)));
                List<byte[]> sorted = new ArrayList<>(entryTlvs.size());
                for (int idx : order) sorted.add(entryTlvs.get(idx));
                entryTlvs = sorted;
            }
            // Option A tag: a CANONICALISE map is an ASN.1 SET OF SEQUENCE{key,value} -> outer
            // SET (0x31); a PRESERVE (orderedmap) is a SEQUENCE OF -> outer SEQUENCE (0x30). The
            // inner per-entry {key,value} stays a SEQUENCE (0x30) either way (§3.8, Option A).
            return canonicalise ? DerWriter.writeSet(entryTlvs) : DerWriter.writeSequence(entryTlvs);
        }

        // set: / orderedset: / list:
        if (!(value instanceof Collection<?> coll)) {
            throw new DerException("ObjectCodec: expected Collection for field '" + fieldName
                    + "' (wireType " + wireType + ") but got " + value.getClass().getName());
        }
        String elemWT = CollectionWireTypes.elementWireType(wireType);
        List<byte[]> elementTlvs = new ArrayList<>(coll.size());
        int i = 0;
        for (Object elem : coll) {
            elementTlvs.add(encodeElementValue(elem, elemWT, fieldName + "[" + i + "]", depth));
            i++;
        }
        if (canonicalise) {
            CollectionWireTypes.octetSort(elementTlvs);
        }
        // Option A tag: a CANONICALISE set/multiset is an ASN.1 SET OF -> SET (0x31); a PRESERVE
        // (orderedset/list) is a SEQUENCE OF -> SEQUENCE (0x30) (§3.8, Option A).
        return canonicalise ? DerWriter.writeSet(elementTlvs) : DerWriter.writeSequence(elementTlvs);
    }

    /**
     * Encodes a single collection element / map key / map value, mirroring the depth
     * accounting of {@link #decodeElementValue}: a nested-collection element recurses into
     * {@link #encodeCollection} at {@code depth + 1} (each nesting level consumes one unit of
     * the {@code MAX_NESTING} budget); every other element type is delegated to
     * {@link #encodeValue} at the unchanged {@code depth} (an {@code @AtomicSerial} element's
     * own {@code depth + 1} step happens inside {@link #encodeNested}, symmetric with decode).
     */
    private static byte[] encodeElementValue(Object elem, String elemWT,
                                             String fieldName, int depth) throws DerException {
        if (CollectionWireTypes.isCollection(elemWT)) {
            return encodeCollection(elem, elemWT, fieldName, depth + 1);
        }
        return encodeValue(elem, elemWT, fieldName, depth);
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
    static byte[] encodeNested(Object value, String fieldName, int depth)
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

        // A polymorphic @AtomicSerial slot may legitimately hold an ENUM constant -- e.g.
        // net.jini.core.constraint.AtomicInputValidation (a public enum implements
        // InvocationConstraint) inside an InvocationConstraint[] / Set<InvocationConstraint>.
        // An enum implementing a marshalled interface is NOT itself @AtomicSerial (a Java enum
        // cannot be, and migrating enum->class is a binary-API break the japicmp gate flags), so
        // it has no @AtomicSerial class in its hierarchy and would fall through to the hard
        // rejection below. Encode it self-describingly as the shared [7] CTX_ENUM leaf --
        // UTF8String(declaringClassName) ++ UTF8String(constantName), byte-identical to the
        // object-stream layer's bare-enum path -- so decodeNested distinguishes an "enum leaf"
        // from an "@AtomicSerial hierarchy leaf" by the context tag (the per-value discriminator,
        // exactly as the [8] CTX_PROXY path carries a runtime shape distinct from the declared
        // slot type). Encoding by NAME is canonical (one encoding per constant), byte-stable, and
        // composes with the SET-OF octet sort. The concrete enum class is admission-gated against
        // the declared slot type at decode (decodeEnumLeaf), so an attacker cannot name an
        // arbitrary enum into a constraint slot. NOTE: this is distinct from a field DECLARED as
        // an enum type (wireType "enum:<class>", encodeEnum), where the class is already fixed by
        // the schema and only the constant name travels -- here the declared type is polymorphic,
        // so the concrete enum class MUST travel, mirroring the nested @AtomicSerial class chain.
        if (value instanceof Enum<?> ev) {
            return encodeEnumLeaf(ev);
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
        // If proxy's live handler is a BoomerangProxyHandler (this node itself decoded proxy from
        // a [8] item that dropped >=1 interface it couldn't resolve locally -- see decodeProxy
        // below), re-emit the RETAINED ORIGINAL wire bytes verbatim, byte-for-byte, rather than
        // re-deriving fresh bytes from proxy.getClass().getInterfaces() (which only shows the
        // narrowed runtime set this node built, and would also forfeit the sender's
        // @AtomicSerial-validated integrity guarantee -- see BoomerangProxyHandler). Otherwise --
        // the common case, nothing was ever dropped -- behaviour is unchanged: encode fresh.
        byte[] retained = ProxyWireSupport.wireContentForBoomerang(proxy);
        if (retained != null) {
            return DerWriter.writeTlv(CTX_PROXY, retained);
        }
        Class<?>[] ifaces = proxy.getClass().getInterfaces();
        if (ifaces.length == 0 || ifaces.length > ProxyWireSupport.MAX_PROXY_INTERFACES) {
            throw new DerException("ObjectCodec: nested proxy field '" + fieldName
                    + "' interface count " + ifaces.length
                    + " out of range (1.." + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
        }
        InvocationHandler h = Proxy.getInvocationHandler(proxy);
        if (nearestAtomicSerial(h.getClass()) == null) {
            throw new DerException("ObjectCodec: nested proxy field '" + fieldName
                    + "' InvocationHandler " + h.getClass().getName()
                    + " is not @AtomicSerial");
        }
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(DerWriter.writeInteger(BigInteger.valueOf(ifaces.length)));
        for (Class<?> iface : ifaces) {
            content.writeBytes(DerWriter.writeUtf8String(iface.getName()));
        }
        // Handler as a nested @AtomicSerial value (depth + 1): reuses the depth bound,
        // ResolutionContext and DGC decode-unit threading of the nested-field path.
        content.writeBytes(encodeNested(h, fieldName + ".proxyHandler", depth + 1));
        return DerWriter.writeTlv(CTX_PROXY, content.toByteArray());
    }

    /**
     * Encodes an {@code Enum} constant that occupies a polymorphic ({@code @AtomicSerial} /
     * interface / abstract) slot as a {@code [7]} CTX_ENUM leaf:
     * {@code UTF8String(declaringClassName) ++ UTF8String(constantName)}. Byte-identical to the
     * object-stream layer's bare-enum form (STD-008 sec.15.2 [7]) so the two layers share the
     * discriminator. Uses {@link Enum#getDeclaringClass()} -- NOT {@code getClass()} -- so a
     * constant with a body (e.g. {@code Op.ADD -> Op$1}) names its declaring enum type, not the
     * anonymous constant-body subclass. Encoding by name is canonical (one encoding per constant),
     * so a {@code Set} of enums octet-sorts deterministically and re-encodes byte-for-byte.
     *
     * @param ev the enum constant value (non-null; caller has verified {@code value instanceof Enum})
     * @return the {@code [7]} CTX_ENUM TLV bytes
     */
    private static byte[] encodeEnumLeaf(Enum<?> ev) throws DerException {
        byte[] clsName = DerWriter.writeUtf8String(ev.getDeclaringClass().getName());
        byte[] name    = DerWriter.writeUtf8String(ev.name());
        byte[] content = new byte[clsName.length + name.length];
        System.arraycopy(clsName, 0, content, 0, clsName.length);
        System.arraycopy(name, 0, content, clsName.length, name.length);
        return DerWriter.writeTlv(CTX_ENUM, content);
    }

    /**
     * Decodes a {@code [7]} CTX_ENUM leaf produced by {@link #encodeEnumLeaf} for an enum sitting
     * in a polymorphic slot, resolving the concrete enum class through the endpoint-assigned
     * {@link ResolutionContext} (NEVER the thread-context loader -- Warres) and reconstructing the
     * constant via {@link Enum#valueOf}.
     *
     * <p><b>Decode-admission (security review R2):</b> the wire-named enum class MUST be admissible
     * into the receiving slot's DECLARED type ({@code expectedSupertype}) BEFORE the constant is
     * resolved. For an inert enum this reduces to {@link #admissibleConstructClass} clause 1
     * ({@code expectedSupertype.isAssignableFrom(enumClass)}) -- an enum is neither a
     * {@code @Serializer} nor a {@code Resolve} proxy, so clauses 2/3 never fire -- which stops a
     * hostile peer from naming an arbitrary enum into e.g. an {@code InvocationConstraint} slot.
     * An unknown constant name fails closed (the enum is otherwise attacker-inert: singleton, no
     * reachable constructor).
     *
     * @param enumTlv           the raw {@code [7]} CTX_ENUM TLV bytes
     * @param expectedSupertype the receiving slot's declared type (never {@code null})
     * @param resolution        the endpoint resolution context for class loading
     * @return the reconstructed enum constant
     */
    private static Object decodeEnumLeaf(byte[] enumTlv, Class<?> expectedSupertype,
                                         ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        DerReader r = new DerReader(enumTlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        if (!CTX_ENUM.equals(hdr.tag())) {
            throw new DerException("ObjectCodec.decodeEnumLeaf: expected [7] CTX_ENUM, got " + hdr.tag());
        }
        byte[] content = r.readRawContent(hdr.contentLength());
        if (r.hasMore()) {
            throw new DerException("ObjectCodec.decodeEnumLeaf: trailing bytes after [7] enum TLV");
        }
        DerReader er = new DerReader(content);
        String className = er.readUtf8String();
        String constant  = er.readUtf8String();
        if (er.hasMore()) {
            throw new DerException("ObjectCodec.decodeEnumLeaf: trailing bytes in [7] enum content");
        }
        // Endpoint-assigned resolution (NEVER the thread-context loader -- Warres).
        Class<?> enumClass = loadClass(className, resolution);
        if (!enumClass.isEnum()) {
            throw new DerException("ObjectCodec.decodeEnumLeaf: [7] enum class '" + className
                    + "' is not an enum");
        }
        // Pre-resolution admission gate: fail closed BEFORE resolving the constant if the named
        // enum class is not admissible into the declared slot type (clause 1 assignability).
        if (!admissibleConstructClass(expectedSupertype, enumClass)) {
            throw new DerException("ObjectCodec.decodeEnumLeaf: enum class '" + className
                    + "' is not admissible into a slot declared '" + expectedSupertype.getName()
                    + "' (not assignable) -- fail-closed before resolution.");
        }
        try {
            return enumValueOf(enumClass, constant);
        } catch (IllegalArgumentException e) {
            throw new DerException("ObjectCodec.decodeEnumLeaf: unknown enum constant '" + constant
                    + "' in " + className, e);
        }
    }

    /** Resolves an enum constant by declaring-class + name (raw-type bridge for {@link Enum#valueOf}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValueOf(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
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
        // No declared-type information at this call site (e.g. an Any-typed or collection
        // element): admit at Object.class, preserving the pre-existing behaviour. Callers
        // that DO know the slot's declared type use the typed overload below to tighten.
        return decodeNested(nestedRecordBytes, Object.class, depth, decodeUnit, resolution);
    }

    /**
     * Type-threading variant of {@link #decodeNested(byte[], int, DeserializationCompletion,
     * ResolutionContext)}: {@code expectedSupertype} is the receiving slot's <b>declared</b>
     * Java type (e.g. {@code callerClass.getDeclaredField(name).getType()} for a nested field,
     * or the array component type), enforced by the pre-construction admission gate
     * ({@link #admissibleConstructClass}) BEFORE the nested object's {@code (GetArg)} ctor /
     * {@code check(GetArg)} runs. Pass {@code Object.class} when no declared type is known
     * (preserving the untyped behaviour). Accounts for the {@code @Serializer}/{@code Resolve}
     * substitution mechanisms so a legitimate substituted serializer (e.g.
     * {@code X500PrincipalSerializer} for an {@code X500Principal} slot) still decodes, while a
     * foreign {@code @AtomicSerial}/serializer named in a concrete slot is failed closed
     * without constructing.
     *
     * @param nestedRecordBytes the raw bytes of the nested record TLV (SEQUENCE or NULL)
     * @param expectedSupertype the receiving slot's declared type (never {@code null})
     * @param depth             current nesting depth (for the DoS guard)
     * @param decodeUnit        the per-decode-unit completion sink, or {@code null}
     * @param resolution        the endpoint resolution context for class loading
     */
    public static Object decodeNested(byte[] nestedRecordBytes, Class<?> expectedSupertype,
                                      int depth, DeserializationCompletion decodeUnit,
                                      ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(nestedRecordBytes, "nestedRecordBytes");
        Objects.requireNonNull(expectedSupertype, "expectedSupertype");
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
        // [7] CTX_ENUM -> an enum constant sitting in this polymorphic slot (see encodeNested's
        // enum branch). Resolved and admission-gated against the declared slot type in
        // decodeEnumLeaf (fail-closed BEFORE resolution if the named enum class is not assignable
        // to expectedSupertype; fail-closed on an unknown constant name).
        if (CTX_ENUM.equals(new DerReader(nestedRecordBytes).peekTag())) {
            return decodeEnumLeaf(nestedRecordBytes, expectedSupertype, resolution);
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

        // Thread the receiving slot's DECLARED type into decodeHierarchy, which enforces the
        // pre-construction admission gate (admissibleConstructClass) against the wire-named
        // leaf BEFORE the nested (GetArg) ctor / check(GetArg) runs. A legitimate @Serializer
        // (e.g. X500PrincipalSerializer for an X500Principal slot) or java.io Resolve proxy is
        // admitted; a foreign @AtomicSerial named in a concrete slot is failed closed. For a
        // genuinely Object/broad-interface slot expectedSupertype is broad (residual).
        Object decoded = decodeHierarchy(expectedSupertype, chain, payloadBytes, depth + 1, decodeUnit, resolution);
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
        if (count <= 0 || count > ProxyWireSupport.MAX_PROXY_INTERFACES) {
            throw new DerException("ObjectCodec.decodeProxy: interface count " + count
                    + " out of range (1.." + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
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
        // The handler slot is known to require an InvocationHandler: thread that declared type
        // so the pre-construction admission gate rejects a foreign leaf before its ctor runs
        // (the post-decode instanceof check below remains as defence in depth).
        Object handler = decodeNested(handlerTlv, InvocationHandler.class, depth + 1, decodeUnit, resolution);
        if (!(handler instanceof InvocationHandler)) {
            throw new DerException("ObjectCodec.decodeProxy: handler is not an InvocationHandler ("
                    + (handler == null ? "null" : handler.getClass().getName()) + ")");
        }
        // Endpoint-assigned TOLERANT resolution of the proxy class (the raw loader stays inside
        // the ResolutionContext): resolves each interface name independently rather than failing
        // the whole item when a single name doesn't resolve locally. Names that don't resolve are
        // dropped (and logged); the proxy still builds over the resolvable subset, wrapped in a
        // BoomerangProxyHandler that retains the full original wire bytes so a later re-forward of
        // this proxy doesn't silently lose the dropped interfaces -- and re-emits those bytes
        // byte-for-byte rather than re-deriving them (see ProxyWireSupport / BoomerangProxyHandler
        // and the write side above).
        ProxyWireSupport.Resolved resolved = ProxyWireSupport.resolveTolerant(names, resolution);
        // DeSerializationPermission("PROXY") gate runs on the RESOLVED (narrowed) interfaces
        // actually being instantiated, not the full original names.
        checkProxyDeSerializationPermitted(resolved.interfaces);
        InvocationHandler realHandler = (InvocationHandler) handler;
        InvocationHandler toUse = resolved.droppedNames.length == 0
                ? realHandler
                : ProxyWireSupport.wrapForDrop(realHandler, content);
        try {
            return Proxy.newProxyInstance(
                    resolved.proxyClass.getClassLoader(), resolved.interfaces, toUse);
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

        // Pre-construction admission gate (security review R2): enforce the caller's declared
        // (expectedSupertype) type against the wire-named leaf BEFORE any (GetArg) ctor /
        // check(GetArg) runs. For a concrete/narrowly-typed nested field this closes the
        // name-driven nested door (a hostile peer naming e.g. ThrowableSerializer in an
        // X500Principal slot is rejected before its reflective-construction ctor fires); for a
        // genuinely Object/broad-interface slot expectedSupertype is broad and clause 1 admits
        // (documented residual). See admissibleConstructClass.
        if (!admissibleConstructClass(expectedSupertype, constructClass)) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: the chain's construct class '"
                    + constructClassName + "' is not admissible into a nested slot declared '"
                    + expectedSupertype.getName() + "' (not a subtype, not a @Serializer for it, "
                    + "and not a Resolve serialization proxy) -- fail-closed before construction.");
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
        // No declared component type known at this call site: admit each element at
        // Object.class (pre-existing behaviour). Callers that know the receiving field's
        // declared component type use the typed overload below to tighten per element.
        return decodeNestedArray(rawBytes, componentClassName, Object.class, depth,
                                 decodeUnit, resolution);
    }

    /**
     * Type-threading variant of {@link #decodeNestedArray(byte[], String, int,
     * DeserializationCompletion, ResolutionContext)}: {@code expectedComponentType} is the
     * receiving array field's <b>declared</b> component type (e.g.
     * {@code callerClass.getDeclaredField(name).getType().getComponentType()}), enforced by the
     * per-element pre-construction admission gate. {@code componentClassName} (the wire
     * component class) still governs the allocated array's runtime component type; the declared
     * component type governs admission of each element's wire-named leaf. Pass
     * {@code Object.class} when the declared component type is unknown.
     *
     * @param rawBytes             the raw TLV bytes (DER NULL or SEQUENCE)
     * @param componentClassName   fully-qualified name of the WIRE component class (allocation)
     * @param expectedComponentType the receiving field's declared component type (admission;
     *                             never {@code null})
     * @param depth                current nesting depth
     * @param decodeUnit           the per-decode-unit completion sink, or {@code null}
     * @param resolution           the endpoint resolution context
     */
    public static Object decodeNestedArray(byte[] rawBytes,
                                            String componentClassName,
                                            Class<?> expectedComponentType,
                                            int depth,
                                            DeserializationCompletion decodeUnit,
                                            ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(rawBytes, "rawBytes");
        Objects.requireNonNull(componentClassName, "componentClassName");
        Objects.requireNonNull(expectedComponentType, "expectedComponentType");

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
            // Each element is decoded with the THREADED depth (not 0!) and the receiving
            // field's DECLARED component type as the admission bound (pre-construction gate).
            // This is the critical invariant for the cumulative depth guard.
            Object element = decodeNested(elementRaws.get(i), expectedComponentType, depth,
                                          decodeUnit, resolution);
            Array.set(result, i, element); // null element is fine (nullable elements)
        }

        return result;
    }

    // =========================================================================
    // Collection / Map field decode (STD-006 sec.3.8)
    // =========================================================================

    /** As {@link #decodeCollection(byte[], String, int, DeserializationCompletion, ResolutionContext)}
     *  with no decode-unit and {@link ResolutionContext#NONE}. */
    public static Object decodeCollection(byte[] rawBytes, String wireType, int depth)
            throws DerException, IOException, ClassNotFoundException {
        return decodeCollection(rawBytes, wireType, depth, null, ResolutionContext.NONE);
    }

    /**
     * Decodes a {@code Collection}/{@code Map} field record produced by
     * {@link #encodeCollection} for a {@code set:}/{@code orderedset:}/{@code list:}/
     * {@code map:}/{@code orderedmap:} token (STD-006 §3.8).
     *
     * <p>Called from {@link DerGetArg#lookup} when the field store reports the field as a
     * collection. The depth is threaded so the cumulative {@code MAX_NESTING} guard applies
     * per element (nested {@code @AtomicSerial} and nested-collection elements).
     *
     * <p><b>Tag (Option A, §3.8):</b> a CANONICALISE field ({@code set:}/{@code bag:}/{@code map:})
     * is read as an ASN.1 {@code SET OF} (outer tag {@code 0x31}); a PRESERVE field
     * ({@code orderedset:}/{@code list:}/{@code orderedmap:}) as a {@code SEQUENCE OF}
     * ({@code 0x30}). The wrong outer tag for the field's discipline is a {@link DerException}.
     *
     * <p><b>Wire-order enforcement (mandatory DER, §11.6):</b> canonicalise fields are order-checked
     * in O(n) against the immediate predecessor only -- {@code set:}/{@code map:}-keys must be
     * strictly ascending (this subsumes the duplicate check), {@code bag:} must be non-decreasing
     * (duplicates retained). Preserve fields get no order check (transmitted order is the value).
     * Element/entry count is capped at {@link #MAX_COLLECTION} (§4.5) before building. The
     * reconstructed kind is order-retaining ({@code LinkedHashSet}/{@code LinkedHashMap}/
     * {@code ArrayList}) so the decoded logical value equals the original; re-encoding decoded
     * canonical bytes reproduces them exactly.
     *
     * @param rawBytes   the raw TLV bytes (DER NULL, or SET/SEQUENCE per discipline)
     * @param wireType   the full collection wire-type token
     * @param depth      current nesting depth
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @param resolution the endpoint resolution context
     * @return the decoded {@code Collection} / {@code Map}, or {@code null} for DER NULL
     * @throws DerException if the encoding is malformed, the outer tag is wrong for the discipline,
     *                      a canonicalise field is out of §11.6 order, the count exceeds
     *                      {@link #MAX_COLLECTION}, or depth is exceeded
     * @throws IOException  if a nested element's construction fails
     */
    public static Object decodeCollection(byte[] rawBytes, String wireType, int depth,
                                          DeserializationCompletion decodeUnit,
                                          ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        return decodeCollection(rawBytes, wireType, depth, decodeUnit, resolution, null);
    }

    /**
     * As {@link #decodeCollection(byte[], String, int, DeserializationCompletion,
     * ResolutionContext)}, additionally taking the receiving field's LOCAL declared Java type
     * (e.g. from {@code callerClass.getDeclaredField(name).getType()}), consulted <b>only</b> to
     * choose which immutable wrapper INTERFACE shape to hand back for an {@code orderedset:}/
     * {@code orderedmap:} field: {@link CollectionWireTypes#disciplineFor} maps both a {@code
     * SortedSet}/{@code NavigableSet} field and a {@code LinkedHashSet}/{@code EnumSet} field to
     * the same {@code orderedset:} PRESERVE_ORDERED token (symmetrically for maps), so the token
     * alone cannot tell them apart. This parameter never changes which bytes are decoded, the
     * element/entry values, or their order -- only whether the returned object additionally
     * implements {@link java.util.SortedSet}/{@link java.util.SortedMap}.
     *
     * <p>{@code declaredType} is {@code null} when unknown (a nested collection-of-collection
     * element, an {@code Any}-typed element, or a synthesized field with no backing {@code
     * Field}) -- decode then falls back to the plain (non-sorted) wrapper shape, which is exactly
     * what this codec always returned before this parameter existed. A caller whose {@code
     * check(GetArg)} then does {@code arg.get(name, val, SortedSet.class)} on such a field gets a
     * fail-secure {@code InvalidObjectException} (a type mismatch), never a data-integrity issue.
     *
     * @param declaredType the receiving field's local declared Java type, or {@code null} if
     *                     unknown/not applicable
     */
    public static Object decodeCollection(byte[] rawBytes, String wireType, int depth,
                                          DeserializationCompletion decodeUnit,
                                          ResolutionContext resolution,
                                          Class<?> declaredType)
            throws DerException, IOException, ClassNotFoundException {
        // No recovered element types (e.g. an Any-typed or nested-generic-inner collection):
        // gate each element at Object.class -- the pre-existing behaviour. Callers that DO
        // recover the declared element type(s) from the field's generic signature use the typed
        // overload below to tighten the per-element admission gate (F1).
        return decodeCollection(rawBytes, wireType, depth, decodeUnit, resolution, declaredType,
                                Object.class, Object.class);
    }

    /**
     * As {@link #decodeCollection(byte[], String, int, DeserializationCompletion,
     * ResolutionContext, Class)}, additionally taking the receiving field's DECLARED element
     * types recovered from its generic signature (security review R2 F1), threaded into each
     * element's pre-construction admission gate:
     * <ul>
     *   <li>for a {@code Collection}/{@code Iterable} field {@code C<E>}: {@code expectedValueType}
     *       is the raw class of {@code E}; {@code expectedKeyType} is ignored;</li>
     *   <li>for a {@code Map} field {@code M<K,V>}: {@code expectedKeyType} = raw class of
     *       {@code K}, {@code expectedValueType} = raw class of {@code V}.</li>
     * </ul>
     * Both are {@code Object.class} when the element type is unknown/raw/wildcard/type-variable
     * or a nested-generic inner element (the documented residual). A concrete {@code C<Concrete>}
     * or {@code Map<K,V>} thereby rejects a foreign wire-named serializer element before its ctor
     * runs, while a declared interface element (e.g. {@code Set<InvocationConstraint>}) still
     * admits concrete impls via clause 1 and a {@code Set<X500Principal>} still admits
     * {@code X500PrincipalSerializer} via clause 2.
     *
     * @param expectedKeyType   map KEY declared type (admission bound), or {@code Object.class}
     * @param expectedValueType collection ELEMENT / map VALUE declared type, or {@code Object.class}
     */
    public static Object decodeCollection(byte[] rawBytes, String wireType, int depth,
                                          DeserializationCompletion decodeUnit,
                                          ResolutionContext resolution,
                                          Class<?> declaredType,
                                          Class<?> expectedKeyType,
                                          Class<?> expectedValueType)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(rawBytes, "rawBytes");
        Objects.requireNonNull(wireType, "wireType");
        Objects.requireNonNull(expectedKeyType, "expectedKeyType");
        Objects.requireNonNull(expectedValueType, "expectedValueType");
        // Depth-bound DoS guard (STD-008 sec.16.2), symmetric with decodeNested/decodeHierarchy:
        // a nested-collection element (list:list:.../set:set:.../map: whose value is a collection)
        // recurses through decodeElementValue at depth+1, so an attacker-controlled collection
        // token declaring N-deep collection nesting is rejected HERE -- before StackOverflowError.
        // The wire token is attacker-controlled (schemaDigest is self-consistent-only, not bound
        // to a locally-regenerated schema), so this check must run on every recursion, not only
        // the @AtomicSerial (decodeNested) path.
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec.decodeCollection: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }
        if (rawBytes.length == 0) {
            throw new DerException("ObjectCodec.decodeCollection: empty bytes");
        }
        // DER NULL (0x05 0x00) -> null field
        if (rawBytes[0] == 0x05) {
            if (rawBytes.length != 2 || rawBytes[1] != 0x00) {
                throw new DerException(
                        "ObjectCodec.decodeCollection: malformed NULL TLV (expected 05 00)");
            }
            return null;
        }

        // Option A tag selection: a CANONICALISE field (set:/bag:/map:) is an ASN.1 SET OF ->
        // outer SET (0x31); a PRESERVE field (orderedset:/list:/orderedmap:) is a SEQUENCE OF ->
        // outer SEQUENCE (0x30). readSet()/readSequence() REJECT the wrong tag, so a canonicalise
        // field arriving as 0x30 (or a preserve field as 0x31) is a DerException.
        boolean canonicalise = CollectionWireTypes.isCanonicalise(wireType);
        DerReader outer = new DerReader(rawBytes);
        DerReader body = canonicalise ? outer.readSet() : outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeCollection: trailing bytes after collection TLV");
        }

        if (CollectionWireTypes.isMap(wireType)) {
            return decodeMap(body, wireType, canonicalise, depth, decodeUnit, resolution,
                             declaredType, expectedKeyType, expectedValueType);
        }
        return decodeSetOrList(body, wireType, canonicalise, depth, decodeUnit, resolution,
                               declaredType, expectedValueType);
    }

    /**
     * Decodes a {@code set:}/{@code bag:}/{@code orderedset:}/{@code list:} collection body.
     *
     * <p>Wire-order enforcement per discipline (§3.8 / §11.6, mandatory DER):
     * <ul>
     *   <li>{@code set:} (canonicalise set) -- consecutive element encodings MUST be
     *       <b>strictly ascending</b> ({@code compareOctets(prev,cur) < 0}); this subsumes the
     *       duplicate check (a dup is {@code == 0}, rejected) in O(n) with only the immediate
     *       predecessor.</li>
     *   <li>{@code bag:} (canonicalise multiset) -- MUST be <b>non-decreasing</b>
     *       ({@code <= 0}); duplicates are legitimate and retained.</li>
     *   <li>{@code orderedset:}/{@code list:} (preserve) -- <b>no</b> order check; the
     *       transmitted order is the value.</li>
     * </ul>
     * Element count is capped at {@link #MAX_COLLECTION} (§4.5) before building.
     *
     * <p>Elements are accumulated into a plain {@link ArrayList} (a purely positional append --
     * {@code ArrayList.add} never invokes {@code hashCode()}/{@code equals()}/{@code compareTo()}
     * on the element), never a {@code HashSet}/{@code LinkedHashSet} (whose {@code add} hashes the
     * element) and never a {@code TreeSet} (whose {@code add} compares it). The final immutable
     * wrapper ({@link ImmutableList}, {@link ImmutableSet}, or {@link ImmutableSortedSet}) is then
     * built from that list via a single {@code List.toArray()} array copy -- so construction of
     * the returned collection value invokes zero methods on the decoded elements (STD-006 §3.8 /
     * the {@code AtomicSerial.GetArg} contract: fields are replaced by a "safe limited
     * functionality immutable Collection instance").
     */
    private static Object decodeSetOrList(DerReader body, String wireType, boolean canonicalise,
                                          int depth, DeserializationCompletion decodeUnit,
                                          ResolutionContext resolution, Class<?> declaredType,
                                          Class<?> expectedElementType)
            throws DerException, IOException, ClassNotFoundException {
        String elemWT = CollectionWireTypes.elementWireType(wireType);
        boolean setKind = CollectionWireTypes.isSetKind(wireType);   // set: / orderedset: -> Set
        boolean multiset = CollectionWireTypes.isMultiset(wireType); // bag: (non-decreasing)
        // Purely positional accumulator -- see method Javadoc for why this must never be a
        // hash-bucketed (HashSet/LinkedHashSet) or comparison-ordered (TreeSet) structure.
        List<Object> out = new ArrayList<>();

        int count = 0;
        byte[] prevEnc = null; // immediate predecessor encoding (O(1) memory, O(n) total)
        while (body.hasMore()) {
            if (++count > MAX_COLLECTION) {
                throw new DerException("ObjectCodec.decodeCollection: element count exceeds "
                        + "maxCollection (" + MAX_COLLECTION + ", §4.5) for field '" + wireType + "'");
            }
            int start = body.position();
            Object element = decodeElementValue(body, elemWT, depth, decodeUnit, resolution,
                                                expectedElementType);
            int end = body.position();
            if (canonicalise) {
                byte[] enc = body.slice(start, end);
                if (prevEnc != null) {
                    int cmp = CollectionWireTypes.compareOctets(prevEnc, enc);
                    if (multiset) {
                        if (cmp > 0) {
                            throw new DerException("ObjectCodec.decodeCollection: canonicalise "
                                    + "multiset field (wireType " + wireType + ") is not in "
                                    + "non-decreasing X.690 §11.6 octet order (fail-secure)");
                        }
                    } else if (cmp >= 0) {
                        throw new DerException("ObjectCodec.decodeCollection: canonicalise set "
                                + "field (wireType " + wireType + ") is not in strictly ascending "
                                + "X.690 §11.6 octet order -- unsorted or duplicate element "
                                + "encoding (fail-secure, §2/§11.6)");
                    }
                }
                prevEnc = enc;
            }
            out.add(element);
        }

        if (!setKind) {
            // list: (preserve) or bag: (canonicalise multiset, duplicates retained) -> a List
            // (Discipline.CANONICALISE_MULTISET is documented as "reconstructed as a List").
            return new ImmutableList<>(out);
        }
        // set: (canonicalise) never carries SortedSet-declared semantics (CollectionWireTypes
        // .disciplineFor never maps a Comparable-ordered class to CANONICALISE); only
        // orderedset: (PRESERVE_ORDERED, which bundles SortedSet/NavigableSet together with
        // LinkedHashSet/EnumSet under one token) needs the declared-type check.
        if (!canonicalise && isSortedType(declaredType, true)) {
            return new ImmutableSortedSet<>(out);
        }
        return new ImmutableSet<>(out);
    }

    /**
     * Decodes a {@code map:}/{@code orderedmap:} collection body
     * ({@code (SET|SEQUENCE) OF SEQUENCE{key,value}}). A CANONICALISE map ({@code map:})
     * requires the KEY encodings to be <b>strictly ascending</b> ({@code compareOctets(prevKey,
     * curKey) < 0}) -- symmetry with the key-only encode sort, subsuming the duplicate-key check
     * in O(n). A PRESERVE map ({@code orderedmap:}) applies no order check. Entry count is capped
     * at {@link #MAX_COLLECTION} (§4.5).
     *
     * <p>Entries are accumulated into a plain {@link ArrayList} of {@link
     * AbstractMap.SimpleImmutableEntry} pairs (a purely positional append -- constructing a
     * {@code SimpleImmutableEntry} only assigns its key/value fields, and {@code ArrayList.add}
     * never invokes {@code hashCode()}/{@code equals()}/{@code compareTo()} on either), never a
     * {@code HashMap}/{@code LinkedHashMap} (whose {@code put} hashes the key) and never a {@code
     * TreeMap} (whose {@code put} compares it). The final immutable wrapper ({@link ImmutableMap}
     * or {@link ImmutableSortedMap}) is built from that list via a single {@code
     * List.toArray(Object[])} array copy -- so construction of the returned map value invokes
     * zero methods on the decoded keys/values.
     */
    private static Object decodeMap(DerReader body, String wireType, boolean canonicalise,
                                    int depth, DeserializationCompletion decodeUnit,
                                    ResolutionContext resolution, Class<?> declaredType,
                                    Class<?> expectedKeyType, Class<?> expectedValueType)
            throws DerException, IOException, ClassNotFoundException {
        String[] kv = CollectionWireTypes.mapKeyValueWireTypes(wireType);
        String keyWT = kv[0];
        String valWT = kv[1];
        // Purely positional accumulator -- see method Javadoc for why this must never be a
        // hash-bucketed (HashMap/LinkedHashMap) or comparison-ordered (TreeMap) structure.
        List<Map.Entry<?, ?>> out = new ArrayList<>();

        int count = 0;
        byte[] prevKeyEnc = null;
        while (body.hasMore()) {
            if (++count > MAX_COLLECTION) {
                throw new DerException("ObjectCodec.decodeCollection: entry count exceeds "
                        + "maxCollection (" + MAX_COLLECTION + ", §4.5) for field '" + wireType + "'");
            }
            // Each entry is a SEQUENCE{key,value} (0x30) regardless of the outer SET/SEQUENCE tag.
            DerReader entry = body.readSequence();
            int keyStart = entry.position();
            Object key = decodeElementValue(entry, keyWT, depth, decodeUnit, resolution,
                                            expectedKeyType);
            int keyEnd = entry.position();
            Object val = decodeElementValue(entry, valWT, depth, decodeUnit, resolution,
                                            expectedValueType);
            if (entry.hasMore()) {
                throw new DerException("ObjectCodec.decodeCollection: map entry SEQUENCE has "
                        + "more than {key,value} (wireType " + wireType + ")");
            }
            if (canonicalise) {
                byte[] keyEnc = entry.slice(keyStart, keyEnd);
                if (prevKeyEnc != null
                        && CollectionWireTypes.compareOctets(prevKeyEnc, keyEnc) >= 0) {
                    throw new DerException("ObjectCodec.decodeCollection: canonicalise map field "
                            + "(wireType " + wireType + ") keys are not in strictly ascending "
                            + "X.690 §11.6 octet order -- unsorted or duplicate key encoding "
                            + "(fail-secure, §2/§11.6)");
                }
                prevKeyEnc = keyEnc;
            }
            out.add(new AbstractMap.SimpleImmutableEntry<>(key, val));
        }

        // map: (canonicalise) never carries SortedMap-declared semantics (see decodeSetOrList's
        // matching comment); only orderedmap: (PRESERVE_ORDERED) needs the declared-type check.
        if (!canonicalise && isSortedType(declaredType, false)) {
            return new ImmutableSortedMap<>(out);
        }
        return new ImmutableMap<>(out);
    }

    /**
     * Whether {@code declaredType} -- the receiving field's LOCAL declared Java type, or {@code
     * null} if unknown -- is itself a {@code SortedSet}/{@code NavigableSet} ({@code set == true})
     * or a {@code SortedMap}/{@code NavigableMap} ({@code set == false}). Used only to select the
     * immutable wrapper shape for an {@code orderedset:}/{@code orderedmap:} field; see {@link
     * #decodeCollection(byte[], String, int, DeserializationCompletion, ResolutionContext, Class)}.
     * {@code null} (unknown) conservatively returns {@code false} (the plain, non-sorted shape).
     */
    private static boolean isSortedType(Class<?> declaredType, boolean set) {
        if (declaredType == null) {
            return false;
        }
        return set
                ? (java.util.SortedSet.class.isAssignableFrom(declaredType)
                        || java.util.NavigableSet.class.isAssignableFrom(declaredType))
                : (java.util.SortedMap.class.isAssignableFrom(declaredType)
                        || java.util.NavigableMap.class.isAssignableFrom(declaredType));
    }

    /**
     * Decodes a single collection element / map key / map value from {@code reader},
     * dispatching on its wire-type. Scalar / {@code String} / {@code enum} / {@code array:}
     * types are decoded by the package-private {@link DerFieldStore#decodeScalarElement}
     * bridge; {@code @AtomicSerial} and nested-collection types are decoded here so the
     * nesting depth is threaded. The reader advances past the element TLV.
     *
     * @param expectedElementType the receiving field's DECLARED element (or map key/value) type,
     *        recovered from its generic signature, threaded into the {@code @AtomicSerial}
     *        element's pre-construction admission gate (security review R2 F1). {@code Object.class}
     *        for a raw/wildcard/type-variable element or a nested-generic inner element (residual).
     */
    private static Object decodeElementValue(DerReader reader, String elemWT, int depth,
                                             DeserializationCompletion decodeUnit,
                                             ResolutionContext resolution,
                                             Class<?> expectedElementType)
            throws DerException, IOException, ClassNotFoundException {
        if ("@AtomicSerial".equals(elemWT)) {
            // Read the element's complete TLV (nested record SEQUENCE or DER NULL), then decode
            // it with the threaded depth so the cumulative MAX_NESTING guard applies AND the
            // declared element type bounds the pre-construction admission gate (F1).
            byte[] elemTlv = readOneTlv(reader);
            return decodeNested(elemTlv, expectedElementType, depth, decodeUnit, resolution);
        }
        if (CollectionWireTypes.isCollection(elemWT)) {
            // A nested collection element (a set: of set:, a map: value that is a set:, ...):
            // read its complete TLV and recurse -- bottom-up canonicalisation is a natural
            // consequence, since the inner encoding is already canonical before the outer sort.
            // Recurse at depth + 1 so each nested-collection level consumes one unit of the
            // MAX_NESTING budget (symmetric with the @AtomicSerial decodeNested->decodeHierarchy
            // depth+1 step); decodeCollection's entry check rejects the over-deep level fail-secure.
            // The INNER element type is NOT recovered (nested-generic residual, chosen flat scope):
            // recurse with unknown element types (Object.class) -- see the F1 residual note.
            byte[] elemTlv = readOneTlv(reader);
            return decodeCollection(elemTlv, elemWT, depth + 1, decodeUnit, resolution);
        }
        if (AnyCodec.ANY.equals(elemWT)) {
            // The self-describing Any element (memo §4): dispatch on the element's own context tag.
            // AnyCodec.decode reads exactly one AnyElement TLV, advancing the reader, and enforces
            // the four decoder fences (depth-thread, ATOMIC-gate reuse, hard-reject, canonical tag).
            return AnyCodec.decode(reader, depth, decodeUnit, resolution);
        }
        // Scalar / String / byte[] / enum: / array: element -> WireTypes via the getarg bridge.
        return DerFieldStore.decodeScalarElement(reader, elemWT, resolution);
    }

    /**
     * Reads one complete TLV (tag + length + content) from {@code reader} and returns its
     * full bytes, advancing the cursor. Used to capture a nested element for depth-threaded
     * decode.
     */
    private static byte[] readOneTlv(DerReader reader) throws DerException {
        DerReader.TlvHeader hdr = reader.readTlvHeader();
        byte[] content = reader.readRawContent(hdr.contentLength());
        byte[] tagBytes    = hdr.tag().encode();
        byte[] lengthBytes = DerWriter.encodeLength(hdr.contentLength());
        byte[] tlv = new byte[tagBytes.length + lengthBytes.length + content.length];
        int pos = 0;
        System.arraycopy(tagBytes,    0, tlv, pos, tagBytes.length);    pos += tagBytes.length;
        System.arraycopy(lengthBytes, 0, tlv, pos, lengthBytes.length); pos += lengthBytes.length;
        System.arraycopy(content,     0, tlv, pos, content.length);
        return tlv;
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
