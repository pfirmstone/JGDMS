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

import au.net.zeus.jgdms.cel.ast.ExprNode;

/**
 * Appendix B §B.3/§B.9's {@code CelFilterRecord} -- the parameter shape an
 * expression travels in: {@code formatVersion} (this decoder only accepts the
 * pinned value 1), {@code context} (predicate/transform, with the transform
 * arm carrying the declared result type), and {@code expression} (the AST).
 */
public record CelFilterRecord(int formatVersion, EvaluationContext context, ExprNode expression) {

    /** The only {@code formatVersion} value Appendix B §B.9 pins for v0.1. */
    public static final int FORMAT_VERSION = 1;

    public CelFilterRecord {
        if (context == null) throw new NullPointerException("context");
        if (expression == null) throw new NullPointerException("expression");
    }
}
