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

package au.net.zeus.jgdms.der.object.fixtures;

/**
 * Phase 4.4 fixture — a plain (non-{@code @AtomicSerial}) subclass of {@link Foo}.
 *
 * <p>Per §3.10 (first rule): {@code Bar}'s state is dropped on serialisation.
 * The wire carries only {@link Foo}'s SEQUENCE; deserialisation produces a {@code Foo},
 * not a {@code Bar}. {@code Bar} does not exist in the deserialised form.
 *
 * <p>The extra field {@code barOnly} is intentionally NOT preserved across a
 * round-trip — it exists solely to prove that it is indeed dropped.
 */
public class Bar extends Foo {

    /**
     * A Bar-specific field that must be DROPPED on serialisation.
     * It is NOT in any {@code serialForm()} and is never encoded.
     */
    private final String barOnly;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Bar(int fooId, String fooLabel, String barOnly) {
        super(fooId, fooLabel);
        this.barOnly = barOnly;
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    public String getBarOnly() { return barOnly; }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "Bar{fooId=" + fooId + ", fooLabel='" + fooLabel
                + "', barOnly='" + barOnly + "'}";
    }
}
