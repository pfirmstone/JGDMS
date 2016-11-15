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
package net.jini.discovery;

import java.util.EventListener;

/**
 * This interface must be implemented by parties that wish to obtain
 * notifications from a LookupDiscovery object.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LookupDiscovery
 */
public interface DiscoveryListener extends EventListener {
    /**
     * Called when one or more lookup service registrars has been discovered.
     * The method should return quickly; e.g., it should not make remote
     * calls.
     *
     * @param e the event that describes the discovered registrars
     */
    void discovered(DiscoveryEvent e);

    /**
     * Called when one or more lookup service registrars has been discarded.
     * The method should return quickly; e.g., it should not make remote
     * calls.
     *
     * @param e the event that describes the discarded registrars
     */
    void discarded(DiscoveryEvent e);
}
