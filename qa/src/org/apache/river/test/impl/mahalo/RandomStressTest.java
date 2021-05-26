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
package org.apache.river.test.impl.mahalo;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.TxnManagerTest;
import org.apache.river.thread.wakeup.WakeupManager;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.jini.core.transaction.server.TransactionManager;


/**
 * This class subjects the TransactionManager to randomly selected
 * stresses in an effor to subject it to the types of load which
 * may be encountered in a real situation.
 *
 */
public class RandomStressTest extends TxnManagerTest {
    private static final boolean DEBUG = false;

    // RandomStressTask enumeration
    private static final int COMMIT_STRESS = 0;
    private static final int ABORT_STRESS = 1;
    private static final int COMMITABORT_STRESS = 2;
    private static final int ABORTCOMMIT_STRESS = 3;
    private static final int COUNT_STRESS_TASK = 4;

    // Default values.
    private static final int PARTS = 20; // Number of participants
    private static final int TASKS = 1000;
    private static final int THREADS = 150;
    private static final long TIMEOUT = 1000 * 15;
    private static final long TEST_TIMEOUT = 1000 * 1800;
    private static final long SLEEP_TIME = 1000 * 15;
    private static final float LOAD = 1.0f;

    // Values with defaults.
    private int parts = PARTS; // Number of participants
    private int tasks = TASKS; // Number of stress tasks
    private int threads = THREADS;
    private long timeout = TIMEOUT;
    private long test_timeout = TEST_TIMEOUT;
    private long sleep_time = SLEEP_TIME;

    // Another values.
    private volatile TransactionManager mgr = null;
    private ExecutorService threadpool = null;
    private volatile WakeupManager wakeupManager = null;
    private volatile Random random;
    private volatile long seed = 0;

    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        this.parse();
        return this;
    }

    /**
     * Parse our args and get values from property file for this test.
     * <DL>
     *
     * <DT>-seed <var>long</var><DD> Random seed.
     *
     * <DT>-parts <var>int</var><DD>Maximum number of participants.
     *
     * <DT>-tasks <var>int</var><DD>Number of RandomStressTask.
     *
     * <DT>-threads <var>int</var><DD>Number of threads for RandomStressTask.
     *
     * <DT>-timeout <var>long</var><DD>Timeout for TaskManager.
     *
     * </DL>
     */
    public void parse() throws Exception {

        // Get values from property file for this test.
        String s = "org.apache.river.test.impl.mahalo.RandomStressTest.";
        seed = getConfig().getLongConfigVal(s + "seed", 
					    System.currentTimeMillis());
        parts = getConfig().getIntConfigVal(s + "parts", PARTS);
        tasks = getConfig().getIntConfigVal(s + "tasks", TASKS);
        threads = getConfig().getIntConfigVal(s + "threads", THREADS);
        timeout = getConfig().getLongConfigVal(s + "timeout", TIMEOUT);	
        test_timeout = getConfig().getLongConfigVal(s + "test_timeout",
						    TEST_TIMEOUT);	
        sleep_time = getConfig().getLongConfigVal(s + "sleep_time", SLEEP_TIME);
    }

    /**
     * Return a random integer number.
     */
    private int randomInt() {
        return Math.abs(random.nextInt());
    }

    /**
     * Create one of RandomStressTask.
     */
    private RandomStressTask chooseTask() {
        RandomStressTask result = null;
        int numparts = (randomInt() % parts) + 1;

        switch (randomInt() % COUNT_STRESS_TASK) {
          case COMMIT_STRESS:
            result = new CommitStressTask(threadpool, wakeupManager, mgr,
					  numparts);
            break;
          case ABORT_STRESS:
            result = new AbortStressTask(threadpool, wakeupManager, mgr,
					 numparts);
            break;
          case COMMITABORT_STRESS:
            result = new CommitAbortStressTask(threadpool, wakeupManager, mgr,
					       numparts);
            break;
          case ABORTCOMMIT_STRESS:
            result = new AbortCommitStressTask(threadpool, wakeupManager, mgr,
					       numparts);
            break;
        }

        if (DEBUG) {
            logger.log(Level.INFO, 
		       "RandomStressTest: created " + result + "with "
		      + numparts + " participants");
        }
        return result;
    }

    private void markTime() {
        logger.log(Level.INFO, "RandomStressTest: @ " + new
                Date(System.currentTimeMillis()));
    }

    /**
     * Let GC to work after stresses.
     */
    public void tearDown() {
        super.tearDown();
        super.fullGC(); // Aggressively frees evrything by used Runtime.gc()
    }

    public void run() throws Exception {
        logger.log(Level.INFO, "RandomStressTest: To repeat the test, note "
                + "down the seed");
        logger.log(Level.INFO, "RandomStressTest: seed = " + seed);
        logger.log(Level.INFO, "TEST NOT FINISHED UNTIL I SAY DONE");

        if (DEBUG) {
            logger.log(Level.INFO, "RandomStressTest: parts   = " + parts);
            logger.log(Level.INFO, "RandomStressTest: tasks   = " + tasks);
            logger.log(Level.INFO, "RandomStressTest: threads = " + threads);
            logger.log(Level.INFO, "RandomStressTest: timeout = " + timeout);
            logger.log(Level.INFO, "RandomStressTest: test_timeout = "
                    + test_timeout);
            logger.log(Level.INFO, "RandomStressTest: sleep_time   = "
                    + sleep_time);
        }
        random = new Random(seed);
        threadpool = Executors.newFixedThreadPool(threads);
                //new TaskManager(threads, timeout, LOAD);
	wakeupManager = new WakeupManager();

	startTxnMgr();

	mgr = manager();
	RandomStressTask[] alltasks = new RandomStressTask[tasks];
	RandomStressTask task = null;

            // Create, queue and remember all the tasks.
	for (int i = 0; i < tasks; i++) {
	    task = chooseTask();

	    if (task == null) {
		throw new TestException("error creating a RandomStressTask");
	    }
	    threadpool.submit(task);
	    alltasks[i] = task;

	    if (DEBUG) {
		logger.log(Level.INFO, "RandomStressTest: " + (i + 1)
			   + " tasks created.");
		markTime();
	    }
	}

	// Wait for all the tasks to complete.
	boolean allComplete = true;
        long max_count = test_timeout / sleep_time;
	String log_msg = "Wait about " + (sleep_time / 1000) + " sec.";

	for (long j = 0; j < max_count; j++) {
	    allComplete = true;

	    for (int i = 0; i < tasks; i++) {
		if (alltasks[i] == null) {
		    continue;
		}
		boolean done = alltasks[i].isDone();

		if (DEBUG) {
		    logger.log(Level.INFO, 
			       "Task #" + i + " completed: " + done);
		    markTime();
		}

		if (done) {
		    alltasks[i] = null; // git along little gc
		} else {
		    allComplete = false;
		}
	    }

	    if (allComplete) {
		break;
	    }
	    logger.log(Level.INFO, log_msg);

	    try {
		Thread.sleep(sleep_time);
	    } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
	}
	}

	if (!allComplete) {
	    throw new TestException("Some of RandomStressTask tasks "
				  + "not yet finished");
	}
	logger.log(Level.INFO, "TEST DONE");
    }
}
