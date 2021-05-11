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

package org.apache.river.api.io;

import org.apache.river.api.io.AtomicSerial.SerialForm;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupDesc.CommandEnvironment;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Not sure if it's a good idea to make this serializable, it is really up to
 * the user to check the arguments it contains are legal.
 *
 * @author peter
 */
@Serializer(replaceObType = ActivationGroupDesc.class)
@AtomicSerial
class ActivationGroupDescSerializer implements Serializable, Resolve {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	serialForm();
    
    private static final String CLASSNAME = "className";
    private static final String LOCATION = "location";
    private static final String DATA = "data";
    private static final String PROP = "properties";
    private static final String CMD_ENV = "cmdEnv";
    
    /**
     * AtomicSerial declared serial arguments.
     * @return 
     */
    public static final SerialForm [] serialForm(){
        return new SerialForm [] {
            new SerialForm(CLASSNAME, String.class),
	    new SerialForm(LOCATION, String.class),
	    new SerialForm(DATA, MarshalledObject.class),
	    new SerialForm(PROP, Properties.class),
	    new SerialForm(CMD_ENV, CommandEnvironment.class)
            
        };
    }
    
    public static void serialize(AtomicSerial.PutArg args, ActivationGroupDescSerializer obj) throws IOException {
        putArgs(args, obj);
        args.writeArgs();
    }
    
    private static void putArgs(ObjectOutputStream.PutField pf, ActivationGroupDescSerializer obj) {
        pf.put(CLASSNAME, obj.className);
	pf.put(LOCATION, obj.location);
	pf.put(DATA, obj.data);
	pf.put(PROP, obj.properties);
	pf.put(CMD_ENV, obj.cmdEnv);
    }
    
    final String className;
    final String location;
    final MarshalledObject data;
    final Properties properties;
    final CommandEnvironment cmdEnv;
    final ActivationGroupDesc actGroupDesc;
    
    ActivationGroupDescSerializer(ActivationGroupDesc agd){
	className = agd.getClassName();
	location = agd.getLocation();
	data = agd.getData();
	properties = agd.getPropertyOverrides();
	cmdEnv = agd.getCommandEnvironment();
	actGroupDesc = agd;
    }
    
    /**
     * TODO: Parsing className, location, permission and restrictions
     * @param arg
     * @throws IOException 
     */
    ActivationGroupDescSerializer(GetArg arg) throws IOException, ClassNotFoundException {
	this( 
	    new ActivationGroupDesc(
	    // A null group class name indicates the system's default ActivationGroup implementation.
	    // Is null safe?
	    arg.get(CLASSNAME, null, String.class), 
	    arg.get(LOCATION, null, String.class),
	    arg.get(DATA, null, MarshalledObject.class), 
            (Properties) Valid.copyMap(
                    (Map) arg.get(PROP, null, Properties.class),
                    (Map) new Properties(),
                    String.class, // Key type check
                    String.class), // Value type check
	    arg.get(CMD_ENV, null, CommandEnvironment.class))
	);
    }
    
    @Override
    public Object readResolve() throws ObjectStreamException {
	if (actGroupDesc != null) return actGroupDesc;
	return new ActivationGroupDesc(className, location, data, properties, cmdEnv);
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	putArgs(out.putFields(), this);
	out.writeFields();
    }
    
    @AtomicSerial
    static class CmdEnv implements Serializable {
	private static final long serialVersionUID = 1L;
    
	/**
	 * By defining serial persistent fields, we don't need to use transient fields.
	 * All fields can be final and this object becomes immutable.
	 */
	private static final ObjectStreamField[] serialPersistentFields = 
	    {
		new ObjectStreamField("cmdPath", String.class),
		new ObjectStreamField("argv", String[].class)
	    };
	
	private final String cmdPath;
	private final String[] argv;
	private final CommandEnvironment env;
		
	
	CmdEnv(CommandEnvironment env){
	    cmdPath = env.getCommandPath();
	    argv = env.getCommandOptions();
	    this.env = env;
	}
	
	/**
	 * TODO: Parsing command path, filters, permission and restrictions
	 * @param arg
	 * @throws IOException 
	 */
	CmdEnv(GetArg arg) throws IOException, ClassNotFoundException{
	    this( new CommandEnvironment(
		    arg.get("cmdPath", null, String.class),
		    arg.get("argv", null, String[].class)
	    ));
	}
	
	Object readResolve() throws ObjectStreamException {
	    if (env != null) return env;
	    return new CommandEnvironment(cmdPath, argv);
	}

	/**
	 * @serialData 
	 * @param out
	 * @throws IOException 
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
	    ObjectOutputStream.PutField pf = out.putFields();
	    pf.put("cmdPath", cmdPath);
	    pf.put("argv", argv);
	    out.writeFields();
	}
	
    }
    
    
}
