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

import net.jini.core.discovery.LookupLocator;

/**
 * This interface defines methods related to the management of the set of
 * lookup services that are to be discovered using the unicast discovery
 * protocol; that is, lookup services that are discovered by way of
 * locator discovery. The methods of this interface define how an entity
 * retrieves or modifies the set of locators associated with those lookup
 * services.
 *
 * @author Sun Microsystems, Inc.
 */
public interface DiscoveryLocatorManagement {

    /**
     * Returns an array consisting of the elements of the managed set
     * of locators; that is, instances of <code>LookupLocator</code> in
     * which each instance corresponds to a specific lookup service to
     * discover. The returned set will include both the set of 
     * <code>LookupLocator</code>s corresponding to lookup services 
     * that have already been discovered as well as the set of those
     * that have not yet been discovered. If the managed set of locators
     * is empty, this method will return the empty array. This method
     * returns a new array upon each invocation.
     *
     * @return <code>LookupLocator</code> array consisting of the elements
     *         of the managed set of locators
     * @see #setLocators
     */
    public LookupLocator[] getLocators();

    /**
     * Adds a set of locators to the managed set of locators. Elements in the
     * input set that duplicate (using the <code>LookupLocator.equals</code>
     * method) elements already in the managed set will be ignored. If the
     * empty array is input, the managed set of locators will not change.
     *
     * @param locators <code>LookupLocator</code> array consisting of the
     *                 locators to add to the managed set.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>locators</code>
     *         parameter, or one or more of the elements of the
     *         <code>locators</code> parameter is <code>null</code>.
     * @see #removeLocators
     */
    public void addLocators(LookupLocator[] locators);
    
    /**
     * Replaces all of the locators in the managed set with locators from
     * a new set, and discards any already-discovered lookup service that
     * corresponds to a locator that is removed from the managed set
     * as a result of an invocation of this method. For any such lookup
     * service that is discarded, a discard notification is sent; and that
     * lookup service will not be eligible for re-discovery (assuming it is
     * not currently eligible for discovery through other means, such as
     * group discovery).
     * <p>
     * If the empty array is input, locator discovery will cease until this
     * method is invoked with an input parameter that is non-<code>null</code>
     * and non-empty.
     *
     * @param locators <code>LookupLocator</code> array consisting of the 
     *                 locators that will replace the current locators in the
     *                 managed set.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>locators</code>
     *         parameter.
     * @see #getLocators
     */
    public void setLocators(LookupLocator[] locators);
   
    /**
     * Deletes a set of locators from the managed set of locators, and discards
     * any already-discovered lookup service that corresponds to a deleted
     * locator. For any lookup service that is discarded as a result of an
     * invocation of this method, a discard notification is sent; and that
     * lookup service will not be eligible for re-discovery (assuming it is
     * not currently eligible for discovery through other means, such as
     * group discovery).
     * <p>
     * If the empty array is input, this method takes no action.
     *
     * @param locators <code>LookupLocator</code> array consisting of the
     *                 locators that will be removed from the managed set.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>locators</code>
     *         parameter.
     * @see #addLocators
     */
    public void removeLocators(LookupLocator[] locators);
}
