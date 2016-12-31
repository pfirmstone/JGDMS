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
package org.apache.river.tool.envcheck.plugins;

import org.apache.river.tool.envcheck.SubVMTask;
import org.apache.river.tool.envcheck.Util;
import org.apache.river.api.security.CombinerSecurityManager;

/**
 * A subtask which checks for the accessibility of a file identified
 * by a system property.
 */
public class FileAccessCheckTask implements SubVMTask {
    
    /**
     * Check for the accessibility of a file identified by a
     * system property. Returns <code>null</code> if the file exists
     * and is readable. Any non-<code>null</code> return will either
     * be a <code>String</code> containing an error message, or a
     * <code>Throwable</code> that was thrown in the course of the check.
     * <code>args[0]</code> must contain the name of the system property. <code>args[1]</code>
     * must contain a localized description of the file being accessed.
     *
     * @param args the command line args
     * @return the result of the check
     */
    public Object run(String[] args) {
        if (System.getSecurityManager() == null) {
	System.setSecurityManager(new CombinerSecurityManager());
        }
	return Util.checkSystemPropertyFile(args[0], args[1]);
    }
}	

