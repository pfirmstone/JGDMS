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
package com.sun.jini.landlord;

/**
 * Provided for backward compatibility, migrate to new name space.
 */
@Deprecated
public interface Landlord extends org.apache.river.landlord.Landlord {
    
    /**
     * Note that for security reasons, the public api of this class has
     * changed in the new org.apache.river.landlord name space.
     * 
     * Public mutable fields are a playground for gadget attacks, which is also
     * why this class also doesn't implement {@link org.apache.river.api.io.AtomicSerial AtomicSerial}
     */
    public class RenewResults extends org.apache.river.landlord.Landlord.RenewResults {
        static final long serialVersionUID = 2L; 
  
        /** 
        * For each cookie passed to {@link Landlord#renewAll renewAll}, 
        * <code>granted[i]</code> is the granted lease time, or -1 if the 
        * renewal for that lease generated an exception.  If there was 
        * an exception, the exception is held in <code>denied</code>. 
        * 
        * @see #denied 
        * @serial 
        */ 
        public long[] granted; 

        /** 
        * The <code>i</code><sup><i>th</i></sup> -1 in <code>granted</code> 
        * was denied because of <code>denied[i]</code>.  If nothing was  
        * denied, this field is <code>null</code>. 
        * 
        * @serial 
        */ 
        public Exception[] denied; 

        public RenewResults(long[] granted) {
            super(granted);
            this.granted = granted;
        }
        
        public RenewResults(long[] granted, Exception[] denied) {
            super(granted, denied);
            this.granted = granted;
            this.denied = denied;
        }
        
    }
}
