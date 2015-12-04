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

package org.apache.river.test.impl.lookupdiscovery;

import java.util.logging.Level;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * With respect to the current implementation of the
 * <code>LookupDiscovery</code> utility, this class verifies
 * that if the <code>setGroups</code> method is invoked
 * after the lookup discovery utility has been terminated, an
 * <code>IllegalStateException</code> results.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of LookupDiscovery
 *   <li> after invoking the terminate method on the lookup discovery utility,
 *        the setGroups method is invoked
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as intended,
 * upon invoking the <code>setGroups</code> method after the
 * that utility has been terminated, an <code>IllegalStateException</code>
 * will occur.
 *
 */
public class SetGroupsAfterTerminate
                                   extends AddDiscoveryListenerAfterTerminate
{
    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        methodType = SET_GROUPS;
        return this;
    }
}

