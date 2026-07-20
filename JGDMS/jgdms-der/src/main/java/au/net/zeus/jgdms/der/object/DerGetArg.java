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
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import net.jini.io.context.DeserializationCompletion;
import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A real {@link AtomicSerial.GetArg} implementation backed by a
 * {@code Map<Class<?>, DerFieldStore>} -- one {@link DerFieldStore} per
 * {@code @AtomicSerial} class in the hierarchy (exactly one entry for Phase 4.1;
 * the map structure is ready for Phase 4.3 hierarchy support).
 *
 * <h2>Caller dispatch and idempotency (shared base)</h2>
 * <p>
 * As of Inc2, caller resolution, per-{@code (caller, field)} memoization and all
 * the typed {@code get} accessors live in the {@link AtomicSerial.GetArg} base
 * (which resolves the caller via {@code StackWalker} and makes the {@code get}
 * overloads {@code final} and idempotent). This class supplies only:
 * <ul>
 *   <li>{@link #lookup(Class, String)} -- the untyped value hook, called at most
 *       once per {@code (caller, name)} by the memoizing base. Lazily-decoded
 *       nested {@code @AtomicSerial} / {@code @AtomicSerial[]} fields are decoded
 *       HERE (so the cumulative {@code MAX_NESTING} depth guard is threaded), and
 *       the decoded value is then memoized by the base (decode-once);</li>
 *   <li>{@link #isDefaulted(Class, String)} -- the decode-free presence hook;</li>
 *   <li>{@code serialClasses()} and {@code getObjectStreamContext()} (returns the
 *       decode-unit context, or an empty list).</li>
 * </ul>
 * Because the base memoizes the first value returned per field, a hostile or
 * replayed {@code GetArg} cannot return one value to a class's
 * {@code check(GetArg)} and a different value to its {@code (GetArg)} constructor.
 *
 * <h2>check-before-construction contract</h2>
 * <p>
 * The {@code @AtomicSerial} pattern requires that every {@code (GetArg)}
 * constructor's first statement is a call to a static {@code check(GetArg)} method.
 * {@code check} reads and validates field values via {@code arg.get(...)}, throwing
 * {@link InvalidObjectException} on invariant violation BEFORE the object is
 * constructed. Because {@link ObjectCodec} drives construction by reflectively
 * invoking the {@code (GetArg)} constructor and propagating
 * {@link java.lang.reflect.InvocationTargetException}, a failing {@code check}
 * unwinds completely: NO object is returned and no partially-constructed instance
 * escapes.
 */
public final class DerGetArg extends AtomicSerial.GetArg {

    /**
     * One entry per {@code @AtomicSerial} class in the hierarchy.
     * Insertion order is superclass-first, leaf-last (matches {@code serialClasses()}).
     * For Phase 4.1 there is exactly one entry.
     */
    private final Map<Class<?>, DerFieldStore> storeMap;

    /**
     * Nesting depth (0 at top level) of the object being constructed. Threaded into
     * {@link ObjectCodec#decodeNested} so the {@code MAX_NESTING} DoS guard is
     * CUMULATIVE across the construction-driven recursion: a nested {@code @AtomicSerial}
     * field is decoded when this object's {@code (GetArg)} constructor calls
     * {@code arg.get(...)}, so without threading the depth the guard would reset to 0 at
     * every level and never trip (a hostile deeply-nested payload would overflow the stack).
     */
    private final int depth;

    /**
     * Per-decode-unit completion sink, exposed through {@link #getObjectStreamContext()}
     * as a {@link DeserializationCompletion} context element so a decoded DGC live
     * reference can register its batched {@code dirty}. {@code null} when there is no
     * decode-unit context (e.g. a standalone object decode). The same instance is threaded
     * to every nested {@code DerGetArg} of the decode unit, so nested DGC refs batch with
     * the outer refs.
     */
    private final DeserializationCompletion decodeUnit;

    /**
     * The endpoint-assigned {@link ResolutionContext} (default/verifier loaders + integrity
     * settings) used to resolve nested classes against the receiving endpoint's loader rather
     * than the thread-context loader (the Warres failure). A {@code ClassLoader} is a capability:
     * this field is {@code private}, reached only through the PACKAGE-PRIVATE accessors below by
     * trusted same-package resolution code ({@link ObjectCodec}, {@link DerProxySerializer}), and
     * is deliberately NEVER exposed via {@link #getObjectStreamContext()} -- broadcasting it to
     * every object in the decode graph would let a hostile object grab the loader and escalate.
     * Never {@code null} ({@link ResolutionContext#NONE} for a standalone decode).
     */
    private final ResolutionContext resolution;

    /**
     * Values INJECTED into this top-level {@code GetArg} by trusted decode code
     * ({@link ObjectCodec#decodeProxy}), keyed by a well-known injection key such as
     * {@link RawWireFormRetaining#RAW_FORM_KEY}. Read ONLY through {@link #getInjected(String)};
     * NEVER through the wire-field {@link #lookup(Class, String) lookup}/{@code get} path -- the
     * two channels are strictly disjoint (DC-1 WIRE-DISJOINT). This map is populated purely from
     * the receiver's local decode intent (never from the transmitted schema/data, never
     * auto-filled from ambient context -- DC-2 Model A), and is threaded to the ONE target frame
     * only: every nested {@code DerGetArg} built during this object's field decode is constructed
     * with an EMPTY injected map (DC-3 SCOPED), so an injected value never propagates into a
     * nested-object decode frame. Never {@code null} ({@link Collections#emptyMap()} when nothing
     * is injected -- the common case).
     */
    private final Map<String, Object> injected;

    /** Empty injected map shared by every non-injecting {@code DerGetArg} (DC-3: nested frames). */
    static final Map<String, Object> NO_INJECTION = Collections.emptyMap();

    /**
     * Constructs a {@code DerGetArg} at nesting depth 0 (top-level decode).
     *
     * @param storeMap ordered map of class -> DerFieldStore (must not be {@code null};
     *                 must not be empty; for Phase 4.1 has exactly one entry)
     * @throws NullPointerException if {@code storeMap} is {@code null} or empty
     */
    public DerGetArg(Map<Class<?>, DerFieldStore> storeMap) {
        this(storeMap, 0, null);
    }

    /**
     * Constructs a {@code DerGetArg} at the given nesting depth (used by
     * {@link ObjectCodec} when decoding a nested {@code @AtomicSerial} object so the
     * {@code MAX_NESTING} guard accumulates across nesting levels).
     *
     * @param storeMap ordered map of class -> DerFieldStore (must not be {@code null}/empty)
     * @param depth    the nesting depth of the object being constructed
     * @throws NullPointerException if {@code storeMap} is {@code null} or empty
     */
    public DerGetArg(Map<Class<?>, DerFieldStore> storeMap, int depth) {
        this(storeMap, depth, null);
    }

    /**
     * Constructs a {@code DerGetArg} at the given nesting depth with a decode-unit
     * completion token (threaded by {@link ObjectCodec} on the JERI/DER stream path so a
     * decoded DGC live reference can register its batched {@code dirty}).
     *
     * @param storeMap   ordered map of class -> DerFieldStore (must not be {@code null}/empty)
     * @param depth      the nesting depth of the object being constructed
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @throws NullPointerException if {@code storeMap} is {@code null} or empty
     */
    public DerGetArg(Map<Class<?>, DerFieldStore> storeMap, int depth,
                     DeserializationCompletion decodeUnit) {
        this(storeMap, depth, decodeUnit, ResolutionContext.NONE);
    }

    /**
     * Canonical constructor, additionally carrying the endpoint-assigned {@link ResolutionContext}
     * for trusted same-package class resolution ({@link ObjectCodec} nested decode,
     * {@link DerProxySerializer}). Package-private: only {@link ObjectCodec} (same package) threads
     * it in, so objects in the decode graph cannot reach the loaders it holds.
     *
     * @param storeMap   ordered map of class -> DerFieldStore (must not be null/empty)
     * @param depth      the nesting depth of the object being constructed
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @param resolution the endpoint-assigned resolution context (must not be {@code null};
     *                   {@link ResolutionContext#NONE} for a standalone decode)
     */
    DerGetArg(Map<Class<?>, DerFieldStore> storeMap, int depth,
              DeserializationCompletion decodeUnit,
              ResolutionContext resolution) {
        this(storeMap, depth, decodeUnit, resolution, NO_INJECTION);
    }

    /**
     * Canonical constructor additionally carrying the trusted decode-code {@code injected} map
     * (see {@link #injected}). Package-private: only {@link ObjectCodec} threads it in, and only
     * into the ONE target frame -- nested frames use {@link #NO_INJECTION}.
     *
     * @param storeMap   ordered map of class -> DerFieldStore (must not be null/empty)
     * @param depth      the nesting depth of the object being constructed
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @param resolution the endpoint-assigned resolution context (must not be {@code null})
     * @param injected   the trusted-decoder-populated injection map (must not be {@code null};
     *                   {@link #NO_INJECTION} when nothing is injected)
     */
    DerGetArg(Map<Class<?>, DerFieldStore> storeMap, int depth,
              DeserializationCompletion decodeUnit,
              ResolutionContext resolution,
              Map<String, Object> injected) {
        super(); // As of Inc2 step 6 the protected GetArg() constructor is a no-op
                 // (the SerializablePermission "enableSubclassImplementation" /
                 // Check.check() guard was dropped: idempotency of the final memoizing
                 // get() accessors makes check-then-construct sound even against an
                 // untrusted GetArg, so the subclass-construction permission is no
                 // longer required).
        Objects.requireNonNull(storeMap, "storeMap");
        if (storeMap.isEmpty()) {
            throw new IllegalArgumentException("storeMap must not be empty");
        }
        // Defensive copy preserving insertion order
        this.storeMap = Collections.unmodifiableMap(new LinkedHashMap<>(storeMap));
        this.depth = depth;
        this.decodeUnit = decodeUnit;
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.injected = Objects.requireNonNull(injected, "injected");
    }

    /**
     * Package-private: the endpoint-assigned resolution context, reached DIRECTLY by trusted
     * same-package code ({@link ObjectCodec} for nested decode). Not on the public {@code GetArg}
     * surface and not broadcast via {@link #getObjectStreamContext()} -- it holds capabilities.
     */
    ResolutionContext resolution() {
        return resolution;
    }

    /**
     * Package-private: the stream's default class loader, reached DIRECTLY by trusted same-package
     * resolution code ({@link DerProxySerializer}, which hands it to {@code ProxyCodebaseSpi.resolve}).
     * Not on the public {@code GetArg} surface and not broadcast via {@link #getObjectStreamContext()}.
     */
    ClassLoader streamDefaultLoader() {
        return resolution.defaultLoader();
    }

    /** Package-private: the stream's verifier class loader (see {@link #streamDefaultLoader()}). */
    ClassLoader streamVerifierLoader() {
        return resolution.verifierLoader();
    }

    // =========================================================================
    // AtomicSerial.GetArg base hooks
    // =========================================================================

    /**
     * Resolves the {@link DerFieldStore} for the already-resolved
     * {@code callerClass} (the base performs caller resolution against
     * {@link #serialClasses()}, which is exactly {@code storeMap.keySet()}, so a
     * missing entry indicates a programming error rather than an attacker-driven
     * mismatch).
     */
    private DerFieldStore store(Class<?> callerClass) throws InvalidObjectException {
        DerFieldStore store = storeMap.get(callerClass);
        if (store == null) {
            throw new InvalidObjectException(
                    "DerGetArg: no field store for caller class " + callerClass.getName()
                    + "; registered classes: " + storeMap.keySet());
        }
        return store;
    }

    /**
     * Untyped value hook. Returns the boxed field value, or {@link #ABSENT} when
     * the field is absent/defaulted. Nested {@code @AtomicSerial} and
     * {@code @AtomicSerial[]} fields are decoded here (depth-threaded); the base
     * memoizes the result so the decode happens exactly once per field.
     */
    @Override
    protected Object lookup(Class<?> callerClass, String name) throws IOException {
        DerFieldStore store = store(callerClass);
        // Collection/Map field (STD-006 §3.8): decode lazily, threading depth so nested
        // @AtomicSerial / nested-collection elements share the cumulative MAX_NESTING guard.
        if (store.isCollection(name)) {
            try {
                // Recover the DECLARED element type(s) from the field's generic signature and
                // thread them into the per-element pre-construction admission gate (security
                // review R2 F1): a Collection<Concrete>/Map<K,V> element rejects a foreign
                // wire-named serializer before its ctor runs. Recovery fails OPEN to Object.class
                // (prior behaviour) -- never a spurious rejection of a legitimate element.
                Class<?>[] elem = declaredElementTypes(callerClass, name);
                return ObjectCodec.decodeCollection(
                        store.rawCollection(name),
                        store.collectionWireType(name),
                        depth, decodeUnit, resolution,
                        declaredFieldType(callerClass, name),
                        elem[0], elem[1]);
            } catch (DerException e) {
                throw nested("failed to decode collection field", name, e);
            } catch (ClassNotFoundException e) {
                throw nested("class not found decoding collection field", name, e);
            }
        }
        // Nested @AtomicSerial[] array field: decode lazily (STD-008 sec.17.2).
        // Check BEFORE isNested (different wrapper type); depth is threaded so the
        // cumulative MAX_NESTING guard applies per element.
        if (store.isNestedArray(name)) {
            try {
                // Thread the receiver's DECLARED array component type as the per-element
                // pre-construction admission bound (security review R2). The WIRE component
                // class name still drives array allocation; the declared component type gates
                // which wire-named leaf each element may reconstruct. Unknown (no backing
                // field / non-array) -> Object.class, preserving prior behaviour.
                Class<?> declared = declaredFieldType(callerClass, name);
                Class<?> expectedComponent =
                        (declared != null && declared.isArray())
                                ? declared.getComponentType()
                                : Object.class;
                return ObjectCodec.decodeNestedArray(
                        store.rawNestedArray(name),
                        store.nestedArrayComponentClassName(name),
                        expectedComponent,
                        depth, decodeUnit, resolution);
            } catch (DerException e) {
                throw nested("failed to decode nested @AtomicSerial[] field", name, e);
            } catch (ClassNotFoundException e) {
                throw nested("class not found decoding nested array field", name, e);
            }
        }
        // Nested @AtomicSerial field: decode lazily (STD-008 sec.16). The store
        // holds the raw TLV bytes; ObjectCodec.decodeNested does the actual decode
        // here in der.object so that der.getarg stays cycle-free.
        if (store.isNested(name)) {
            try {
                // Thread the receiver's DECLARED field type as the pre-construction admission
                // bound (security review R2): a concrete/narrowly-typed field (e.g. an
                // X500Principal) rejects a foreign wire-named @AtomicSerial/serializer BEFORE
                // its ctor runs, while a legitimate @Serializer/Resolve substitution still
                // decodes. Unknown (synthesized field / no backing Field) -> Object.class,
                // preserving prior (untyped) behaviour -- a genuinely Object/broad-interface
                // slot is the documented residual.
                Class<?> declared = declaredFieldType(callerClass, name);
                Class<?> expected = declared != null ? declared : Object.class;
                return ObjectCodec.decodeNested(store.rawNested(name), expected,
                                                depth, decodeUnit, resolution);
            } catch (DerException e) {
                throw nested("failed to decode nested @AtomicSerial field", name, e);
            } catch (ClassNotFoundException e) {
                throw nested("class not found decoding nested field", name, e);
            }
        }
        // Plain field: ABSENT (decode-free) when absent/defaulted, else the decoded
        // value. defaulted() already treats a null map value as absent, so a present
        // non-nested value is never null here.
        if (store.defaulted(name)) {
            return ABSENT;
        }
        return store.get(name, null);
    }

    /**
     * Decode free presence hook.
     *
     * @return true if defaulted.
     */
    protected boolean isDefaulted(Class<?> callerClass, String name) throws IOException {
        return store(callerClass).defaulted(name);
    }

    /**
     * Returns a value injected by trusted decode code, or {@code null} if none.
     *
     * <p><b>DC-1 (WIRE-DISJOINT).</b> This reads ONLY the {@link #injected} map, which is
     * populated exclusively by {@link ObjectCodec#decodeProxy} from the receiver's local decode
     * intent -- NEVER the positional wire {@link DerFieldStore} consulted by {@link #lookup}. A
     * hostile peer that transmits a schema declaring a real wire field named identically to an
     * injection key has that field land in the {@code DerFieldStore} (reachable only via
     * {@code get}) and IGNORED here: {@code getInjected} never consults {@code storeMap}. The
     * transmitted schema/data can therefore neither populate nor influence what this returns.
     */
    @Override
    public Object getInjected(String name) {
        return injected.get(name);
    }

    private static InvalidObjectException nested(String what, String name, Throwable cause) {
        InvalidObjectException ioe = new InvalidObjectException(
                "DerGetArg: " + what + " '" + name + "': " + cause.getMessage());
        ioe.initCause(cause);
        return ioe;
    }

    /**
     * Best-effort resolution of the LOCAL declared Java type of {@code callerClass}'s field
     * {@code name}. It is consulted for two purposes, both narrowing-only:
     * <ol>
     *   <li>by {@link ObjectCodec#decodeCollection(byte[], String, int,
     *       DeserializationCompletion, ResolutionContext, Class)} to choose which immutable
     *       wrapper INTERFACE shape (plain vs {@code SortedSet}/{@code SortedMap}) to hand the
     *       receiving {@code check(GetArg)} for an {@code orderedset:}/{@code orderedmap:}
     *       field; and</li>
     *   <li>by {@link #lookup} as the receiving slot's declared type threaded into
     *       {@link ObjectCodec#decodeNested(byte[], Class, int, DeserializationCompletion,
     *       ResolutionContext)} / {@link ObjectCodec#decodeNestedArray(byte[], String, Class,
     *       int, DeserializationCompletion, ResolutionContext)} so the pre-construction
     *       admission gate can reject a foreign wire-named leaf in a concrete/narrowly-typed
     *       nested field BEFORE its ctor runs (security review R2). A {@code null} return
     *       (synthesized field / no backing {@code Field}) degrades to {@code Object.class} at
     *       the caller -- the prior untyped behaviour, and the documented broad-slot residual.</li>
     * </ol>
     * As before, this never selects WHICH schema drives decoding (STD-006 §7.8 -- always the
     * schema that travelled with the data); it only bounds/wraps the ALREADY-schema-selected
     * decode. A disagreement with the wire degrades fail-secure (a type mismatch surfaced to
     * the caller), never a data-integrity issue.
     *
     * <p>On purpose 1 specifically: STD-006 §3.8's {@code PRESERVE_ORDERED} discipline bundles
     * {@code SortedSet}/{@code
     * NavigableSet} together with {@code LinkedHashSet}/{@code EnumSet} into the SAME wire token,
     * so the token alone cannot distinguish them (see {@code CollectionWireTypes#disciplineFor}).
     * This mirrors, on the decode side, the same {@code declaring.getDeclaredField(sf.getName())}
     * lookup {@code SchemaGenerator.backingFieldGenericType} performs on the encode side to derive
     * the token in the first place.
     *
     * <h2>Scope -- never affects WHAT is decoded, only how it is WRAPPED</h2>
     * <p>This lookup does <b>not</b> select which schema/field-list drives decoding -- per
     * STD-006 §7.8 (normative), decode always uses the schema that travelled with the data; the
     * receiver's own {@code serialForm()}/fields are never consulted for that. This is strictly
     * narrower: it only ever changes which {@code java.util} interface the ALREADY-decoded,
     * already-ordered elements are exposed through. A disagreement with the wire (schema
     * mismatch, a renamed/retyped field, or a synthesized field with no backing {@code Field})
     * degrades to the plain (non-sorted) shape -- the same shape this codec always returned
     * before this feature existed -- so at worst a caller's {@code arg.get(name, val,
     * SortedSet.class)} throws a fail-secure {@code InvalidObjectException}; it can never corrupt
     * the decoded elements or their order.
     *
     * <p>Never throws: returns {@code null} (unknown) if there is no such field, if a {@code
     * SecurityException} is thrown under a restrictive policy, or on any other reflective
     * failure. Reading a {@link java.lang.reflect.Field}'s declared type is metadata-only (no
     * {@code setAccessible} / value access), so this is run privileged purely to avoid making an
     * optional, best-effort convenience fail under a caller-sensitive policy that would otherwise
     * be unrelated to whether this field decodes correctly.
     */
    private static Class<?> declaredFieldType(Class<?> callerClass, String name) {
        try {
            return java.security.AccessController.doPrivileged(
                    (java.security.PrivilegedExceptionAction<Class<?>>)
                            () -> callerClass.getDeclaredField(name).getType());
        } catch (Exception e) {
            // Best-effort only -- see method Javadoc. Any failure here (no such field, a
            // SecurityException, ...) simply means the plain, non-sorted wrapper shape is used.
            return null;
        }
    }

    /**
     * Recovers the receiving collection/map field's DECLARED element type(s) from its generic
     * signature, for the per-element pre-construction admission gate (security review R2 F1).
     * Returns a 2-element array {@code [keyType, valueType]}:
     * <ul>
     *   <li>a {@code Map} field {@code M<K,V>} &rarr; {@code [rawClassOf(K), rawClassOf(V)]}
     *       (KEY elements gated at {@code [0]}, VALUE elements at {@code [1]});</li>
     *   <li>a {@code Collection}/{@code Iterable} field {@code C<E>} &rarr;
     *       {@code [Object.class, rawClassOf(E)]} (elements gated at {@code [1]});</li>
     *   <li>a raw {@code Set}/{@code Map} (no type args), an unparameterized field, or any
     *       reflective failure &rarr; {@code [Object.class, Object.class]} (unchanged residual).</li>
     * </ul>
     *
     * <p><b>Fail-open.</b> This is a narrowing-only, best-effort recovery: it NEVER throws and
     * NEVER returns a type that could spuriously reject a legitimate element -- any uncertainty
     * (raw type, wildcard, type variable, nested-generic inner type, missing/renamed field,
     * {@code SecurityException}) degrades to {@code Object.class}, which the gate always admits
     * (clause 1). Reading a {@code Field}'s generic type is metadata-only (no {@code setAccessible}
     * / value access); run privileged only so an optional convenience does not fail under a
     * caller-sensitive policy unrelated to whether the field decodes.
     */
    private static Class<?>[] declaredElementTypes(Class<?> callerClass, String name) {
        Class<?>[] residual = { Object.class, Object.class };
        try {
            return java.security.AccessController.doPrivileged(
                    (java.security.PrivilegedExceptionAction<Class<?>[]>) () -> {
                        Field f = callerClass.getDeclaredField(name);
                        Type generic = f.getGenericType();
                        if (!(generic instanceof ParameterizedType pt)) {
                            return residual; // raw Set/Map or non-generic field
                        }
                        Type[] args = pt.getActualTypeArguments();
                        Class<?> raw = f.getType();
                        if (Map.class.isAssignableFrom(raw) && args.length == 2) {
                            return new Class<?>[] { rawClassOf(args[0]), rawClassOf(args[1]) };
                        }
                        // Collection / Iterable single-arg element (Set<E>, List<E>, ...).
                        if (Iterable.class.isAssignableFrom(raw) && args.length == 1) {
                            return new Class<?>[] { Object.class, rawClassOf(args[0]) };
                        }
                        return residual;
                    });
        } catch (Exception e) {
            return residual;
        }
    }

    /**
     * The raw erasure {@link Class} of a generic {@link Type} for the F1 element-admission gate,
     * fail-open to {@code Object.class}:
     * <ul>
     *   <li>a {@code Class} (e.g. {@code X500Principal}, {@code InvocationConstraint}) &rarr; itself;</li>
     *   <li>a {@code ParameterizedType} (nested generic, e.g. {@code List<X>}) &rarr; its RAW type
     *       erasure ({@code List.class}) -- the inner element stays an {@code Object.class} residual
     *       (chosen flat scope);</li>
     *   <li>a wildcard {@code ? extends B} &rarr; the raw class of {@code B} (its upper bound);
     *       a bare {@code ?} or {@code ? super X} has upper bound {@code Object} &rarr;
     *       {@code Object.class};</li>
     *   <li>a type variable or generic array &rarr; {@code Object.class}.</li>
     * </ul>
     */
    private static Class<?> rawClassOf(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            return raw instanceof Class<?> rc ? rc : Object.class;
        }
        if (t instanceof WildcardType w) {
            Type[] upper = w.getUpperBounds();
            // ? extends B -> B; bare ? and ? super X both have upper bound Object -> Object.class.
            return upper.length == 1 ? rawClassOf(upper[0]) : Object.class;
        }
        return Object.class; // TypeVariable, GenericArrayType, or anything unexpected
    }

    // =========================================================================
    // AtomicSerial.GetArg metadata methods
    // =========================================================================

    /**
     * Returns the set of {@code @AtomicSerial} classes whose field stores are held
     * by this {@code DerGetArg}, in insertion order (superclass-first, leaf-last).
     *
     * @return array of registered classes
     */
    @Override
    public Class[] serialClasses() {
        return storeMap.keySet().toArray(new Class[0]);
    }

    /**
     * Returns the decode-unit context for this object stream.
     *
     * <p>When this {@code DerGetArg} was constructed with a decode-unit completion token
     * (the JERI/DER stream path), the returned collection holds a single
     * {@link DeserializationCompletion} element: a decoded DGC live reference (e.g.
     * {@code net.jini.jeri.BasicObjectEndpoint}) registers its batched {@code dirty}
     * callback on it, to be fired when the decode unit completes and before the stream is
     * acknowledged. Otherwise the collection is empty.
     */
    @Override
    public Collection getObjectStreamContext() {
        return decodeUnit == null
                ? Collections.emptyList()
                : Collections.singletonList(decodeUnit);
    }
}
