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
import java.io.FilePermission;
import java.security.AccessControlException;
import java.util.HashSet;
import java.util.Set;

/**
 * Define a security manager that throws an exception when an attempt is made
 * to call System.exit, to permit testing main method that calls exit.  Does
 * not throw if noExit is set to false.
 *
 * It also forces checkRead to throw an AccessControlException for any files
 * in badFiles.
 */
public class NoExit extends SecurityManager {

    public static boolean noExit = true;
    public static Set badFiles = new HashSet();

    public void checkRead(String file) {
	if (badFiles.contains(file)) {
	    FilePermission p = new FilePermission(file, "read");
	    throw new AccessControlException("access denied " + p, p);
	}
	super.checkRead(file);
    }

    public void checkExit(int status) {
	if (noExit) {
	    throw new AccessControlException("No exit");
	}
    }
}
