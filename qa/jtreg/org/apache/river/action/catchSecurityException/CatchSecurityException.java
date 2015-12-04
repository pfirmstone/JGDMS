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
/* @test
 * @bug 6194337
 * @summary The PrivilegedAction implementations in
 * org.apache.river.action that read system properties should catch
 * SecurityException, in case the caller's protection domain or their
 * own protection domain does not even have the necessary permission,
 * and return the default value instead.
 *
 * @build CatchSecurityException
 * @run main/othervm/policy=security.policy CatchSecurityException
 */

import org.apache.river.action.GetBooleanAction;
import org.apache.river.action.GetIntegerAction;
import org.apache.river.action.GetLongAction;
import org.apache.river.action.GetPropertyAction;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

public class CatchSecurityException {

    private static final Integer INTEGER_DEFAULT = new Integer(1111);
    private static final Integer INTEGER_VALUE = new Integer(2222);

    private static final Long LONG_DEFAULT = new Long(111111111111L);
    private static final Long LONG_VALUE = new Long(222222222222L);

    private static final String PROPERTY_DEFAULT = "foo";
    private static final String PROPERTY_VALUE = "bar";

    private static final AccessControlContext unrestrictiveContext =
	new AccessControlContext(new ProtectionDomain[0]);

    private static final AccessControlContext restrictiveContext =
	new AccessControlContext(new ProtectionDomain[] {
	    new ProtectionDomain(null, null),
	});

    private static int failureCount = 0;

    public static void main(String[] args) throws Exception {
	System.err.println("Regression test for RFE 6194337\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	System.setProperty("test.Boolean", Boolean.TRUE.toString());
	System.setProperty("test.Integer", INTEGER_VALUE.toString());
	System.setProperty("test.Long", LONG_VALUE.toString());
	System.setProperty("test.Property", PROPERTY_VALUE);

	tryAction(new GetBooleanAction("test.Boolean"),
		  false,
		  Boolean.TRUE);
	tryAction(new GetBooleanAction("test.Boolean"),
		  true,
		  Boolean.FALSE);

	tryAction(new GetIntegerAction("test.Integer"),
		  false,
		  INTEGER_VALUE);
	tryAction(new GetIntegerAction("test.Integer"),
		  true,
		  null);

	tryAction(new GetIntegerAction("test.Integer",
				       INTEGER_DEFAULT.intValue()),
		  false,
		  INTEGER_VALUE);
	tryAction(new GetIntegerAction("test.Integer",
				       INTEGER_DEFAULT.intValue()),
		  true,
		  INTEGER_DEFAULT);

	tryAction(new GetLongAction("test.Long"),
		  false,
		  LONG_VALUE);
	tryAction(new GetLongAction("test.Long"),
		  true,
		  null);

	tryAction(new GetLongAction("test.Long", LONG_DEFAULT.longValue()),
		  false,
		  LONG_VALUE);
	tryAction(new GetLongAction("test.Long", LONG_DEFAULT.longValue()),
		  true,
		  LONG_DEFAULT);

	tryAction(new GetPropertyAction("test.Property"),
		  false,
		  PROPERTY_VALUE);
	tryAction(new GetPropertyAction("test.Property"),
		  true,
		  null);

	tryAction(new GetPropertyAction("test.Property", PROPERTY_DEFAULT),
		  false,
		  PROPERTY_VALUE);
	tryAction(new GetPropertyAction("test.Property", PROPERTY_DEFAULT),
		  true,
		  PROPERTY_DEFAULT);

	int failures = failureCount;
	System.err.println(failures +
			   " failure" + (failures == 1 ? "" : "s"));
	if (failures > 0) {
	    throw new RuntimeException("TEST FAILED");
	}
	System.err.println("TEST PASSED");
    }

    private static void tryAction(PrivilegedAction action,
				  boolean restrictContext,
				  Object expectedResult)
    {
	System.err.println("Trying privileged action: " + action);
	System.err.println("\twith " + (restrictContext ? "" : "un") +
			   "restricted context");
	System.err.println("Expected result: " + expectedResult);
	Object result;
	try {
	    result = AccessController.doPrivileged(action,
		restrictContext ? restrictiveContext : unrestrictiveContext);
	} catch (SecurityException e) {
	    failureCount++;
	    System.err.print("XX security exception occurred: ");
	    e.printStackTrace();
	    return;
	}
	System.err.println("Actual result:   " + result);
	if (equals(result, expectedResult)) {
	    System.err.println("-- result equals expected result");
	} else {
	    failureCount++;
	    System.err.println("XX result does not equal expected result");
	}
	System.err.println();
    }

    private static boolean equals(Object subject, Object object) {
	return subject == null ? object == null : subject.equals(object);
    }
}
