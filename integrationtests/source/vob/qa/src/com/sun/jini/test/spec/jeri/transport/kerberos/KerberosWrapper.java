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
package com.sun.jini.test.spec.jeri.transport.kerberos;

import java.util.logging.Level;

//harness imports
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;
import com.sun.jini.test.spec.jeri.transport.util.SubjectProvider;

//java.land.reflect
import java.lang.reflect.Method;

//java.security
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

//javax.security
import javax.security.auth.Subject;

public class KerberosWrapper extends AbstractEndpointTest {

    public void run() throws Exception {
        Subject subject = SubjectProvider
            .getSubject("transport.KerberosClient");
        String wrappedTest = sysConfig
            .getStringConfigVal("jeri.transport.wrappedTest",null);
        Class c = Class.forName(wrappedTest);
        final Test test = (Test) c.newInstance();
        test.setup(sysConfig);
        Subject.doAs(subject,
            new PrivilegedExceptionAction(){
                public Object run() throws Exception {
                    test.run();
                    return null;
                }
        });
        test.tearDown();
    }

}
