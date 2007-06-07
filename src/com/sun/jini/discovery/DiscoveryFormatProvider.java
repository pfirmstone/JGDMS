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

/**
 * Interface implemented by all discovery format provider classes, which is
 * used by the {@link Discovery} class to identify format providers available
 * via resource, as described in the documentation for the
 * {@link Discovery#getProtocol2(ClassLoader)} method.  Format provider classes
 * should not implement this interface directly, but should instead implement
 * at least one of the format provider sub-interfaces extending this
 * interface--{@link MulticastRequestEncoder}, {@link MulticastRequestDecoder},
 * {@link MulticastAnnouncementEncoder}, {@link MulticastAnnouncementDecoder},
 * {@link UnicastDiscoveryClient} and {@link UnicastDiscoveryServer}--which
 * declare methods for the various provider operations, such as encoding or
 * decoding discovery data.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface DiscoveryFormatProvider {

    /**
     * Returns the name of the format implemented by this provider.
     *
     * @return the name of the format implemented by this provider
     */
    String getFormatName();
}
