/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.zeus.jgdms.security.jwt;

/**
 * The JWKS endpoint could not be read: the connection failed or timed out, it
 * answered with an HTTP status other than 200, the response was over the size
 * limit, or the body was not a JWKS document.
 *
 * <p>This says nothing about the token.  A JWKS that was read and does not list
 * the token's {@code kid} is a plain {@link JwtValidationException}, never this
 * type, so a token cannot turn itself into a retry.  The message never holds
 * token text.
 */
final class JwksUnavailableException extends JwtValidationException {

    private static final long serialVersionUID = 1L;

    JwksUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
