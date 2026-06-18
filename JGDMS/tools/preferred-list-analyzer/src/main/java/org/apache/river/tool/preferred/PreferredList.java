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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Parse / render / diff for a {@code META-INF/PREFERRED.LIST}
 * ({@code net.jini.loader.pref.PreferredResources} format).
 *
 * <p>A list has a {@code PreferredResources-Version} header, a global default
 * {@code Preferred:} value, and a sequence of {@code Name:}/{@code Preferred:}
 * entries.  A {@code Name} may be an exact resource ({@code pkg/Foo.class}), a
 * one-level wildcard ({@code pkg/*}) matching resources directly in a package,
 * or a recursive wildcard ({@code pkg/-}) matching a package and its
 * sub-packages.  {@link #resolve(String)} follows the most-specific-wins
 * semantics: exact &gt; {@code *} &gt; longest {@code -} &gt; default.
 */
public final class PreferredList {

    /** A single {@code Name:}/{@code Preferred:} entry. */
    public static final class Entry {
        final String  name;
        final boolean preferred;
        Entry(String name, boolean preferred) {
            this.name = name;
            this.preferred = preferred;
        }
        public String getName()      { return name; }
        public boolean isPreferred() { return preferred; }
    }

    private final String       version;
    private final boolean      defaultPreferred;
    private final List<Entry>  entries;

    PreferredList(String version, boolean defaultPreferred, List<Entry> entries) {
        this.version          = version;
        this.defaultPreferred = defaultPreferred;
        this.entries          = entries;
    }

    public String getVersion()           { return version; }
    public boolean getDefaultPreferred() { return defaultPreferred; }
    public List<Entry> getEntries()      { return entries; }

    // ---- resolution ---------------------------------------------------------

    /**
     * Resolves the effective {@code Preferred:} value for a resource name
     * (e.g. {@code net/jini/id/UuidFactory.class}) using most-specific-wins.
     */
    public boolean resolve(String resourceName) {
        long bestScore = -1;
        boolean best = defaultPreferred;
        for (Entry e : entries) {
            long score = matchScore(e.name, resourceName);
            if (score > bestScore) {
                bestScore = score;
                best = e.preferred;
            }
        }
        return best;
    }

    /** Returns a match score (higher = more specific), or -1 for no match. */
    static long matchScore(String pattern, String resource) {
        if (pattern.equals(resource)) {
            return 1_000_000_000L;                 // exact
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // keep trailing '/'
            if (resource.startsWith(prefix)
                    && resource.indexOf('/', prefix.length()) < 0) {
                return 1_000_000L + prefix.length(); // one-level wildcard
            }
            return -1;
        }
        if (pattern.endsWith("/-")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // keep trailing '/'
            if (resource.startsWith(prefix)) {
                return prefix.length();              // recursive wildcard (longest wins)
            }
            return -1;
        }
        return -1;
    }

    // ---- parsing ------------------------------------------------------------

    public static PreferredList parse(String text) throws IOException {
        return parse(new StringReader(text == null ? "" : text));
    }

    public static PreferredList parse(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String version = "1.0";
        boolean defaultPreferred = false;
        boolean sawDefault = false;
        List<Entry> entries = new ArrayList<Entry>();

        String pendingName = null;
        String line;
        while ((line = br.readLine()) != null) {
            String t = line.trim();
            if (t.isEmpty() || t.charAt(0) == '#') continue;
            int colon = t.indexOf(':');
            if (colon < 0) continue;
            String key = t.substring(0, colon).trim();
            String val = t.substring(colon + 1).trim();
            if ("PreferredResources-Version".equalsIgnoreCase(key)) {
                version = val;
            } else if ("Name".equalsIgnoreCase(key)) {
                pendingName = val;
            } else if ("Preferred".equalsIgnoreCase(key)) {
                boolean pref = "true".equalsIgnoreCase(val);
                if (pendingName == null) {
                    if (!sawDefault) {          // first bare Preferred is the global default
                        defaultPreferred = pref;
                        sawDefault = true;
                    }
                } else {
                    entries.add(new Entry(pendingName, pref));
                    pendingName = null;
                }
            }
        }
        return new PreferredList(version, defaultPreferred, entries);
    }

    // ---- rendering ----------------------------------------------------------

    /**
     * Renders a minimal {@code PREFERRED.LIST} from decisions: a global default
     * of {@code Preferred: false} and one explicit {@code Preferred: true} entry
     * per preferred class (SOW &sect;5).  Output is deterministic (entries sorted
     * by name) and uses {@code \n} line endings.
     */
    public static String render(List<ClassDecision> decisions) {
        return render(decisions, false, "1.0");
    }

    public static String render(List<ClassDecision> decisions,
                                boolean defaultPreferred, String version) {
        List<ClassDecision> sorted = new ArrayList<ClassDecision>(decisions);
        Collections.sort(sorted, new Comparator<ClassDecision>() {
            public int compare(ClassDecision a, ClassDecision b) {
                return a.getResourceName().compareTo(b.getResourceName());
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("PreferredResources-Version: ").append(version).append('\n');
        sb.append('\n');
        sb.append("Preferred: ").append(defaultPreferred).append('\n');
        for (ClassDecision d : sorted) {
            if (d.isPreferred() != defaultPreferred) {
                sb.append('\n');
                sb.append("Name: ").append(d.getResourceName()).append('\n');
                sb.append("Preferred: ").append(d.isPreferred()).append('\n');
            }
        }
        return sb.toString();
    }

    // ---- diff ---------------------------------------------------------------

    /** One drift row: the resource, the checked-in value, and the computed value. */
    public static final class Drift {
        final String  resourceName;
        final boolean checkedIn;
        final boolean computed;
        Drift(String resourceName, boolean checkedIn, boolean computed) {
            this.resourceName = resourceName;
            this.checkedIn    = checkedIn;
            this.computed     = computed;
        }
        public String getResourceName() { return resourceName; }
        public boolean getCheckedIn()   { return checkedIn; }
        public boolean getComputed()    { return computed; }
        @Override public String toString() {
            return resourceName + ": checked-in=" + checkedIn + " computed=" + computed;
        }
    }

    /**
     * Compares a checked-in list against freshly computed decisions: for every
     * analyzed class, the checked-in effective value vs the computed value.
     *
     * @return the drift rows (empty when the checked-in list is up to date)
     */
    public List<Drift> diff(List<ClassDecision> decisions) {
        List<Drift> drift = new ArrayList<Drift>();
        for (ClassDecision d : decisions) {
            boolean checkedIn = resolve(d.getResourceName());
            boolean computed  = d.isPreferred();
            if (checkedIn != computed) {
                drift.add(new Drift(d.getResourceName(), checkedIn, computed));
            }
        }
        return drift;
    }
}
