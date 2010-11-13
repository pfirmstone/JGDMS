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
package com.sun.jini.outrigger;

/**
 * This is used as a handback object to indicate that a proxy's blocked
 * call should "return" by throwing an exception.
 *
 * @author Sun Microsystems, Inc.
 *
 */
// @see SpaceProxy
class ThrowThis implements java.io.Serializable {
    static final long serialVersionUID = -7432214583908049814L;

    private Long	evID;		// the event ID
    private Exception	toThrow;	// the exception to throw

    ThrowThis(Long evID, Exception toThrow) {
	this.evID = evID;
	this.toThrow = toThrow;
    }

    Long id() {
	return evID;
    }

    Exception toThrow() {
	return toThrow;
    }

    public String toString()  {
	return evID + "->" + toThrow;
    }
}
