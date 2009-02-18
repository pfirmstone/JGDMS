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
import com.sun.jini.thread.TaskManager;
import com.sun.jini.thread.TaskManager.Task;

//java.util
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements a regression test to bugID 4868259 "Task Manager
 * should be resilient to Tasks throwing exceptions."  The test verifies that
 * tasks are run after a given task throws an exception.
 */
public class TaskManagerTaskExceptionTest extends AbstractThreadTest {

    /**
     * Schedule two tasks.  The first task throws an exception.  Verify that
     * the second task is run.
     */
    public void run() throws Exception {
        TaskManager manager = new TaskManager();
        long badTaskTime = System.currentTimeMillis() + (10*1000);
        long goodTaskTime = badTaskTime + (10*1000);
        ArrayList taskList = new ArrayList();
        taskList.add(0, new Task() {
            public boolean runAfter(List tasks, int size){
                return false;

            }
            public void run() {
                throw new RuntimeException("Expected Exception");
            }
        });
        final boolean result[] = new boolean[]{false};
        taskList.add(1, new Task() {
            public boolean runAfter(List tasks, int size){
                return (tasks.size()>1);
            }
            public void run() {
                result[0] = true;
            }
        });
        manager.addAll(taskList);
        Thread.sleep(10 * 1000);
        if (!result[0]) {
            throw new TestException("A task that throws a runtime exception"
                + " prevents other tasks from running");
        }
    }
}
