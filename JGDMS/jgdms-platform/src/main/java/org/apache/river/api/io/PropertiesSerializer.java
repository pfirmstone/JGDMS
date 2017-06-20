/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

/**
 *
 * @author peter
 */
@Serializer(replaceObType = Properties.class)
@AtomicSerial
class PropertiesSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("m", MapSerializer.class)
	};
    
    
    private final Properties p;
    private final MapSerializer m;
    
    PropertiesSerializer(Properties p){
	this.p = p;
	m = new MapSerializer(p);
    }
    
    PropertiesSerializer(GetArg arg) throws IOException{
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
    
    Object readResolve() throws ObjectStreamException {
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
