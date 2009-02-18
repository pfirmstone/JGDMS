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
/* @summary Verify proper basic functionality of Security.grant(Class, Class)
 */

import java.security.*;
import net.jini.security.Security;

public class Setup implements Runnable {
    public void run() {
	AccessController.doPrivileged(new Action());
    }

    static class Action implements PrivilegedAction {
	public Object run() {
	    Security.grant(Test.cl1, 
			   new Permission[]{ Test.pA, Test.pB, Test.pC });
	    return null;
	}
    }
}
