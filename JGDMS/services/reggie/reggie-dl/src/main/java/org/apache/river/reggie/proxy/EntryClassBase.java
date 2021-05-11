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
 * An EntryClass annotated with a codebase.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public class EntryClassBase implements Serializable {

    private static final long serialVersionUID = 2L;
    
    private static final String ECLASS = "eclass";
    private static final String CODEBASE = "codebase";
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(ECLASS, EntryClass.class),
            new SerialForm(CODEBASE, String.class)
        };
    }
    
    public static void serialize(PutArg arg, EntryClassBase ecb) throws IOException{
        arg.put(ECLASS, ecb.eclass);
        arg.put(CODEBASE, ecb.codebase);
        arg.writeArgs();
    }

    /**
     * The EntryClass.
     *
     * @serial
     */
    public final EntryClass eclass;
    /**
     * The codebase.
     *
     * @serial
     */
    public String codebase;

    /** Simple constructor */
    public EntryClassBase(EntryClass eclass, String codebase) {
	this.eclass = eclass;
	this.codebase = codebase;
    }
    
    public EntryClassBase(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("eclass", null, EntryClass.class),
	    arg.get("codebase", null, String.class)
	);
    }

    /** Sets the codebase to the codebase of the given class. */
    public void setCodebase(Class cls) {
	codebase = CodebaseProvider.getClassAnnotation(cls);
    }

    /**
     * Converts an array of EntryClassBase to an array of Class.  If a
     * class cannot be loaded, it is left as null.
     */
    public static Class[] toClass(EntryClassBase[] eclasses)
    {
	Class[] classes = null;
	if (eclasses != null) {
	    classes = new Class[eclasses.length];
	    for (int i = eclasses.length; --i >= 0; ) {
		try {
		    EntryClassBase eclass = eclasses[i];
		    classes[i] = eclass.eclass.toClass(eclass.codebase);
		} catch (Throwable e) {
		    RegistrarProxy.handleException(e);
		}
	    }
	}
	return classes;
    }
}
