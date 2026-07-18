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
                return ObjectCodec.decodeCollection(
                        store.rawCollection(name),
                        store.collectionWireType(name),
                        depth, decodeUnit, resolution,
                        declaredFieldType(callerClass, name));
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
