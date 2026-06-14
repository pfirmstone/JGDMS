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

package au.net.zeus.jgdms.der.evolution.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.util.Objects;

/**
 * Phase 6 / §11.5 fixture — the class that formerly HAD {@code @AtomicSerial}
 * (now still has it, but the TEST simulates it having been removed by using
 * a hand-built OLD embedded schema that INCLUDES this class's SEQUENCE, and
 * decoding against a chain that also includes it — the "present but unrequested
 * SEQUENCE" outcome is demonstrated at the level of the store being built but
 * not consumed).
 *
 * <p>In the §11.5 scenario:
 * <ul>
 *   <li>OLD data was encoded with chain {@code [Ev5_Leaf, Ev5_Root]} (both
 *       {@code @AtomicSerial}).</li>
 *   <li>After the evolution, {@code Ev5_Root} loses {@code @AtomicSerial}.
 *       NEW data is encoded with chain {@code [Ev5_Leaf]} only.</li>
 *   <li>OLD data decoded by a receiver that uses the OLD embedded schema: all
 *       SEQUENCEs are decoded into stores, but if no constructor consumes
 *       {@code Ev5_Root}'s store (because the chain still has it), the bytes sit
 *       unrequested in memory.  No error occurs.</li>
 * </ul>
 *
 * <p>Because we cannot remove {@code @AtomicSerial} from this class without
 * losing the ability to test both paths in the same JVM, the test instead proves
 * the OBSERVABLE consequence using {@code MarshalledInstanceRecord} + the OLD
 * embedded schema (which includes this class).  See {@code Ev5_Leaf} for details.
 */
@AtomicSerial
public class Ev5_Root {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("rootVal", int.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    final int rootVal;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev5_Root(int rootVal) {
        this.rootVal = rootVal;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev5_Root(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.rootVal = arg.get("rootVal", 0);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getRootVal() { return rootVal; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev5_Root that)) return false;
        return rootVal == that.rootVal;
    }

    @Override
    public int hashCode() { return Objects.hash(rootVal); }

    @Override
    public String toString() { return "Ev5_Root{rootVal=" + rootVal + "}"; }
}
