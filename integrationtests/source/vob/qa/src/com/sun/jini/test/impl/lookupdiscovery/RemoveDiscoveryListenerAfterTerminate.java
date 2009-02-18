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

package com.sun.jini.test.impl.lookupdiscovery;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QAConfig;

/**
 * With respect to the current implementation of the
 * <code>LookupDiscovery</code> utility, this class verifies
 * that if the <code>removeDiscoveryListener</code> method is invoked
 * after the lookup discovery utility has been terminated, an
 * <code>IllegalStateException</code> results.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of LookupDiscovery
 *   <li> after invoking the terminate method on the lookup discovery utility,
 *        the removeDiscoveryListener method is invoked
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as intended,
 * upon invoking the <code>removeDiscoveryListener</code> method after the
 * that utility has been terminated, an <code>IllegalStateException</code>
 * will occur.
 *
 */
public class RemoveDiscoveryListenerAfterTerminate
                                   extends AddDiscoveryListenerAfterTerminate
{
    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        methodType = REMOVE_DISCOVERY_LISTENER;
    }
}

