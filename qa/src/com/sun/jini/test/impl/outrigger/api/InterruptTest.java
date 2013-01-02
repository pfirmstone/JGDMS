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
package com.sun.jini.test.impl.outrigger.api;

import java.util.logging.Level;

// java classes
import java.rmi.RemoteException;

// jini classes
import net.jini.space.JavaSpace;
import net.jini.admin.Administrable;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;



// Shared classes
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.TestBase;


/**
 * Test to make sure blocking reads can be interrupted
 */
public class InterruptTest extends TestBase implements Test {


    private class ReadThread extends Thread {
        Exception rslt = null;
        JavaSpace space;

        ReadThread(JavaSpace s) {
            super("ReadThread");
            space = s;
        }

        public void run() {
            try {
                space.read(null, null, 10000);
            } catch (InterruptedException e) {
                rslt = e;
            } catch (Exception e) {
                rslt = e;
            }
        }
    }

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        super.parse();
        return this;
    }

    public void run() throws Exception {
        specifyServices(new Class[] {JavaSpace.class});
        final JavaSpace space = (JavaSpace) services[0];

        // Kick of read in a seperate thread
        ReadThread readThread = new ReadThread(space);
        readThread.start();

	Thread.sleep(5000);
	readThread.interrupt();
	readThread.join();

        if (readThread.rslt == null) {
            throw new TestException("Read returned normally");
        } else if (readThread.rslt instanceof InterruptedException) {
            return;
        } else {
            throw new TestException(
                    "Expected: InterruptedException. Returned: "
                    + readThread.rslt.getMessage());
        }
    }
}
