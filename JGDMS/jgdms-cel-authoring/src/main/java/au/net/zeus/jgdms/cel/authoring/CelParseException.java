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
 * Thrown when a DETERMINISTIC CEL textual expression fails to parse, or
 * violates any of STD-011 §5.2 (lexical), §5.3 (syntax), §5.3.1/§5.3.2/§5.3.3
 * (well-formedness), §5.4 (exclusions), or the {@code CelCeilings} bounds.
 * <p>
 * Per STD-011 §5.1 a failing text MUST be rejected <em>wholesale</em> -- the
 * parser never returns a partial AST -- so every rejection surfaces as this
 * single checked exception carrying a human-readable diagnostic.
 */
public final class CelParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public CelParseException(String message) {
        super(message);
    }

    public CelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
