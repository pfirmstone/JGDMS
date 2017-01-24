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
import org.apache.river.thread.wakeup.WakeupManager;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.TransactionManager;


/**
 */
public class AbortStressTask extends RandomStressTask {
    private volatile int attempt;// thread confined
    private static final int MAX_ATTEMPTS = 5;
    private static final boolean DEBUG = false;

    public AbortStressTask(ExecutorService executor, WakeupManager wakeupManager, TransactionManager mgr, int numParts) 
    {
        super(executor, wakeupManager, mgr, numParts);
    }

    /*
     * RetryTask abstract method definition
     */
    public boolean tryOnce() {
        attempt++;
        setBehavior(OP_JOIN);
        setBehavior(OP_VOTE_PREPARED);
        doBehavior();

        try {
            abort();
        } catch (RemoteException e) {
            if (attempt < MAX_ATTEMPTS) {
                return false;
            }
        } catch (TransactionException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}
