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
 * @summary test constraint class readObject methods
 * @author Bob Scheifler
 * @run main/othervm ReadObject
 */
import java.io.*;
import java.security.Principal;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.*;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.constraint.BasicMethodConstraints.MethodDesc;

public class ReadObject {

    /**
     * Serialization-equivalent of ClientMinPrincipal.
     */
    public static class CMinP implements Serializable {
	private Principal[] principals;

	public CMinP(Principal[] principals) {
	    this.principals = principals;
	}
    }

    /**
     * Serialization-equivalent of ClientMinPrincipalType.
     */
    public static class CMinT implements Serializable {
	private Class[] classes;

	public CMinT(Class[] classes) {
	    this.classes = classes;
	}
    }

    /**
     * Serialization-equivalent of ClientMaxPrincipal.
     */
    public static class CMaxP implements Serializable {
	private Principal[] principals;

	public CMaxP(Principal[] principals) {
	    this.principals = principals;
	}
    }

    /**
     * Serialization-equivalent of ClientMaxPrincipalType.
     */
    public static class CMaxT implements Serializable {
	private Class[] classes;

	public CMaxT(Class[] classes) {
	    this.classes = classes;
	}
    }

    /**
     * Serialization-equivalent of ServerMinPrincipal.
     */
    public static class SMinP implements Serializable {
	private Principal[] principals;

	public SMinP(Principal[] principals) {
	    this.principals = principals;
	}
    }

    /**
     * Serialization-equivalent of DelegationAbsoluteTime.
     */
    public static class DAT implements Serializable {
	private long minStart;
	private long maxStart;
	private long minStop;
	private long maxStop;

	public DAT(long minStart,
		   long maxStart,
		   long minStop,
		   long maxStop)
	{
	    this.minStart = minStart;
	    this.maxStart = maxStart;
	    this.minStop = minStop;
	    this.maxStop = maxStop;
	}
    }

    /**
     * Serialization-equivalent of DelegationRelativeTime.
     */
    public static class DRT implements Serializable {
	private long minStart;
	private long maxStart;
	private long minStop;
	private long maxStop;

	public DRT(long minStart,
		   long maxStart,
		   long minStop,
		   long maxStop)
	{
	    this.minStart = minStart;
	    this.maxStart = maxStart;
	    this.minStop = minStop;
	    this.maxStop = maxStop;
	}
    }

    /**
     * Serialization-equivalent of ConnectionRelativeTime.
     */
    public static class CRT implements Serializable {
	private long time;

	public CRT(long time) {
	    this.time = time;
	}
    }

    /**
     * Serialization-equivalent of ConstraintAlternatives.
     */
    public static class Alt implements Serializable {
	private InvocationConstraint[] constraints;

	public Alt(InvocationConstraint[] constraints) {
	    this.constraints = constraints;
	}
    }

    /**
     * Serialization-equivalent of MethodDesc.
     */
    public static class MD implements Serializable {
	private String name;
	private Class[] types;
	private InvocationConstraints constraints;

	public MD(String name, Class[] types, InvocationConstraints constraints)
	{
	    this.name = name;
	    this.types = types;
	    this.constraints = constraints;
	}
    }

    /**
     * Serialization-equivalent of BasicMethodConstraints.
     */
    public static class BMC implements Serializable {
	private MethodDesc[] descs;

	public BMC(MethodDesc[] descs) {
	    this.descs = descs;
	}
    }

    /**
     * Stream to serialize serialization-equivalent classes as actual classes.
     */
    public static class Output extends ObjectOutputStream {
	private Map map = new HashMap();

	public Output(OutputStream out) throws IOException {
	    super(out);
	    put(CMinP.class, ClientMinPrincipal.class);
	    put(CMinT.class, ClientMinPrincipalType.class);
	    put(CMaxP.class, ClientMaxPrincipal.class);
	    put(CMaxT.class, ClientMaxPrincipalType.class);
	    put(SMinP.class, ServerMinPrincipal.class);
	    put(DAT.class, DelegationAbsoluteTime.class);
	    put(DRT.class, DelegationRelativeTime.class);
	    put(CRT.class, ConnectionRelativeTime.class);
	    put(Alt.class, ConstraintAlternatives.class);
	    put(MD.class, MethodDesc.class);
	    put(BMC.class, BasicMethodConstraints.class);
	}

	private void put(Class c1, Class c2) {
	    map.put(c1, ObjectStreamClass.lookup(c2));
	}

	protected void writeClassDescriptor(ObjectStreamClass desc)
	    throws IOException
	{
	    ObjectStreamClass ndesc =
		(ObjectStreamClass) map.get(desc.forClass());
	    if (ndesc != null) {
		desc = ndesc;
	    }
	    super.writeClassDescriptor(desc);
	}
    }

    /**
     * Check that object deserializes with InvalidObjectException.
     */
    public static void check(Object obj, String why) throws Exception {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	ObjectOutputStream out = new Output(bos);
	out.writeObject(obj);
	out.close();
	ObjectInputStream in =
	    new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
	try {
	    in.readObject();
	    throw new RuntimeException("object deserialized");
	} catch (InvalidObjectException e) {
	    if (why == null ?
		e.getMessage() != null : !why.equals(e.getMessage()))
	    {
		throw e;
	    }
	}
    }

