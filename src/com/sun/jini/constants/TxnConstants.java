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
package com.sun.jini.constants;

import net.jini.core.transaction.server.TransactionConstants;

/**
 * Names for constants common to transaction managers and participants.
 *
 * @author Sun Microsystems, Inc.
 *
 */

public class TxnConstants implements TransactionConstants {
    /** Names of each of the states */
    private static final String[] stateNames = {
	null, "active", "voting", "prepared",
	"notchanged", "committed", "aborted"
    };

    /**
     * Returns a <code>String</code> which describes
     * the state of the <code>Transaction</code>.
     *
     * @see net.jini.core.transaction.server.TransactionConstants
     */
    public static String getName(int state) {
	String name = "UNKNOWN";
	switch (state) {
	  case ACTIVE:
	  case VOTING:
	  case PREPARED:
	  case NOTCHANGED:
	  case COMMITTED:
	  case ABORTED:
	    name = stateNames[state];
	}
	return name;
    }
}
