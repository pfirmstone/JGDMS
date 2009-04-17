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
/* 
 * @summary Verify basic functionality of Security.doPrivileged() methods.
 */

import java.lang.reflect.Constructor;
import java.security.*;
import javax.security.auth.Subject;
import net.jini.security.Security;

public class CallerImpl implements Caller {
    public void doCall(Permission[] passUnprivileged,
		       Permission[] failUnprivileged,
		       Permission[] passPrivileged,
		       Permission[] failPrivileged,
		       Constructor checkPermActionConstructor,
		       Subject expectedSubject)
	throws Exception
    {
	SecurityManager sm = System.getSecurityManager();
	for (int i = 0; i < passUnprivileged.length; i++) {
	    sm.checkPermission(passUnprivileged[i]);
	}
	for (int i = 0; i < failUnprivileged.length; i++) {
	    try {
		sm.checkPermission(failUnprivileged[i]);
		throw new Error();
	    } catch (SecurityException ex) {
	    }
	}
	for (int i = 0; i < passPrivileged.length; i++) {
	    Security.doPrivileged((PrivilegedAction)
		checkPermActionConstructor.newInstance(
		    new Object[]{passPrivileged[i]}));
	}
	for (int i = 0; i < failPrivileged.length; i++) {
	    try {
		Security.doPrivileged((PrivilegedAction)
		    checkPermActionConstructor.newInstance(
			new Object[]{failPrivileged[i]}));
		throw new Error("XXX: " + failPrivileged[i]);
	    } catch (SecurityException ex) {
	    }
	}
	Security.doPrivileged(new CheckSubjectAction(expectedSubject));
    }
}
