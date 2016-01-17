/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
	this(arg.get("instance", null, MarshalledInstance.class));
    }
    
    Object readResolve() throws ObjectStreamException {
	return instance.convertToMarshalledObject();
    }
}
