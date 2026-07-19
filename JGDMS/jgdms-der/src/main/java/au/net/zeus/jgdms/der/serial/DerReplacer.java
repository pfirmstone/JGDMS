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

package au.net.zeus.jgdms.der.serial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectStreamException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import au.net.zeus.jgdms.der.DerException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Replace;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;

/**
 * Lets the DER codec encode a type that is <em>not</em> itself {@code @AtomicSerial}
 * by substituting an {@code @AtomicSerial} <em>serializer</em> for it on the wire
 * (the DER analogue of {@code writeReplace}), and rebuilding the original object on
 * decode via {@code readResolve}.
 *
 * <p><b>Registration honours {@link Serializer @Serializer}.</b> A serializer is an
 * existing {@code @AtomicSerial} class annotated {@code @Serializer(replaceObType = X)}
 * with a constructor that accepts {@code X} and (for decode) implementing
 * {@link Resolve}. These already exist in {@code org.apache.river.api.io}
 * (e.g. {@code X500PrincipalSerializer}); the DER codec reuses them as-is rather than
 * reimplementing them. Because such a serializer is itself {@code @AtomicSerial}, the
 * DER value-tree encodes it natively once substituted.
 *
 * <p><b>What the registry is -- and is NOT (security review R2 correction).</b> The
 * registered set is an <b>encode-substitution + schema-generation determinism control</b>:
 * it governs which non-{@code @AtomicSerial} value the <em>sender</em> substitutes on encode
 * ({@link #replace}) and which declared field types the {@code SchemaGenerator} admits
 * ({@link #isRegistered}). It is a CLOSED part of the wire contract (WI-2) -- loaded from a
 * <em>single platform-controlled resource</em> that travels in jgdms-der's own module/jar
 * (see {@link #load()}), <b>not</b> merged over every {@value #SERIALIZER_LIST_RESOURCE} on
 * the application classpath -- precisely because {@link #isRegistered} feeds the
 * {@code SchemaGenerator} (so an open set would make the wire {@code schemaDigest} a function
 * of deployment classpath). Adding a serializer -- especially an <em>interface-keyed</em> one
 * -- is a versioned, board-reviewed schema change (governance clause: <b>STD-006 §7.6.1</b>;
 * decode-admission: STD-008 §16.2).
 *
 * <p><b>The registry is NOT a decode-admission boundary.</b> On decode the codec does
 * <em>not</em> consult this registry at all: {@link #resolve} simply honours the java.io
 * {@code Resolve} interface ({@code readResolve()}), and the nested-record path
 * ({@code ObjectCodec.decodeNested} &rarr; {@code decodeHierarchy}) reconstructs whatever
 * {@code @AtomicSerial} leaf the transmitted schema chain names, whether or not it is
 * registered here. Decode admission for a nested field is therefore enforced <b>elsewhere</b>:
 * the caller's <em>declared-type assignability gate</em>
 * ({@code ObjectCodec.admissibleConstructClass}, run before construction), the
 * {@code DeSerializationPermission("ATOMIC")} gate (a no-op under SM-less / DirtyChai
 * deployments), each class's {@code check(GetArg)}, and the decode depth/size bounds. Do not
 * rely on this registry to gate what a peer may reconstruct.
 *
 * <p><b>Deterministic selection (WI-1).</b> {@link #serializerFor} resolves a target
 * type by <em>exact-match &rarr; unique most-specific assignable &rarr; fail-closed</em>
 * over the full class-and-interface assignability partial order; incomparable
 * multiplicity throws {@link DerException} at encode/schema-generation time rather
 * than silently picking one. The same predicate backs both {@link #isRegistered}
 * (schema generation) and {@link #replace} (runtime encode) so schema and encode can
 * never diverge.
 *
 * <p><b>No visibility or permission obstacle.</b> A serializer class is referenced by
 * name, so it need not be {@code public}; the {@code (X)} constructor is invoked via
 * {@code setAccessible(true)} -- the same reflective access (and the same
 * {@code ReflectPermission("suppressAccessChecks")}) the codec already uses for every
 * {@code @AtomicSerial} field and constructor.
 */
public final class DerReplacer {

    /** Classpath resource listing serializer class names (one per line). */
    public static final String SERIALIZER_LIST_RESOURCE = "META-INF/jgdms/der-serializers";

    /** Absolute form used for the single closed-resource lookup on the codec's own loader. */
    private static final String SERIALIZER_LIST_ABSOLUTE = "/" + SERIALIZER_LIST_RESOURCE;

    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.serial");

