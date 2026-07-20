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

/**
 * Appendix B §B.3's {@code DeclaredResultType} -- the declared result type of
 * a TRANSFORM registration (STD-011 §4.1's eight expression types, minus
 * {@code object} appearing only via {@link ObjectType} and {@code list<T>}
 * restricted to scalar {@code T} per §4.7).
 */
public sealed interface DeclaredResultType {

    record Scalar(ScalarType type) implements DeclaredResultType {
        public Scalar { if (type == null) throw new NullPointerException("type"); }
    }

    record NullType() implements DeclaredResultType {}

    record ListType(ScalarType elementType) implements DeclaredResultType {
        public ListType { if (elementType == null) throw new NullPointerException("elementType"); }
    }

    record ObjectType() implements DeclaredResultType {}
}
