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
 * @bug 4526514
 *
 * @library ../../../../../testlibrary
 * @build ActivationLibrary RMID
 * @build EnsureRestart
 * @run main/othervm/policy=security.policy/timeout=240 EnsureRestart
 */
import java.io.File;
import java.io.Serializable;
import java.rmi.*;
import java.rmi.activation.*;
import java.util.Properties;
import net.jini.export.Exporter;
import net.jini.activation.ActivationExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class EnsureRestart implements ActivateMe, Serializable {
    private Exporter exporter;
    private Remote stub;
    private ActivationID aid;

    public EnsureRestart(ActivationID id, MarshalledObject mobj)
	throws Exception
    {
	aid = id;
	if (mobj != null) {
	    Callback cb = (Callback) mobj.get();
	    cb.activated();
	}
	Exporter basicExporter =
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				  new BasicILFactory(), false, true);
	exporter = new ActivationExporter(id, basicExporter);
	stub = exporter.export(this);
    }

    private Object writeReplace() {
	return stub;
    }

    public void ping() {
    }

    public void justGoAway() {
	System.exit(0);
    }

    public void goInactive() {
	new Thread() {
	    public void run() {
		ActivationLibrary.deactivate(EnsureRestart.this, aid,
					     exporter);
	    }
	}.start();
    }

    static class CallbackImpl implements Callback {
	int count = 0;

	public synchronized void activated() {
	    count++;
	    notifyAll();
	}
    }

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	RMID.removeLog();
	RMID rmid = RMID.createRMID();
	rmid.start();
	try {
	    ActivationSystem sys = ActivationGroup.getSystem();
	    CallbackImpl cb = new CallbackImpl();
	    BasicJeriExporter basicExporter =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory(), true, true);
	    MarshalledObject mo =
		new MarshalledObject(basicExporter.export(cb));
	    Properties props = new Properties();
	    props.put("java.security.policy",
		      TestParams.testSrc + File.separator +
		      "group.security.policy");
	    ActivationGroupID gid =
		sys.registerGroup(new ActivationGroupDesc(props, null));
	    ActivationID aid1 =
		sys.registerObject(new ActivationDesc(gid, "EnsureRestart",
						      null, null, false));
	    ActivateMe obj1 = (ActivateMe) aid1.activate(false);
	    ActivationID aid2 =
		sys.registerObject(new ActivationDesc(gid, "EnsureRestart",
						      null, mo, true));
	    synchronized (cb) {
		int expect = cb.count + 1;
		try {
		    obj1.justGoAway();
		    throw new RuntimeException("justGoAway call succeeded");
		} catch (RemoteException e) {
		}
		cb.wait(30000);
		if (cb.count != expect) {
		    throw new RuntimeException("obj2 did not restart");
		}
	    }
	    ActivateMe obj2 = (ActivateMe) aid2.activate(false);
	    obj2.ping();
	    sys.unregisterObject(aid2);
	    try {
		obj1.justGoAway();
		throw new RuntimeException("justGoAway call succeeded");
	    } catch (RemoteException e) {
	    }
	    try {
		obj2.ping();
		throw new RuntimeException("obj2 still exists");
	    } catch (NoSuchObjectException e) {
	    }
	    obj1.ping();
	    sys.setActivationDesc(aid1,
				  new ActivationDesc(gid, "EnsureRestart",
						     null, mo, true));
	    synchronized (cb) {
		int expect = cb.count + 1;
		try {
		    obj1.justGoAway();
		    throw new RuntimeException("justGoAway call succeeded");
		} catch (RemoteException e) {
		}
		cb.wait(30000);
		if (cb.count != expect) {
		    throw new RuntimeException("obj1 did not restart");
		}
	    }
	    synchronized (cb) {
		int expect = cb.count;
		obj1.goInactive();
		cb.wait(30000);
		if (cb.count != expect) {
		    throw new RuntimeException("obj1 restarted");
		}
	    }
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
