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
 * 
 * @summary test getActivationGroups and getActivableObjects methods
 * @author Bob Scheifler
 * @library ../../../../../testlibrary
 * @build ActivationLibrary RMID
 * @build GetGroupsObjects
 * @run shell classpath.sh main/othervm/timeout=240/policy=security.policy GetGroupsObjects
 */
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.activation.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GetGroupsObjects {
    private static final int NGROUPS = 10;
    private static final int NOBJECTS = 10;

    public static void main(String[] args) throws Exception {
	RMID.removeLog();
	RMID rmid = RMID.createRMID();
	rmid.start();
	try {
	    ActivationSystem sys = ActivationGroup.getSystem();
	    Method getGroups =
		sys.getClass().getMethod("getActivationGroups", null);
	    Method getObjects =
		sys.getClass().getMethod("getActivatableObjects",
					 new Class[]{ActivationGroupID.class});
	    Map groups = new HashMap();
	    Map objects = new HashMap();
	    if (!groups.equals(getGroups.invoke(sys, null))) {
		throw new RuntimeException("groups not empty");
	    }
	    for (int i = 0; i < NGROUPS; i++) {
		ActivationGroupDesc gdesc =
		    new ActivationGroupDesc(
			"group" + i,
			"file:/group" + i,
			new MarshalledObject(new Integer(i)),
			null, null);
		ActivationGroupID gid = sys.registerGroup(gdesc);
		groups.put(gid, gdesc);
		if (!groups.equals(getGroups.invoke(sys, null))) {
		    throw new RuntimeException("groups don't match");
		}
		Map objs = new HashMap();
		objects.put(gid, objs);
		Object[] gida = new Object[]{gid};
		if (!objs.equals(getObjects.invoke(sys, gida))) {
		    throw new RuntimeException("objects not empty");
		}
		for (int j = 0; j < NOBJECTS; j++) {
		    ActivationDesc odesc =
			new ActivationDesc(
			    gid,
			    "object" + j,
			    "file:/object" + j,
			    new MarshalledObject(new Integer(j)));
		    ActivationID aid = sys.registerObject(odesc);
		    objs.put(aid, odesc);
		    if (!objs.equals(getObjects.invoke(sys, gida))) {
			throw new RuntimeException("objects don't match");
		    }
		}
	    }
	    for (Iterator giter = groups.keySet().iterator();
		 giter.hasNext(); )
	    {
		ActivationGroupID gid = (ActivationGroupID) giter.next();
		Map objs = (Map) objects.get(gid);
		Object[] gida = new Object[]{gid};
		for (Iterator oiter = objs.keySet().iterator();
		     oiter.hasNext(); )
		{
		    sys.unregisterObject((ActivationID) oiter.next());
		    oiter.remove();
		    if (!objs.equals(getObjects.invoke(sys, gida))) {
			throw new RuntimeException("objects don't match");
		    }
		}
		sys.unregisterGroup(gid);
		giter.remove();
		if (!groups.equals(getGroups.invoke(sys, null))) {
		    throw new RuntimeException("groups don't match");
		}
		try {
		    getObjects.invoke(sys, gida);
		    throw new RuntimeException("getActivatableObjects worked");
		} catch (InvocationTargetException e) {
		    if (!(e.getCause() instanceof UnknownGroupException)) {
			throw (Exception) e.getCause();
		    }
		}
	    }
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
