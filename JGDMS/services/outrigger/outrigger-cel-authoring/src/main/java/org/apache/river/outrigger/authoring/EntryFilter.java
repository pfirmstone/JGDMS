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

import net.jini.core.entry.Entry;

import org.apache.river.outrigger.proxy.FilterEnvelope;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.authoring.CelEncodeException;
import au.net.zeus.jgdms.cel.authoring.CelEncoder;
import au.net.zeus.jgdms.cel.authoring.CelParseException;
import au.net.zeus.jgdms.cel.authoring.CelRecordBuilder;
import au.net.zeus.jgdms.cel.authoring.CelTextParser;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;

/**
 * Client-side compiler from a CEL text rule to an Outrigger filter envelope
 * (SOW Part&nbsp;B / JGDMS-STD-011). The produced {@code byte[]} is a canonical
 * {@link FilterEnvelope} ready to pass as the {@code filter} parameter of a
 * {@code FilteredJavaSpace} operation.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>{@link CelTextParser#parse(String, au.net.zeus.jgdms.cel.verifier.SchemaView)}
 *       against {@link EntrySchemaView#of(Class)} — typed and overload-resolved
 *       against the entry class's own on-wire schema, so a mistyped rule is
 *       caught here (or, definitively, at server admission), not silently
 *       accepted.</li>
 *   <li>{@link CelRecordBuilder#predicate(ExprNode)} — wraps the AST in a
 *       {@code Predicate}-context {@code CelFilterRecord} (a boolean gate, never
 *       a value transform).</li>
 *   <li>{@link CelEncoder#encode(CelFilterRecord)} — canonical DER CEL wire.</li>
 *   <li>{@link FilterEnvelope#encode(byte[])} — wraps the opaque CEL wire in the
 *       {@code {version, celWire}} envelope.</li>
 * </ol>
 *
 * <p>The schema this compiles against is the same one the server re-derives from
 * the template's wire body at admission, so a rule that compiles here type-checks
 * there; the server nonetheless re-verifies from scratch and never trusts the
 * client (the envelope carries no name table). A well-typed rule is accepted at
 * admission; a mistyped one is rejected there with a
 * {@code FilterRejectedException} of a {@code FILTER_TYPE_MISMATCH} reason.
 *
 * @since JGDMS 4.0.0
 */
public final class EntryFilter {

    private EntryFilter() { throw new AssertionError("no instances"); }

    /**
     * Compiles a CEL predicate text rule against an entry class into a canonical
     * filter envelope.
     *
     * @param rule the CEL predicate text (must not be null)
     * @param type the entry class the rule's field references resolve against
     *             (must not be null)
     * @return the canonical {@link FilterEnvelope} bytes
     * @throws NullPointerException if {@code rule} or {@code type} is null
     * @throws IllegalArgumentException if {@code type}'s on-wire schema cannot be
     *         derived (see {@link EntrySchemaView#of(Class)})
     * @throws CelParseException if the rule is syntactically invalid or fails
     *         static resolution against the schema
     * @throws CelEncodeException if the resulting record cannot be canonically
     *         encoded (e.g. a ceiling breach)
     */
    public static byte[] compile(String rule, Class<? extends Entry> type)
            throws CelParseException, CelEncodeException {
        if (rule == null) throw new NullPointerException("rule");
        if (type == null) throw new NullPointerException("type");
        EntrySchemaView view = EntrySchemaView.of(type);
        ExprNode node = CelTextParser.parse(rule, view);
        CelFilterRecord record = CelRecordBuilder.predicate(node);
        byte[] celWire = CelEncoder.encode(record);
        return FilterEnvelope.encode(celWire);
    }
}
