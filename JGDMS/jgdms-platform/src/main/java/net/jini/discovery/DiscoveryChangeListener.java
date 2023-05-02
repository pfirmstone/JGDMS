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

/**
 * This interface must be implemented by entities that wish to receive
 * notification of the following events.
 * <ul>
 *   <li> discovery of new lookup services
 *   <li> re-discovery of previously discovered but discarded lookup services
 *   <li> discard of previously discovered lookup services
 *   <li> changes in the member groups of previously discovered lookup services
 * </ul>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.DiscoveryListener
 * @see net.jini.discovery.DiscoveryEvent
 * @see net.jini.discovery.LookupDiscovery
 */
public interface DiscoveryChangeListener extends DiscoveryListener {
    /**
     * Called when changes are detected in the discovery state of 
     * one or more of the previously discovered lookup services;
     * in particular, when changes occur in the member groups to
     * which those lookup services belong.
     * 
     * Note that implementations of this method should return quickly;
     * e.g., such implementations should never make remote invocations.
     *
     * @param e instance of <code>net.jini.discovery.DiscoveryEvent</code>
     *          representing the event that describes the lookup services
     *          whose discovery state has changed
     */
    void changed(DiscoveryEvent e);
}
