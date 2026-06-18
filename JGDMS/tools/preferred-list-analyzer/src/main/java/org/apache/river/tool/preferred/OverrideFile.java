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
package org.apache.river.tool.preferred;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A checked-in override file holding the human judgement calls the analyzer
 * cannot make objectively (chiefly the cross-boundary determinations) &mdash;
 * SOW &sect;6.
 *
 * <p>Format: one entry per line,
 * <pre>
 *   fully.qualified.Class = true|false   # optional reason
 * </pre>
 * Blank lines and lines whose first non-whitespace character is {@code #} are
 * ignored.  Class names are fully qualified with {@code .} separators; an
 * optional trailing {@code .class} is tolerated.  The reason (everything after
 * {@code #}) is retained for the report.
 *
 * <p>{@link #apply(List)} applies the overrides over a list of
 * {@link ClassDecision}s and returns a report of which overrides were applied,
 * which diverged from the analysis, and which referenced classes not present in
 * the analyzed set.
 */
public final class OverrideFile {

    /** A single parsed override entry. */
    public static final class Entry {
        final String  internalName;  // slash form
        final boolean preferred;
        final String  reason;

        Entry(String internalName, boolean preferred, String reason) {
            this.internalName = internalName;
            this.preferred    = preferred;
            this.reason       = reason;
        }

        public String getInternalName() { return internalName; }
        public boolean isPreferred()    { return preferred; }
        public String getReason()       { return reason; }
    }

    /** Outcome of applying overrides, for the report. */
    public static final class Result {
        private final List<ClassDecision> applied    = new ArrayList<ClassDecision>();
        private final List<ClassDecision> divergent  = new ArrayList<ClassDecision>();
        private final List<String>        unmatched  = new ArrayList<String>();

        /** Decisions an override touched. */
        public List<ClassDecision> getApplied()   { return applied; }
        /** Decisions where the override forced a different value than the analysis. */
        public List<ClassDecision> getDivergent() { return divergent; }
        /** Override entries that matched no analyzed class (likely stale). */
        public List<String>        getUnmatched() { return unmatched; }
    }

    private final Map<String, Entry> byName; // internal name -> entry

    private OverrideFile(Map<String, Entry> byName) {
        this.byName = byName;
    }

    /** An empty override set. */
    public static OverrideFile empty() {
        return new OverrideFile(new LinkedHashMap<String, Entry>());
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    public List<Entry> entries() {
        return new ArrayList<Entry>(byName.values());
    }

    /**
     * Parses override text.
     *
     * @param text the override file contents (may be empty)
     * @return the parsed overrides; never null
     * @throws IOException if a line is malformed
     */
    public static OverrideFile parse(String text) throws IOException {
        return parse(new StringReader(text == null ? "" : text));
    }

    public static OverrideFile parse(Reader reader) throws IOException {
        Map<String, Entry> byName = new LinkedHashMap<String, Entry>();
        BufferedReader br = new BufferedReader(reader);
        String raw;
        int lineNo = 0;
        while ((raw = br.readLine()) != null) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;

            String reason = null;
            int hash = line.indexOf('#');
            if (hash >= 0) {
                reason = line.substring(hash + 1).trim();
                line   = line.substring(0, hash).trim();
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IOException("Malformed override (missing '=') at line "
                        + lineNo + ": " + raw);
            }
            String name = line.substring(0, eq).trim();
            String val  = line.substring(eq + 1).trim().toLowerCase();
            if (name.endsWith(".class")) {
                name = name.substring(0, name.length() - ".class".length());
            }
            boolean preferred;
            if ("true".equals(val))       preferred = true;
            else if ("false".equals(val)) preferred = false;
            else throw new IOException("Override value must be true|false at line "
                    + lineNo + ": " + raw);

            String internal = name.replace('.', '/');
            byName.put(internal, new Entry(internal, preferred, reason));
        }
        return new OverrideFile(byName);
    }

    /**
     * Applies the overrides over {@code decisions} in place, recording the
     * override value/reason on each touched {@link ClassDecision}.
     *
     * @param decisions the analyzer decisions; mutated in place
     * @return the application result for reporting
     */
    public Result apply(List<ClassDecision> decisions) {
        Result result = new Result();
        Map<String, ClassDecision> byClass = new LinkedHashMap<String, ClassDecision>();
        for (ClassDecision d : decisions) {
            byClass.put(d.getInternalName(), d);
        }
        for (Entry e : byName.values()) {
            ClassDecision d = byClass.get(e.internalName);
            if (d == null) {
                result.unmatched.add(e.internalName.replace('/', '.'));
                continue;
            }
            d.applyOverride(e.preferred, e.reason);
            result.applied.add(d);
            if (d.isOverrideDivergent()) {
                result.divergent.add(d);
            }
        }
        return result;
    }
}
