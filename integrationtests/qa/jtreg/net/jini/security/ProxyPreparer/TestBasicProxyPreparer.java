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
 * @summary Tests the BasicProxyPreparer class
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test RS AddConstraintsProxyPreparer
 *	  AllPrincipalsGrantProxyPreparer LocalClassLoaderVerifyProxyPreparer
 * @run main/othervm/policy=policy TestBasicProxyPreparer
 */

import java.io.*;
import java.rmi.RemoteException;
import java.security.AccessControlException;
import java.security.Permission;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.PropertyPermission;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.policy.DynamicPolicyProvider;

public abstract class TestBasicProxyPreparer extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".") + File.separator;
    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    static abstract class BooleanEnum {
	final boolean value;
	BooleanEnum(boolean value) { this.value = value; }
    }
    static class Args extends BooleanEnum {
	static final Args YES = new Args(true);
	static final Args NO = new Args(false);
	private Args(boolean value) { super(value); }
    }
    static class Verify extends BooleanEnum {
	static final Verify YES = new Verify(true);
	static final Verify NO = new Verify(false);
	private Verify(boolean value) { super(value); }
    }
    static class ConstraintsSpecified extends BooleanEnum {
	static final ConstraintsSpecified YES = new ConstraintsSpecified(true);
	static final ConstraintsSpecified NO = new ConstraintsSpecified(false);
	private ConstraintsSpecified(boolean value) { super(value); }
    }

    static class UntrustedConstraint implements InvocationConstraint {
	public InvocationConstraint reduceBy(InvocationConstraint sc) {
	    if (sc instanceof UntrustedConstraint) {
		return this;
	    } else {
		return null;
	    }
	}
	public boolean equals(Object o) {
	    throw new FailedException(
		"Don't call equals on untrusted object!");
	}
    }

    static class TestPreparer extends BasicProxyPreparer {
	private transient boolean preparing;
	private transient Object proxy;

	TestPreparer() {
	    check(false, false, null, EMPTY_PERMISSIONS);
	}
	
	TestPreparer(boolean verify, Permission[] permissions) {
	    super(verify, permissions);
	    check(verify, false, null,
		  permissions != null ? permissions : EMPTY_PERMISSIONS);
	}

	TestPreparer(boolean verify,
		     MethodConstraints methodConstraints,
		     Permission[] permissions)
	{
	    super(verify, methodConstraints, permissions);
	    check(verify, true, methodConstraints,
		  permissions != null ? permissions : EMPTY_PERMISSIONS);
	}

	private void check(boolean verify, boolean methodConstraintsSpecified,
			   MethodConstraints methodConstraints,
			   Permission[] permissions)
	{
	    if (this.verify != verify) {
		throw new Test.FailedException("verify should be " + verify);
	    } else if (this.methodConstraintsSpecified !=
		       methodConstraintsSpecified)
	    {
		throw new Test.FailedException(
		    "methodConstraintsSpecified should be " +
		    methodConstraintsSpecified);
	    } else if (!safeEquals(this.methodConstraints, methodConstraints)) {
		throw new Test.FailedException(
		    "Wrong methodConstraints: " + this.methodConstraints);
	    } else if (this.permissions == null ||
		       !Arrays.equals(this.permissions, permissions))
	    {
		throw new Test.FailedException(
		    "Wrong permissions: " +
		    UnitTestUtilities.toString(this.permissions));
	    }
	}

	protected MethodConstraints getMethodConstraints(Object proxy) {
	    try {
		MethodConstraints result = super.getMethodConstraints(proxy);
		if (methodConstraintsSpecified) {
		    if (!safeEquals(result, methodConstraints)) {
			throw new Test.FailedException(
			    "Wrong methodConstraints: " + result);
		    }
		} else if (proxy instanceof RemoteMethodControl) {
		    MethodConstraints proxyConstraints =
			((RemoteMethodControl) proxy).getConstraints();
		    if (!safeEquals(result, proxyConstraints)) {
			throw new Test.FailedException(
			    "Wrong methodConstraints: " + result);
		    }
		} else if (result != null) {
		    throw new Test.FailedException(
			"Wrong methodConstraints: " + result);
		}
		return result;
	    } catch (Exception e) {
		throw (Test.FailedException) new Test.FailedException(
		    "Unexpected exception").initCause(e);
	    }
	}				    

	protected Permission[] getPermissions(Object proxy) {
	    try {
		Permission[] result = super.getPermissions(proxy);
		if (!safeEquals(result, permissions)) {
		    throw new Test.FailedException(
			"Wrong permissions: " +
			UnitTestUtilities.toString(result));
		}
		return result;
	    } catch (Exception e) {
		throw (Test.FailedException) new Test.FailedException(
		    "Unexpected exception").initCause(e);
	    }
	}

	public synchronized Object prepareProxy(Object proxy)
	    throws RemoteException
	{
	    if (proxy == null) {
		try {
		    super.verify(null);
		    throw new Test.FailedException(
			"No NullPointerException for verify(null)");
		} catch (NullPointerException e) {
		    debugPrint(30, e.toString());
		}
		try {
		    super.grant(null);
		    throw new Test.FailedException(
			"No NullPointerException for grant(null)");
		} catch (NullPointerException e) {
		    debugPrint(30, e.toString());
		}
		try {
		    super.grant(null);
		    throw new Test.FailedException(
			"No NullPointerException for " +
			"setConstraints(null);");
		} catch (NullPointerException e) {
		    debugPrint(30, e.toString());
		}
		super.getPermissions(null);
		super.getMethodConstraints(null);
	    }
	    preparing = true;
	    this.proxy = proxy;
	    try {
		return super.prepareProxy(proxy);
	    } finally {
		preparing = false;
	    }
	}

	protected void verify(Object proxy) throws RemoteException {
	    if (!preparing) {
		throw new Test.FailedException("Not preparing");
	    } else if (proxy == null || proxy != this.proxy) {
		throw new Test.FailedException("Wrong proxy");
	    }
	    super.verify(proxy);
	}

	protected void grant(Object proxy) {
	    if (!preparing) {
		throw new Test.FailedException("Not preparing");
	    } else if (proxy == null || proxy != this.proxy) {
		throw new Test.FailedException("Wrong proxy");
	    }
	    super.grant(proxy);
	}

	protected Object setConstraints(Object proxy) {
	    if (!preparing) {
		throw new Test.FailedException("Not preparing");
	    } else if (proxy == null || proxy != this.proxy) {
		throw new Test.FailedException("Wrong proxy");
	    }
	    return super.setConstraints(proxy);
	}
    }

    static final MethodConstraints NO_CONSTRAINTS = null;
    static final MethodConstraints EMPTY_CONSTRAINTS =
	new BasicMethodConstraints(InvocationConstraints.EMPTY);
    static final MethodConstraints INTEGRITY =
	new BasicMethodConstraints(
	    new InvocationConstraints(Integrity.YES, null));
    static final MethodConstraints NO_INTEGRITY =
	new BasicMethodConstraints(
	    new InvocationConstraints(Integrity.NO, null));
    static final MethodConstraints CONFIDENTIALITY =
	new BasicMethodConstraints(
	    new InvocationConstraints(Confidentiality.YES, null));
    static final MethodConstraints UNTRUSTED =
	new BasicMethodConstraints(
	    new InvocationConstraints(new UntrustedConstraint(), null));

    static final Permission[] NO_PERMISSIONS = null;
    static final Permission[] EMPTY_PERMISSIONS = { };
    static final Permission[] READ_PERMISSION =
	new Permission[] { new PropertyPermission("foo", "read") };
    static final Permission[] WRITE_PERMISSION =
	new Permission[] { new PropertyPermission("foo", "write") };

    static Object[] tests = {
	TestConstructorThrows.localtests,
	TestReadObject.localtests,
	TestEquals.localtests,
	TestToString.localtests,
	TestPrepare.localtests
    };

    final boolean noArgs;
    final boolean verify;
    final boolean methodConstraintsSpecified;
    final MethodConstraints methodConstraints;
    final Permission[] permissions;

    public static void main(String[] args) {
	test(tests);
    }

    TestBasicProxyPreparer(String name,
			   Args args,
			   Verify verify,
			   ConstraintsSpecified methodConstraintsSpecified,
			   MethodConstraints methodConstraints,
			   Permission[] permissions,
			   Object shouldBe)
    {
	super(name +
	      (!args.value ? ""
	       : ((name == "" ? "" : ", ") +
		  "verify = " + verify.value + 
		  (methodConstraintsSpecified.value
		   ? ", methodConstraints = " + methodConstraints : "") +
		  ", permissions = " + toString(permissions))),
	      shouldBe);
	this.noArgs = !args.value;
	this.verify = verify.value;
	this.methodConstraintsSpecified = methodConstraintsSpecified.value;
	this.methodConstraints = methodConstraints;
	this.permissions = permissions;
    }

    public static class TestConstructorThrows extends TestBasicProxyPreparer {
	static Test[] localtests = {
	    new TestConstructorThrows(
		Verify.YES,
		new Permission[] {
		    new PropertyPermission("foo", "read"), null
		},
		NullPointerException.class),
	    new TestConstructorThrows(
		Verify.NO, INTEGRITY, new Permission[] { null },
		NullPointerException.class)
	};

	public static void main(String[] tests) {
	    test(localtests);
	}
	    
	TestConstructorThrows(Verify verify,
			      Permission[] permissions,
			      Object shouldBe)
	{
	    super("", Args.YES, verify, ConstraintsSpecified.NO, null,
		  permissions, shouldBe);
	}

	TestConstructorThrows(Verify verify,
			      MethodConstraints methodConstraints,
			      Permission[] permissions,
			      Object shouldBe)
	{
	    super("", Args.YES, verify, ConstraintsSpecified.YES,
		  methodConstraints, permissions, shouldBe);
	}

	public Object run() {
	    try {
		if (methodConstraintsSpecified) {
		    return new BasicProxyPreparer(
			verify, methodConstraints, permissions);
		} else {
		    return new BasicProxyPreparer(verify, permissions);
		}
	    } catch (Exception e) {
		return e;
	    }
	}

	public void check(Object result) throws Exception {
	    Object compareTo = getCompareTo();
	    if (compareTo instanceof Class) {
		if (result == null || compareTo != result.getClass()) {
		    throw new FailedException(
			"Should be of type " + compareTo);
		}
	    } else {
		super.check(result);
	    }
	}	    
    }

    /** Test that the readObject method throws NullPointerException. */
    public static class TestReadObject extends BasicTest {
	/**
	 * Define a class with the same data format as BasicProxyPreparer, but
	 * with non-final fields, to use to create illegal serialization data.
	 */
	static class Data extends UnitTestUtilities
	    implements ProxyPreparer, Serializable
	{
	    static final long serialVersionUID = 4439691869768577046L;
	    static final ObjectStreamField[] serialPersistentFields = {
		new ObjectStreamField("verify", boolean.class),
		new ObjectStreamField("methodConstraintsSpecified",
				      boolean.class),
		new ObjectStreamField("methodConstraints",
				      MethodConstraints.class),
		new ObjectStreamField("permissions", Permission[].class, true)
		};
	    boolean verify;
	    boolean methodConstraintsSpecified;
	    MethodConstraints methodConstraints;
	    Permission[] permissions;

	    Data(boolean verify,
		 boolean methodConstraintsSpecified,
		 MethodConstraints methodConstraints,
		 Permission[] permissions)
	    {
		this.verify = verify;
		this.methodConstraintsSpecified = methodConstraintsSpecified;
		this.methodConstraints = methodConstraints;
		this.permissions = permissions;
	    }
	    public Object prepareProxy(Object proxy) { return null; }
	    public String toString() {
		StringBuffer sb = new StringBuffer("Data[");
		sb.append("\n  verify=").append(verify);
		sb.append("\n  methodConstraintsSpecified=");
		sb.append(methodConstraintsSpecified);
		sb.append("\n  methodConstraints=").append(methodConstraints);
		sb.append("\n  permissions=").append(toString(permissions));
		sb.append("\n]");
		return sb.toString();
	    }
	}
	static class Foo1 implements Serializable {
	    static final long serialVersionUID = 0;
	}
	static class Foo2 extends BasicProxyPreparer implements Serializable {
	    static final long serialVersionUID = 0;	    
	}
	static Test[] localtests = {
	    new TestReadObject(
		new Data(false, false, null, null), BasicProxyPreparer.class,
		InvalidObjectException.class),
	    new TestReadObject(
		new Data(false, false, null, new Permission[] { null }),
		BasicProxyPreparer.class,
		InvalidObjectException.class),
	    new TestReadObject(
		new Data(false, false,
			 new BasicMethodConstraints(
			     new InvocationConstraints(Integrity.YES, null)),
			 new Permission[0]),
		BasicProxyPreparer.class,
		InvalidObjectException.class),
	    new TestReadObject(
		new Foo1(), Foo2.class, InvalidObjectException.class)
	};

	final Object data;
	final Class targetClass;

	TestReadObject(Object data, Class targetClass, Object shouldBe) {
	    super(String.valueOf(data) + ", " + targetClass.getName(),
		  shouldBe);
	    this.data = data;
	    this.targetClass = targetClass;
	}

	public static void main(String[] args) {
	    test(localtests);
	}

	public Object run() throws IOException {
	    ObjectInputStream in =
		writeReplaceType(data, data.getClass(), targetClass);
	    try {
		return in.readObject();
	    } catch (Exception e) {
		return e;
	    } finally {
		in.close();
	    }
	}

	/**
	 * Returns an object input stream that contains the specified object,
	 * but represents instances of oldType as newType.
	 */
	static ObjectInputStream writeReplaceType(Object object,
						  final Class oldType,
						  final Class newType)
	    throws IOException
	{
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream out =
		new ObjectOutputStream(bos) {
		    protected void writeClassDescriptor(ObjectStreamClass desc)
			throws IOException
		    {
			if (desc.forClass() == oldType) {
			    desc = ObjectStreamClass.lookup(newType);
			}
			super.writeClassDescriptor(desc);
		    }
		};
	    out.writeObject(object);
	    out.flush();
	    ObjectInputStream in =
		new ObjectInputStream(
		    new ByteArrayInputStream(
			bos.toByteArray()));
	    out.close();
	    return in;
	}

	public void check(Object result) {
	    Object compareTo = getCompareTo();
	    if (result == null || compareTo != result.getClass()) {
		throw new FailedException("Should be of type " + compareTo);
	    }
	}
    }

    /** Test the equals method. */
    public static class TestEquals extends BasicTest {
	static class I {
	    int i;
	    Object item;
	    /*
	     * Creates an object to test equals on.  If i is greater than 0,
	     * all items with the same value of i should be equal, otherwise
	     * the item is only equal to itself.
	     */
	    I(int i, Object item) { this.i = i; this.item = item; }
	};
	static I[] items = {
	    new I(-1, null),
	    new I(-1, new Integer(3)),
	    new I(-1,
		  new BasicProxyPreparer() {
		      public String toString() { return getClass().getName(); }
		  }),
	    new I( 2, new BasicProxyPreparer()),
	    new I( 2, bpp(Verify.NO,  EMPTY_PERMISSIONS)),
	    new I( 2, bpp(Verify.NO,  NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.NO,  READ_PERMISSION)),
	    new I(-1, bpp(Verify.NO,  WRITE_PERMISSION)),
	    new I( 3, bpp(Verify.YES, EMPTY_PERMISSIONS)),
	    new I( 3, bpp(Verify.YES, NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.YES, READ_PERMISSION)),
	    new I(-1, bpp(Verify.YES, WRITE_PERMISSION)),
	    new I( 4, bpp(Verify.NO,  INTEGRITY,      EMPTY_PERMISSIONS)),
	    new I( 4, bpp(Verify.NO,  INTEGRITY,      NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.NO,  INTEGRITY,      READ_PERMISSION)),
	    new I(-1, bpp(Verify.NO,  INTEGRITY,      WRITE_PERMISSION)),
	    new I( 5, bpp(Verify.NO,  NO_CONSTRAINTS, EMPTY_PERMISSIONS)),
	    new I( 5, bpp(Verify.NO,  NO_CONSTRAINTS, NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.NO,  NO_CONSTRAINTS, READ_PERMISSION)),
	    new I(-1, bpp(Verify.NO,  NO_CONSTRAINTS, WRITE_PERMISSION)),
	    new I( 6, bpp(Verify.NO,  NO_INTEGRITY,   EMPTY_PERMISSIONS)),
	    new I( 6, bpp(Verify.NO,  NO_INTEGRITY,   NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.NO,  NO_INTEGRITY,   READ_PERMISSION)),
	    new I(-1, bpp(Verify.NO,  NO_INTEGRITY,   WRITE_PERMISSION)),
	    new I( 7, bpp(Verify.YES, INTEGRITY,      EMPTY_PERMISSIONS)),
	    new I( 7, bpp(Verify.YES, INTEGRITY,      NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.YES, INTEGRITY,      READ_PERMISSION)),
	    new I(-1, bpp(Verify.YES, INTEGRITY,      WRITE_PERMISSION)),
	    new I( 8, bpp(Verify.YES, NO_CONSTRAINTS, EMPTY_PERMISSIONS)),
	    new I( 8, bpp(Verify.YES, NO_CONSTRAINTS, NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.YES, NO_CONSTRAINTS, READ_PERMISSION)),
	    new I(-1, bpp(Verify.YES, NO_CONSTRAINTS, WRITE_PERMISSION)),
	    new I( 9, bpp(Verify.YES, NO_INTEGRITY,   EMPTY_PERMISSIONS)),
	    new I( 9, bpp(Verify.YES, NO_INTEGRITY,   NO_PERMISSIONS)),
	    new I(-1, bpp(Verify.YES, NO_INTEGRITY,   READ_PERMISSION)),
	    new I(-1, bpp(Verify.YES, NO_INTEGRITY,   WRITE_PERMISSION)),
	    new I(10, bpp(Verify.YES, NO_INTEGRITY,
			  new Permission[] {
			      new PropertyPermission("foo", "read"),
			      new PropertyPermission("foo", "read"),
			      new PropertyPermission("foo", "write") })),
	    new I(10, bpp(Verify.YES, NO_INTEGRITY,
			  new Permission[] {
			      new PropertyPermission("foo", "read"),
			      new PropertyPermission("foo", "write"),
			      new PropertyPermission("foo", "read") })),
	    new I(11, bpp(Verify.YES, NO_INTEGRITY,
			  new Permission[] {
			      new PropertyPermission("foo", "read"),
			      new PropertyPermission("foo", "write"),
			      new PropertyPermission("foo", "write") }))
	};

	static Collection localtests = new ArrayList();
	static {
	    for (int i = items.length; --i >= 0; ) {
		for (int j = items.length; --j >= 0; ) {
		    Object x = items[i].item;
		    Object y = items[j].item;
		    boolean result =
			i == j || items[i].i > 0 && items[i].i == items[j].i;
		    localtests.add(new TestEquals("", x, y, result));
		    if (x instanceof BasicProxyPreparer) {
			try {
			    x = serialized(x);
			} catch (IOException e) {
			    throw unexpectedException(e);
			}
			localtests.add(
			    new TestEquals("Serialized ", x, y, result));
		    }
		}
	    }
	}

	final Object x;
	final Object y;

	public static void main(String[] args) {
	    test(localtests);
	}

	static Object bpp(Verify verify, Permission[] permissions) {
	    return new BasicProxyPreparer(verify.value, permissions);
	}
	static Object bpp(Verify verify,
			  MethodConstraints methodConstraints,
			  Permission[] permissions)
	{
	    return new BasicProxyPreparer(
		verify.value, methodConstraints, permissions);
	}

	TestEquals(String name, Object x, Object y, boolean result)
	{
	    super(name + x + ", " + y, Boolean.valueOf(result));
	    this.x = x;
	    this.y = y;
	}

	public Object run() {
	    return Boolean.valueOf(x == null ? y == null : x.equals(y));
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    super.check(Boolean.valueOf(
		y == null ? x == null : y.equals(x)));
	    if (Boolean.TRUE.equals(result)) {
		int h1 = x == null ? 0 : x.hashCode();
		int h2 = y == null ? 0 : y.hashCode();
		if (h1 != h2) {
		    throw new FailedException("Hash codes differ");
		}
	    }
	}
    }

    /** Test the toString method. */
    public static class TestToString extends BasicTest {
	static Permission[] READ_AND_WRITE_PERMISSION = {
	    new PropertyPermission("foo", "read"),
	    new PropertyPermission("foo", "write")
	};

	static Test[] localtests = {
	    test(new BasicProxyPreparer(), "BasicProxyPreparer[]"),
	    test(new BasicProxyPreparer(false, READ_PERMISSION),
		 "BasicProxyPreparer[{" + READ_PERMISSION[0] + "}]"),
	    test(new BasicProxyPreparer(true, null),
		 "BasicProxyPreparer[verify]"),
	    test(new BasicProxyPreparer(true, READ_AND_WRITE_PERMISSION),
		 "BasicProxyPreparer[verify, {" +
		 READ_PERMISSION[0] + ", " + WRITE_PERMISSION[0] + "}]"),
	    test(new BasicProxyPreparer(false, INTEGRITY, null),
		 "BasicProxyPreparer[" + INTEGRITY + "]"),
	    test(new BasicProxyPreparer(false, CONFIDENTIALITY,
					READ_PERMISSION),
		 "BasicProxyPreparer[" + CONFIDENTIALITY + ", {" +
		 READ_PERMISSION[0] + "}]"),
	    test(new BasicProxyPreparer(true, INTEGRITY, READ_PERMISSION),
		 "BasicProxyPreparer[verify, " + INTEGRITY + ", {" +
		 READ_PERMISSION[0] + "}]"),
	    test(new AddConstraintsProxyPreparer(true, INTEGRITY, null),
		 "AddConstraintsProxyPreparer[verify, " + INTEGRITY + "]")
	};

	static Test test(BasicProxyPreparer preparer, String shouldBe) {
	    return new TestToString(preparer, shouldBe);
	}
	
	TestToString(BasicProxyPreparer preparer, String shouldBe) {
	    super(preparer.toString(), shouldBe);
	}

	public Object run() {
	    return name();
	}
    }

    /** Test the prepareProxy method. */
    public static class TestPrepare extends TestBasicProxyPreparer {
	static class Dynamic extends BooleanEnum {
	    static final Dynamic YES = new Dynamic(true);
	    static final Dynamic NO = new Dynamic(false);
	    private Dynamic(boolean value) { super(value); }
	}

	static int i = 0;

	static Policy dynamicPolicy;

	static Test[] localtests = {

	    /* NullPointerException */

	    new TestPrepare(null, NullPointerException.class),
	    new TestPrepare(Verify.NO, NO_PERMISSIONS, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.YES, NO_PERMISSIONS, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.NO, WRITE_PERMISSION, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.YES, WRITE_PERMISSION, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.NO, NO_CONSTRAINTS, NO_PERMISSIONS, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.YES, NO_CONSTRAINTS, NO_PERMISSIONS, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.NO, INTEGRITY, NO_PERMISSIONS, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.YES, INTEGRITY, NO_PERMISSIONS, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.NO, INTEGRITY, WRITE_PERMISSION, null,
			    NullPointerException.class),
	    new TestPrepare(Verify.YES, INTEGRITY, WRITE_PERMISSION, null,
			    NullPointerException.class),

	    /* Permit non-secure */

	    new TestPrepare(new Integer(3), new Integer(3)),
	    new TestPrepare(new RS(++i, null), new RS(i, null)),
	    new TestPrepare(Verify.NO, NO_PERMISSIONS, new Integer(3),
			    new Integer(3)),
	    new TestPrepare(Verify.NO, READ_PERMISSION, new RS(++i, null),
			    new RS(i, null)),
	    new TestPrepare(Verify.NO, NO_PERMISSIONS, new RS(++i, UNTRUSTED),
			    new RS(i, UNTRUSTED)),
	    new TestPrepare(Verify.NO, INTEGRITY, READ_PERMISSION,
			    new RS(++i, UNTRUSTED), new RS(i, INTEGRITY)),

	    /* RemoteException */

	    new TestPrepare(Verify.YES, READ_PERMISSION,
			    new RS.VerifyThrows(++i, null),
			    RemoteException.class),
	    new TestPrepare(Verify.YES, INTEGRITY, READ_PERMISSION,
			    new RS.VerifyThrows(++i, UNTRUSTED),
			    RemoteException.class),
	    new TestPrepare(new RS.VerifyThrows(++i, null),
			    new RS.VerifyThrows(i, null)),
	    new TestPrepare(Verify.NO, INTEGRITY, NO_PERMISSIONS,
			    new RS.VerifyThrows(++i, null),
			    new RS.VerifyThrows(i, INTEGRITY)),

	    /* SecurityException */

	    new TestPrepare(Verify.YES, NO_PERMISSIONS, new Integer(3),
			    SecurityException.class),
	    new TestPrepare(Verify.YES, WRITE_PERMISSION, new RS(++i, null),
			    SecurityException.class),
	    new TestPrepare(Verify.NO, NO_CONSTRAINTS, NO_PERMISSIONS,
			    new Integer(3), SecurityException.class),
	    new TestPrepare(Verify.YES, INTEGRITY, NO_PERMISSIONS,
			    new Integer(3), SecurityException.class),
	    new TestPrepare(Verify.YES, INTEGRITY, WRITE_PERMISSION,
			    new RS(++i, null), SecurityException.class),
	    new TestPrepare(Args.YES, Verify.YES, ConstraintsSpecified.NO,
			    null, WRITE_PERMISSION, Dynamic.NO,
			    new RS.Trusted(++i, null), SecurityException.class),

	    /* AccessControlException */

	    new TestPrepare(Verify.NO, WRITE_PERMISSION, new Integer(3),
			    AccessControlException.class),
	    new TestPrepare(Verify.YES, WRITE_PERMISSION,
			    new RS.Trusted(++i, null),
			    AccessControlException.class),
	    new TestPrepare(Verify.NO, INTEGRITY, WRITE_PERMISSION,
			    new Integer(3), AccessControlException.class),
	    new TestPrepare(Verify.NO, INTEGRITY, WRITE_PERMISSION,
			    new RS(++i, null), AccessControlException.class),
	    new TestPrepare(Verify.YES, INTEGRITY, WRITE_PERMISSION,
			    new RS.Trusted(++i, UNTRUSTED),
			    AccessControlException.class),

	    /* Set constraints */

	    new TestPrepare(Verify.NO, NO_CONSTRAINTS, NO_PERMISSIONS,
			    new RS(++i, null), new RS(i, null)),
	    new TestPrepare(Verify.NO, NO_CONSTRAINTS, NO_PERMISSIONS,
			    new RS(++i, INTEGRITY), new RS(i, null)),
	    new TestPrepare(Verify.NO, EMPTY_CONSTRAINTS, NO_PERMISSIONS,
			    new RS(++i, null), new RS(i, EMPTY_CONSTRAINTS)),
	    new TestPrepare(Verify.NO, EMPTY_CONSTRAINTS, NO_PERMISSIONS,
			    new RS(++i, EMPTY_CONSTRAINTS),
			    new RS(i, EMPTY_CONSTRAINTS)),
	    new TestPrepare(Verify.NO, EMPTY_CONSTRAINTS, NO_PERMISSIONS,
			    new RS(++i, UNTRUSTED),
			    new RS(i, EMPTY_CONSTRAINTS)),
	    new TestPrepare(Verify.NO, INTEGRITY, NO_PERMISSIONS,
			    new RS(++i, null),
			    new RS(i, INTEGRITY)),
	    new TestPrepare(Verify.NO, INTEGRITY, NO_PERMISSIONS,
			    new RS(++i, INTEGRITY),
			    new RS(i, INTEGRITY)),
	    new TestPrepare(Verify.NO, INTEGRITY, NO_PERMISSIONS,
			    new RS(++i, UNTRUSTED),
			    new RS(i, INTEGRITY)),

	    /* Verify */

	    new TestPrepare(Verify.YES, NO_PERMISSIONS,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, NO_PERMISSIONS,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, INTEGRITY)),

	    new TestPrepare(Verify.YES, NO_CONSTRAINTS, NO_PERMISSIONS,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, NO_CONSTRAINTS, NO_PERMISSIONS,
			    new RS.Trusted(++i, UNTRUSTED),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, INTEGRITY, NO_PERMISSIONS,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, INTEGRITY)),
	    new TestPrepare(Verify.YES, INTEGRITY, NO_PERMISSIONS,
			    new RS.Trusted(++i, UNTRUSTED),
			    new RS.Trusted(i, INTEGRITY)),
	    new TestPrepare(Verify.YES, UNTRUSTED, NO_PERMISSIONS,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, UNTRUSTED)),

	    new TestPrepare(Verify.YES, EMPTY_PERMISSIONS,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, EMPTY_PERMISSIONS,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, INTEGRITY)),

	    new TestPrepare(Verify.YES, READ_PERMISSION,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, READ_PERMISSION,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, INTEGRITY)),
	    new TestPrepare(Verify.YES, READ_PERMISSION,
			    new RS.Trusted(++i, UNTRUSTED),
			    new RS.Trusted(i, UNTRUSTED)),

	    new TestPrepare(Verify.YES, NO_CONSTRAINTS, READ_PERMISSION,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, NO_CONSTRAINTS, READ_PERMISSION,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, null)),
	    new TestPrepare(Verify.YES, INTEGRITY, READ_PERMISSION,
			    new RS.Trusted(++i, null),
			    new RS.Trusted(i, INTEGRITY)),
	    new TestPrepare(Verify.YES, INTEGRITY, READ_PERMISSION,
			    new RS.Trusted(++i, UNTRUSTED),
			    new RS.Trusted(i, INTEGRITY)),

	    new TestPrepare(Args.YES, Verify.YES, ConstraintsSpecified.NO,
			    null, NO_PERMISSIONS, Dynamic.NO,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, INTEGRITY)),
	    new TestPrepare(Args.YES, Verify.YES, ConstraintsSpecified.NO,
			    null, EMPTY_PERMISSIONS, Dynamic.NO,
			    new RS.Trusted(++i, INTEGRITY),
			    new RS.Trusted(i, INTEGRITY)),

	    /* Subclass preparers */

	    new TestPrepare(Verify.YES, INTEGRITY, NO_PERMISSIONS,
			    new RS.Trusted(++i, CONFIDENTIALITY),
			    new RS.Trusted(
				i,
				new AddConstraintsProxyPreparer
				.CombinedConstraints(
				    INTEGRITY, CONFIDENTIALITY)))
	    {
		ProxyPreparer createPreparer(Permission[] perms) {
		    return new AddConstraintsProxyPreparer(
			verify, methodConstraints, perms);
		}
	    }
	};

	final boolean useDynamicPolicy;
	final Object proxy;
	ProxyPreparer preparer;

	public static void main(String[] tests) {
	    test(localtests);
	}
	    
	TestPrepare(Object proxy, Object shouldBe) {
	    this(Args.NO, Verify.NO, ConstraintsSpecified.NO, null,
		 NO_PERMISSIONS, Dynamic.YES, proxy, shouldBe);
	}

	TestPrepare(Verify verify,
		    Permission[] permissions,
		    Object proxy,
		    Object shouldBe)
	{
	    this(Args.YES, verify, ConstraintsSpecified.NO, null, permissions,
		 Dynamic.YES, proxy, shouldBe);
	}

	TestPrepare(Verify verify,
		    MethodConstraints methodConstraints,
		    Permission[] permissions,
		    Object proxy,
		    Object shouldBe)
	{
	    this(Args.YES, verify, ConstraintsSpecified.YES, methodConstraints,
		 permissions, Dynamic.YES, proxy, shouldBe);
	}

	TestPrepare(Args args,
		    Verify verify,
		    ConstraintsSpecified methodConstraintsSpecified,
		    MethodConstraints methodConstraints,
		    Permission[] permissions,
		    Dynamic dynamic,
		    Object proxy,
		    Object shouldBe)
	{
	    super((dynamic.value  ? "" : "Static policy, ") +
		  "proxy = " + proxy,
		  args, verify, methodConstraintsSpecified,
		  methodConstraints, permissions, shouldBe);
	    this.useDynamicPolicy = dynamic.value;
	    this.proxy = proxy;
	}

	public Object run() throws Exception {
	    /*
	     * Copy the permissions and then modify them to make sure they are
	     * not retained.
	     */
	    Permission[] perms = permissions == null
		? permissions : (Permission[]) permissions.clone();
	    preparer = createPreparer(perms);
	    if (perms != null) {
		Arrays.fill(perms, null);
	    }
	    debugPrint(30, preparer.toString());
	    Thread thread = Thread.currentThread();
	    ClassLoader loader = thread.getContextClassLoader();
	    Policy policy = Policy.getPolicy();
	    try {
		thread.setContextClassLoader(Verifier.CLASSLOADER);
		if (useDynamicPolicy) {
		    if (dynamicPolicy == null) {
			dynamicPolicy = new DynamicPolicyProvider();
			dynamicPolicy.refresh();
		    }
		    Policy.setPolicy(dynamicPolicy);
		}
		try {
		    return preparer.prepareProxy(proxy);
		} catch (Exception e) {
		    return e;
		}
	    } finally {
		thread.setContextClassLoader(loader);
		policy.setPolicy(policy);
	    }
	}

	ProxyPreparer createPreparer(Permission[] perms) {
	    return
		noArgs ? new TestPreparer()
		: methodConstraintsSpecified
		? new TestPreparer(verify, methodConstraints, perms)
		: new TestPreparer(verify, perms);
	}

	public void check(Object result) throws Exception {
	    Object compareTo = getCompareTo();
	    if (compareTo instanceof Class) {
		if (result == null || compareTo != result.getClass()) {
		    throw new FailedException(
			"Should be of type " + compareTo);
		}
	    } else {
		super.check(result);
	    }
	}
    }
}
