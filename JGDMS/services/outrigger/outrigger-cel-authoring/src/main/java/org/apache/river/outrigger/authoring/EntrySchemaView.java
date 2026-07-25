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
package org.apache.river.outrigger.authoring;

import java.util.List;
import java.util.Optional;

import net.jini.core.entry.Entry;

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.verifier.DerSchemaChainView;
import au.net.zeus.jgdms.cel.verifier.SchemaView;
import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.entry.EntrySchemaGenerator;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

/**
 * A CEL {@link SchemaView} over an Outrigger {@link Entry} class, for
 * client-side filter authoring (SOW Part&nbsp;B / JGDMS-STD-011).
 *
 * <p><b>Drift-free by construction.</b> This view is built from
 * {@link EntrySchemaGenerator#forClass}, the <em>single</em> reflective view of
 * an entry class's on-wire schema. That is the exact same generator the wire
 * body ({@code EntryRepV2Codec}) and the server-side admission seam derive their
 * schema from, so the field set, positional order, and wire types an author
 * type-checks against here are identical to what the server verifies against.
 * The usable-field rules (public, non-static, non-transient, non-final;
 * primitive usable fields rejected; super-before-subclass, alphabetical order)
 * are <b>reused</b>, never re-implemented — a divergence between authoring and
 * the wire schema would silently corrupt type-checking, so there is deliberately
 * only one implementation of those rules.
 *
 * <p>Internally this delegates every {@link SchemaView} method to a
 * {@link DerSchemaChainView} constructed over the generator's leaf-first schema
 * chain, so the wire-type &rarr; {@link CelType} mapping is also shared with the
 * server path.
 *
 * @since JGDMS 4.0.0
 */
public final class EntrySchemaView implements SchemaView {

    private final SchemaView delegate;
    private final Class<? extends Entry> entryClass;

    private EntrySchemaView(Class<? extends Entry> entryClass, SchemaView delegate) {
        this.entryClass = entryClass;
        this.delegate = delegate;
    }

    /**
     * Builds a schema view for the given entry class by reusing
     * {@link EntrySchemaGenerator#forClass}.
     *
     * @param entryClass the entry class to view (must not be null); a
     *                   {@code @SerialEntry} (STD-005) class is rejected by the
     *                   generator, as is a class with a usable primitive field
     * @return the schema view
     * @throws NullPointerException if {@code entryClass} is null
     * @throws IllegalArgumentException if the entry class's schema cannot be
     *         derived (e.g. a {@code @SerialEntry} class or an illegal field);
     *         the underlying {@link DerException} is the cause
     */
    public static EntrySchemaView of(Class<? extends Entry> entryClass) {
        if (entryClass == null) throw new NullPointerException("entryClass");
        try {
            EntrySchemaGenerator.EntrySchema schema = EntrySchemaGenerator.forClass(entryClass);
            List<AtomicSerialSchemaRecord> chain = schema.chain().chain();
            return new EntrySchemaView(entryClass, new DerSchemaChainView(chain));
        } catch (DerException e) {
            throw new IllegalArgumentException(
                    "cannot derive an on-wire schema for entry class "
                    + entryClass.getName() + " (" + e.getMessage() + ")", e);
        }
    }

    /**
     * @return the entry class this view was built for
     */
    public Class<? extends Entry> entryClass() {
        return entryClass;
    }

    @Override
    public List<String> namespaceChain() {
        return delegate.namespaceChain();
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        return delegate.declaresField(className, fieldName);
    }

    @Override
    public Optional<CelType> fieldType(String className, String fieldName) {
        return delegate.fieldType(className, fieldName);
    }

    @Override
    public Optional<SchemaView> nestedSchema(String className, String fieldName) {
        return delegate.nestedSchema(className, fieldName);
    }
}
