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

/**
 * A minimal {@code @AtomicSerial} value type wrapping a single {@code int}, used as a
 * collection element for the {@code @AtomicSerial}-element and recursive collection tests
 * (a canonicalise-set of nested {@code @AtomicSerial} values must octet-sort by the nested
 * element's canonical DER encoding).
 */
@AtomicSerial
public final class IntBox {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("v", int.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, IntBox o) throws IOException {
        arg.put("v", o.v);
        arg.writeArgs();
    }

    private final int v;

    public IntBox(int v) {
        this.v = v;
    }

    public IntBox(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
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
    public boolean equals(Object o) {
        return (o instanceof IntBox that) && this.v == that.v;
    }

    @Override
    public int hashCode() {
        return v;
    }

    @Override
    public String toString() {
        return "IntBox(" + v + ')';
    }
}
