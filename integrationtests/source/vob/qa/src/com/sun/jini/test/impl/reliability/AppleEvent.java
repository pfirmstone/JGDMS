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
package com.sun.jini.test.impl.reliability;

import java.io.Serializable;
import java.util.Date;

/**
 * The AppleEvent class is simply an object to be passed to a
 * remote object exported by an applet.  The intent is to verify 
 * proper object serialization of arrays.
 */
public class AppleEvent implements Serializable {

    public static final int BUY   = 0;
    public static final int EAT   = 1;
    public static final int THROW = 2;

    private final int what;
    private final Date when;

    public AppleEvent(int what) {
	this.what = what;
	this.when = new Date();
    }

    public String toString() {
	String desc = "[";
	switch (what) {
	case BUY:
	    desc += "BUY";
	    break;
	case EAT:
	    desc += "EAT";
	    break;
	case THROW:
	    desc += "THROW";
	    break;
	}
	desc += " @ " + when + "]";
	return desc;
    }
}
