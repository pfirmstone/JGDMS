/*
 * Copyright 2016 Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.api.io;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.util.Date;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = Date.class)
@AtomicExternal
class DateSerializer implements Externalizable {
    private final static long serialVersionUID = 1L;
    
    private Date d;
    
    DateSerializer(Date d){
	this.d = d;
    }
    
    DateSerializer(long date){
	this.d = new Date(date);
    }
    
    public DateSerializer(){}
    
    public DateSerializer(ObjectInput in) throws IOException{
	this(in.readLong());
    }
    
    Object readResolve() throws ObjectStreamException {
	return d;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	out.writeLong(d.getTime());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	d = new Date(in.readLong());
    }
    
    @Override
    public boolean equals(Object o){
	if (o == this) return true;
	if (!(o instanceof DateSerializer)) return false;
	DateSerializer that = (DateSerializer) o;
	return that.d == d;
    }

    @Override
    public int hashCode() {
	int hash = 5;
	hash = 79 * hash + (this.d != null ? this.d.hashCode() : 0);
	return hash;
    }
}
