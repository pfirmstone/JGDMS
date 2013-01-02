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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the constructor that takes a 
 * <code>ServiceIDListener</code> in its argument list is used to
 * construct a join manager that registers a service with N lookup services,
 * each time the method <code>getJoinSet</code> is invoked, it returns
 * a new array that contains the same lookup services as those with which
 * the service was registered by the join manager.
 * 
 */
public class GetJoinSetCallbackNew extends GetJoinSetCallback {

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that each invocation of method <code>getJoinSet</code>
     *   returns a new array that contains the same lookup services as those
     *   with which the service was registered by the join manager
     *   created during construct.
     */
    public void run() throws Exception {
        super.run();
        verifyNewArray();
    }//end run

} //end class GetJoinSetCallbackNew


