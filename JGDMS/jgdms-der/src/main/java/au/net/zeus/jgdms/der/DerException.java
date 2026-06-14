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

package au.net.zeus.jgdms.der;

import java.io.IOException;

/**
 * Thrown by the DER codec on any decode failure or constraint violation
 * (JGDMS-STD-006 S3 principle 6: fail-secure decode).
 * <p>
 * A {@code DerException} is raised, and no object is constructed, whenever a
 * decoder detects:
 * <ul>
 *   <li>a malformed or non-canonical TLV (indefinite length, non-minimal
 *       length, non-minimal INTEGER, unexpected end-of-buffer);</li>
 *   <li>a BOOLEAN content octet that is neither {@code 0x00} nor {@code 0xFF}
 *       (DER strictness);</li>
 *   <li>an INTEGER with zero-length content or a non-minimal leading byte;</li>
 *   <li>a SEQUENCE that overruns its declared boundary;</li>
 *   <li>any other schema or constraint violation.</li>
 * </ul>
 */
public class DerException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a {@code DerException} with the specified detail message.
     *
     * @param message human-readable explanation of the failure
     */
    public DerException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code DerException} with a detail message and cause.
     *
     * @param message human-readable explanation of the failure
     * @param cause   the underlying cause (may be {@code null})
     */
    public DerException(String message, Throwable cause) {
        super(message, cause);
    }
}
