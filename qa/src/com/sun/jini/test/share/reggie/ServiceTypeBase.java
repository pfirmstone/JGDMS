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
package com.sun.jini.test.share.reggie;

import java.rmi.server.RMIClassLoader;
import java.io.IOException;

/**
 * A ServiceType annotated with a codebase.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class ServiceTypeBase implements java.io.Serializable {
    /**
     * The ServiceType.
     *
     * @serial
     */
    public final ServiceType type;
    /**
     * The codebase.
     *
     * @serial
     */
    public String codebase;

    private static final long serialVersionUID = -2930430739238582517L;

    /** Simple constructor */
    public ServiceTypeBase(ServiceType type, String codebase) {
	this.type = type;
	this.codebase = codebase;
    }

    /** Sets the codebase to the codebase of the given class. */
    public void setCodebase(Class cls) {
	codebase = RMIClassLoader.getClassAnnotation(cls);
    }

    /**
     * Converts an array of ServiceTypeBase to an array of Class.  If a
     * class cannot be loaded, it is left as null.
     */
    public static Class[] toClass(ServiceTypeBase[] stypes)
    {
	Class[] classes = null;
	if (stypes != null) {
	    classes = new Class[stypes.length];
	    for (int i = stypes.length; --i >= 0; ) {
		try {
		    ServiceTypeBase stype = stypes[i];
		    classes[i] = stype.type.toClass(stype.codebase);
		} catch (Throwable e) {
		    RegistrarProxy.handleException(e);
		}
	    }
	}
	return classes;
    }
}