    public static void main(String[] args) throws Exception {
	Principal p = new X500Principal("CN=joe");
	Principal[] noPrins = new Principal[0];
	Principal[] nullPrin = new Principal[]{p, null};
	Principal[] dupPrins = new Principal[]{p, p};
	Class c = X500Principal.class;
	Class[] noClasses = new Class[0];
	Class[] nullClass = new Class[]{c, null};
	Class[] dupClasses = new Class[]{c, c};
	Class[] primClass = new Class[]{int.class};
	Class[] arrayClass = new Class[]{X500Principal[].class};
	Class[] stringClass = new Class[]{String.class};
	check(new CMinP(null),
	      "cannot create constraint with no elements");
	check(new CMinP(noPrins),
	      "cannot create constraint with no elements");
	check(new CMinP(nullPrin),
	      "elements cannot be null");
	check(new CMinP(dupPrins),
	      "cannot create constraint with duplicate elements");
	check(new CMinT(null),
	      "cannot create constraint with no elements");
	check(new CMinT(noClasses),
	      "cannot create constraint with no elements");
	check(new CMinT(nullClass),
	      "elements cannot be null");
	check(new CMinT(dupClasses),
	      "cannot create constraint with redundant elements");
	check(new CMinT(primClass),
	      "invalid class");
	check(new CMinT(arrayClass),
	      "invalid class");
	check(new CMinT(stringClass),
	      "invalid class");
	check(new CMaxP(null),
	      "cannot create constraint with no elements");
	check(new CMaxP(noPrins),
	      "cannot create constraint with no elements");
	check(new CMaxP(nullPrin),
	      "elements cannot be null");
	check(new CMaxP(dupPrins),
	      "cannot create constraint with duplicate elements");
	check(new CMaxT(null),
	      "cannot create constraint with no elements");
	check(new CMaxT(noClasses),
	      "cannot create constraint with no elements");
	check(new CMaxT(nullClass),
	      "elements cannot be null");
	check(new CMaxT(dupClasses),
	      "cannot create constraint with redundant elements");
	check(new CMaxT(primClass),
	      "invalid class");
	check(new CMaxT(arrayClass),
	      "invalid class");
	check(new CMaxT(stringClass),
	      "invalid class");
	check(new SMinP(null),
	      "cannot create constraint with no elements");
	check(new SMinP(noPrins),
	      "cannot create constraint with no elements");
	check(new SMinP(nullPrin),
	      "elements cannot be null");
	check(new SMinP(dupPrins),
	      "cannot create constraint with duplicate elements");
	check(new DAT(1, 0, 3, 4),
	      "invalid times");
	check(new DAT(1, 5, 3, 4),
	      "invalid times");
	check(new DAT(1, 5, 6, 4),
	      "invalid times");
	check(new DRT(1, 0, 3, 4),
	      "invalid durations");
	check(new DRT(1, 5, 3, 4),
	      "invalid durations");
	check(new DRT(1, 5, 6, 4),
	      "invalid durations");
	check(new DRT(-4, -3, -1, 7),
	      "invalid durations");
	check(new CRT(-1),
	      "invalid duration");
	check(new Alt(null),
	      "cannot create constraint with no elements");
	check(new Alt(new InvocationConstraint[0]),
	      "cannot create constraint with less than 2 elements");
	check(new Alt(new InvocationConstraint[]{Integrity.YES}),
	      "cannot create constraint with less than 2 elements");
	check(new Alt(new InvocationConstraint[]{null, null}),
	      "elements cannot be null");
	check(new Alt(new InvocationConstraint[]{
		new ConstraintAlternatives(new InvocationConstraint[]{
		    Integrity.YES, Integrity.NO}),
		new ConstraintAlternatives(new InvocationConstraint[]{
		    Delegation.YES, Delegation.NO})}),
	     "elements cannot be ConstraintAlternatives instances");
	check(new Alt(new InvocationConstraint[]{
			new ClientMinPrincipal(p),
			new ClientMinPrincipal(p)}),
	      "cannot create constraint with duplicate elements");
	check(new MD(null, noClasses, null),
	      "cannot have types with null name");
	check(new MD("", null, null),
	      "method name cannot be empty");
	check(new MD("1foo", null, null),
	      "invalid method name");
	check(new MD("f^oo", null, null),
	      "invalid method name");
	check(new MD("*f^oo", null, null),
	      "invalid method name");
	check(new MD("f^oo*", null, null),
	      "invalid method name");
	check(new MD("*foo*", null, null),
	      "invalid method name");
	check(new MD("foo", nullClass, null),
	      "class cannot be null");
	check(new MD("foo", null, InvocationConstraints.EMPTY),
	      "constraints cannot be empty");
	check(new BMC(null), null);
	check(new BMC(new MethodDesc[0]),
	      "must have at least one descriptor");
	check(new BMC(new MethodDesc[]{
		  new MethodDesc(null),
		  new MethodDesc("foo", null)}),
	      "default descriptor must be last");
	check(new BMC(new MethodDesc[]{
		  new MethodDesc("foo*", null),
		  new MethodDesc("foo", null)}),
	      "foo* cannot precede foo");
	check(new BMC(new MethodDesc[]{
		  new MethodDesc("foo", null),
		  new MethodDesc("foo", noClasses, null)}),
	      "foo cannot precede foo()");
	check(new BMC(new MethodDesc[]{
		  new MethodDesc("*bar", null),
		  new MethodDesc("*foobar", null)}),
	      "*bar cannot precede *foobar");
    }
}
