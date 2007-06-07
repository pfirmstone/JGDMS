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

package com.sun.jini.discovery;

import javax.security.auth.Subject;

/**
 * Interface for approving or rejecting client subjects during unicast
 * discovery and decoding of multicast requests.  Instances of this interface
 * are passed to the {@link Discovery#decodeMulticastRequest
 * decodeMulticastRequest} and {@link Discovery#handleUnicastDiscovery
 * handleUnicastDiscovery} methods of the {@link Discovery} class as a means of
 * controlling whether or not data can be accepted from or exchanged with a
 * client authenticated as the given subject.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface ClientSubjectChecker {

    /**
     * Checks whether or not to permit exchanging or accepting data with/from a
     * client authenticated as the given subject.  The passed subject is
     * <code>null</code> if the client is unauthenticated; if
     * non-<code>null</code>, it must be read-only.  Returns normally if the
     * subject is acceptable; throws a <code>SecurityException</code> if not.
     *
     * @param subject the client subject to check
     * @throws SecurityException if the client subject check fails
     * @throws IllegalArgumentException if the given subject is not read-only
     */
    void checkClientSubject(Subject subject);
}
