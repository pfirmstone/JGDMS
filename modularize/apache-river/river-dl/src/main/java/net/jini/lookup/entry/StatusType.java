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
package net.jini.lookup.entry;

import java.io.Serializable;

/**
 * Information pertaining to the severity of a Status object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Status
 */
public class StatusType implements Serializable {
    private static final long serialVersionUID = -8268735508512712203L;

    /** @serial */
    private final int type;

    private StatusType(int t) {
	type = t;
    }

    /**
     * An error has occurred that is preventing a service from
     * operating correctly.
     */
    public static final StatusType ERROR = new StatusType(1);

    /**
     * A problem merits attention, but is not impeding the operation
     * of the service.
     */
    public static final StatusType WARNING = new StatusType(2);

    /**
     * An unusual event is occurring, but the event is not necessarily
     * a problem.
     */
    public static final StatusType NOTICE = new StatusType(3);

    /**
     * A noteworthy event is occurring during the normal operation of
     * a service.
     */
    public static final StatusType NORMAL = new StatusType(4);

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	if (type == ERROR.type) {
	    return "StatusType.ERROR";
	} else if (type == WARNING.type) {
	    return "StatusType.WARNING";
	} else if (type == NOTICE.type) {
	    return "StatusType.NOTICE";
	} else if (type == NORMAL.type) {
	    return "StatusType.NORMAL";
	} else {
	    return "StatusType." + type; // not possible ...
	}
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     */
    private Object readResolve() {
	if (type == ERROR.type) {
	    return ERROR;
	} else if (type == WARNING.type) {
	    return WARNING;
	} else if (type == NOTICE.type) {
	    return NOTICE;
	} else if (type == NORMAL.type) {
	    return NORMAL;
	} else {
	    throw new IllegalArgumentException("illegal type");
	}
    }
}
