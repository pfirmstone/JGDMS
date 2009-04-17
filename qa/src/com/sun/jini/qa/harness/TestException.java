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

package com.sun.jini.qa.harness;

/** 
 * An exception which may be thrown at any time during test setup
 * and execution. May be used to wrap other unexpected exceptions
 * which occur.
 */
public class TestException extends Exception {

    /** 
     *Creates a <code>TestException</code>. 
     */
    public TestException(){ 
        super();
    }

    /** 
     * Creates a <code>TestException</code> with the given detail message.
     *
     *  @param s the detail message
     */
    public TestException(String s){
        super(s);
    }

    /**
     * Creates a <code>TestException</code> with the given detail message
     * and cause.
     *
     *  @param s the detail message
     *  @param e the cause
     */
    public TestException(String s, Throwable e){
        super(s, e);
    }

    /** 
     * Returns the detail message; includes the detail message from
     * the cause if there is one.
     *
     * @return the detail message
     */
    public String getMessage(){
        if (getCause() == null) {
            return super.getMessage();
	} else {
            return super.getMessage() + "; nested exception is: \n\t"
                                      + getCause().getMessage();
	}
    }
}
