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
@Serializer(replaceObType = Byte.class)
@AtomicExternal
public class ByteSerializer implements Externalizable {
    private static final long serialVersionUID = 1L;
    
    private byte b;
    
    ByteSerializer(Byte b){
	this.b = b;
    }
    
    public ByteSerializer(){}
    
    public ByteSerializer(ObjectInput in) throws IOException{
	this(in.readByte());
//	this(arg.get("b", (byte)0));
    }
    
    Object readResolve() throws ObjectStreamException {
	return b;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	out.writeByte(b);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	b = in.readByte();
    }
    
    @Override
    public boolean equals(Object o){
	if (o == this) return true;
	if (!(o instanceof ByteSerializer)) return false;
	ByteSerializer that = (ByteSerializer) o;
	return that.b == b;
    }

    @Override
    public int hashCode() {
	int hash = 5;
	hash = 11 * hash + this.b;
	return hash;
    }
}
