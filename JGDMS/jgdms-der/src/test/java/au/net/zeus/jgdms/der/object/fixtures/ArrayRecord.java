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
import java.util.Arrays;
import java.util.Objects;

/**
 * B1 inc-3 fixture: an {@code @AtomicSerial} class with primitive array fields
 * ({@code int[]}, {@code long[]}, {@code short[]}, {@code boolean[]}) and a
 * {@code String[]} with nullable elements. All arrays may themselves be null.
 *
 * <p>This fixture exercises the value-type array decode path in {@code WireTypes}
 * (no {@code der.object} involvement -- all decoded eagerly by {@code DerFieldStore}).
 */
@AtomicSerial
public final class ArrayRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[]{
            new AtomicSerial.SerialForm("ints",     int[].class),
            new AtomicSerial.SerialForm("longs",    long[].class),
            new AtomicSerial.SerialForm("shorts",   short[].class),
            new AtomicSerial.SerialForm("booleans", boolean[].class),
            new AtomicSerial.SerialForm("strings",  String[].class),
        };
    }

    private final int[]     ints;
    private final long[]    longs;
    private final short[]   shorts;
    private final boolean[] booleans;
    private final String[]  strings; // elements may be null

    public ArrayRecord(int[] ints, long[] longs, short[] shorts,
                       boolean[] booleans, String[] strings) {
        this.ints     = ints     == null ? null : ints.clone();
        this.longs    = longs    == null ? null : longs.clone();
        this.shorts   = shorts   == null ? null : shorts.clone();
        this.booleans = booleans == null ? null : booleans.clone();
        this.strings  = strings  == null ? null : strings.clone();
    }

    public ArrayRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.ints     = (int[])     arg.get("ints",     null);
        this.longs    = (long[])    arg.get("longs",    null);
        this.shorts   = (short[])   arg.get("shorts",   null);
        this.booleans = (boolean[]) arg.get("booleans", null);
        this.strings  = (String[])  arg.get("strings",  null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        // No mandatory invariants -- allow all-null arrays for null tests
        return arg;
    }

    public int[]     getInts()     { return ints     == null ? null : ints.clone(); }
    public long[]    getLongs()    { return longs    == null ? null : longs.clone(); }
    public short[]   getShorts()   { return shorts   == null ? null : shorts.clone(); }
    public boolean[] getBooleans() { return booleans == null ? null : booleans.clone(); }
    public String[]  getStrings()  { return strings  == null ? null : strings.clone(); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayRecord that)) return false;
        return Arrays.equals(ints,     that.ints)
                && Arrays.equals(longs,    that.longs)
                && Arrays.equals(shorts,   that.shorts)
                && Arrays.equals(booleans, that.booleans)
                && Arrays.equals(strings,  that.strings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(ints), Arrays.hashCode(longs),
                Arrays.hashCode(shorts), Arrays.hashCode(booleans),
                Arrays.hashCode(strings));
    }

    @Override
    public String toString() {
        return "ArrayRecord{"
                + "ints=" + Arrays.toString(ints)
                + ", longs=" + Arrays.toString(longs)
                + ", shorts=" + Arrays.toString(shorts)
                + ", booleans=" + Arrays.toString(booleans)
                + ", strings=" + Arrays.toString(strings)
                + '}';
    }
}