    /**
     * The immutable production registry, loaded once from the single platform
     * resource. {@code replaceObType -> serializer class}.
     */
    private static final Map<Class<?>, Class<?>> PRODUCTION =
            Collections.unmodifiableMap(load());

    /**
     * The registry actually consulted at runtime. In production this is
     * {@link #PRODUCTION}. The package-private {@link #registerForTest}/{@link #resetForTest}
     * hooks publish an alternative (superset) map so tests can exercise the end-to-end
     * {@code SchemaGenerator}/{@code ObjectCodec} path for synthetic types WITHOUT a
     * classpath-merge and WITHOUT mutating {@link #PRODUCTION} in place -- and MUST call
     * {@link #resetForTest} afterwards so no test serializer leaks into production
     * selection. Declared {@code volatile} for safe publication of the swapped map.
     */
    private static volatile Map<Class<?>, Class<?>> registry = PRODUCTION;

    private DerReplacer() {
    }

    /**
     * Loads the CLOSED production registry from a single platform-controlled resource
     * (WI-2).
     *
     * <p><b>Closure mechanism.</b> We read exactly ONE resource -- the copy that travels
     * in jgdms-der's own module/jar -- via
     * {@code DerReplacer.class.getResourceAsStream("/META-INF/jgdms/der-serializers")}
     * on the codec's <em>defining</em> loader. This deliberately does <b>not</b> use
     * {@code ClassLoader.getResources()} (which would MERGE every {@code der-serializers}
     * on the whole application classpath). The registered set is part of the wire
     * contract ({@link #isRegistered} &rarr; {@code SchemaGenerator} &rarr;
     * {@code schemaDigest}); an open merge would let an arbitrary third-party classpath
     * jar contribute an encode/schema serializer or shift the {@code schemaDigest}
     * of a value across deployments. (Closure here bounds ENCODE substitution and the
     * schema-affecting set; it is NOT the decode-admission boundary -- see the class
     * Javadoc: decode reconstructs any schema-named {@code @AtomicSerial} leaf regardless
     * of this registry, gated by {@code ObjectCodec.admissibleConstructClass} + the ATOMIC
     * gate + {@code check(GetArg)}.)
     *
     * <p><b>Residual (for reviewers).</b> {@code getResourceAsStream} returns the FIRST
     * resource of this name on the defining loader's search path. In a proper
     * per-jar/modular deployment that is jgdms-der.jar's own copy. In a <em>flat
     * classpath</em> deployment (one fat classpath) an entry appearing EARLIER in
     * classpath order could shadow it. This is strictly narrower than the previous
     * {@code getResources()} union (which admitted EVERY copy); fully closing the
     * residual requires module encapsulation (do not export/open the resource) or a
     * signed-jar check, documented as the flat-classpath shadowing residual in
     * STD-006 §7.6.1(4).
     */
    private static Map<Class<?>, Class<?>> load() {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>();
        ClassLoader cl = DerReplacer.class.getClassLoader();
        try (InputStream in = DerReplacer.class.getResourceAsStream(SERIALIZER_LIST_ABSOLUTE)) {
            if (in == null) {
                // No platform resource on the codec's own module/jar -> empty registry
                // (nothing is replaced; every value must be @AtomicSerial natively).
                return m;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String name = line.trim();
                    if (name.isEmpty() || name.startsWith("#")) {
                        continue;
                    }
                    registerByName(m, name, cl);
                }
            }
        } catch (IOException e) {
            // A malformed/unreadable list must not break encoding of @AtomicSerial
            // types; replacement simply remains unavailable. Log loudly so a broken
            // platform resource is diagnosable rather than a silently empty registry.
            LOGGER.log(Level.WARNING,
                    "Unable to read DER serializer registry " + SERIALIZER_LIST_ABSOLUTE, e);
        }
        return m;
    }

    private static void registerByName(Map<Class<?>, Class<?>> m, String name, ClassLoader cl) {
        Class<?> serializer;
        try {
            serializer = Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            // A serializer named in the (wire-contract) platform resource but absent from
            // this deployment's codec classloader is a build/packaging defect. Warn.
            LOGGER.log(Level.WARNING,
                    "DER serializer '" + name + "' listed in " + SERIALIZER_LIST_ABSOLUTE
                    + " is not on the codec classloader -- skipped", e);
            return;
        }
        Serializer ann = serializer.getAnnotation(Serializer.class);
        if (ann == null) {
            LOGGER.log(Level.WARNING,
                    "DER serializer '" + name + "' is not annotated @Serializer -- skipped");
            return;
        }
        // WI-3: the serializer must itself be @AtomicSerial, else ObjectCodec cannot
        // encode the substituted value and it would fail DEEP in encode with the
        // confusing "wireType @AtomicSerial but runtime type ... has no @AtomicSerial
        // class in its hierarchy" error. Reject LOUDLY here, at load. This is what
        // excludes an Externalizable serializer (e.g. UIDSerializer / DateSerializer,
        // which are @AtomicExternal, not @AtomicSerial -- jgdms-der has no Externalizable
        // path). (annotation Class elements are never null, so no replaceObType()==null
        // check is needed.)
        if (!serializer.isAnnotationPresent(AtomicSerial.class)) {
            LOGGER.log(Level.SEVERE,
                    "DER serializer '" + name + "' is annotated @Serializer but NOT "
                    + "@AtomicSerial -- it cannot be DER-encoded; refusing to register "
                    + "(STD-008 WI-3)");
            return;
        }
        m.putIfAbsent(ann.replaceObType(), serializer);
    }

    /**
     * The shared, deterministic selection predicate (WI-1):
     * <em>exact-match &rarr; unique most-specific assignable &rarr; fail-closed</em>.
     *
     * <p>Selection is a pure function of {@code (registry, target)} -- independent of
     * {@code getResources()}/insertion order. Package-private so tests can drive it over
     * an explicit synthetic registry without touching the production set.
     *
     * @param reg    the registry to resolve against
     * @param target the runtime (or declared) type being encoded
     * @return the serializer class for {@code target}, or {@code null} if none is registered
     * @throws DerException if two or more <em>incomparable</em> registered keys are
     *         assignable from {@code target} (no unique most-specific serializer);
     *         reachable only via interface keys, since a superclass chain is totally
     *         ordered. Never silently picks one.
     */
    static Class<?> selectSerializer(Map<Class<?>, Class<?>> reg, Class<?> target)
            throws DerException {
        // 1. Exact match wins unconditionally -- a concrete/final key such as
        //    X500Principal short-circuits before any assignability search.
        Class<?> exact = reg.get(target);
        if (exact != null) {
            return exact;
        }
        // 2. Collect every registered key assignable FROM target (a supertype or
        //    interface of target that could stand in for it on the wire).
        List<Class<?>> assignable = new ArrayList<>(2);
        for (Class<?> key : reg.keySet()) {
            if (key.isAssignableFrom(target)) {
                assignable.add(key);
            }
        }
        if (assignable.isEmpty()) {
            return null;
        }
        if (assignable.size() == 1) {
            return reg.get(assignable.get(0));
        }
        // 3. Reduce to the MINIMAL (most-specific) elements of the assignability partial
        //    order over the matched keys: key K is most-specific iff no OTHER matched key
        //    K' is a strict subtype of K (i.e. no other assignable K' has
        //    K.isAssignableFrom(K')). Distinct Class objects can never be mutually
        //    assignable, so this is a proper partial order; a finite non-empty set always
        //    has at least one minimal element.
        List<Class<?>> minimal = new ArrayList<>(2);
        for (Class<?> k : assignable) {
            boolean dominated = false; // some other matched key is more specific than k
            for (Class<?> other : assignable) {
                if (other != k && k.isAssignableFrom(other)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                minimal.add(k);
            }
        }
        if (minimal.size() == 1) {
            return reg.get(minimal.get(0));
        }
        // 4. Incomparable multiplicity -> FAIL CLOSED at encode/schema-gen time. This
        //    keeps selection deterministic (never insertion-order dependent) and makes a
        //    misconfigured registry fail loudly at first export -- the fail-secure outcome.
        StringBuilder keys = new StringBuilder();
        for (Class<?> k : minimal) {
            if (keys.length() > 0) {
                keys.append(", ");
            }
            keys.append(k.getName());
        }
        throw new DerException(
                "DER serializer selection is ambiguous for " + target.getName()
                + ": incomparable registered keys [" + keys + "] -- no unique "
                + "most-specific serializer (fail-closed; STD-006/008 WI-1)");
    }

    /** The serializer registered for {@code target} per the WI-1 rule; {@code null} if none. */
    private static Class<?> serializerFor(Class<?> target) throws DerException {
        return selectSerializer(registry, target);
    }

    /**
     * @param type the declared field type under consideration by the schema generator
     * @return {@code true} if a DER replacement serializer is registered for this type
     *         (used by the schema generator to admit the field as a nested
     *         {@code @AtomicSerial}).
     * @throws DerException if serializer selection for {@code type} is ambiguous
     *         (WI-1 fail-closed) -- surfaced at schema-generation time.
     */
    public static boolean isRegistered(Class<?> type) throws DerException {
        return type != null && serializerFor(type) != null;
    }

    /**
     * Substitutes a registered non-{@code @AtomicSerial} value with its
     * {@code @AtomicSerial} serializer (constructed via the serializer's
     * {@code replaceObType} constructor); otherwise returns {@code value} unchanged.
     *
     * @param value the value about to be encoded (may be {@code null}).
     * @return the serializer to encode, or {@code value} itself when no substitution applies.
     * @throws IOException if a registered serializer cannot be constructed, or (WI-1) if
     *         serializer selection for the value's type is ambiguous.
     */
    public static Object replace(Object value) throws IOException {
        if (value == null) {
            return null;
        }
        Class<?> c = value.getClass();
        if (c.isAnnotationPresent(AtomicSerial.class)) {
            return value;
        }
        Class<?> serializer = serializerFor(c);
        if (serializer == null) {
            // No registered @Serializer: honour the java.io Replace interface, the
            // symmetric encode-side counterpart of resolve()'s Resolve handling.
            // A value that nominates an @AtomicSerial replacement (e.g. AID /
            // ConstrainableAID -> their @AtomicSerial State) is substituted here so
            // the DER codec can encode it natively; the reciprocal readResolve() is
            // applied by resolve() on decode.
            if (value instanceof Replace r) {
                return r.writeReplace();
            }
            return value;
        }
        Class<?> replaceType = serializer.getAnnotation(Serializer.class).replaceObType();
        try {
            Constructor<?> ctor = serializer.getDeclaredConstructor(replaceType);
            ctor.setAccessible(true);
            return ctor.newInstance(value);
        } catch (NoSuchMethodException e) {
            throw new IOException("DER serializer " + serializer.getName()
                    + " has no (" + replaceType.getName() + ") constructor", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("DER serializer " + serializer.getName()
                    + " failed to wrap " + c.getName(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IOException("DER serializer " + serializer.getName()
                    + " is not constructable", e);
        }
    }

    /**
     * Rebuilds the original object from a decoded serializer by honouring
     * {@code readResolve()} when the decoded value implements {@link Resolve};
     * otherwise returns {@code decoded} unchanged.
     *
     * @param decoded the freshly decoded value.
     * @return the resolved object.
     * @throws ObjectStreamException if {@code readResolve()} rejects the value.
     */
    public static Object resolve(Object decoded) throws ObjectStreamException {
        if (decoded instanceof Resolve r) {
            return r.readResolve();
        }
        return decoded;
    }

    // =========================================================================
    // Package-private TEST seam (WI-6 reconciliation).
    //
    // These hooks let jgdms-der tests exercise the end-to-end SchemaGenerator/
    // ObjectCodec path (which is hard-wired to the static registry) for a synthetic
    // type, and drive the load-time @AtomicSerial admission check (WI-3), WITHOUT the
    // old classpath merge and WITHOUT mutating the production set in place. Tests MUST
    // call resetForTest() in teardown so nothing leaks into production selection.
    // =========================================================================

    /**
     * Publishes a registry that is {@link #PRODUCTION} plus {@code (type -> serializer)}
     * (added over any current test overlay). For test use only.
     */
    static synchronized void registerForTest(Class<?> type, Class<?> serializer) {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>(registry);
        m.put(type, serializer);
        registry = Collections.unmodifiableMap(m);
    }

    /** Restores the closed production registry. For test use only. */
    static synchronized void resetForTest() {
        registry = PRODUCTION;
    }

    /**
     * Runs the load-time registration logic (WI-3 admission check) for a single class
     * name against a fresh map and returns it, so a test can assert that a listed
     * serializer is accepted or (e.g. an Externalizable one) rejected -- exactly the
     * path {@link #load()} drives per line. For test use only.
     */
    static Map<Class<?>, Class<?>> registerByNameForTest(String name) {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>();
        registerByName(m, name, DerReplacer.class.getClassLoader());
        return m;
    }
}
