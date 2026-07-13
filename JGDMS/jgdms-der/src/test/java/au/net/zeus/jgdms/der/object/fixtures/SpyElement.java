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

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal {@code @AtomicSerial} value type, like {@link IntBox}, but instrumented to COUNT
 * every {@link #hashCode()}, {@link #equals(Object)}, and {@link #compareTo(SpyElement)}
 * invocation. Used as a collection-element / map-key type to prove, directly, that decoding a
 * {@code set:}/{@code bag:}/{@code orderedset:}/{@code list:}/{@code map:}/{@code orderedmap:}
 * field invokes ZERO of these methods on its elements during construction of the returned
 * immutable collection/map (a hard security requirement: construction must be a pure array copy,
 * never a hash-bucketed insertion or a sort/compare).
 *
 * <p>The counters are static and process-wide (mirroring the field-element pattern the DER codec
 * itself uses); callers MUST call {@link #resetCounters()} immediately before the operation being
 * measured (e.g. right before {@code ObjectCodec.decode(...)}), since simply constructing the test
 * INPUT collection (e.g. {@code new HashSet<>(List.of(new SpyElement(1), ...))}) legitimately
 * calls {@code hashCode()}/{@code equals()} itself and must not be counted.
 */
@AtomicSerial
public final class SpyElement implements Comparable<SpyElement> {

    public static final AtomicInteger HASHCODE_CALLS = new AtomicInteger();
    public static final AtomicInteger EQUALS_CALLS = new AtomicInteger();
    public static final AtomicInteger COMPARETO_CALLS = new AtomicInteger();

    /** Resets all three counters to zero. Call immediately before the measured operation. */
    public static void resetCounters() {
        HASHCODE_CALLS.set(0);
        EQUALS_CALLS.set(0);
        COMPARETO_CALLS.set(0);
    }

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("v", int.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, SpyElement o) throws IOException {
        arg.put("v", o.v);
        arg.writeArgs();
    }

    private final int v;

    public SpyElement(int v) {
        this.v = v;
    }

    public SpyElement(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.v = arg.get("v", 0);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public int getV() {
        return v;
    }

    @Override
    public int hashCode() {
        HASHCODE_CALLS.incrementAndGet();
        return v;
    }

    @Override
    public boolean equals(Object o) {
        EQUALS_CALLS.incrementAndGet();
        return (o instanceof SpyElement that) && this.v == that.v;
    }

    @Override
    public int compareTo(SpyElement o) {
        COMPARETO_CALLS.incrementAndGet();
        return Integer.compare(v, o.v);
    }

    @Override
    public String toString() {
        return "SpyElement(" + v + ')';
    }
}
