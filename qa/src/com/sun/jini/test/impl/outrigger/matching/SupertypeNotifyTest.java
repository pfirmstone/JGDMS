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
import java.rmi.*;
import java.util.List;
import java.util.Iterator;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;


/**
 * JavaSpaces test that registers with a space for write events using
 * all wildcard templates from each class in the UniqueEntry hierarchy
 * and then writes a number of entry and makes sure all the appropriate
 * matches come back
 */
public class SupertypeNotifyTest extends MatchTestBase {
    private NotifyTestUtil testUtil;

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();
    }

    protected void parse() throws Exception {
        super.parse();
        testUtil = new NotifyTestUtil(getConfig(), this);
    }

    public void run() throws Exception {
        testUtil.init((JavaSpaceAuditor) space);

        // Register for events
        int count = 0;

        for (Iterator i = writeList.iterator(); i.hasNext();) {
            final Entry toRegister = (Entry) i.next();
            final TemplateGenerator gen = new SuperclassTmplGen(toRegister);

            if (gen != null) {
                Entry tmpl;

                while ((tmpl = gen.next()) != null) {
                    testUtil.registerForNotify(tmpl);
                    logger.log(Level.INFO, "Notify Registration " + ++count);
                }
            }
        }
        spaceSet();
        writeBunch();
        final String rslt = testUtil.waitAndCheck();

        if (rslt != null) {
            throw new TestException(rslt);
        }
    }
}
