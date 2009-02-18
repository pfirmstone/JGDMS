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
package com.sun.jini.test.spec.javaspace.conformance;

// net.jini
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.core.transaction.server.TransactionManager;


/**
 * Class which is needed to prevent transaction's
 * normal committing completion.
 *
 * @author Mikhail A. Markov
 */
public class Committer extends Thread {

    /** TransactionParticipant which will abort the transaction */
    private TransactionParticipant tp;

    /** Transaction which is needed to be aborted */
    private ServerTransaction st;

    /** Transaction manager which manages transactions */
    private TransactionManager mgr;

    /**
     * Constructor which initialize fields of the class.
     *
     * @param tp TransactionParticipant instance.
     * @param st ServerTransaction which is needed to be aborted.
     * @param mgr TransactionManager which manages transations.
     */
    public Committer(TransactionParticipant tp, ServerTransaction st,
            TransactionManager mgr) {
        this.tp = tp;
        this.st = st;
        this.mgr = mgr;
    }

    /**
     * Main method which will abort the transaction.
     */
    public void run() {
        try {
            sleep(2000);
        } catch (InterruptedException e) {}

        /*
         * commit method in special TransactionParticipant implementation
         * will return ABORTED state wich will make the transaction
         * to abort.
         */
        try {
            tp.commit(mgr, st.id);
        } catch (Exception ex) {}

        try {
            sleep(1000);
        } catch (InterruptedException e) {}
    }
}
