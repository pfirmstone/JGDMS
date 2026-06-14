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
import java.io.InvalidObjectException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Fixture 3 — {@code @AtomicSerial} class exercising all supported wire types:
 * {@code boolean}, {@code byte}, {@code short}, {@code int}, {@code long},
 * {@link String}, and {@code byte[]}.
 *
 * <p>Used to verify the complete type-mapping table (Phase 4.1 and 4.2).
 */
@AtomicSerial
public final class MultiTypeRecord {

    // -------------------------------------------------------------------------
    // Serial form — one field per supported wire type
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("flag",   boolean.class),
            new AtomicSerial.SerialForm("tiny",   byte.class),
            new AtomicSerial.SerialForm("small",  short.class),
            new AtomicSerial.SerialForm("medium", int.class),
            new AtomicSerial.SerialForm("large",  long.class),
            new AtomicSerial.SerialForm("text",   String.class),
            new AtomicSerial.SerialForm("data",   byte[].class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final boolean flag;
    private final byte    tiny;
    private final short   small;
    private final int     medium;
    private final long    large;
    private final String  text;
    private final byte[]  data;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public MultiTypeRecord(boolean flag, byte tiny, short small, int medium,
                            long large, String text, byte[] data) {
        this.flag   = flag;
        this.tiny   = tiny;
        this.small  = small;
        this.medium = medium;
        this.large  = large;
        this.text   = Objects.requireNonNull(text, "text");
        this.data   = data == null ? new byte[0] : data.clone();
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public MultiTypeRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.flag   = arg.get("flag",   false);
        this.tiny   = arg.get("tiny",   (byte) 0);
        this.small  = arg.get("small",  (short) 0);
        this.medium = arg.get("medium", 0);
        this.large  = arg.get("large",  0L);
        this.text   = (String) arg.get("text", null);
        byte[] d    = (byte[]) arg.get("data", null);
        this.data   = d == null ? new byte[0] : d.clone();
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        // text must be non-null
        String t = (String) arg.get("text", null);
        if (t == null) {
            throw new InvalidObjectException("MultiTypeRecord: text must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isFlag()   { return flag; }
    public byte    getTiny()  { return tiny; }
    public short   getSmall() { return small; }
    public int     getMedium(){ return medium; }
    public long    getLarge() { return large; }
    public String  getText()  { return text; }
    public byte[]  getData()  { return data.clone(); }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiTypeRecord that)) return false;
        return flag == that.flag
                && tiny == that.tiny
                && small == that.small
                && medium == that.medium
                && large == that.large
                && Objects.equals(text, that.text)
                && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(flag, tiny, small, medium, large, text);
        return 31 * h + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "MultiTypeRecord{flag=" + flag + ", tiny=" + tiny
                + ", small=" + small + ", medium=" + medium
                + ", large=" + large + ", text='" + text
                + "', data=" + Arrays.toString(data) + '}';
    }
}
