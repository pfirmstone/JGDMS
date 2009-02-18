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

package com.sun.jini.test.spec.servicediscovery.event;

import com.sun.jini.lookup.entry.LookupAttributes;

import net.jini.core.entry.Entry;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceRegistration;

import java.rmi.RemoteException;

/**
 * This class verifies that the event mechanism defined by the
 * <code>LookupCache</code> interface operates as specified with respect
 * to <code>serviceAdded</code> events when the set of attributes
 * associated with a set of registered services is augmented (add attributes).
 */
public class AddAttrServiceChanged extends ModifyAttrServiceChanged {

    /** Based on the how the current test is to change the service's set of
     *  attribute(s) (modification, replacement, or augmentation), this method
     *  constructs and returns the set of attributes that will result after 
     *  the change is requested.
     * 
     *  @return the set of attributes that will result after the particular
     *          change is made to the test service's set of attribute(s).
     */
    protected Entry[] getNewAttrs(Entry[] oldAttrs, Entry[] chngAttr) {
        return LookupAttributes.add(oldAttrs,chngAttr);
    }//end getNewAttrs

    /** Based on the how the current test is to change the service's set of
     *  attribute(s) (modification, replacement, or augmentation), this method
     *  makes the request for the actual change.
     */
    protected void changeAttributes(ServiceRegistration srvcReg, 
                                    Entry[] oldAttrs,
                                    Entry[] chngAttr)
                                throws UnknownLeaseException, RemoteException
    {
        srvcReg.addAttributes(chngAttr);
    }//end changeAttributes

}//end class AddAttrServiceChanged


