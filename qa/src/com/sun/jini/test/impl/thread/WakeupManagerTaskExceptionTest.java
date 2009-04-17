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
package com.sun.jini.test.impl.thread;

//harness imports
import com.sun.jini.qa.harness.TestException;

//jini imports
import com.sun.jini.thread.WakeupManager;

/**
 * This class implements a regression test to bugID 4879941 "WakeupManager
 * should protect itself against tasks that throw exceptions."
 * The test verifies that tasks are run after a given task throws an exception.
 */
public class WakeupManagerTaskExceptionTest extends AbstractThreadTest {

    /**
     * Schedule two tasks.  The first task throws an exception.  Verify that
     * the second task is run.
     */
    public void run() throws Exception {
        WakeupManager manager = new WakeupManager();
        long badTaskTime = System.currentTimeMillis() + (10*1000);
        long goodTaskTime = badTaskTime + (10*1000);
        manager.schedule(badTaskTime, new Runnable() {
            public void run() {
                throw new RuntimeException("Expected Exception");
            }
        });
        final boolean result[] = new boolean[]{false};
        manager.schedule(goodTaskTime, new Runnable() {
            public void run() {
                result[0] = true;
            }
        });
        while (System.currentTimeMillis() < goodTaskTime + 10) {
            Thread.sleep((goodTaskTime+10)-System.currentTimeMillis());
        }
        if (!result[0]) {
            throw new TestException("A task that throws a runtime exception"
                + " prevents other tasks from running");
        }
    }
}
