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
package com.sun.jini.test.impl.start;

import java.util.logging.Level;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.ActivatableServiceStarterAdmin;
import com.sun.jini.qa.harness.QAConfig;

// java.rmi
import java.rmi.RemoteException;

// java.io
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * This test ensures that different service instances in the same VM properly
 * consult their respective security policies and ONLY their security policies.
 * <p>
 * Given the VM, two activated service instances and the SharedGroup service,
 * each started with some mutually exclusive security permissions and some
 * overlapping security permissions, each instance attempts operations that
 * are only permitted for it and unpermitted operations that are only
 * permitted for the other instance, VM, and SharedGroup.  The results of
 * the attempts are returned to the test.
 */
public class SecurityTestNonActivatable extends AbstractStartBaseTest {

    private String tmpdir;

    // javadoc inherited from super class
    public void run() throws Exception {
        // check tmp dir property exists
        tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir == null) {
            throw new TestException("java.io.tmpdir property not set");
        }

        // start test services
        String propertyKey = "com.sun.jini.test.impl.start.SecurityTest";
        TestService service1 = null;
        TestService service2 = null;
        try {
            logger.log(Level.FINE, "activating test service 1");
	    service1 = 
		(TestService) manager.startService(propertyKey + "1");
        } catch (RemoteException re) {
            re.printStackTrace();
        }
        try {
            logger.log(Level.FINE, "activating test service 2");
	    service2 = 
		(TestService) manager.startService(propertyKey + "2");
        } catch (RemoteException re) {
            re.printStackTrace();
        }

//TODO - verify that services are in the same VM
/**************
	ActivatableServiceStarterAdmin admin1 = 
	    (ActivatableServiceStarterAdmin) manager.getAdmin(service1);
	ActivatableServiceStarterAdmin admin2 = 
	    (ActivatableServiceStarterAdmin) manager.getAdmin(service2);

        if (!admin1.getGroupID().equals(admin2.getGroupID())) {
            throw new TestException("Test services have different "
                + "ActivationGroupIDs which means that services are not "
                + "being run in a shared VM");
        }
********/

        // create tmp files
        File vm           = writeTmpFile("vm");
        File sharedgroup  = writeTmpFile("sharedgroup");
        File testservice1 = writeTmpFile("testservice1");
        File testservice2 = writeTmpFile("testservice2");

        // check permitted operations by both test services
        if (!isRemoteFileReadable(testservice1,service1,"1")) {
            throw new TestException("contents of file returned "
                    + "from test service 1 are incorrect");
        }
        if (!isRemoteFileReadable(testservice2,service2,"2")) {
            throw new TestException("contents of file returned "
                    + "from test service 2 are incorrect");
        }

        // check unpermitted operations by test service 1
        try {
            isRemoteFileReadable(testservice2,service1,"1");
            throw new TestException("test service 1 was able "
                    + "to read a test service 2 file");
        } catch (SecurityException ignore) { /* should occur */ }
        try {
            isRemoteFileReadable(sharedgroup,service1,"1");
            throw new TestException("test service 1 was able "
                    + "to read a shared group file");
        } catch (SecurityException ignore) { /* should occur */ }
        try {
            isRemoteFileReadable(vm,service1,"1");
            throw new TestException("test service 1 was able "
                    + "to read an activation group file");
        } catch (SecurityException ignore) { /* should occur */ }

        // check unpermitted operations by test service 2
        try {
            isRemoteFileReadable(testservice1,service2,"2");
            throw new TestException("test service 2 was able "
                    + "to read a test service 2 file");
        } catch (SecurityException ignore) { /* should occur */ }
        try {
            isRemoteFileReadable(sharedgroup,service2,"2");
            throw new TestException("test service 2 was able "
                    + "to read a shared group file");
        } catch (SecurityException ignore) { /* should occur */ }
        try {
            isRemoteFileReadable(vm,service2,"2");
            throw new TestException("test service 2 was able "
                    + "to read an activation group file");
        } catch (SecurityException ignore) { /* should occur */ }
    }

    /**
     * Writes a temp file with the specified fileName to the temp directory.
     * The fileName string is also written to the created temp file.
     */
    private File writeTmpFile(String fileName) throws IOException {
        File file = new File(tmpdir,fileName);
        file.deleteOnExit();
        logger.log(Level.FINE, "writing temp file " + file);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(fileName.getBytes());
	fos.flush();
        fos.close();
        return file;
    }

    /**
     * Calls the test service and requests it to load the specified
     * file and return it's bytes.
     *
     * return true if the returned bytes are identical to the 
     *        file's name; false otherwise
     */
    private boolean isRemoteFileReadable(File file, 
        TestService service, String serviceNumber)
        throws RemoteException, FileNotFoundException, IOException
    {
        logger.log(Level.FINE, "retrieving file " + file
            + " from test service " + serviceNumber);
        byte[] fileBytes = service.loadFile(file);
        String fileString = new String(fileBytes);
        logger.log(Level.FINE, "expected file contents: "
            + file.getName() + "; actual file contents: " + fileString);
        return file.getName().equals(fileString);
    }

}
