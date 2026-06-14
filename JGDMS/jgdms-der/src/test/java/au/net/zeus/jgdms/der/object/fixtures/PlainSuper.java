/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright agreements.
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

package au.net.zeus.jgdms.der.object.fixtures;

import java.util.Objects;

/**
 * Phase 4.4 fixture -- a plain (non-{@code @AtomicSerial}) superclass.
 *
 * <p>Per S3.10 (second rule): {@code PlainSuper} has no {@code serialForm()},
 * no {@code (GetArg)} constructor, and no wire presence of its own. Its
 * subclass {@link Sub} (which IS {@code @AtomicSerial}) is responsible for
 * preserving any state from {@code PlainSuper} it wishes to survive a
 * round-trip, in {@code Sub}'s own namespace.
 *
 * <p>{@code PlainSuper} has a single field {@code legacyName} passed via a
 * normal constructor -- representing state that predates the {@code @AtomicSerial}
 * protocol.
 */
public class PlainSuper {

    /** State that Sub must carry in its own namespace to preserve across round-trips. */
    final String legacyName;

    // -------------------------------------------------------------------------
    // Normal constructor -- no GetArg, no serialForm
    // -------------------------------------------------------------------------

    public PlainSuper(String legacyName) {
        this.legacyName = Objects.requireNonNull(legacyName, "legacyName");
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    public String getLegacyName() { return legacyName; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlainSuper that)) return false;
        return Objects.equals(legacyName, that.legacyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legacyName);
    }

    @Override
    public String toString() {
        return "PlainSuper{legacyName='" + legacyName + "'}";
    }
}
