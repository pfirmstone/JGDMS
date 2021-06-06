/*
 * Copyright 2021 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.activation.arg;

import java.rmi.server.UID;

/**
 * The identifier for a registered activation group serves several purposes:
 * <ul>
 *    <li>identifies the group uniquely within the activation system, and</li>
 *    <li>contains a reference to the group's activation system so that
 * the group can contact its activation system when necessary.</li>
 * </ul>
 * <p>
 * The ActivationGroupID is returned from the call to 
 * ActivationSystem.registerGroup and is used to identify the group 
 * within the activation system. This group id is passed as one of the 
 * arguments to the activation group's special constructor when an 
 * activation group is created/recreated.
 * <p>
 * Note the implementation also must override {@link Object#equals(java.lang.Object) }
 * and {@link Object#hashCode() } 
 * <p>
 * {@link Object#equals(java.lang.Object) } Compares two group identifiers for content equality.
 * Returns true if both of the following conditions are true:
 * <ol>
 * <li>the unique identifiers are equivalent (by content), and</li>
 * <li>the activation system specified in each refers to the same remote object.</li>
 * </ol>
 * <p>
 * {@link Object#hashCode() } Returns a hashcode for the group's identifier.
 * Two group identifiers that refer to the same remote group will have 
 * the same hash code.
 * 
 */
public interface ActivationGroupID {
    /**
     * Returns the group's activation system.
     * @return the group's activation system.
     */
    public ActivationSystem getSystem();
    /**
     * Returns the group's unique identifier.
     * @return the group's unique identifier.
     */
    public UID getUID();
}
