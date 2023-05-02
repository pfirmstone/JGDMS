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
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;

/*
 * RenewResults contains the return values of a renewLeases call on the
 * registrar.  Instances are never visible to clients, they are private
 * to the communication between the LeaseMap proxy and the registrar.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public class RenewResults implements Serializable {

    private static final long serialVersionUID = 2L;
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("durations", long[].class),
            new SerialForm("exceptions", Exception[].class)
        };
    }
    
    public static void serialize(PutArg arg, RenewResults rr) throws IOException{
        arg.put("durations", rr.durations);
        arg.put("exceptions", rr.exceptions);
        arg.writeArgs();
    }
    
    // TODO: investigate visibility and shared mutability

    /**
     * The granted duration for each lease.  The length of this array
     * is the same as the length of the durations parameter to renewLeases,
     * and is in the same order.  If a duration is -1, it indicates that
     * an exception was thrown for this lease.
     *
     * @serial
     */
    long[] durations;
    /**
     * Any exceptions thrown.  The length of this array is the same as
     * the number of -1 elements in durations.  The exceptions are in
     * order.
     *
     * @serial
     */
    Exception[] exceptions;

    /** Simple constructor */
    public RenewResults(long[] durations, Exception[] exceptions) {
	this.durations = durations;
	this.exceptions = exceptions;
    }
    
    public RenewResults(GetArg arg) throws IOException, ClassNotFoundException{
	this(Valid.copy(arg.get("durations", null, long[].class)),
		Valid.copy(arg.get("exceptions", null, Exception[].class)));
    }
}
