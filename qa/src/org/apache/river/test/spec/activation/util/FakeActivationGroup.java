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
package org.apache.river.test.spec.activation.util;
import net.jini.activation.ActivationGroup;
import net.jini.export.Exporter;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationID;
import net.jini.activation.arg.UnknownObjectException;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationDesc;
import net.jini.activation.arg.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * A fake implementation of the <code>ActivationGroup</code>
 * abstract class for test purposes.
 */
public class FakeActivationGroup extends ActivationGroup {

    ActivationGroupID agid;
    
    public static Logger logger;
    public static void setLogger(Logger logger){
        FakeActivationGroup.logger = logger;
    }
    
    public static boolean inactiveObjectTouch = false;
    public static void resetInactiveObjectTouch(){
        inactiveObjectTouch = false;
    }
    public static boolean getInactiveObjectTouch(){
        return inactiveObjectTouch;
    }

    public static boolean inactiveObjectReturn = true;
    public static void setInactiveObjectReturn(boolean inactiveObjectReturn){
        FakeActivationGroup.inactiveObjectReturn = inactiveObjectReturn;
    }

    public static Exporter inactiveObjectExporter = null;
    public static Exporter getInactiveObjectExporter(){
        return FakeActivationGroup.inactiveObjectExporter;
    }
    
    public static ActivationID inactiveObjectActivationID = null;
    public static ActivationID getInactiveObjectActivationID(){
        return FakeActivationGroup.inactiveObjectActivationID;
    }
    
    public FakeActivationGroup(ActivationGroupID agid) 
	throws RemoteException 
    {
        super(agid);
        this.agid = agid;
        logger.log(Level.FINEST,
                "FakeActivationGroup.Constructor(" + agid + ")");
    }
    
    public FakeActivationGroup(ActivationGroupID agid, String[] data)
	throws ActivationException, RemoteException
    {
	super(agid);
        this.agid = agid;
        logger.log(Level.FINEST,
                "FakeActivationGroup.Constructor(" + agid + ", " + data + ")");
    }

    @Override
    public boolean inactiveObject(ActivationID id, Exporter exporter)
	throws ActivationException, RemoteException {
        inactiveObjectActivationID = id;
        inactiveObjectExporter = exporter;
        inactiveObjectTouch = true;
	return inactiveObjectReturn;
    }

    @Override
    public void activeObject(ActivationID id, Remote obj)
	throws ActivationException, UnknownObjectException, RemoteException{
    }

    public MarshalledObject newInstance(ActivationID id, ActivationDesc desc)
	throws ActivationException, RemoteException{
	return null;
    }

}
