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
package org.apache.river.qa.harness;


import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicMarshalledInstance;

/**
 * A container for nonactivatable services. This class is the
 * main class exec'd in a separate VM. The main method creates
 * an instance of a <code>NonActivatableGroup</code> and returns
 * its proxy to the parent process by serializing the object to the
 * file named by the {@link NonActivatableGroupAdmin#PROXY_FILE_PROPERTY}
 * system property. There is no <code>Configuration</code> associated with
 * this service.
 *
 * <p>The proxy used to be written over <code>System.err</code> (after a
 * marker token), which forced this VM's logging onto <code>System.out</code>
 * and still let {@code java.util.logging} corrupt/consume the proxy stream,
 * hiding service start-up failures. Returning the proxy through a file leaves
 * both standard streams free for logging, so failures in this VM are visible
 * in the test log. See {@code qa/doc/DESIGN-harness-group-ipc.md}.
 */
class NonActivatableGroupImpl {

    /** the logger */
    private static Logger logger =
	Logger.getLogger("org.apache.river.qa.harness");

    /** the reference to the group, save to ensure it won't be GC'd */
    private static NonActivatableGroup nonActGroup;

    /**
     * Set up the VM to act as a NonActivatableGroup and return the group
     * proxy to the parent process. An instance of GroupImpl is created and a
     * reference saved to ensure it is not GC'd, then its proxy is serialized
     * to a temporary file and atomically renamed into the location named by
     * {@link NonActivatableGroupAdmin#PROXY_FILE_PROPERTY}, so the parent
     * never observes a partial file. {@code System.out}/{@code System.err}
     * are left untouched and carry this VM's logging.
     *
     * @param args the command line arguments, which are unused
     */
    public static void main(String[] args) {
	try {
	    GroupImpl group = new GroupImpl();
	    group.export();
	    nonActGroup = group;

	    String path =
		System.getProperty(NonActivatableGroupAdmin.PROXY_FILE_PROPERTY);
	    if (path == null) {
		throw new IllegalStateException(
		    NonActivatableGroupAdmin.PROXY_FILE_PROPERTY + " not set");
	    }
	    File tmp = new File(path + ".tmp");
	    try (OutputStream fos = new FileOutputStream(tmp);
		 ObjectOutputStream os = new AtomicMarshalOutputStream(fos, null))
	    {
		os.writeObject(new AtomicMarshalledInstance(group.getProxy()));
		os.flush();
	    }
	    // Atomic rename: the parent only ever observes a complete file.
	    Files.move(tmp.toPath(), new File(path).toPath(),
		       StandardCopyOption.ATOMIC_MOVE,
		       StandardCopyOption.REPLACE_EXISTING);
	} catch (Throwable e) {
	    e.printStackTrace();
	    logger.log(Level.SEVERE,
		       "Failed to create/return NonActivatableGroup proxy", e);
	    System.exit(1);
	}
    }
}
