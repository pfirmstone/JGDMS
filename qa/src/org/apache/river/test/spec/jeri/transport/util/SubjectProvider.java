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
/**
 * Utility class for obtaining subjects from LoginModules
 */
package org.apache.river.test.spec.jeri.transport.util;

//javax.security
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.Subject;

public class SubjectProvider {

    public static Subject getSubject(String name) {
        Subject s = null;
        try {
            LoginContext lc = new LoginContext(name);
            lc.login();
            s = lc.getSubject();
        } catch (LoginException e) {
            e.printStackTrace();
        }
        return s;

    }

}
