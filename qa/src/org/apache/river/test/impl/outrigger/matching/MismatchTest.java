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
package org.apache.river.test.impl.outrigger.matching;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// All other imports
import org.apache.river.qa.harness.Test;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.lease.Lease;


/**
 * JavaSpace matching test that writes one entry and trys to read or
 * take it under a number of mismatching template.
 */
public class MismatchTest extends SingletonMatchTestBase {
    private boolean useRead;

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

    /**
     * Parse our args.
     * <code>argv[]</code> is parsed to control various options
     * <DL>
     * <DT>-use_takes<DD> Sets the test to use <code>take</code> or
     * <code>takeIfExists</code> This is off by default
     * (<code>take</code> v.s. <code>takeIfExists</code> is controlled
     * by the -use_IfExists command line arguments
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        useRead = !getConfig().getBooleanConfigVal("org.apache.river.test.impl.outrigger."
                + "matching.MismatchTest.use_takes", false);
    }

    public void run() throws Exception {
        try {
            final Entry entry = writeOne();
            logger.log(Level.INFO, "Wrote:" + entry);
            spaceSet();

            // Read/take back under mismatches
            TemplateGenerator gen = new MismatchTmplGen(entry, classList);
            Entry tmpl;
            int count = 0;

            while ((tmpl = gen.next()) != null) {
                count++;
                logger.log(Level.INFO, "Query#" + count + " with " + tmpl);
                Entry rslt;

                if (useRead) {
                    rslt = spaceRead(tmpl, null, queryTimeOut);
                } else {
                    rslt = spaceTake(tmpl, null, queryTimeOut);
                }

                if (rslt != null) {
                    throw new TestException(
                            "Got match from a template we should not have");
                }
            }

            // Lets make sure it realy was their
            final Entry rslt = spaceRead(entry, null, queryTimeOut);

            if (rslt == null) {
                throw new TestException("Orginal write did not work");
            }
        } catch (UnusableEntryException e) {
            dumpUnusableEntryException(e);
            throw e;
        }
    }
}
