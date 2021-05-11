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
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = Properties.class)
@AtomicSerial
public class PropertiesSerializer implements Serializable, Resolve {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
            serialForm();
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm("m", MapSerializer.class)
        };
    }
    
    public static void serialize(PutArg arg, PropertiesSerializer p) throws IOException{
        arg.put("m", p.m);
        arg.writeArgs();
    }
    
    private final Properties p;
    private final MapSerializer m;
    
    public PropertiesSerializer(Properties p){
	this.p = p;
	m = new MapSerializer(p);
    }
    
    public PropertiesSerializer(GetArg arg) throws IOException, ClassNotFoundException{
	this(getProperties(arg.get("m", null, MapSerializer.class)));
    }
    
    static Properties getProperties(MapSerializer ms){
	Properties p = new Properties();
	Iterator<Map.Entry> it = ms.entrySet().iterator();
	while (it.hasNext()){
	    Map.Entry e = it.next();
	    String key = (String) e.getKey();
	    String value = (String) e.getValue();
	    p.put(key, value);
	}
	return p;
    }
    
    @Override
    public Object readResolve() throws ObjectStreamException {
	return p;
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("m", m);
	out.writeFields();
    }
}
