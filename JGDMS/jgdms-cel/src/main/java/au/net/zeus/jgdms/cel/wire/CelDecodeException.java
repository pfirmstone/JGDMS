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
 * Thrown by {@link CelDecoder} on any decode failure: a non-canonical
 * encoding, an unknown/reserved node or function-id tag, an arity or shape
 * violation, or a ceiling ({@code maxExprNodes}/{@code maxExprDepth}/
 * {@code maxSelectorSteps}/{@code maxScalarBytes}/name-length) violation.
 * <p>
 * Per JGDMS-STD-011 §11.3 item 2 and Appendix B §B.5/§B.6: a decode problem
 * is always a hard rejection with a diagnostic, never a partial or
 * best-effort abstract syntax tree. There is no recovery path -- catching
 * this exception means "reject the whole expression," never "skip the bad
 * part."
 */
public class CelDecodeException extends Exception {

    private static final long serialVersionUID = 1L;

    public CelDecodeException(String message) {
        super(message);
    }

    public CelDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
