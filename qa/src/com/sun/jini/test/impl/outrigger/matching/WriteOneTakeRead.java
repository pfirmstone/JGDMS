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
package com.sun.jini.test.impl.outrigger.matching;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// All other imports
import com.sun.jini.qa.harness.Test;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.lease.Lease;


/**
 * JavaSpace matching test that writes one entry, takes it and then
 * makes sure it is not their.
 */
public class WriteOneTakeRead extends SingletonMatchTestBase {

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        return this;
    }

    public void run() throws Exception {
        final Entry entry = writeOne();
        logger.log(Level.INFO, "Wrote:" + entry);
        spaceSet();

        try {

            // Take it back
            final Entry takeRslt = spaceTake(entry, null, queryTimeOut);

            if (takeRslt == null) {
                throw new TestException(
                        "Failed to take entry just wrote");
            }
            logger.log(Level.INFO, "Took:" + entry);

            // Read and make sure it is not their
            final Entry readRslt = spaceRead(entry, null, queryTimeOut);

            if (readRslt != null) {
                throw new TestException(
                        "Got entry (" + readRslt
                        + ") read after it was removed by a take");
            }
        } catch (UnusableEntryException e) {
            dumpUnusableEntryException(e);
	    throw e;
        }
    }
}
