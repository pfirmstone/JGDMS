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
 * A generator for creating the method permissions for the security
 * policy file. The output of this program will generally be combined with
 * additional policy content to create a complete policy file
 * <p>
 * This program uses the e2etest.ConstraintsInterface methods to construct
 * entries of the form:
 * <p>
 * permission e2etest.SecureServerPermission "methodName"
 * <p>
 * for every method in <code>ConstraintsInterface</code> except for
 * <code>vBogus</code> and <code>multi</code>. <code>vBogus</code>
 * is excluded so that a security exception will be thrown, and
 * <code>multi</code> is only included once to verify that this
 * method is checked by name regardless of method signature. See the
 * export logic in <code>SecureClient</code> for more info.
 * <p>
 * Entries are created for all four client principals which make up
 * the client subject for the test.
 */
package com.sun.jini.test.impl.end2end.generators;

import java.rmi.Remote;
import java.rmi.RemoteException;
import com.sun.jini.test.impl.end2end.e2etest.ConstraintsInterface;
import java.lang.reflect.Method;

public class SecurityGenerator {

    public static void main(String[] args) {

        boolean gotMulti = false;
        String[] principals = {
			       "clientDSA1DN",
			       "clientDSA2DN",
			       "clientRSA1DN",
			       "clientRSA2DN",
                   "kerberosClient"
			      };
	Method[] methods = ConstraintsInterface.class.getMethods();
	for (int p=0; p<principals.length; p++) {
	    System.out.println("grant principal "
			     + "javax.security.auth.x500.X500Principal \"${"
			     + principals[p]
			     + "}\" {");
	    for (int i=0; i<methods.length; i++) {
                if (methods[i].getName().equals("vBogus")) continue;
                if (methods[i].getName().equals("multi")) {
		    if (gotMulti) continue;
		    gotMulti = true;
		}
		System.out.println(
			   "    permission e2etest.SecureServerPermission \""
			 +  methods[i].getName() + "\";");
	    }
	    System.out.println("};");
	    System.out.println();
	}
    }
}
