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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationGroupID;

public class TestUtil {

    /**
     * Restores the <code>SharedCreated</code> object from a well known file
     * under the provided <code>log</code> path.
     */

    static ActivationGroupID loadSharedCreate(String log)
        throws Exception
    {

        ActivationGroupID gid = null;
        File dir = new File(log);
        try {
            gid = (ActivationGroupID)restoreGroupCookie(dir);
        } catch (Exception e) {
            e.printStackTrace();
	    throw e;
        }

        return gid;
    }

    /**
     * Utility method that restores the object stored in a well known file
     * under the provided <code>dir</code> path.
     */
    private static Object restoreGroupCookie(File dir)
        throws IOException, java.lang.ClassNotFoundException
    {
        ObjectInputStream ois = null;
        Object cookie = null;
        try {
            if (!dir.exists()) {
                throw new IOException("Log " + dir + " does not exist");
            }
            if (!dir.isDirectory()) {
                throw new IOException("Log " + dir + " is not a directory");
            }
            // No need to check lock file for read-only access.
            // Do we need to insure the cookie file isn't modified?
            File cookieFile = new File(dir, "cookie");
            ois = new ObjectInputStream(
                      new BufferedInputStream(
                          new FileInputStream(cookieFile)));
            MarshalledObject mo = (MarshalledObject)ois.readObject();
	    cookie = mo.get();
        } finally {
            if (ois != null) ois.close();
        }
        return cookie;
    }

    static private void dumpArgs(String[] args, PrintWriter pw) {
	if (args == null) 
	    throw new NullPointerException("Can't pass a null argument.");
	for (int i=0; i < args.length; i++) {
	    pw.println("args[" + i + "]: " + args[i]);
	}
    }

    static void dumpArgs(String[] args, boolean valid) {
        System.out.print("\nCmd: ");
        for (int i=0; i < args.length; i++) {
            System.out.print(args[i] + ",");
        }
        System.out.println(" -> " + valid);
    }
}
	
