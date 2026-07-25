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

package au.net.zeus.jgdms.der.entry;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, {@link ClassValue}-cached generator of an Outrigger Entry class's
 * on-wire schema chain and its ordered usable-field list (JGDMS-STD-006 EntryRep-v2
 * amendment, &sect;A.3 / &sect;A.5).
 *
 * <p>This is the <b>single</b> reflective view of an Entry class used by both
 * EntryRep-v2 record building ({@link EntryRepV2Codec}) and (later) CEL filter
 * authoring. It reproduces {@code org.apache.river.outrigger.proxy.EntryRep}'s own
 * field-selection rules exactly:
 * <ul>
 *   <li><b>usable field</b> = a {@code public}, non-{@code static},
 *       non-{@code transient}, non-{@code final} field. A usable field of primitive
 *       type is illegal in an Entry and is rejected ({@link DerException}).</li>
 *   <li><b>order</b> = {@code FieldComparator}: superclass fields before subclass
 *       fields; within one declaring class, ascending by field name.</li>
 *   <li><b>namespace</b> = declaring class. A field named {@code x} declared in a
 *       superclass and a field named {@code x} declared in a subclass are two distinct
 *       usable fields at two distinct positions (no shadowing collapse).</li>
 * </ul>
 *
 * <p>The entry chain reuses the proven {@link AtomicSerialSchemaRecord} /
 * {@link SchemaChain} machinery (structurally identical to STD-006 &sect;7.7.1
 * {@code EntrySchemaRecord}: {@code className} / recursive {@code superclassHash} /
 * ordered {@code {wireName, wireType}} field list) and the pinned declared-type
 * &rarr; {@code wireType} mapping {@link SchemaGenerator#toWireType(Class, Class)}
 * (one source of truth for the type table). Each class in the hierarchy from the leaf
 * up to (but excluding) {@code Object} contributes one record carrying its own
 * declared usable fields (an empty-field namespace is permitted).
 *
 * <h2>Determinism</h2>
 * <p>The chain is a pure function of the class (its hierarchy + each class's declared
 * usable fields + the pinned type map). Re-generating produces byte-identical chain
 * bytes and therefore an identical {@code entrySchemaDigest} on every JVM. The result
 * is memoised per class in a {@link ClassValue}.
 *
 * <h2>NOTE on the release-8 duplication (flagged for the board)</h2>
 * <p>{@code EntryRep} (outrigger-dl, {@code release 8}) cannot depend on this module
 * (jgdms-der is JDK 25+). Its own {@code getFields}/{@code usableField}/
 * {@code FieldComparator} therefore necessarily coexist with the rules here; the two
 * MUST agree. This is a field-<i>selection</i> duplication forced by the
 * release-8/JDK25 split, not a second source of the schema <i>bytes</i> (there is one:
 * this class). {@code EntrySchemaGeneratorFieldOrderTest} pins the agreement (field order,
 * namespace/shadowing, usable-field edge cases, and primitive-field rejection).
 */
public final class EntrySchemaGenerator {

    private EntrySchemaGenerator() {
        throw new AssertionError("no instances");
    }

    // =========================================================================
    // Result
    // =========================================================================

    /**
     * The generated schema for one Entry class.
     *
     * @param chain            the linked schema chain (leaf-first), whose
     *                         {@link SchemaChain.Result#leafDigest()} is the
     *                         {@code entrySchemaDigest}
     * @param chainBytes       the concatenated leaf-first DER of {@code chain}'s
     *                         records (the {@code SchemaEntry.chainBytes} form)
     * @param orderedFields    the usable fields in flat {@code FieldComparator} order
     *                         (super-first, alphabetical within a class) &mdash; the
     *                         positional order of {@code EntryRepV2Body.fields}
     */
    public record EntrySchema(SchemaChain.Result chain,
                              byte[] chainBytes,
                              List<Field> orderedFields) {

        /** The 32-byte {@code entrySchemaDigest} (leaf record digest). */
        public byte[] entrySchemaDigest() {
            return chain.leafDigest();
        }
    }

    // =========================================================================
    // Public entry point (memoised)
    // =========================================================================

    /**
     * Generates (or returns the cached) {@link EntrySchema} for {@code entryClass}.
     *
     * @param entryClass a Jini {@code net.jini.core.entry.Entry} implementation class
     * @return the deterministic entry schema
     * @throws DerException         if a usable field's declared type is unsupported,
     *                              or a usable field is of primitive type
     * @throws NullPointerException if {@code entryClass} is {@code null}
     */
    public static EntrySchema forClass(Class<?> entryClass) throws DerException {
        Objects.requireNonNull(entryClass, "entryClass");
        Object v = CACHE.get(entryClass);
        if (v instanceof EntrySchema es) {
            return es;
        }
        throw new DerException((String) v);
    }

    /** Memoised {@link #forClass}: an {@link EntrySchema} on success, the failure message otherwise. */
    private static final ClassValue<Object> CACHE = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> type) {
            try {
                return generate(type);
            } catch (DerException e) {
                return e.getMessage() != null ? e.getMessage() : e.toString();
            }
        }
    };

    // =========================================================================
    // Generation
    // =========================================================================

    private static EntrySchema generate(Class<?> entryClass) throws DerException {
        // D4/G8: a @SerialEntry (STD-005) class declares its wire schema via entryForm()
        // (developer-controlled wireName/wireType), NOT via reflection. Silently building a
        // reflective schema for it would mis-schema the class. Reject loudly until unit 2
        // implements the entryForm()-driven branch (see EntryRepV2Support.encodeSerialEntry).
        if (entryClass.isAnnotationPresent(net.jini.core.entry.SerialEntry.class)) {
            throw new DerException("EntrySchemaGenerator: @SerialEntry class "
                    + entryClass.getName() + " must derive its schema from entryForm()"
                    + " -- reflective schema generation is not valid for it (unit 2)");
        }
        // Per-class records, leaf-first (matches SchemaChain.linkAndGetLeafDigest input
        // convention). Each class in the hierarchy leaf..(Object exclusive) is one record
        // carrying its OWN declared usable fields (alphabetical within the class).
        List<AtomicSerialSchemaRecord> leafFirst = new ArrayList<>();
        for (Class<?> c = entryClass; c != null && c != Object.class; c = c.getSuperclass()) {
            leafFirst.add(recordFor(c));
        }
        if (leafFirst.isEmpty()) {
            // entryClass == Object (degenerate). A no-field record for Object is not a
            // valid Entry class; produce an empty single-record chain so degenerate
            // templates (G11) still encode. Use the class name verbatim.
            leafFirst.add(new AtomicSerialSchemaRecord(entryClass.getName(),
                    (byte[]) null, List.of()));
        }
        SchemaChain.Result chain = SchemaChain.linkAndGetLeafDigest(leafFirst);
        byte[] chainBytes = concatChain(chain.chain());

        // Flat field order: FieldComparator over ALL usable fields (super-first, then
        // alphabetical within a declaring class) -- the positional order of the slices.
        List<Field> ordered = orderedUsableFields(entryClass);

        return new EntrySchema(chain, chainBytes, ordered);
    }

    /**
     * Builds the {@link AtomicSerialSchemaRecord} for one class namespace using its own
     * declared usable fields (alphabetical), mapping each field's DECLARED type via the
     * pinned {@link SchemaGenerator#toWireType(Class, Class)} table. The
     * {@code parentSchemaHash} is left {@code null} here; {@link SchemaChain} sets it
     * when linking.
     */
    private static AtomicSerialSchemaRecord recordFor(Class<?> c) throws DerException {
        Field[] declared = c.getDeclaredFields();
        List<Field> usable = new ArrayList<>(declared.length);
        for (Field f : declared) {
            if (isUsable(f)) {
                usable.add(f);
            }
        }
        usable.sort(Comparator.comparing(Field::getName)); // alphabetical within the class
        List<AtomicSerialFieldDef> defs = new ArrayList<>(usable.size());
        for (Field f : usable) {
            // The DECLARED field type is the wireType (declared-type metadata for later
            // name-resolution/type-checking); NEVER the runtime value class. Use the
            // GENERIC-signature overload (D2/G8): a Collection/Map field records its
            // discipline token over the declared element type (Field.getGenericType()),
            // not "@AtomicSerial" -- so e.g. List<String> is admitted with the right token.
            String wireType = SchemaGenerator.toWireType(f.getGenericType(), c);
            defs.add(new AtomicSerialFieldDef(f.getName(), wireType));
        }
        return new AtomicSerialSchemaRecord(c.getName(), (byte[]) null, defs);
    }

    /**
     * The usable fields of {@code entryClass} (including inherited public fields) in
     * flat {@code FieldComparator} order: superclass fields first, then alphabetical
     * within a declaring class. Mirrors {@code EntryRep.getFields}.
     */
    public static List<Field> orderedUsableFields(Class<?> entryClass) throws DerException {
        Field[] all = entryClass.getFields(); // all public fields, inherited included
        List<Field> usable = new ArrayList<>(all.length);
        for (Field f : all) {
            if (isUsable(f)) {
                usable.add(f);
            }
        }
        usable.sort(FIELD_COMPARATOR);
        return usable;
    }

    /**
     * Returns {@code true} for a usable Entry field (public, non-static, non-transient,
     * non-final). Throws {@link DerException} for a public non-ignored field of
     * primitive type (illegal in an Entry) -- mirrors {@code EntryRep.usableField}'s
     * {@code IllegalArgumentException}, surfaced here as a checked {@link DerException}.
     */
    static boolean isUsable(Field field) throws DerException {
        final int ignoreMods = (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL);
        if ((field.getModifiers() & ignoreMods) != 0) {
            return false;
        }
        if (!Modifier.isPublic(field.getModifiers())) {
            // getDeclaredFields() can return non-public fields; a non-public mutable
            // field is not a wire field (getFields() would exclude it).
            return false;
        }
        if (field.getType().isPrimitive()) {
            throw new DerException("EntrySchemaGenerator: primitive field '" + field
                    + "' not allowed in an Entry");
        }
        return true;
    }

    /** {@code FieldComparator} from {@code EntryRep}: super before subclass, alphabetical within a class. */
    private static final Comparator<Field> FIELD_COMPARATOR = (f1, f2) -> {
        if (f1 == f2) return 0;
        Class<?> d1 = f1.getDeclaringClass();
        Class<?> d2 = f2.getDeclaringClass();
        if (d1 == d2) {
            return f1.getName().compareTo(f2.getName());
        }
        if (d1.isAssignableFrom(d2)) {
            return -1; // f1 declared in a supertype of f2's declaring class -> f1 first
        }
        return 1;
    };

    /** Concatenates the DER-encoded bytes of each chain record (leaf-first), the {@code chainBytes} form. */
    static byte[] concatChain(List<AtomicSerialSchemaRecord> chain) {
        int total = 0;
        List<byte[]> encoded = new ArrayList<>(chain.size());
        for (AtomicSerialSchemaRecord r : chain) {
            byte[] b = r.encode();
            encoded.add(b);
            total += b.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] b : encoded) {
            System.arraycopy(b, 0, out, pos, b.length);
            pos += b.length;
        }
        return out;
    }

    /** Byte-length of the {@code SchemaEntry.chainBytes} for an entry class (diagnostics/dedup measurement). */
    public static int chainByteLength(Class<?> entryClass) throws DerException {
        return forClass(entryClass).chainBytes().length;
    }

    // Suppress unused warning for Arrays import kept for potential digest comparisons in tests.
    @SuppressWarnings("unused")
    private static boolean eq(byte[] a, byte[] b) { return Arrays.equals(a, b); }
}
