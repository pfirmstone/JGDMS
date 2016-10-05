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
@Serializer(replaceObType = Double.class)
@AtomicExternal
public class DoubleSerializer implements Externalizable {
    private final static long serialVersionUID = 1L;
    
    private double d;
    
    DoubleSerializer(Double d){
	this.d = d;
    }
    
    public DoubleSerializer(){}
    
    public DoubleSerializer(ObjectInput in) throws IOException{
	this(in.readDouble());
    }
    
    Object readResolve() throws ObjectStreamException {
	return d;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	out.writeDouble(d);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	d = in.readDouble();
    }
    
    @Override
    public boolean equals(Object o){
	if (o == this) return true;
	if (!(o instanceof DoubleSerializer)) return false;
	DoubleSerializer that = (DoubleSerializer) o;
	return that.d == d;
    }

    @Override
    public int hashCode() {
	int hash = 3;
	hash = 19 * hash + (int) (Double.doubleToLongBits(this.d) ^ (Double.doubleToLongBits(this.d) >>> 32));
	return hash;
    }
}
