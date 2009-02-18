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
/* @test 
 * @summary Test activatable exports using JrmpExporter.
 * @library ../../../../testlibrary
 * @build TestActivatableExport TestActivatableExport_Stub 
 * @build CountedSocketFactory Echo 
 * @build RMID ActivationLibrary
 * @run main/othervm/policy=security.policy TestActivatableExport
 */

import java.io.File;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.util.Properties;
import net.jini.jrmp.JrmpExporter;

public class TestActivatableExport implements Echo, Runnable, Serializable {

    ActivationID id;
    CountedSocketFactory csf;
    JrmpExporter exporter;
    Remote stub;
    
    public TestActivatableExport(ActivationID id, MarshalledObject mobj)
	throws Exception
    {
	this.id = id;
	csf = (CountedSocketFactory) mobj.get();
	exporter = new JrmpExporter(id, 0, csf, csf);
	stub = exporter.export(this);
    }
    
    public int echo(int val) throws RemoteException { 
	return val;
    }
    
    public void shutdown() throws Exception {
	new Thread(this).start();
	// assume some calls have occurred
	if (csf != null && 
	    (csf.serverSocketsCreated == 0 || csf.serverSocketsAccepted == 0))
	{
	    throw new Exception("server socket factory not used");
	}
    }
    
    private Object writeReplace() {
	return stub;
    }
    
    public void run() {
	ActivationLibrary.deactivate(this, id, exporter);
    }
    
    public static void main(String[] args) throws Exception {
	RMID rmid = null;
	try {
	    RMID.removeLog();
	    rmid = RMID.createRMID();
	    rmid.start();

	    Properties props = new Properties();
	    props.put("java.security.policy",
		      TestParams.testSrc + File.separator +
		      "group.security.policy");
	    ActivationGroupDesc gdesc = new ActivationGroupDesc(props, null);
	    ActivationGroupID gid = 
		ActivationGroup.getSystem().registerGroup(gdesc);
	    
	    testActivate(gid, null);
	    testActivate(gid, new CountedSocketFactory());
	} finally {
	    try { Thread.sleep(4000); } catch (InterruptedException ex) {}
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
    
    static void testActivate(ActivationGroupID gid, CountedSocketFactory csf) 
	throws Exception 
    {
	MarshalledObject mobj = new MarshalledObject(csf);
	ActivationDesc desc = new ActivationDesc(
	    gid, TestActivatableExport.class.getName(), null, mobj);
	Echo stub = (Echo) Activatable.register(desc);
	for (int i = 0; i < 100; i++) {
	    if (stub.echo(i) != i) {
		throw new Error();
	    }
	}
	stub.shutdown();
    }
}
