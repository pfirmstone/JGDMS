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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = Integer.class)
@AtomicExternal
class IntSerializer implements Externalizable {
    private final static long serialVersionUID = 1L;
    
    private int i;
    
    public IntSerializer(){}
    
    IntSerializer(Integer i){
	this.i = i;
    }
    
    public IntSerializer(ObjectInput in) throws IOException{
	this(in.readInt());
    }
    
    Object readResolve() throws ObjectStreamException {
	return i;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	out.writeInt(i);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	i = in.readInt();
    }
    
    @Override
    public boolean equals(Object o){
	if (o == this) return true;
	if (!(o instanceof IntSerializer)) return false;
	IntSerializer that = (IntSerializer) o;
	return that.i == i;
    }

    @Override
    public int hashCode() {
	return i;
    }
}
