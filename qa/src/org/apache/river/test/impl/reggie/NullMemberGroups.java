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
package org.apache.river.test.impl.reggie;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import java.util.logging.Level;
import net.jini.config.ConfigurationException;

/**
 * Verifies fix for 4935237 ("reggie should throw clearer exception if member
 * groups set to ALL_GROUPS").
 */
public class NullMemberGroups extends QATestEnvironment implements Test {

    public void run() throws Exception {
	logger.log(Level.INFO, "run()");
	try {
	    getManager().startLookupService();
	    throw new TestException(
		"Started service with invalid configuration");
	} catch (Exception e) {
	    logger.log(Level.INFO, "Caught expected exception");
	    e.printStackTrace();
	    Throwable cause = rootCause(e);
	    if (!(cause instanceof ConfigurationException)) {
		throw new TestException("Unexpected cause: " + cause);
	    }
	}
    }

    private static Throwable rootCause(Throwable t) {
	while (t.getCause() != null) {
	    t = t.getCause();
	}
	return t;
    }
}
