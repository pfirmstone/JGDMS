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

package au.net.zeus.jgdms.cel.authoring;

/**
 * Thrown when a {@code CelFilterRecord}/{@code ExprNode} cannot be encoded to
 * canonical Appendix B DER -- the only such condition is a value the canonical
 * form forbids that the AST types nonetheless permit a hand-built tree to
 * carry: a non-finite {@code LIT_DOUBLE} (Appendix B §B.5 item 5), or a value
 * outside a ceiling the encoder refuses to emit.
 * <p>
 * The encoder is otherwise total over well-formed ASTs: every AST built by
 * {@link CelTextParser} encodes without this exception.
 */
public final class CelEncodeException extends Exception {

    private static final long serialVersionUID = 1L;

    public CelEncodeException(String message) {
        super(message);
    }
}
