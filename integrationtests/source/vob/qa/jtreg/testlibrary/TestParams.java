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
 * 
 */
import java.io.File;

/**
 * Setup static variables to represent properties in test environment.  
 */
public class TestParams {

    /** variables that hold value property values */
    public static String testSrc = TestLibrary.
		getProperty("test.src", ".");
    public static String testClasses = TestLibrary.
		getProperty("test.classes", ".");

    /** name of default security policy */
    public static String defaultPolicy = TestLibrary.
	getProperty("java.security.policy",
	    testSrc + File.separatorChar + "security.policy");

    /** name of default security policy for RMID */
    public static String defaultRmidPolicy =
	testSrc + File.separatorChar + "rmid.security.policy";

    /** name of default security policy for activation groups */
    public static String defaultGroupPolicy =
	testSrc + File.separatorChar + "group.security.policy";

    /** name of default security manager */
    public static String defaultSecurityManager = TestLibrary.
	getProperty("java.security.manager", "java.rmi.RMISecurityManager");
}
