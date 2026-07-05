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

package au.net.zeus.jgdms.der.schema.ruletypes;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.util.Objects;

/**
 * A minimal concrete {@code @AtomicSerial} element type used by the element-derivation-rule tests
 * (memo §3 / §6 edge cases E1, E4, E5, E6, E8). A {@code Set<RuleFoo>} field must resolve to
 * {@code set:@AtomicSerial} (full digest coverage), NOT to {@code Any}.
 */
@AtomicSerial
public final class RuleFoo {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("id", int.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, RuleFoo o) throws IOException {
        arg.put("id", o.id);
        arg.writeArgs();
    }

    private final int id;

    public RuleFoo(int id) {
        this.id = id;
    }

    public RuleFoo(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.id = arg.get("id", 0);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public int getId() { return id; }

    @Override
    public boolean equals(Object o) {
        return o instanceof RuleFoo f && f.id == id;
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() { return "RuleFoo{" + id + "}"; }
}
