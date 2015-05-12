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
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

// All other imports
import java.util.List;
import java.util.Iterator;
import java.rmi.*;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;


/**
 * Base class for matching tests that write only one entry.
 */
public abstract class SingletonMatchTestBase extends MatchTestBase {

    // Class of the entry writeOne() will write
    private String classToWrite;

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
     * Parse are args
     * <DL>
     * <DT>-write_class <string><DD> Sets what class to write for this
     * test, if <string> is not a valid class,
     * org.apache.river.test.impl.outrigger.matching.<string> will be tried as well.
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        classToWrite = getConfig().getStringConfigVal("org.apache.river.test.impl."
                + "outrigger.matching.write_class", "GrandChildOfStringMatch");
    }

    /**
     * Return one entry
     */
    protected Entry getOne() throws TestException {
        try {
            for (Iterator i = writeList.iterator(); i.hasNext();) {
                final Entry ent = (Entry) i.next();
                final String className = ent.getClass().getName();

                // Is it of the right class
                if (className.equals(classToWrite) || className.equals(
                        "org.apache.river.test.impl.outrigger.matching."
                        + classToWrite)) {
                    return ent;
                }
            }

            // if we are here then bad stuff
            fail("Could not find specified class to write into Space");
        } catch (Exception e) {
            fail("Failure filling JavaSpace with match set", e);
        }

        // Will never get here, but the compiler can't figure that out
        return null;
    }

    /**
     * Write one entry to the JavaSpace and return the entry.
     */
    protected Entry writeOne() throws TestException {
        try {
            final Entry ent = getOne();
            addOutriggerLease(space.write(ent, null, Lease.ANY), true);
            logger.log(Level.INFO, "Wrote " + classToWrite + " Entry");
            return ent;
        } catch (Exception e) {
            fail("Failure filling JavaSpace with match set", e);
        }

        // Will never get here, but the compiler can't figure that out
        return null;
    }
}
