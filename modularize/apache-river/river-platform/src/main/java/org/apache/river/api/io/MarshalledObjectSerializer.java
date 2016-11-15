/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = MarshalledObject.class)
@AtomicSerial
class MarshalledObjectSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final MarshalledInstance instance;
    
    MarshalledObjectSerializer(MarshalledObject obj){
	this(new MarshalledInstance(obj));
    }
    
    MarshalledObjectSerializer(MarshalledInstance inst){
	this.instance = inst;
    }
    
    public MarshalledObjectSerializer(GetArg arg) throws IOException{
	this(check(arg.get("instance", null, MarshalledInstance.class)));
    }
    
    private static MarshalledInstance check(MarshalledInstance mi){
	return mi;
    }
    
    Object readResolve() throws ObjectStreamException {
	return instance.convertToMarshalledObject();
    }
}
