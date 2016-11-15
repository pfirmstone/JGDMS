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

package net.jini.core.lease;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamField;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;

/**
 * An exception generated when a LeaseMap renewAll or cancelAll call
 * generates exceptions on one or more leases in the map.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class LeaseMapException extends LeaseException {

    private static final long serialVersionUID = -4854893779678486122L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     * 
     * In earlier versions the extra fields will duplicate those of Throwable,
     * so only ref id's will be sent, the objects these fields refer to will
     * only be sent once.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("exceptionMap", Map.class)
	}; 

    /**
     * A Map from Lease to Exception, containing each lease that failed to
     * renew or cancel, and the exception that resulted from that lease's
     * renewal or cancel attempt.
     *
     * @serial
     */
    public final Map<Lease,Throwable> exceptionMap;

    /**
     * AtomicSerial constructor
     * @param arg
     * @throws IOException 
     */
    public LeaseMapException(GetArg arg) throws IOException{
	this(arg,
	     Valid.copyMap( //Defensive copy of exceptionMap into new HashMap
		 Valid.notNull(
		     arg.get("exceptionMap", null, Map.class),
		     "exceptionMap is null"
		 ), 
		 // New map to contain checked keys and values
		 new HashMap<Lease,Throwable>(), 
		 Lease.class, // keys must be instanceof Lease
		 Throwable.class // values must be instanceof Throwable
	     ) // returns populated and checked map.
	);
    }
    
    private LeaseMapException(GetArg arg,
			      Map<Lease,Throwable> exceptionMap) throws IOException 
    {
        super(arg);
	this.exceptionMap = exceptionMap;
    }
    
    /**
     * Constructs a LeaseMapException for the specified map with a
     * detail message.
     *
     * @param s             the detail message
     * @param exceptionMap  the <tt>Map</tt> object on which the exception 
     *                      occurred
     * @throws NullPointerException if <code>exceptionMap</code> is
     *         <code>null</code> or contains a <code>null</code> key
     *         or a <code>null</code> value
     * @throws IllegalArgumentException if <code>exceptionMap</code>
     *         contains any key which is not an instance of 
     *         {@link Lease}, or any value which is not an instance of
     *         <code>Throwable</code>
     */
    public LeaseMapException(String s, Map<Lease,Throwable> exceptionMap) {
	super(s);

	final Set mapEntries = exceptionMap.entrySet();
	for (Iterator i=mapEntries.iterator(); i.hasNext(); ) {
	    final Map.Entry entry = (Entry)i.next();
	    final Object key = entry.getKey();
	    final Object value = entry.getValue();

	    if (key == null) {
		throw new NullPointerException("exceptionMap contains a null key");
	    }

	    if (!(key instanceof Lease)) {
		throw new IllegalArgumentException("exceptionMap contains an " +
		    "a key which is not a Lease:" + key);
	    }

	    if (value == null) {
		throw 
		    new NullPointerException("exceptionMap contains a null value");
	    }

	    if (!(value instanceof Throwable)) {
		throw new IllegalArgumentException("exceptionMap contains an " +
		    "a value which is not a Throwable:" + value);
	    }
	}

	this.exceptionMap = exceptionMap;
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	PutField pf = out.putFields();
	pf.put("exceptionMap", exceptionMap);
	out.writeFields();
    }

    /**
     * @throws InvalidObjectException if <code>exceptionMap</code> is 
     * <code>null</code>, contains any key which is not an instance of
     * {@link Lease}, or contains any value which in not an instance of
     * <code>Throwable</code>
     * @param in ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream in) 
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (exceptionMap == null)
	    throw new InvalidObjectException("exceptionMap is null");

	final Set mapEntries = exceptionMap.entrySet();
	for (Iterator i=mapEntries.iterator(); i.hasNext(); ) {
	    final Map.Entry entry = (Entry)i.next();
	    final Object key = entry.getKey();
	    final Object value = entry.getValue();

	    if (!(key instanceof Lease)) {
		throw new InvalidObjectException("exceptionMap contains an " +
		    "a key which is not a Lease:" + key);
	    }

	    if (!(value instanceof Throwable)) {
		throw new InvalidObjectException("exceptionMap contains an " +
		    "a value which is not a Throwable:" + value);
	    }
	}
    }

    /** 
     * @throws InvalidObjectException if called
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "LeaseMapException should always have data");
    }
    
    public String getMessage(){
        String lease = "Lease: ";
        String exception = "Exception: ";
        String ret = "\n";
        StringBuilder sb = new StringBuilder(1024);
        sb.append(super.getMessage());
        sb.append(ret);
        Iterator<Entry<Lease,Throwable>> it = exceptionMap.entrySet().iterator();
        while (it.hasNext()){
            Entry<Lease,Throwable> entry = it.next();
            sb.append(lease);
            sb.append(entry.getKey());
            sb.append(exception);
            sb.append(entry.getValue().getMessage());
            sb.append(ret);
        }
        return sb.toString();
    }
}
