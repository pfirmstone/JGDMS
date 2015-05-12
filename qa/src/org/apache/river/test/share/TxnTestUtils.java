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

package org.apache.river.test.share;

import java.rmi.RemoteException;
import java.util.Random;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;

public class TxnTestUtils {
    private static final boolean DEBUG = false;

    public static TestParticipant[] createParticipants(int howMany)
	throws RemoteException
    {
	if (DEBUG) {
	    System.out.println("TxnTestUtils: createParticipants: creating " +
				howMany + " participants");
	}

	TestParticipant[] tps = new TestParticipant[howMany];

	for (int i = 0; i < howMany; i++) {
	    if (DEBUG) {
		System.out.println("TxnTestUtils: createParticipants: " +
				    "creating Participant(" + (i+1) + ")" );
	    }

	    tps[i] = new TestParticipantImpl(TestParticipant.DEFAULT_NAME +
							        "-" + i);
	}

	return tps;
    }

    public static void setBulkBehavior(int how, TestParticipant[] parts)
	throws RemoteException
    {
        int l = parts.length;
	for (int i = 0; i < l; i++) {
	    parts[i].setBehavior(how);
	}
    }

    public static void doBulkBehavior(Transaction txn,
					TestParticipant[] parts)
	throws RemoteException, TransactionException
    {
        int l = parts.length;
	for (int i = 0; i < l; i++) {
	    if (DEBUG) {
		System.out.println("TxnTestUtils: doBulkBehavior: instructing" +
				    "Participant(" + (i+1) + ")" );
	    }

	    parts[i].behave(txn);
	}
    }

    public static TestParticipant chooseOne(TestParticipant[] parts)
	throws RemoteException, TransactionException
    {
	Random rnd = new Random();
	int i = Math.abs(rnd.nextInt() % parts.length);

	return parts[i];
    }
}
