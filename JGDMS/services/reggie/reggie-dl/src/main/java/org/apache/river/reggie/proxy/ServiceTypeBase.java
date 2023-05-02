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
import org.apache.river.proxy.CodebaseProvider;

/**
 * A ServiceType annotated with a codebase.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public class ServiceTypeBase implements Serializable {

    private static final long serialVersionUID = 2L;
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("type", ServiceType.class),
            new SerialForm("codebase", String.class)
        };
    }
    
    public static void serialize(PutArg arg, ServiceTypeBase stb) throws IOException{
        arg.put("type", stb.type);
        arg.put("codebase", stb.codebase);
        arg.writeArgs();
    }

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

    /** Simple constructor */
    public ServiceTypeBase(ServiceType type, String codebase) {
	this.type = type;
	this.codebase = codebase;
    }
    
    public ServiceTypeBase(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("type", null, ServiceType.class),
	    arg.get("codebase", null, String.class));
    }

    /** Sets the codebase to the codebase of the given class. */
    public void setCodebase(Class cls) {
	codebase = CodebaseProvider.getClassAnnotation(cls);
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
