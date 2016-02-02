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
@AtomicExternal
class ShortSerializer implements Externalizable {
    private final static long serialVersionUID = 1L;
    
    private final short s;
    
    ShortSerializer(short s){
	this.s = s;
    }
    
    public ShortSerializer(ObjectInput in) throws IOException{
	this(in.readShort());
    }
    
    Object readResolve() throws ObjectStreamException {
	return s;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	out.writeShort(s);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public boolean equals(Object o){
	if (o == this) return true;
	if (!(o instanceof ShortSerializer)) return false;
	ShortSerializer that = (ShortSerializer) o;
	return that.s == s;
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 37 * hash + this.s;
	return hash;
    }
}
