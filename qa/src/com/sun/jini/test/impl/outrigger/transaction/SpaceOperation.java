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
package com.sun.jini.test.impl.outrigger.transaction;

// All imports
import net.jini.space.JavaSpace;
import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import java.rmi.RemoteException;


/**
 * This class wraps four kinds of JavaSpace operations to get an entry,
 * <code>read</code>, <code>readIfExists</code>,
 * <code>take</code> and <code>takeIfExists</code>.
 *
 * 
 */
public class SpaceOperation {
    private boolean useRead;
    private boolean useIfExists;
    private int txnType;

    /** Transaction type. No transaction is used */
    public final static int USE_NULL = 0;

    /** Transaction type. The same transaction with writing is used */
    public final static int USE_SAME = 1;

    /** Transaction type. Another transaction is used */
    public final static int USE_DIFF = 2;

    /**
     * Constructor. There is no default constructor.
     *
     * @param useRead if <code>true</code> <code>read</code> or
     *         <code>readIfExists</code> is used.
     * @param useIfExists if <code>true</code> <code>readIfExists</code> or
     *         <code>takeIfExists</code> is used.
     * @param txnType Applied transaction type. <code>USE_NULL</code>,
     *         <code>USE_SAME</code> or <code>USE_DIFF</code> must be specified.
     *         This parameter only affects to <code>toString</code> method.
     */
    public SpaceOperation(boolean useRead, boolean useIfExists, int txnType) {
        this.useRead = useRead;
        this.useIfExists = useIfExists;
        this.txnType = txnType;
    }

    /**
     * Get transaction type.
     *
     * @return number of transaction type.
     */
    public int getTxnType() {
        return txnType;
    }

    /**
     * Check operation type.
     *
     * @return <code>true</code>, if <tt>read</tt> or <tt>readIfExists</tt>
     * is specified.
     */
    public boolean isRead() {
        return useRead;
    }

    /**
     * Check operation type.
     *
     * @return <code>true</code>, if <tt>takeIfExists</tt> or
     * <tt>readIfExists</tt> is specified.
     */
    public boolean isIfExists() {
        return useIfExists;
    }

    /**
     * Issue the selected operation.
     *
     * @param space target JavaSpace
     * @param template template entry to be used.
     * @param txn transaction object to be used.
     * @param timeout timeout value.
     * @return got entry
     */
    public Entry get(JavaSpace space, Entry template, Transaction txn,
            long timeout)
            throws TransactionException, UnusableEntryException,
            RemoteException, InterruptedException {
        Entry entry = null;

        if (useRead) {
            if (useIfExists) {
                entry = space.readIfExists(template, txn, timeout);
            } else {
                entry = space.read(template, txn, timeout);
            }
        } else {
            if (useIfExists) {
                entry = space.takeIfExists(template, txn, timeout);
            } else {
                entry = space.take(template, txn, timeout);
            }
        }
        return entry;
    }

    /**
     * Returns string expression of this operation.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer("");

        if (useRead) {
            sb.append("read");
        } else {
            sb.append("take");
        }

        if (useIfExists) {
            sb.append("IfExists");
        }

        switch (txnType) {
          case USE_NULL:
            sb.append(" without transaction");
            break;
          case USE_SAME:
            sb.append(" with transaction");
            break;
          case USE_DIFF:
            sb.append(" with other transaction");
            break;
        }
        return sb.toString();
    }
}
