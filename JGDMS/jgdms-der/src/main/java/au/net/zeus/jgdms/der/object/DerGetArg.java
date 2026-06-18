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
 *   <li>{@code serialClasses()}, {@code getReader()} (returns {@code null}) and
 *       {@code getObjectStreamContext()} (returns an empty list).</li>
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
        // Nested @AtomicSerial[] array field: decode lazily (STD-008 sec.17.2).
        // Check BEFORE isNested (different wrapper type); depth is threaded so the
        // cumulative MAX_NESTING guard applies per element.
        if (store.isNestedArray(name)) {
            try {
                return ObjectCodec.decodeNestedArray(
                        store.rawNestedArray(name),
                        store.nestedArrayComponentClassName(name),
                        depth, decodeUnit);
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
                return ObjectCodec.decodeNested(store.rawNested(name), depth, decodeUnit);
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
     * Decode-free presence hook backing {@link #defaulted(String)}.
     */
    @Override
    public <T> T get(String name, T val, Class<T> type) throws IOException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        DerFieldStore store = callerStore();
        // Nested @AtomicSerial[] array field: decode lazily (same as get(String, Object))
        if (store.isNestedArray(name)) {
            Object decoded;
            try {
                decoded = ObjectCodec.decodeNestedArray(
                        store.rawNestedArray(name),
                        store.nestedArrayComponentClassName(name),
                        depth, decodeUnit);
            } catch (DerException e) {
                InvalidObjectException ioe = new InvalidObjectException(
                        "DerGetArg: failed to decode nested @AtomicSerial[] field '"
                        + name + "': " + e.getMessage());
                ioe.initCause(e);
                throw ioe;
            } catch (ClassNotFoundException e) {
                InvalidObjectException ioe = new InvalidObjectException(
                        "DerGetArg: class not found decoding nested array field '"
                        + name + "': " + e.getMessage());
                ioe.initCause(e);
                throw ioe;
            }
            if (decoded == null) return val;
            if (type.isInstance(decoded)) {
                @SuppressWarnings("unchecked")
                T result = (T) decoded;
                return result;
            }
            InvalidObjectException e = new InvalidObjectException(
                    "DerGetArg: nested array field '" + name + "' type mismatch");
            e.initCause(new ClassCastException(
                    "Expected instance of " + type.getName()
                    + " but got " + decoded.getClass().getName()));
            throw e;
        }
        // Nested @AtomicSerial field: decode lazily (same as get(String, Object))
        if (store.isNested(name)) {
            Object decoded;
            try {
                decoded = ObjectCodec.decodeNested(store.rawNested(name), depth, decodeUnit);
            } catch (DerException e) {
                InvalidObjectException ioe = new InvalidObjectException(
                        "DerGetArg: failed to decode nested @AtomicSerial field '"
                        + name + "': " + e.getMessage());
                ioe.initCause(e);
                throw ioe;
            } catch (ClassNotFoundException e) {
                InvalidObjectException ioe = new InvalidObjectException(
                        "DerGetArg: class not found decoding nested field '"
                        + name + "': " + e.getMessage());
                ioe.initCause(e);
                throw ioe;
            }
            if (decoded == null) {
                return val; // null -> return default (consistent with other get overloads)
            }
            if (type.isInstance(decoded)) {
                @SuppressWarnings("unchecked")
                T result = (T) decoded;
                return result;
            }
            InvalidObjectException e = new InvalidObjectException(
                    "DerGetArg: nested field '" + name + "' type mismatch");
            e.initCause(new ClassCastException(
                    "Expected instance of " + type.getName()
                    + " but got " + decoded.getClass().getName()));
            throw e;
        }
        Object stored = store.get(name, null);
        if (stored == null) {
            // Field is absent -- return default
            return val;
        }
        if (type.isInstance(stored)) {
            @SuppressWarnings("unchecked")
            T result = (T) stored;
            return result;
        }
        InvalidObjectException e = new InvalidObjectException(
                "DerGetArg: input validation failed for field '" + name + "'");
        e.initCause(new ClassCastException(
                "Expected instance of " + type.getName()
                + " but got " + stored.getClass().getName()));
        throw e;
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
     * Returns {@code null} -- there is no {@link AtomicSerial.ReadObject} in the DER
     * path. DER-path classes do not use {@code @ReadInput}.
     */
    @Override
    public AtomicSerial.ReadObject getReader() {
        return null;
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
