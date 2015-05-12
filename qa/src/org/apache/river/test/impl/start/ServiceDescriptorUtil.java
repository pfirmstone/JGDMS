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
package org.apache.river.test.impl.start;

import java.io.File;
import java.io.IOException;

public class ServiceDescriptorUtil {

    public static final String jini_root_dir = "/view/resendes/vob/jive";
    public static final String jini_lib_dir = jini_root_dir + "/lib";
    public static final String jini_policy_dir = jini_root_dir + "/policy";

    public static final String jini_host ="resendes";
    public static final String jini_port ="8080";
    public static final String jini_url = 
	"http://" + jini_host + ":" + jini_port + "/";

    public static String getLogDir() {
	File log = null;
	try {
	    log = File.createTempFile("SDU", "tmp");
	    return log.getCanonicalPath();
	} catch (IOException ioe) {
	    System.out.println("Exception creating log dir: " + ioe);
	} finally {
	    if (log != null) log.delete();
	}
	return null;
    }
}
