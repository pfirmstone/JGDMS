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
import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamClass;
import java.lang.StackWalker.Option;
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
 * <h2>Caller dispatch via StackWalker</h2>
 * <p>
 * Every {@code get}, {@code defaulted}, and {@code getReader} call resolves
 * to the {@link DerFieldStore} of the {@code @AtomicSerial} class whose
 * {@code (GetArg)} constructor (or its static {@code check} method) is currently
 * on the call stack. The resolution is performed by
 * {@link #callerClass()}, which uses
 * {@code StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)} to walk frames
 * and return the FIRST frame whose declaring class is a key in the map.
 *
 * <h2>Why StackWalker for caller resolution</h2>
 * <p>
 * The reference implementation {@code GetArgImpl} resolves the calling class via a
 * nested {@code SecurityManager} subclass that calls {@code getClassContext()}.
 * That mechanism remains valid on DirtyChai (the JGDMS target JDK), which
 * <em>retains and advances</em> the Authorization / {@code SecurityManager}
 * framework ({@code au.zeus.jdk.authorization.*}) rather than removing it.
 * {@code DerGetArg} instead uses {@code StackWalker} -- the same caller-stack
 * introspection API the DirtyChai JDK itself uses for caller validation: it
 * resolves the caller class directly, without instantiating a
 * {@code SecurityManager} subclass, and is lazy-streaming and {@code null}-safe.
 * Both approaches are valid on DirtyChai; StackWalker is chosen here as the
 * cleaner, dependency-free option (and it is also the only one of the two that
 * survives on a stock OpenJDK that has dropped the Authorization framework).
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
 *
 * <h2>getObjectStreamClass</h2>
 * <p>
 * Returns {@code null} in the DER path -- there is no {@link ObjectStreamClass} in
 * a DER-decoded object. Fixture constructors (and production code in the DER path)
 * MUST NOT call this method. It is present only to satisfy the abstract contract.
 *
 * <h2>getReader</h2>
 * <p>
 * Returns {@code null} -- there is no {@link AtomicSerial.ReadObject} in the DER
 * path; DER classes do not use {@code @ReadInput}.
 *
 * <h2>getObjectStreamContext</h2>
 * <p>
 * Returns {@link Collections#emptyList()} -- no ObjectStreamContext in the DER path.
 */
public final class DerGetArg extends AtomicSerial.GetArg {

    private static final StackWalker WALKER =
            StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

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
     * Constructs a {@code DerGetArg} at nesting depth 0 (top-level decode).
     *
     * @param storeMap ordered map of class -> DerFieldStore (must not be {@code null};
     *                 must not be empty; for Phase 4.1 has exactly one entry)
     * @throws NullPointerException if {@code storeMap} is {@code null} or empty
     */
    public DerGetArg(Map<Class<?>, DerFieldStore> storeMap) {
        this(storeMap, 0);
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
        super(); // protected GetArg() performs a SerializablePermission
                 // "enableSubclassImplementation" check (AtomicSerial.Check.check()).
                 // GetArgImpl avoids it via the package-private GetArg(boolean)
                 // constructor (it shares AtomicSerial's package); DerGetArg lives in
                 // a separate module/package and intentionally goes through the
                 // checked constructor, so under an active DirtyChai SecurityManager
                 // the DER deserializer's codebase must be granted that
                 // SerializablePermission by policy.
        Objects.requireNonNull(storeMap, "storeMap");
        if (storeMap.isEmpty()) {
            throw new IllegalArgumentException("storeMap must not be empty");
        }
        // Defensive copy preserving insertion order
        this.storeMap = Collections.unmodifiableMap(new LinkedHashMap<>(storeMap));
        this.depth = depth;
    }

    // =========================================================================
    // Caller dispatch
    // =========================================================================

    /**
     * Resolves the {@link DerFieldStore} for the class currently calling via
     * {@link #callerClass()}.
     *
     * @return the {@code DerFieldStore} for the calling class
     * @throws InvalidObjectException if no matching class is found on the stack
     */
    private DerFieldStore callerStore() throws InvalidObjectException {
        Class<?> caller = callerClass();
        DerFieldStore store = storeMap.get(caller);
        if (store == null) {
            throw new InvalidObjectException(
                    "DerGetArg: no field store for caller class " + caller.getName()
                    + "; registered classes: " + storeMap.keySet());
        }
        return store;
    }

    /**
     * Walks the call stack and returns the first frame whose declaring class is
     * a key in {@link #storeMap}.
     *
     * <p>If exactly one class is registered (Phase 4.1) and no matching frame is
     * found, returns that single class as a safe fallback. (This handles edge cases
     * where synthetic bridge methods or lambdas appear between the constructor and
     * this call.)
     *
     * @return the {@code @AtomicSerial} class currently invoking this {@code GetArg}
     * @throws InvalidObjectException if the stack has no recognisable @AtomicSerial
     *         frame and the map has more than one entry (hierarchy: ambiguous)
     */
    private Class<?> callerClass() throws InvalidObjectException {
        Class<?> found = WALKER.walk(frames ->
            frames.map(StackWalker.StackFrame::getDeclaringClass)
                  .filter(storeMap::containsKey)
                  .findFirst()
                  .orElse(null)
        );
        if (found != null) {
            return found;
        }
        // Fallback: if only one class registered, use it (safe for Phase 4.1)
        if (storeMap.size() == 1) {
            return storeMap.keySet().iterator().next();
        }
        throw new InvalidObjectException(
                "DerGetArg: cannot determine caller @AtomicSerial class from stack; "
                + "registered classes: " + storeMap.keySet());
    }

    // =========================================================================
    // ObjectInputStream.GetField abstract methods
    // =========================================================================

    /**
     * Returns {@code null} -- no {@link ObjectStreamClass} exists in the DER path.
     * DER-path constructors and {@code check} methods MUST NOT call this method.
     */
    @Override
    public ObjectStreamClass getObjectStreamClass() {
        return null;
    }

    @Override
    public boolean defaulted(String name) throws IOException {
        Objects.requireNonNull(name, "name");
        return callerStore().defaulted(name);
    }

    @Override
    public boolean get(String name, boolean val) throws IOException {
        Objects.requireNonNull(name, "name");
        return callerStore().get(name, val);
    }

    @Override
    public byte get(String name, byte val) throws IOException {
        Objects.requireNonNull(name, "name");
        return callerStore().get(name, val);
    }

    /**
     * {@code char} fields are deferred per STD-006 S7.6.
     * Always throws {@link InvalidObjectException}.
     */
    @Override
    public char get(String name, char val) throws IOException {
        throw new InvalidObjectException(
                "DerGetArg: char fields are deferred per S7.6; field: " + name);
    }

    @Override
    public short get(String name, short val) throws IOException {
        Objects.requireNonNull(name, "name");
        return callerStore().get(name, val);
    }

    @Override
    public int get(String name, int val) throws IOException {
        Objects.requireNonNull(name, "name");
        return callerStore().get(name, val);
    }

    @Override
    public long get(String name, long val) throws IOException {
        Objects.requireNonNull(name, "name");
        return callerStore().get(name, val);
    }

    /**
     * {@code float} fields are deferred per STD-006 S7.6.
     * Always throws {@link InvalidObjectException}.
     */
    @Override
    public float get(String name, float val) throws IOException {
        throw new InvalidObjectException(
                "DerGetArg: float fields are deferred per S7.6; field: " + name);
    }

    /**
     * {@code double} fields are deferred per STD-006 S7.6.
     * Always throws {@link InvalidObjectException}.
     */
    @Override
    public double get(String name, double val) throws IOException {
        throw new InvalidObjectException(
                "DerGetArg: double fields are deferred per S7.6; field: " + name);
    }

    @Override
    public Object get(String name, Object val) throws IOException {
        Objects.requireNonNull(name, "name");
        DerFieldStore store = callerStore();
        // Nested @AtomicSerial field: decode lazily on access (STD-008 sec.16).
        // The store holds the raw TLV bytes; ObjectCodec.decodeNested does the
        // actual decode here in der.object so that der.getarg stays cycle-free.
        if (store.isNested(name)) {
            try {
                return ObjectCodec.decodeNested(store.rawNested(name), depth);
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
        }
        return store.get(name, val);
    }

    /**
     * Typed Object get with type check (mirrors {@code GetArgImpl}).
     *
     * <p>If the stored value is {@code null} (field absent), returns {@code val}.
     * If the stored value is not an instance of {@code type}, throws
     * {@link InvalidObjectException} with a {@link ClassCastException} cause.
     *
     * @param <T>  the expected type
     * @param name the field name
     * @param val  the default value if absent
     * @param type the expected runtime type (must not be {@code null})
     * @return the decoded value cast to T, or {@code val} if absent
     * @throws InvalidObjectException if the stored value is not an instance of {@code type}
     * @throws NullPointerException   if {@code name} or {@code type} is {@code null}
     */
    @Override
    public <T> T get(String name, T val, Class<T> type) throws IOException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        DerFieldStore store = callerStore();
        // Nested @AtomicSerial field: decode lazily (same as get(String, Object))
        if (store.isNested(name)) {
            Object decoded;
            try {
                decoded = ObjectCodec.decodeNested(store.rawNested(name), depth);
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

    // =========================================================================
    // AtomicSerial.GetArg abstract methods
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
     * Returns an empty, immutable list -- there is no ObjectStreamContext in the
     * DER path.
     */
    @Override
    public Collection getObjectStreamContext() {
        return Collections.emptyList();
    }

    /**
     * Validates invariants for the calling class's fields (mirrors
     * {@code GetArgImpl.validateInvariants}).
     *
     * <p>For each field in {@code fields}:
     * <ul>
     *   <li>If the field type is primitive, calls the corresponding typed
     *       {@code get} to verify the field is decodable.</li>
     *   <li>If the field type is an Object type, retrieves the value and checks:
     *       <ul>
     *         <li>If {@code nonNull[i]} is {@code true} and the value is
     *             {@code null} (absent), throws {@link InvalidObjectException}.</li>
     *         <li>If the value is non-null and not an instance of
     *             {@code types[i]}, throws {@link InvalidObjectException}.</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param fields  array of field names
     * @param types   array of expected types, parallel to {@code fields}
     * @param nonNull array of non-null flags, parallel to {@code fields}
     * @return {@code this} (fluent)
     * @throws IOException              if any invariant is violated
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if array lengths differ
     */
    @Override
    public AtomicSerial.GetArg validateInvariants(String[] fields, Class[] types,
                                                   boolean[] nonNull) throws IOException {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(nonNull, "nonNull");
        if (fields.length != types.length || fields.length != nonNull.length) {
            throw new IllegalArgumentException(
                    "validateInvariants: arrays must have equal length");
        }
        // Resolve caller store once, outside the loop (same caller throughout)
        DerFieldStore store = callerStore();
        for (int i = 0; i < fields.length; i++) {
            Class<?> t = types[i];
            String fieldName = fields[i];
            if (t.isPrimitive()) {
                // Force a get to confirm the field is decodable / present
                if (t == boolean.class) store.get(fieldName, false);
                else if (t == byte.class)  store.get(fieldName, (byte) 0);
                else if (t == short.class) store.get(fieldName, (short) 0);
                else if (t == int.class)   store.get(fieldName, 0);
                else if (t == long.class)  store.get(fieldName, 0L);
                // char/float/double are deferred -- skip silently (S7.6)
            } else {
                // For nested @AtomicSerial fields, decode via get(name, null) so the
                // NestedRaw wrapper is resolved through ObjectCodec.decodeNested.
                Object v;
                if (store.isNested(fieldName)) {
                    v = get(fieldName, (Object) null); // routes through nested decode
                } else {
                    v = store.get(fieldName, null);
                }
                if (nonNull[i] && v == null) {
                    throw new InvalidObjectException(
                            "validateInvariants: field '" + fieldName + "' must not be null");
                }
                if (v != null && !t.isInstance(v)) {
                    throw new InvalidObjectException(
                            "validateInvariants: field '" + fieldName
                            + "' must be an instance of " + t.getName()
                            + " but got " + v.getClass().getName());
                }
            }
        }
        return this;
    }
}
