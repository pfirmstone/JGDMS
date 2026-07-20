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

package au.net.zeus.jgdms.cel.wire;

import au.net.zeus.jgdms.cel.CelType;

/**
 * Appendix B §B.3's {@code ScalarType ::= ENUMERATED { boolT(0), intT(1),
 * doubleT(2), stringT(3), bytesT(4) }} -- the five scalar expression types
 * admissible as a transform's declared result type ({@link DeclaredResultType}).
 */
public enum ScalarType {
    BOOL_T(0),
    INT_T(1),
    DOUBLE_T(2),
    STRING_T(3),
    BYTES_T(4);

    private final int wireValue;

    ScalarType(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    public static ScalarType fromWireValue(int v) {
        for (ScalarType t : values()) {
            if (t.wireValue == v) return t;
        }
        throw new IllegalArgumentException("unknown ScalarType enumerated value " + v);
    }

    public CelType toCelType() {
        return switch (this) {
            case BOOL_T -> CelType.BOOL;
            case INT_T -> CelType.INT;
            case DOUBLE_T -> CelType.DOUBLE;
            case STRING_T -> CelType.STRING;
            case BYTES_T -> CelType.BYTES;
        };
    }
}
