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
package com.sun.jini.test.impl.outrigger.admin;

// base class
import com.sun.jini.test.share.LookupGroupAdminTest;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Test tests different operations with groups.
 */
public class LookupGroupAdminTestOutrigger extends LookupGroupAdminTest {

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);

        // Log out test parameters.
        logger.log(Level.INFO, "tryShutdown = " + tryShutdown);
        logger.log(Level.INFO, "restart_wait = " + minPostKillWait);
        return this;
    }
}
