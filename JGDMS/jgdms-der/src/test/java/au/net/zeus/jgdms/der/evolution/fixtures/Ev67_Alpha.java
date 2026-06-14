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
 * Phase 6 / §11.6 and §11.7 fixture — root class in the three-level
 * {@code Alpha ← Mid ← Beta} hierarchy.
 *
 * <h2>Shared use</h2>
 * <ul>
 *   <li>§11.6 (insert Mid): old data has [Alpha+Beta] SEQUENCEs; new data has
 *       [Alpha+Mid+Beta].  This class is present in both schemas.</li>
 *   <li>§11.7 (remove Mid): old data has [Alpha+Mid+Beta]; new data has
 *       [Alpha+Beta].  This class is present in both schemas.</li>
 * </ul>
 */
@AtomicSerial
public class Ev67_Alpha {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("alphaVal", int.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    final int alphaVal;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev67_Alpha(int alphaVal) {
        this.alphaVal = alphaVal;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev67_Alpha(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.alphaVal = arg.get("alphaVal", 0);
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

    public int getAlphaVal() { return alphaVal; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev67_Alpha that)) return false;
        return alphaVal == that.alphaVal;
    }

    @Override
    public int hashCode() { return Objects.hash(alphaVal); }

    @Override
    public String toString() { return "Ev67_Alpha{alphaVal=" + alphaVal + "}"; }
}
