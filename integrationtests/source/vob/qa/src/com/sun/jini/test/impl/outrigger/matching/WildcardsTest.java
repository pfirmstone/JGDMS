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
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.lease.Lease;


/**
 * JavaSpace matching test that writes one entry and reads under all
 * possiable combiations of field values and wildcards
 */
public class WildcardsTest extends SingletonMatchTestBase {

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
    }

    public void run() throws Exception {
        final Entry entry = writeOne();
        logger.log(Level.INFO, "Wrote:" + entry);
        spaceSet();

        // Read back under all combos
        try {
            final Template writen = new Template(entry);
            TemplateGenerator gen = new AllMatchingInClassTmplGen(entry);
            Entry tmpl;
            int count = 0;

            while ((tmpl = gen.next()) != null) {
                count++;
                logger.log(Level.INFO, "Read #" + count + " with " + tmpl);
                final Entry rslt = spaceRead(tmpl, null, queryTimeOut);

                if (rslt == null) {
                    throw new TestException(
                            "Failed to match under all templates");
                }
                final Template read = new Template(rslt);

                if (!writen.matchFieldAreEqual(read)) {
                    throw new TestException(
                            "Got back an entry that did not match");
                }
            }
        } catch (UnusableEntryException e) {
            dumpUnusableEntryException(e);
	    throw e;
        }
    }
}
