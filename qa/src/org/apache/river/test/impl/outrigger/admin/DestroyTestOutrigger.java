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
package org.apache.river.test.impl.outrigger.admin;

// Shared classes.
import org.apache.river.test.share.DestroyTest;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Test tests DestroyAdmin features for outrigger service.
 */
public class DestroyTestOutrigger extends DestroyTest {

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);

        // Log out test parameters.
        logger.log(Level.INFO, "checkPersistenceDir = " + checkDir);
        return this;
    }
}
