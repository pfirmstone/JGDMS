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

/**
 * The structure of a command sent to a TestParticipant is as follows
 *
 *  Operation field                     Exception field
 *  -------------------------------------------------
 *  |31|30|29|28|27|26|25|24|23|22|21|20|19|18|17|16| * * *
 *  -------------------------------------------------
 *  |<---------------- upper 16-------------------->|
 *
 *        ---------------------------------------
 *  * * * |15|14|13|12|11|10|9|8|7|6|5|4|3|2|1|0|
 *        |--------------------------------------
 *
 *        |<------------ lower 16 ------------->|
 *
 *
 *  bit#	name				description
 *  =======================================================================
 *  0		EXCEPTION_REMOTE		throw a RemoteException
 *  1		EXCEPTION_CANNOT_COMMIT		throw CannotCommitException
 *  2		EXCEPTION_CANNOT_ABORT		throw CannotAbortExceptoin
 *  3		EXCEPTION_CANNOT_JOIN		throw CannotJoinException
 *  4		OP_JOIN_IDEMPOTENT		rejoin the transaction
 *  5		OP_VOTE_NOTCHANGED		vote NOTCHANGED on prepare
 *  6		OP_VOTE_PREPARED		vote PREPARED on prepare
 *  7		OP_VOTE_ABORTED			vote ABORTED on prepare
 *  8		OP_INCR_CRASHCOUNT		increment the crash count
 *  9		OP_JOIN				join the transaction
 *  10		OP_EXCEPTION_ON_PREPARE		allow exception on prepare
 *  11		OP_EXCEPTION_ON_COMMIT		allow exception on commit
 *  12		OP_EXCEPTION_ON_ABORT		allow exception on abort
 *  13		OP_EXCEPTION_ON_PREPARECOMMIT   allow exception on p&c
 *  14		OP_TIMEOUT_PREPARE	        take long time to return
 *  15          OP_TIMEOUT_ABORT	        take long time to return
 *  16		OP_TIMEOUT_COMMIT		take long time to return
 *  17		OP_TIMEOUT_PREPARECOMMIT 	take long time to return
 *  18		OP_TIMEOUT_VERYLONG		timeout a very long time(60s)
 *  19		OP_TIMEOUT_JOIN			timout on any join
 *  20		EXCEPTION_TRANSACTION		throw UnknownTransactionException
 *  21	        OPERATION_COUNT			placeholder to indicate
 *						how many operations
 *  
 */

public interface TxnManagerTestOpcodes {
    static final int EXCEPTION_REMOTE = 0;
    static final int EXCEPTION_CANNOT_COMMIT = 1;
    static final int EXCEPTION_CANNOT_ABORT = 2;
    static final int EXCEPTION_CANNOT_JOIN = 3;
    static final int OP_JOIN_IDEMPOTENT = 4;
    static final int OP_VOTE_NOTCHANGED = 5;
    static final int OP_VOTE_PREPARED = 6;
    static final int OP_VOTE_ABORTED = 7;
    static final int OP_INCR_CRASHCOUNT = 8; 
    static final int OP_JOIN = 9;
    static final int OP_EXCEPTION_ON_PREPARE = 10;
    static final int OP_EXCEPTION_ON_COMMIT = 11;
    static final int OP_EXCEPTION_ON_ABORT = 12;
    static final int OP_EXCEPTION_ON_PREPARECOMMIT = 13;
    static final int OP_TIMEOUT_PREPARE = 14;
    static final int OP_TIMEOUT_ABORT = 15;
    static final int OP_TIMEOUT_COMMIT = 16;
    static final int OP_TIMEOUT_PREPARECOMMIT = 17;
    static final int OP_TIMEOUT_VERYLONG = 18;
    static final int OP_TIMEOUT_JOIN = 19;
    static final int EXCEPTION_TRANSACTION = 20;
    static final int OPERATION_COUNT = 21;
}
