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

package org.apache.river.test.spec.config.nosuchentryexception;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.NoSuchEntryException;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructor of
 *   NoSuchEntryException class. There are two forms of constructor:
 *   1) public NoSuchEntryException(String s)
 *   2) public NoSuchEntryException(String s, Throwable t)
 *
 * Actions:
 *   Test checks normal and broken variants of options as a
 *   parameter for NoSuchEntryException constructor.
 *
 *   Test performs the following steps:
 *   1) construct a NoSuchEntryException object
 *      passing null as a parameters for <code>message</code>;
 *      assert the object is constructed and no exceptions are thrown;
 *      assert the call of getMessage from the result is equal to null;
 *   2) construct a NoSuchEntryException object
 *      passing empty string as a parameters for <code>message</code>;
 *      assert the object is constructed and no exceptions are thrown;
 *      assert the call of getMessage from the result is equal to empty string;
 *   3) construct a NoSuchEntryException object
 *      passing options with the some string as a parameters
 *      for <code>message</code>;
 *      assert the object is constructed and no exceptions are thrown;
 *      assert the call of getMessage from the result is equal to the 
 *      passed string;
 *   4) repeat steps 1-3 passing null as a parameters for
 *      <code>causing exception</code>;
 *      assert the call of getCause from the result is equal to null;
 *   5) repeat steps 1-3 passing some exception object as
 *      <code>causing exception</code>;
 *      assert the call of getCause from the result is equal to the 
 *      passed exception;
 * </pre>
 */
public class Constructor_Test extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        NoSuchEntryException ce;
        ce = new NoSuchEntryException(null);
        assertion(ce.getMessage() == null);

        ce = new NoSuchEntryException("");
        assertion(ce.getMessage().equals(""));

        String message = "Some message";
        ce = new NoSuchEntryException(message);
        assertion(ce.getMessage().equals(message));

        ce = new NoSuchEntryException(null, null);
        assertion(ce.getMessage() == null);
        assertion(ce.getCause() == null);

        ce = new NoSuchEntryException("", null);
        assertion(ce.getMessage().equals(""));
        assertion(ce.getCause() == null);

        ce = new NoSuchEntryException(message, null);
        assertion(ce.getMessage().equals(message));
        assertion(ce.getCause() == null);

        Exception e = new Exception();
        ce = new NoSuchEntryException(null, e);
        assertion(ce.getMessage() == null);
        assertion(ce.getCause().equals(e));

        ce = new NoSuchEntryException("", e);
        assertion(ce.getMessage().equals(""));
        assertion(ce.getCause().equals(e));

        ce = new NoSuchEntryException(message, e);
        assertion(ce.getMessage().equals(message));
        assertion(ce.getCause().equals(e));
    }
}
