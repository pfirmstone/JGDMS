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
package org.apache.river.fiddler.proxy;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/*
 * This class acts as a data structure in which the results of renewal 
 * attempts made in the method <code>FiddlerImpl.renewLeases</code> are
 * stored and returned.
 * <p>
 * Instances or this class are never visible to clients, they are private
 * to the communication between the LeaseMap proxy and the Fiddler 
 * implementation of the lookup discovery service.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public class FiddlerRenewResults {

    /**
     * The granted duration for each lease. The length of this array
     * is the same as the length of the durations parameter to renewLeases,
     * and is in the same order.  If a duration is -1, it indicates that
     * an exception was thrown for this lease.
     */
    public final long[] durations;
    /**
     * Exceptions thrown as a result of a renewal attempt in renewLeases.
     * The length of this array is the same as the number of -1 elements
     * in <code>durations</code> field of this class. Furthermore, the
     * exceptions in this array are in order.
     */
    public final Exception[] exceptions;

    /**
     * Serial form for the atomic/DER codecs. Mirrors the fields read by the
     * {@code (GetArg)} constructor.
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("durations", long[].class),
            new SerialForm("exceptions", Exception[].class)
        };
    }

    /** {@code @AtomicSerial} write contract (required by the atomic write engine). */
    public static void serialize(PutArg arg, FiddlerRenewResults o) throws IOException {
        arg.put("durations", o.durations);
        arg.put("exceptions", o.exceptions);
        arg.writeArgs();
    }

    /**
     * Constructs a new instance of FiddlerRenewResults.
     *
     * @param durations  array containing the durations granted as a result
     *                   of successful lease renewals.
     * @param exceptions array containing the exceptions thrown as a result
     *                   of an unsuccessful lease renewal.
     */
    public FiddlerRenewResults(long[] durations, Exception[] exceptions) {
        this.durations  = durations;
        this.exceptions = exceptions;
    }
    
    FiddlerRenewResults(GetArg arg) throws IOException, ClassNotFoundException {
	this(((long[])(arg.get("durations", new long[0]))).clone(),
	    ( arg.get("exceptions", null) == null ?
		(Exception[])null : (Exception[])arg.get("exceptions", null)));
    }
}
