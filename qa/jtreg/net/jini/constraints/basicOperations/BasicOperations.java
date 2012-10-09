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
 * @bug 4302502
 * 
 * @summary test basic operations of the constraint classes
 * 
 * @run main/othervm/timeout=240 BasicOperations
 */
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Set;
import net.jini.core.constraint.*;

public class BasicOperations {

    public static final InvocationConstraint[] none =
						new InvocationConstraint[0];

    public static class TestPrin1 implements Principal, Serializable {
	protected final String name;

	public TestPrin1(String name) {
	    this.name = name;
	}

	public String getName() {
	    return name;
	}

	public int hashCode() {
	    return name.hashCode();
	}

	public boolean equals(Object obj) {
	    return (obj.getClass() == TestPrin1.class &&
		    name.equals(((TestPrin1)obj).name));
	}

	public String toString() {
	    return "TestPrin1[" + name + "]";
	}
    }

    public static class TestPrin2 extends TestPrin1 {
	public TestPrin2(String name) {
	    super(name);
	}

	public boolean equals(Object obj) {
	    return (obj instanceof TestPrin2 &&
		    name.equals(((TestPrin2)obj).name));
	}

	public String toString() {
	    return "TestPrin2[" + name + "]";
	}
    }

    public static class TestPrin3 implements Principal, Serializable {
	protected final String name;

	public TestPrin3(String name) {
	    this.name = name;
	}

	public String getName() {
	    return name;
	}

	public int hashCode() {
	    return name.hashCode();
	}

	public boolean equals(Object obj) {
	    return (obj instanceof TestPrin3 &&
		    name.equals(((TestPrin3)obj).name));
	}

	public String toString() {
	    return "TestPrin3[" + name + "]";
	}
    }

    public static class TestPrin4 implements Principal, Serializable {
	protected final String name;

	public TestPrin4(String name) {
	    this.name = name;
	}

	public String getName() {
	    return name;
	}

	public int hashCode() {
	    return name.hashCode();
	}

	public boolean equals(Object obj) {
	    return (obj.getClass() == TestPrin4.class &&
		    name.equals(((TestPrin4)obj).name));
	}

	public String toString() {
	    return "TestPrin4[" + name + "]";
	}
    }

    public static class TestPrin5 extends TestPrin4 {
	public TestPrin5(String name) {
	    super(name);
	}

	public boolean equals(Object obj) {
	    return (obj.getClass() == TestPrin5.class &&
		    name.equals(((TestPrin5)obj).name));
	}

	public String toString() {
	    return "TestPrin5[" + name + "]";
	}
    }

    public static class TestPrin6 extends TestPrin5 implements Comparable {
	public TestPrin6(String name) {
	    super(name);
	}

	public int compareTo(Object obj) {
	    return name.compareTo(((TestPrin6)obj).name);
	}

	public boolean equals(Object obj) {
	    return (obj instanceof TestPrin6 &&
		    name.equals(((TestPrin6)obj).name));
	}

	public String toString() {
	    return "TestPrin6[" + name + "]";
	}
    }

    public static class TestPrin7 extends TestPrin5 implements Comparable {
	public TestPrin7(String name) {
	    super(name);
	}

	public int compareTo(Object obj) {
	    return name.compareTo(((TestPrin7)obj).name);
	}

	public boolean equals(Object obj) {
	    return (obj instanceof TestPrin7 &&
		    name.equals(((TestPrin7)obj).name));
	}

	public String toString() {
	    return "TestPrin7[" + name + "]";
	}
    }

    public static class TestPrin8 extends TestPrin5 {
	public TestPrin8(String name) {
	    super(name);
	}

	public boolean equals(Object obj) {
	    return (obj instanceof TestPrin8 &&
		    name.equals(((TestPrin8)obj).name));
	}

	public String toString() {
	    return "TestPrin8[" + name + "]";
	}
    }

    public static class TestPrin9 extends TestPrin4 {
	public TestPrin9(String name) {
	    super(name);
	}

	public boolean equals(Object obj) {
	    return (obj instanceof TestPrin9 &&
		    name.equals(((TestPrin9)obj).name));
	}

	public String toString() {
	    return "TestPrin9[" + name + "]";
	}
    }

    /**
     * Return an array containing the singleton element.
     */
    public static InvocationConstraint[] sa(InvocationConstraint c) {
	return new InvocationConstraint[]{c};
    }

    /**
     * Return an array containing both elements.
     */
    public static InvocationConstraint[] sa(InvocationConstraint c1,
					    InvocationConstraint c2)
    {
	return new InvocationConstraint[]{c1, c2};
    }

    /**
     * Return an array containing all three elements.
     */
    public static InvocationConstraint[] sa(InvocationConstraint c1,
					    InvocationConstraint c2,
					    InvocationConstraint c3)
    {
	return new InvocationConstraint[]{c1, c2, c3};
    }

    /**
     * Return an array containing all four elements.
     */
    public static InvocationConstraint[] sa(InvocationConstraint c1,
					    InvocationConstraint c2,
					    InvocationConstraint c3,
					    InvocationConstraint c4)
    {
	return new InvocationConstraint[]{c1, c2, c3, c4};
    }

    /**
     * Return a ConstraintAlternatives containing the two elements.
     */
    public static InvocationConstraint alt(InvocationConstraint c1,
					   InvocationConstraint c2)
    {
	return ConstraintAlternatives.create(sa(c1, c2));
    }

    /**
     * Return a ConstraintAlternatives containing the three elements.
     */
    public static InvocationConstraint alt(InvocationConstraint c1,
					   InvocationConstraint c2,
					   InvocationConstraint c3)
    {
	return ConstraintAlternatives.create(sa(c1, c2, c3));
    }

    /**
     * Return a ConstraintAlternatives containing the four elements.
     */
    public static InvocationConstraint alt(InvocationConstraint c1,
					   InvocationConstraint c2,
					   InvocationConstraint c3,
					   InvocationConstraint c4)
    {
	return ConstraintAlternatives.create(sa(c1, c2, c3, c4));
    }

    /**
     * Return a InvocationConstraints containing c1 as a requirement and
     * c2 as a preference.
     */
    public static InvocationConstraints sc(InvocationConstraint c1,
					   InvocationConstraint c2)
    {
	return new InvocationConstraints(c1, c2);
    }

    /**
     * Return a InvocationConstraints containing c1 as requirements and
     * c2 as preferences.
     */
    public static InvocationConstraints sc(InvocationConstraint[] c1,
					   InvocationConstraint[] c2)
    {
	return new InvocationConstraints(c1, c2);
    }

    /**
     * Return a InvocationConstraints containing both as requirements.
     */
    public static InvocationConstraints reqs(InvocationConstraint c1,
					     InvocationConstraint c2)
    {
	return new InvocationConstraints(sa(c1, c2), null);
    }

    /**
     * Return a InvocationConstraints containing both as preferences.
     */
    public static InvocationConstraints prefs(InvocationConstraint c1,
					      InvocationConstraint c2)
    {
	return new InvocationConstraints(null, sa(c1, c2));
    }

    /**
     * Return InvocationConstraints.combine on the two arguments.
     */
    public static InvocationConstraints combine(InvocationConstraints c1,
						InvocationConstraints c2)
    {
	return InvocationConstraints.combine(c1, c2);
    }

    /**
     * Return a TestPrin1 with the given name.
     */
    public static TestPrin1 p1(String name) {
	return new TestPrin1(name);
    }

    /**
     * Return a TestPrin2 with the given name.
     */
    public static TestPrin2 p2(String name) {
	return new TestPrin2(name);
    }

    /**
     * Return a TestPrin3 with the given name.
     */
    public static TestPrin3 p3(String name) {
	return new TestPrin3(name);
    }

    /**
     * Return a TestPrin4 with the given name.
     */
    public static TestPrin4 p4(String name) {
	return new TestPrin4(name);
    }

    /**
     * Return a TestPrin5 with the given name.
     */
    public static TestPrin5 p5(String name) {
	return new TestPrin5(name);
    }

    /**
     * Return a TestPrin6 with the given name.
     */
    public static TestPrin6 p6(String name) {
	return new TestPrin6(name);
    }

    /**
     * Return a TestPrin7 with the given name.
     */
    public static TestPrin7 p7(String name) {
	return new TestPrin7(name);
    }

    /**
     * Return a TestPrin8 with the given name.
     */
    public static TestPrin8 p8(String name) {
	return new TestPrin8(name);
    }

    /**
     * Return a TestPrin9 with the given name.
     */
    public static TestPrin9 p9(String name) {
	return new TestPrin9(name);
    }

    /**
     * Return an array containing the singleton element.
     */
    public static Principal[] pa(Principal p) {
	return new Principal[]{p};
    }

    /**
     * Return an array containing the both elements.
     */
    public static Principal[] pa(Principal p1, Principal p2) {
	return new Principal[]{p1, p2};
    }

    /**
     * Return an array containing the three elements.
     */
    public static Principal[] pa(Principal p1, Principal p2, Principal p3) {
	return new Principal[]{p1, p2, p3};
    }

    /**
     * Return an array containing the singleton element.
     */
    public static Class[] ca(Class c) {
	return new Class[]{c};
    }

    /**
     * Return an array containing the both elements.
     */
    public static Class[] ca(Class c1, Class c2) {
	return new Class[]{c1, c2};
    }

    /**
     * Return an array containing the three elements.
     */
    public static Class[] ca(Class c1, Class c2, Class c3) {
	return new Class[]{c1, c2, c3};
    }

    /**
     * Return a ClientMinPrincipal containing the singleton element.
     */
    public static ClientMinPrincipal cmin(Principal p) {
	return new ClientMinPrincipal(p);
    }

    /**
     * Return a ClientMinPrincipal containing the given elements.
     */
    public static ClientMinPrincipal cmin(Principal[] pl) {
	return new ClientMinPrincipal(pl);
    }

    /**
     * Return a ClientMaxPrincipal containing the singleton element.
     */
    public static ClientMaxPrincipal cmax(Principal p) {
	return new ClientMaxPrincipal(p);
    }

    /**
     * Return a ClientMaxPrincipal containing the given elements.
     */
    public static ClientMaxPrincipal cmax(Principal[] pl) {
	return new ClientMaxPrincipal(pl);
    }

    /**
     * Return a ServerMinPrincipal containing the singleton element.
     */
    public static ServerMinPrincipal smin(Principal p) {
	return new ServerMinPrincipal(p);
    }

    /**
     * Return a ServerMinPrincipal containing the given elements.
     */
    public static ServerMinPrincipal smin(Principal[] pl) {
	return new ServerMinPrincipal(pl);
    }

    /**
     * Return a ClientMinPrincipalType containing the singleton element.
     */
    public static ClientMinPrincipalType cmin(Class c) {
	return new ClientMinPrincipalType(c);
    }

    /**
     * Return a ClientMinPrincipalType containing the given elements.
     */
    public static ClientMinPrincipalType cmin(Class[] cl) {
	return new ClientMinPrincipalType(cl);
    }

    /**
     * Return a ClientMaxPrincipalType containing the singleton element.
     */
    public static ClientMaxPrincipalType cmax(Class c) {
	return new ClientMaxPrincipalType(c);
    }

    /**
     * Return a ClientMaxPrincipalType containing the given elements.
     */
    public static ClientMaxPrincipalType cmax(Class[] cl) {
	return new ClientMaxPrincipalType(cl);
    }

    /**
     * Return a DelegationRelativeTime containing the given times.
     */
    public static DelegationRelativeTime rel(long minStart,
					     long maxStart,
					     long minStop,
					     long maxStop)
    {
	return new DelegationRelativeTime(minStart, maxStart,
					  minStop, maxStop);
    }

    /**
     * Return a DelegationAbsolueTime containing the given times.
     */
    public static DelegationAbsoluteTime abs(long minStart,
					     long maxStart,
					     long minStop,
					     long maxStop)
    {
	return new DelegationAbsoluteTime(minStart, maxStart,
					  minStop, maxStop);
    }

    /**
     * Return a ConnectionRelativeTime containing the given time.
     */
    public static ConnectionRelativeTime rel(long t) {
	return new ConnectionRelativeTime(t);
    }

    /**
     * Return a ConnectionAbsolueTime containing the given time.
     */
    public static ConnectionAbsoluteTime abs(long t) {
	return new ConnectionAbsoluteTime(t);
    }

    /**
     * Throw an exception if b is false.
     */
    public static void v(boolean b) {
	if (!b) {
	    throw new RuntimeException(
				 "test failed; see stack trace for details");
	}
    }

    /**
     * Check that the two constraints are functionally equivalent.
     */
    public static void same(InvocationConstraint c1, InvocationConstraint c2) {
	v(c1.equals(c2));
	v(c2.equals(c1));
	v(c1.hashCode() == c2.hashCode());
	same(sc(c1, null), sa(c2), none, false);
	same(sc(null, c1), none, sa(c2), false);
	same(reqs(c1, c2), sa(c1), none, false);
	same(combine(sc(c1, null), sc(c2, null)), sa(c2), none, false);
	same(reqs(c2, c1), sa(c1), none, false);
	same(combine(sc(c2, null), sc(c1, null)), sa(c1), none, false);
	same(prefs(c1, c2), none, sa(c2), false);
	same(combine(sc(null, c1), sc(null, c2)), none, sa(c2), false);
	same(prefs(c2, c1), none, sa(c2), false);
	same(combine(sc(null, c2), sc(null, c1)), none, sa(c2), false);
	if (!(c1 instanceof ConstraintAlternatives)) {
	    v(c1.equals(alt(c1, c2)));
	}
	same(sc(c1, c2), sa(c1), none, false);
	same(combine(sc(c1, null), sc(null, c2)), sa(c1), none, false);
    }

    /**
     * Check that a copy made by marshalling and unmarshalling is equal
     * to the original, and that two marshalled objects are equal.
     */
    public static void copy(Object c) {
	try {
	    MarshalledObject mo = new MarshalledObject(c);
	    Object cc = mo.get();
	    v(c.equals(cc));
	    v(cc.equals(c));
	    v(c.hashCode() == cc.hashCode());
	    if (c instanceof InvocationConstraints) {
		InvocationConstraints sc = (InvocationConstraints) c;
		InvocationConstraints csc = (InvocationConstraints) cc;
		v(sc.isEmpty() == csc.isEmpty());
	    }
	    v(mo.equals(new MarshalledObject(cc)));
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new RuntimeException();
	}
    }

    /**
     * Check that a copy made by marshalling and unmarshalling is ==
     * to the original, and that two marshalled objects are equal.
     */
    public static void eqcopy(Object c) {
	try {
	    MarshalledObject mo = new MarshalledObject(c);
	    Object cc = mo.get();
	    v(c == cc);
	    v(mo.equals(new MarshalledObject(cc)));
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new RuntimeException();
	}
    }

    /**
     * Check that the set contains exactly the given elements.
     */
    public static void same(Set set, InvocationConstraint[] cl) {
	v(set.size() == cl.length);
	for (int i = cl.length; --i >= 0; ) {
	    v(set.contains(cl[i]));
	}
    }

    /**
     * Check that the InvocationConstraints instance contains exactly
     * the given requirements and preferences and conflicts state.
     */
    public static void same(InvocationConstraints c,
			    InvocationConstraint[] reqs,
			    InvocationConstraint[] prefs,
			    boolean conflicts)
    {
	same(c.requirements(), reqs);
	same(c.preferences(), prefs);
	v(c.isEmpty() == (reqs.length == 0 && prefs.length == 0));
	copy(c);
    }

    /**
     * Check that the given constraint is a ConstraintAlternatives instance
     * containing exactly the given elements.
     */
    public static void same(InvocationConstraint c,
			    InvocationConstraint[] alts)
    {
	v(c instanceof ConstraintAlternatives);
	same(((ConstraintAlternatives)c).elements(), alts);
    }

    /**
     * Check that the two InvocationConstraints are equal.
     */
    public static void same(InvocationConstraints c1,
			    InvocationConstraints c2)
    {
	v(c1.equals(c2));
	v(c2.equals(c1));
	v(c1.hashCode() == c2.hashCode());
	v(c1.isEmpty() == c2.isEmpty());
	copy(c1);
    }

    /**
     * Check that the two constraints are not equal.
     */
    public static void neq(InvocationConstraint c1, InvocationConstraint c2) {
	v(!c1.equals(c2));
	v(!c2.equals(c1));
	diff(sc(c1, null), sc(c2, null));
	same(reqs(c1, c2), reqs(c2, c1));
	same(combine(sc(c1, null), sc(c2, null)),
	     combine(sc(c2, null), sc(c1, null)));
	same(prefs(c1, c2), prefs(c2, c1));
	same(combine(sc(null, c1), sc(null, c2)),
	     combine(sc(null, c2), sc(null, c1)));
	if (c1.getClass() == c2.getClass() &&
	    !(c1 instanceof ConstraintAlternatives))
	{
	    same(alt(c1, c2), alt(c2, c1));
	}
    }

    /**
     * Check that the two constraints are not equal.
     */
    public static void diff(InvocationConstraint c1, InvocationConstraint c2) {
	neq(c1, c2);
	if (c1.getClass() == c2.getClass() &&
	    !(c1 instanceof ConstraintAlternatives))
	{
	    sameorimplies(c1, alt(c2, c1));
	    sameorimplies(c2, alt(c1, c2));
	}
    }

    /**
     * Check that the two InvocationConstraints are not equal.
     */
    public static void diff(InvocationConstraints c1,
			    InvocationConstraints c2)
    {
	v(!c1.equals(c2));
	v(!c2.equals(c1));
    }

    /**
     * Check that c1 and c2 are not equal and intersect to produce i.
     */
    public static void intersect(InvocationConstraint c1,
				 InvocationConstraint c2,
				 InvocationConstraint i)
    {
	diff(c1, c2);
	implies(i, c1);
	implies(i, c2);
	same(reqs(c1, c2), sa(c1, c2), none, false);
	same(combine(sc(c1, null), sc(c2, null)), sa(c1, c2), none, false);
	same(reqs(c2, c1), sa(c1, c2), none, false);
	same(combine(sc(c2, null), sc(c1, null)), sa(c1, c2), none, false);
	if (c1 instanceof ConstraintAlternatives &&
	    c2 instanceof ConstraintAlternatives)
	{
	} else if (c1 instanceof ConstraintAlternatives) {
	    same(sc(c1, c2), sa(c1), sa(c2), false);
	    same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	    same(sc(c2, c1), sa(c2), sa(c1), false);
	    same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1), false);
	} else if (c2 instanceof ConstraintAlternatives) {
	    same(sc(c1, c2), sa(c1), sa(c2), false);
	    same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	    if (i instanceof ConstraintAlternatives) {
		same(sc(c2, c1), sa(c2), sa(c1), false);
		same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1),
		     false);
	    } else {
		same(sc(c2, c1), sa(c2), sa(c1), false);
		same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1),
		     false);
	    }
	} else {
	    same(sc(c1, c2), sa(c1), sa(c2), false);
	    same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	    same(sc(c2, c1), sa(c2), sa(c1), false);
	    same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1), false);
	    if (c1.getClass() == c2.getClass()) {
		implies(i, alt(c1, c2));
		implies(i, alt(c2, c1));
	    }
	}
    }

    /**
     * Check that c1 and c2 are not equal, c1.reduceBy(c2) is i, and
     * c2.reduceBy(c1) is c2.
     */
    public static void reduce(InvocationConstraint c1,
			      InvocationConstraint c2,
			      InvocationConstraint i)
    {
	reduce(c1, c2, i, c2);
    }

    /**
     * Check that c1 and c2 are not equal, c1.reduceBy(c2) is i1, and
     * c2.reduceBy(c1) is i2.
     */
    public static void reduce(InvocationConstraint c1,
			      InvocationConstraint c2,
			      InvocationConstraint i1,
			      InvocationConstraint i2)
    {
	diff(c1, c2);
	implies(i1, c1);
	sameorimplies(i2, c2);
	same(reqs(c2, c1), sa(c1, c2), none, false);
	same(combine(sc(c2, null), sc(c1, null)), sa(c1, c2), none, false);
	same(reqs(c1, c2), sa(c1, c2), none, false);
	same(combine(sc(c1, null), sc(c2, null)), sa(c1, c2), none, false);
	if (i1 instanceof ConstraintAlternatives &&
	    !(c1 instanceof ConstraintAlternatives))
	{
	    same(sc(c2, c1), sa(c2), sa(c1), false);
	    same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1), false);
	}
	if (i2 instanceof ConstraintAlternatives &&
	    !(c2 instanceof ConstraintAlternatives))
	{
	    same(sc(c1, c2), sa(c1), sa(c2), false);
	    same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	}
	if (!(i1 instanceof ConstraintAlternatives)) {
	    same(sc(c2, c1), sa(c2), sa(c1), false);
	    same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1), false);
	}
	if (!(i2 instanceof ConstraintAlternatives)) {
	    same(sc(c1, c2), sa(c1), sa(c2), false);
	    same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	}
    }

    /**
     * Check that c1 and c2 are not equal and are irreducible wrt each other.
     */
    public static void noreduce(InvocationConstraint c1,
				InvocationConstraint c2)
    {
	diff(c1, c2);
    }

    /**
     * Check that c1 either equals or is a subset of c2.
     */
    public static void sameorimplies(InvocationConstraint c1,
				     InvocationConstraint c2)
    {
	if (c1.equals(c2)) {
	    same(c1, c2);
	} else {
	    implies(c1, c2);
	}
    }

    /**
     * Check that c1 is a strict subset of c2.
     */
    public static void implies(InvocationConstraint c1,
			       InvocationConstraint c2)
    {
	neq(c1, c2);
	if (!(c2 instanceof ConstraintAlternatives)) {
	    same(sc(c1, c2), sa(c1), sa(c2), false);
	    same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	}
	same(reqs(c1, c2), sa(c1, c2), none, false);
	same(combine(sc(c1, null), sc(c2, null)), sa(c1, c2), none, false);
	same(reqs(c2, c1), sa(c1, c2), none, false);
	same(combine(sc(c2, null), sc(c1, null)), sa(c1, c2), none, false);
    }

    /**
     * Check that c1 and c2 have no intersection.
     */
    public static void conflict(InvocationConstraint c1,
				InvocationConstraint c2)
    {
	diff(c1, c2);
	same(reqs(c1, c2), sa(c1, c2), none, true);
	same(combine(sc(c1, null), sc(c2, null)), sa(c1, c2), none, true);
	same(reqs(c2, c1), sa(c1, c2), none, true);
	same(combine(sc(c2, null), sc(c1, null)), sa(c1, c2), none, true);
	same(sc(c1, c2), sa(c1), sa(c2), false);
	same(combine(sc(c1, null), sc(null, c2)), sa(c1), sa(c2), false);
	same(sc(c2, c1), sa(c2), sa(c1), false);
	same(combine(sc(c2, null), sc(null, c1)), sa(c2), sa(c1), false);
    }

    /**
     * Check that calling the ct constructor (with parameter type pt)
     * will null throws NullPointerException.
     */
    public static void npe(Class ct, Class pt) {
	try {
	    Constructor cons = ct.getConstructor(new Class[]{pt});
	    cons.newInstance(new Object[]{null});
	    throw new RuntimeException("NullPointerException not thrown");
	} catch (InvocationTargetException e) {
	    if (e.getTargetException() instanceof NullPointerException) {
		return;
	    }
	    throw new RuntimeException("unexpected exception: " + e);
	} catch (Exception e) {
	    throw new RuntimeException("unexpected exception: " + e);
	}
    }

    /**
     * Check that calling the ct constructor (with parameter type equal
     * to the actual type of arr) with arr as a parameter throws
     * NullPointerException.
     */
    public static void npe(Class ct, Object[] arr) {
	try {
	    Constructor cons = ct.getConstructor(new Class[]{arr.getClass()});
	    cons.newInstance(new Object[]{arr});
	    throw new RuntimeException("NullPointerException not thrown");
	} catch (InvocationTargetException e) {
	    if (e.getTargetException() instanceof NullPointerException) {
		return;
	    }
	    throw new RuntimeException("unexpected exception: " + e);
	} catch (Exception e) {
	    throw new RuntimeException("unexpected exception: " + e);
	}
    }

    /**
     * Check that constructing a InvocationConstraints with the given
     * requirement and preference throws NullPointerException.
     */
    public static void npe(InvocationConstraint req,
			   InvocationConstraint pref)
    {
	try {
	    new InvocationConstraints(req, pref);
	    throw new RuntimeException("NullPointerException not thrown");
	} catch (NullPointerException e) {
	}
    }

    /**
     * Check that constructing a InvocationConstraints with the given
     * requirements and preferences throws NullPointerException.
     */
    public static void npe(InvocationConstraint[] reqs,
			   InvocationConstraint[] prefs)
    {
	try {
	    new InvocationConstraints(reqs, prefs);
	    throw new RuntimeException("NullPointerException not thrown");
	} catch (NullPointerException e) {
	}
    }

    /**
     * Check that calling the ct constructor (with parameter type equal to
     * the actual type of p) with p throws IllegalArgumentException.
     */
    public static void iae(Class ct, Object p) {
	try {
	    Constructor cons = ct.getConstructor(new Class[]{p.getClass()});
	    cons.newInstance(new Object[]{p});
	    throw new RuntimeException("IllegalArgumentException not thrown");
	} catch (InvocationTargetException e) {
	    if (e.getTargetException() instanceof IllegalArgumentException) {
		return;
	    }
	    throw new RuntimeException("unexpected exception: " + e);
	} catch (Exception e) {
	    throw new RuntimeException("unexpected exception: " + e);
	}
    }

    /**
     * Check that calling the ct constructor with the four parameters throws
     * IllegalArgumentException.
     */
    public static void iae(Class ct,
			   long minStart,
			   long maxStart,
			   long minStop,
			   long maxStop)
    {
	try {
	    Constructor cons =
		ct.getConstructor(new Class[]{long.class, long.class,
					      long.class, long.class});
	    cons.newInstance(new Object[]{new Long(minStart),
					  new Long(maxStart),
					  new Long(minStop),
					  new Long(maxStop)});
	    throw new RuntimeException("IllegalArgumentException not thrown");
	} catch (InvocationTargetException e) {
	    if (e.getTargetException() instanceof IllegalArgumentException) {
		return;
	    }
	    throw new RuntimeException("unexpected exception: " + e);
	} catch (Exception e) {
	    throw new RuntimeException("unexpected exception: " + e);
	}
    }

    /**
     * Check that calling the ct constructor with the one parameter throws
     * IllegalArgumentException.
     */
    public static void iae(Class ct, long t) {
	try {
	    Constructor cons =
		ct.getConstructor(new Class[]{long.class});
	    cons.newInstance(new Object[]{new Long(t)});
	    throw new RuntimeException("IllegalArgumentException not thrown");
	} catch (InvocationTargetException e) {
	    if (e.getTargetException() instanceof IllegalArgumentException) {
		return;
	    }
	    throw new RuntimeException("unexpected exception: " + e);
	} catch (Exception e) {
	    throw new RuntimeException("unexpected exception: " + e);
	}
    }

    /**
     * Check that constructors for the given principal-based constraint
     * class throw exceptions when they should.
     */
    public static void badp(Class c) {
	npe(c, Principal.class);
	npe(c, Principal[].class);
	npe(c, pa(null));
	npe(c, pa(p1("rws"), null));
	iae(c, new Principal[0]);
    }

    /**
     * Check that constructors for the given principal type-based constraint
     * class throw exceptions when they should.
     */
    public static void badt(Class c) {
	npe(c, Class.class);
	npe(c, Class[].class);
	npe(c, ca(null));
	npe(c, ca(TestPrin1.class, null));
	iae(c, new Class[0]);
	iae(c, ca(int.class));
	iae(c, ca(Integer.class));
	iae(c, ca(TestPrin1[].class));
    }

    public static void main(String[] args) {
	// basic constraints
	InvocationConstraint[] cl = new InvocationConstraint[]{
	    ClientAuthentication.YES, ClientAuthentication.NO,
	    ServerAuthentication.YES, ServerAuthentication.NO,
	    Integrity.YES, Integrity.NO,
	    Confidentiality.YES, Confidentiality.NO,
	    Delegation.YES, Delegation.NO};
	for (int i = 0; i < cl.length; i++) {
	    same(cl[i], cl[i]);
	    eqcopy(cl[i]);
	    if (i % 2 == 0) {
		conflict(cl[i], cl[i + 1]);
		same(alt(cl[i], cl[i + 1]),
		     alt(cl[i + 1], cl[i]));
	    }
	    for (int j = i; --j >= 0; ) {
		diff(cl[i], cl[j]);
	    }
	}
	implies(ClientAuthentication.NO, Delegation.YES);
	implies(ClientAuthentication.NO, Delegation.NO);

	// ClientMinPrincipal
	copy(cmin(p1("rws")));
	copy(cmin(pa(p1("rws"), p2("bob"))));
	implies(ClientAuthentication.NO, cmin(p1("rws")));
	same(cmin(p1("rws")), cmin(p1("rws")));
	same(cmin(p1("rws")), cmin(pa(p1("rws"))));
	same(cmin(p1("rws")), cmin(pa(p1("rws"), p1("rws"))));
	same(cmin(pa(p1("rws"), p2("bob"))), cmin(pa(p2("bob"), p1("rws"))));
	intersect(cmin(p1("rws")), cmin(p2("bob")),
		  cmin(pa(p1("rws"), p2("bob"))));
	intersect(cmin(pa(p1("rws"), p2("bob"))),
		  cmin(pa(p2("bob"), p3("rws"))),
		  cmin(pa(p1("rws"), p2("bob"), p3("rws"))));
	implies(cmin(pa(p1("rws"), p2("bob"))), cmin(p1("rws")));
	conflict(cmin(p1("rws")), cmax(p1("bob")));
	noreduce(cmin(p1("rws")), cmax(p1("rws")));
	conflict(cmin(pa(p1("rws"), p2("bob"))), cmax(p1("rws")));
	conflict(cmin(pa(p1("rws"), p2("bob"))), cmax(p2("bob")));
	noreduce(cmin(p1("rws")), cmax(pa(p1("rws"), p2("bob"))));
	noreduce(cmin(p1("rws")), cmax(pa(p2("bob"), p1("rws"))));
	noreduce(cmin(pa(p1("rws"), p2("bob"))),
		 cmax(pa(p1("rws"), p2("bob"))));
	noreduce(cmin(pa(p1("rws"), p2("bob"))),
		 cmax(pa(p2("bob"), p1("rws"))));
	noreduce(cmin(pa(p1("rws"), p2("bob"))),
		 cmax(pa(p2("bob"), p3("rws"), p1("rws"))));
	conflict(cmin(p1("rws")), cmax(TestPrin3.class));
	conflict(cmin(p1("rws")), cmax(TestPrin2.class));
	noreduce(cmin(p1("rws")), cmax(TestPrin1.class));
	noreduce(cmin(p2("rws")), cmax(TestPrin1.class));
	noreduce(cmin(p2("rws")), cmax(TestPrin2.class));
	conflict(cmin(pa(p1("rws"), p2("bob"))), cmax(TestPrin3.class));
	conflict(cmin(pa(p1("rws"), p2("bob"))), cmax(TestPrin2.class));
	noreduce(cmin(p1("rws")), cmax(ca(TestPrin1.class, TestPrin3.class)));
	noreduce(cmin(p1("rws")), cmax(ca(TestPrin3.class, TestPrin1.class)));
	same(alt(cmin(p2("bob")), cmin(p3("rws"))),
	     sa(cmin(p2("bob")), cmin(p3("rws"))));
	intersect(cmin(p1("rws")), alt(cmin(p2("bob")), cmin(p3("rws"))),
		  alt(cmin(pa(p1("rws"), p2("bob"))),
		      cmin(pa(p1("rws"), p3("rws")))));
	conflict(cmin(p1("rws")), alt(cmax(p1("bob")), cmax(p2("rws"))));
	conflict(cmin(pa(p1("rws"), p2("bob"))),
		 alt(cmax(p1("rws")), cmax(p2("bob"))));
	conflict(cmin(p1("rws")),
		 alt(cmax(TestPrin3.class), cmax(TestPrin2.class)));
	conflict(cmin(pa(p1("rws"), p2("bob"))),
		 alt(cmax(TestPrin3.class), cmax(TestPrin2.class)));
	noreduce(cmin(p1("rws")),
		 alt(cmax(p1("rws")), cmax(pa(p1("rws"), p2("bob")))));
	noreduce(cmin(p2("rws")),
		 alt(cmax(TestPrin1.class), cmax(TestPrin2.class)));
	noreduce(cmin(p2("rws")),
		 alt(cmax(TestPrin2.class),
		     cmax(ca(TestPrin1.class, TestPrin3.class))));
	intersect(alt(cmin(p1("rws")), cmin(p2("bob"))),
		  alt(cmin(p3("rws")), cmin(p4("bob"))),
		  alt(cmin(pa(p1("rws"), p3("rws"))),
		      cmin(pa(p1("rws"), p4("bob"))),
		      cmin(pa(p2("bob"), p3("rws"))),
		      cmin(pa(p2("bob"), p4("bob")))));
	conflict(alt(cmin(p1("rws")), cmin(p4("rws"))),
		 alt(cmax(TestPrin3.class), cmax(TestPrin2.class)));
	conflict(alt(cmin(p1("rws")), cmin(p3("rws"))),
		 alt(cmax(p1("bob")), cmax(p2("rws"))));
	badp(ClientMinPrincipal.class);

	// ServerMinPrincipal
	copy(smin(p1("rws")));
	copy(smin(pa(p1("rws"), p2("bob"))));
	implies(ServerAuthentication.NO, smin(p2("rws")));
	same(smin(p2("rws")), smin(p2("rws")));
	same(smin(p2("rws")), smin(pa(p2("rws"))));
	same(smin(p2("bob")), smin(pa(p2("bob"), p2("bob"))));
	same(smin(pa(p1("rws"), p2("bob"))), smin(pa(p2("bob"), p1("rws"))));
	intersect(smin(p1("rws")), smin(p2("bob")),
		  smin(pa(p1("rws"), p2("bob"))));
	implies(smin(pa(p1("rws"), p2("bob"))), smin(p1("rws")));
	same(alt(smin(p2("bob")), smin(p3("rws"))),
	     sa(smin(p2("bob")), smin(p3("rws"))));
	intersect(smin(p1("rws")), alt(smin(p2("bob")), smin(p3("rws"))),
		  alt(smin(pa(p1("rws"), p2("bob"))),
		      smin(pa(p1("rws"), p3("rws")))));
	intersect(alt(smin(p1("rws")), smin(p2("bob"))),
		  alt(smin(p3("rws")), smin(p4("bob"))),
		  alt(smin(pa(p1("rws"), p3("rws"))),
		      smin(pa(p1("rws"), p4("bob"))),
		      smin(pa(p2("bob"), p3("rws"))),
		      smin(pa(p2("bob"), p4("bob")))));
	badp(ServerMinPrincipal.class);

	// ClientMaxPrincipal
	copy(cmax(p1("rws")));
	copy(cmax(pa(p1("rws"), p2("bob"))));
	implies(ClientAuthentication.NO, cmax(p3("rws")));
	same(cmax(p3("rws")), cmax(p3("rws")));
	same(cmax(p3("rws")), cmax(pa(p3("rws"))));
	same(cmax(p3("rws")), cmax(pa(p3("rws"), p3("rws"))));
	same(cmax(pa(p1("rws"), p2("bob"))), cmax(pa(p2("bob"), p1("rws"))));
	implies(cmax(p1("rws")), cmax(pa(p1("rws"), p2("bob"))));
	implies(cmax(pa(p1("rws"), p2("bob"))),
		cmax(pa(p1("rws"), p3("rws"), p2("bob"))));
	intersect(cmax(pa(p1("rws"), p3("bob"))),
		  cmax(pa(p2("bob"), p1("rws"))),
		  cmax(p1("rws")));
	intersect(cmax(pa(p1("rws"), p3("bob"), p2("rws"))),
		  cmax(pa(p3("bob"), p1("rws"), p2("bob"))),
		  cmax(pa(p1("rws"), p3("bob"))));
	conflict(cmax(p1("rws")), cmax(p2("bob")));
	conflict(cmax(p1("rws")), cmax(pa(p1("bob"), p2("rws"))));
	conflict(cmax(pa(p1("rws"), p2("bob"))),
		 cmax(pa(p1("bob"), p2("rws"))));
	intersect(cmax(pa(p1("rws"), p2("bob"))), cmax(TestPrin2.class),
		  cmax(p2("bob")));
	intersect(cmax(pa(p1("rws"), p3("rws"), p2("bob"))),
		  cmax(TestPrin1.class),
		  cmax(pa(p1("rws"), p2("bob"))));
	intersect(cmax(pa(p1("rws"), p3("rws"), p2("bob"))),
		  cmax(ca(TestPrin2.class, TestPrin3.class)),
		  cmax(pa(p2("bob"), p3("rws"))));
	intersect(cmax(pa(p2("rws"), p4("bob"))),
		  cmax(ca(TestPrin1.class, TestPrin3.class)),
		  cmax(p2("rws")));
	conflict(cmax(p1("rws")), cmax(TestPrin3.class));
	conflict(cmax(pa(p3("bob"), p1("rws"))), cmax(TestPrin2.class));
	same(alt(cmax(p1("bob")), cmax(p2("rws"))),
	     sa(cmax(p1("bob")), cmax(p2("rws"))));
	implies(cmax(p1("bob")), alt(cmax(p1("bob")), cmax(p2("rws"))));
	intersect(cmax(pa(p1("bob"), p2("rws"))),
		  alt(cmax(pa(p3("rws"), p1("bob"))),
		      cmax(pa(p1("bob"), p2("bob")))),
		  cmax(p1("bob")));
	intersect(cmax(pa(p1("bob"), p2("rws"))),
		  alt(cmax(pa(p3("rws"), p2("bob"))),
		      cmax(pa(p1("bob"), p2("bob")))),
		  cmax(p1("bob")));
	intersect(cmax(pa(p1("bob"), p2("rws"), p3("bob"))),
		  alt(cmax(pa(p3("rws"), p1("bob"))),
		      cmax(pa(p3("bob"), p2("bob")))),
		  alt(cmax(p1("bob")), cmax(p3("bob"))));
	conflict(cmax(p1("rws")), alt(cmax(p1("bob")), cmax(p2("rws"))));
	implies(cmax(p1("bob")),
		alt(cmax(TestPrin1.class), cmax(TestPrin3.class)));
	intersect(cmax(pa(p3("bob"), p6("rws"))),
		  alt(cmax(TestPrin5.class), cmax(Comparable.class)),
		  cmax(p6("rws")));
	intersect(cmax(pa(p1("bob"), p2("rws"))),
		  alt(cmax(TestPrin3.class), cmax(TestPrin2.class)),
		  cmax(p2("rws")));
	intersect(cmax(pa(p1("bob"), p2("rws"), p3("bob"))),
		  alt(cmax(ca(TestPrin3.class, TestPrin2.class)),
		      cmax(TestPrin1.class)),
		  alt(cmax(pa(p2("rws"), p3("bob"))),
		      cmax(pa(p1("bob"), p2("rws")))));
	conflict(cmax(p1("rws")),
		 alt(cmax(TestPrin3.class), cmax(TestPrin2.class)));
	noreduce(cmax(pa(p1("rws"), p2("bob"))),
		 alt(cmin(p1("rws")), cmin(p2("bob"))));
	conflict(cmax(p1("rws")), alt(cmin(p2("bob")), cmin(p3("rws"))));
	reduce(alt(cmax(p1("bob")), cmax(pa(p2("bob"), p1("rws")))),
	       cmin(p1("rws")),
	       cmax(pa(p2("bob"), p1("rws"))));
	intersect(alt(cmax(pa(p1("bob"), p2("rws"))),
		      cmax(pa(p2("rws"), p4("bob")))),
		  alt(cmax(TestPrin3.class), cmax(TestPrin2.class)),
		  cmax(p2("rws")));
	badp(ClientMaxPrincipal.class);

	// ClientPrincipalType
	copy(cmin(TestPrin1.class));
	copy(cmin(ca(TestPrin1.class, TestPrin3.class)));
	implies(ClientAuthentication.NO, cmin(TestPrin1.class));
	same(cmin(TestPrin1.class), cmin(TestPrin1.class));
	same(cmin(TestPrin1.class), cmin(ca(TestPrin1.class)));
	same(cmin(TestPrin1.class),
	     cmin(ca(TestPrin1.class, TestPrin1.class)));
	same(cmin(ca(TestPrin1.class, TestPrin3.class)),
	     cmin(ca(TestPrin3.class, TestPrin1.class)));
	same(cmin(TestPrin2.class),
	     cmin(ca(TestPrin1.class, TestPrin2.class)));
	same(cmin(TestPrin2.class),
	     cmin(ca(TestPrin2.class, TestPrin1.class)));
	implies(cmin(TestPrin2.class), cmin(TestPrin1.class));
	implies(cmin(ca(TestPrin2.class, TestPrin3.class)),
		cmin(ca(TestPrin1.class, TestPrin3.class)));
	intersect(cmin(TestPrin1.class), cmin(TestPrin3.class),
		  cmin(ca(TestPrin1.class, TestPrin3.class)));
	intersect(cmin(ca(TestPrin1.class, TestPrin3.class)),
		  cmin(TestPrin2.class),
		  cmin(ca(TestPrin2.class, TestPrin3.class)));
	implies(cmin(p1("rws")), cmin(TestPrin1.class));
	implies(cmin(p2("rws")), cmin(TestPrin1.class));
	reduce(cmin(TestPrin3.class), cmin(p1("rws")),
	       cmin(ca(TestPrin1.class, TestPrin3.class)));
	noreduce(cmin(TestPrin2.class), cmin(p1("rws")));
	reduce(cmin(TestPrin1.class), cmax(p1("bob")), cmin(p1("bob")));
	reduce(cmin(ca(TestPrin2.class, TestPrin3.class)),
	       cmax(pa(p1("bob"), p2("rws"), p3("bob"))),
	       cmin(pa(p2("rws"), p3("bob"))));
	reduce(cmin(TestPrin1.class),
	       cmax(pa(p1("bob"), p2("rws"), p3("bob"))),
	       cmin(pa(p1("bob"), p2("rws"))));
	conflict(cmin(TestPrin1.class), cmax(p3("rws")));
	conflict(cmin(ca(TestPrin2.class, TestPrin3.class)),
		 cmax(pa(p1("rws"), p3("bob"))));
	reduce(cmin(TestPrin1.class),
	       cmax(pa(p1("bob"), p3("bob"), p2("rws"))),
	       cmin(pa(p2("rws"), p1("bob"))));
	conflict(cmin(TestPrin1.class), cmax(TestPrin3.class));
	noreduce(cmin(TestPrin1.class), cmax(TestPrin1.class));
	noreduce(cmin(TestPrin1.class),
		 cmax(ca(TestPrin3.class, TestPrin1.class)));
	reduce(cmin(TestPrin1.class), cmax(TestPrin2.class),
	       cmin(TestPrin2.class));
	noreduce(cmin(TestPrin2.class), cmax(TestPrin1.class));
	noreduce(cmin(TestPrin2.class), cmax(TestPrin2.class));
	reduce(cmin(TestPrin1.class),
	       cmax(ca(TestPrin2.class, TestPrin3.class)),
	       cmin(TestPrin2.class));
	reduce(cmin(ca(TestPrin1.class, TestPrin3.class)),
	       cmax(ca(TestPrin2.class, TestPrin3.class)),
	       cmin(ca(TestPrin2.class, TestPrin3.class)));
	reduce(cmin(ca(TestPrin3.class, TestPrin1.class)),
	       cmax(ca(TestPrin3.class, TestPrin2.class)),
	       cmin(ca(TestPrin2.class, TestPrin3.class)));
	reduce(cmin(TestPrin4.class),
	       cmax(ca(TestPrin6.class, TestPrin7.class)),
	       cmin(ca(TestPrin5.class, Comparable.class)));
	reduce(cmin(TestPrin5.class),
	       cmax(ca(TestPrin6.class, TestPrin7.class)),
	       cmin(ca(TestPrin5.class, Comparable.class)));
	reduce(cmin(Comparable.class),
	       cmax(ca(TestPrin6.class, TestPrin7.class)),
	       cmin(ca(TestPrin5.class, Comparable.class)));
	reduce(cmin(TestPrin4.class),
	       cmax(ca(TestPrin6.class, TestPrin8.class)),
	       cmin(ca(TestPrin5.class)));
	reduce(cmin(TestPrin4.class),
	       cmax(ca(TestPrin6.class, TestPrin7.class, TestPrin8.class)),
	       cmin(ca(TestPrin5.class)));
	noreduce(cmin(TestPrin4.class),
		 cmax(ca(TestPrin6.class, TestPrin9.class)));
	reduce(cmin(ca(TestPrin5.class, Comparable.class)),
	       cmax(TestPrin6.class),
	       cmin(TestPrin6.class));
	noreduce(cmin(ca(TestPrin5.class, Comparable.class)),
		 cmax(ca(TestPrin6.class, TestPrin7.class)));
	reduce(cmin(ca(TestPrin2.class, TestPrin5.class)),
	       cmax(ca(TestPrin1.class, TestPrin6.class)),
	       cmin(ca(TestPrin2.class, TestPrin6.class)));
	reduce(cmin(ca(TestPrin1.class, TestPrin6.class)),
	       cmax(ca(TestPrin2.class, TestPrin4.class)),
	       cmin(ca(TestPrin2.class, TestPrin6.class)));
	reduce(cmin(ca(TestPrin1.class, TestPrin4.class)),
	       cmax(ca(TestPrin2.class, TestPrin6.class, TestPrin7.class)),
	       cmin(ca(TestPrin2.class, TestPrin5.class, Comparable.class)));
	same(alt(cmin(TestPrin2.class), cmin(TestPrin3.class)),
	     sa(cmin(TestPrin2.class), cmin(TestPrin3.class)));
	intersect(cmin(TestPrin1.class),
		  alt(cmin(TestPrin2.class), cmin(TestPrin3.class)),
		  alt(cmin(TestPrin2.class),
		      cmin(ca(TestPrin1.class, TestPrin3.class))));
	noreduce(cmin(TestPrin1.class), alt(cmin(p1("bob")), cmin(p3("rws"))));
	reduce(cmin(TestPrin1.class), alt(cmin(p2("bob")), cmin(p3("rws"))),
	       alt(cmin(TestPrin2.class),
		   cmin(ca(TestPrin1.class, TestPrin3.class))));
	implies(alt(cmin(p1("bob")), cmin(p2("rws"))), cmin(TestPrin1.class));
	reduce(cmin(TestPrin1.class), alt(cmin(p3("rws")), cmin(p4("rws"))),
	       alt(cmin(ca(TestPrin1.class, TestPrin3.class)),
		   cmin(ca(TestPrin1.class, TestPrin4.class))));
	reduce(cmin(TestPrin1.class), alt(cmax(p1("rws")), cmax(p2("rws"))),
	       alt(cmin(p1("rws")), cmin(p2("rws"))));
	conflict(cmin(TestPrin1.class), alt(cmax(p3("rws")), cmax(p4("rws"))));
	reduce(cmin(TestPrin1.class), alt(cmax(p2("rws")), cmax(p3("rws"))),
	       cmin(p2("rws")), cmax(p2("rws")));
	noreduce(cmin(TestPrin6.class),
		 alt(cmax(TestPrin5.class), cmax(Comparable.class)));
	noreduce(cmin(TestPrin6.class),
		 alt(cmax(TestPrin4.class), cmax(Comparable.class)));
	reduce(cmin(TestPrin1.class),
	       alt(cmax(TestPrin2.class), cmax(TestPrin3.class)),
	       cmin(TestPrin2.class), cmax(TestPrin2.class));
	conflict(cmin(TestPrin1.class),
		 alt(cmax(TestPrin3.class), cmax(TestPrin4.class)));
	implies(alt(cmin(p2("rws")), cmin(p3("rws"))),
		alt(cmin(TestPrin2.class), cmin(TestPrin3.class)));
	implies(alt(cmin(p2("rws")), cmin(p3("rws"))),
		alt(cmin(TestPrin1.class), cmin(TestPrin3.class)));
	implies(alt(cmin(p1("rws")), cmin(p2("rws"))),
		alt(cmin(TestPrin1.class), cmin(TestPrin3.class)));
	reduce(alt(cmin(TestPrin1.class), cmin(TestPrin3.class)),
	       alt(cmin(p6("rws")), cmin(p7("bob"))),
	       alt(cmin(ca(TestPrin1.class, TestPrin6.class)),
		   cmin(ca(TestPrin1.class, TestPrin7.class)),
		   cmin(ca(TestPrin3.class, TestPrin6.class)),
		   cmin(ca(TestPrin3.class, TestPrin7.class))));
	reduce(alt(cmin(TestPrin1.class), cmin(TestPrin3.class)),
	       alt(cmax(p1("rws")), cmax(p2("rws"))),
	       alt(cmin(p1("rws")), cmin(p2("rws"))));
	reduce(alt(cmin(TestPrin1.class), cmin(TestPrin3.class)),
	       alt(cmax(pa(p1("rws"), p3("bob"))),
		   cmax(pa(p2("bob"), p3("rws")))),
	       alt(cmin(p1("rws")), cmin(p2("bob")),
		   cmin(p3("bob")), cmin(p3("rws"))));
	conflict(alt(cmin(TestPrin1.class), cmin(TestPrin3.class)),
		 alt(cmax(p4("rws")), cmax(p5("rws"))));
	implies(alt(cmin(ca(TestPrin1.class, TestPrin3.class)),
		    cmin(ca(TestPrin1.class, TestPrin4.class))),
		alt(cmin(TestPrin3.class), cmin(TestPrin4.class)));
	reduce(alt(cmin(TestPrin5.class), cmin(TestPrin3.class)),
	       alt(cmax(TestPrin6.class), cmax(TestPrin7.class),
		   cmax(TestPrin1.class)),
	       alt(cmin(TestPrin6.class), cmin(TestPrin7.class)),
	       alt(cmax(TestPrin6.class), cmax(TestPrin7.class)));
	reduce(alt(cmin(TestPrin5.class), cmin(TestPrin1.class)),
	       alt(cmax(TestPrin6.class), cmax(TestPrin7.class),
		   cmax(TestPrin2.class)),
	       alt(cmin(TestPrin6.class), cmin(TestPrin7.class),
		   cmin(TestPrin2.class)));
	badt(ClientMinPrincipalType.class);

	// ClientMaxPrincipalType
	copy(cmax(TestPrin1.class));
	copy(cmax(ca(TestPrin1.class, TestPrin3.class)));
	implies(ClientAuthentication.NO, cmax(TestPrin2.class));
	same(cmax(TestPrin2.class), cmax(TestPrin2.class));
	same(cmax(TestPrin2.class), cmax(ca(TestPrin2.class)));
	same(cmax(TestPrin1.class),
	     cmax(ca(TestPrin1.class, TestPrin1.class)));
	same(cmax(ca(TestPrin1.class, TestPrin3.class)),
	     cmax(ca(TestPrin3.class, TestPrin1.class)));
	same(cmax(TestPrin1.class),
	     cmax(ca(TestPrin1.class, TestPrin2.class)));
	same(cmax(TestPrin1.class),
	     cmax(ca(TestPrin2.class, TestPrin1.class)));
	implies(cmax(TestPrin2.class), cmax(TestPrin1.class));
	implies(cmax(TestPrin2.class),
		cmax(ca(TestPrin1.class, TestPrin3.class)));
	conflict(cmax(TestPrin1.class), cmax(TestPrin3.class));
	intersect(cmax(ca(TestPrin1.class, TestPrin5.class)),
		  cmax(ca(TestPrin2.class, TestPrin4.class)),
		  cmax(ca(TestPrin2.class, TestPrin5.class)));
	implies(cmax(ca(TestPrin6.class, TestPrin7.class)),
		cmax(TestPrin4.class));
	implies(cmax(ca(TestPrin6.class, TestPrin7.class)),
		cmax(ca(TestPrin4.class, TestPrin1.class)));
	implies(cmax(ca(TestPrin6.class, TestPrin7.class)),
		cmax(ca(TestPrin5.class, Comparable.class)));
	intersect(cmax(ca(TestPrin6.class, TestPrin1.class, TestPrin7.class)),
		  cmax(ca(TestPrin5.class, TestPrin2.class)),
		  cmax(ca(TestPrin6.class, TestPrin2.class, TestPrin7.class)));
	conflict(cmax(ca(TestPrin1.class, TestPrin6.class, TestPrin7.class)),
		 cmax(ca(TestPrin3.class, TestPrin8.class, TestPrin9.class)));
	implies(cmax(p1("rws")), cmax(TestPrin1.class));
	implies(cmax(p2("rws")), cmax(TestPrin1.class));
	implies(cmax(pa(p1("rws"), p2("bob"))), cmax(TestPrin1.class));
	same(alt(cmax(TestPrin2.class), cmax(TestPrin3.class)),
	     sa(cmax(TestPrin2.class), cmax(TestPrin3.class)));
	intersect(cmax(TestPrin1.class),
		  alt(cmax(TestPrin2.class), cmax(TestPrin3.class)),
		  cmax(TestPrin2.class));
	implies(alt(cmax(TestPrin6.class), cmax(TestPrin8.class)),
		cmax(TestPrin5.class));
	intersect(cmax(ca(TestPrin1.class, TestPrin3.class)),
		  alt(cmax(ca(TestPrin2.class, TestPrin6.class)),
		      cmax(ca(TestPrin3.class, TestPrin7.class))),
		  alt(cmax(TestPrin2.class), cmax(TestPrin3.class)));
	conflict(cmax(TestPrin1.class),
		 alt(cmax(TestPrin3.class), cmax(TestPrin4.class)));
	implies(alt(cmax(p6("rws")), cmax(p7("rws"))),
		cmax(TestPrin4.class));
	intersect(cmax(ca(TestPrin1.class)),
		  alt(cmax(p1("rws")), cmax(p3("rws"))),
		  cmax(p1("rws")));
	intersect(cmax(ca(TestPrin1.class, TestPrin3.class)),
		  alt(cmax(pa(p2("rws"), p6("rws"))),
		      cmax(pa(p3("rws"), p7("rws")))),
		  alt(cmax(p2("rws")), cmax(p3("rws"))));
	conflict(cmax(TestPrin1.class),
		 alt(cmax(p3("rws")), cmax(p4("rws"))));
	noreduce(cmax(TestPrin1.class),
		 alt(cmin(p1("rws")), cmin(p2("rws"))));
	conflict(cmax(TestPrin1.class),
		 alt(cmin(p3("rws")), cmin(p4("rws"))));
	reduce(alt(cmax(TestPrin3.class), cmax(TestPrin1.class)),
	       cmin(p2("rws")),
	       cmax(TestPrin1.class));
	reduce(alt(cmax(TestPrin3.class), cmax(TestPrin1.class)),
	       alt(cmin(p2("rws")), cmin(p4("bob"))),
	       cmax(TestPrin1.class), cmin(p2("rws")));
	reduce(alt(cmax(TestPrin3.class), cmax(TestPrin4.class),
		   cmax(TestPrin1.class)),
	       alt(cmin(p2("rws")), cmin(p4("bob"))),
	       alt(cmax(TestPrin1.class), cmax(TestPrin4.class)));
	badt(ClientMaxPrincipalType.class);

	// DelegationRelativeTime
	copy(rel(-10, 0, 10, 30));
	implies(ClientAuthentication.NO, rel(-10, 0, 10, 30));
	implies(Delegation.NO, rel(-10, 0, 10, 50));
	same(rel(-10, 0, 10, 50), rel(-10, 0, 10, 50));
	same(rel(-100, -10, 0, 500), rel(-100, -10, 0, 500));
	conflict(rel(0, 0, 10, 50), rel(0, 0, 60, 100));
	implies(rel(0, 0, 10, 50), rel(0, 0, 0, 60));
	implies(rel(0, 0, 40, 40), rel(0, 0, 40, 60));
	implies(rel(0, 0, 60, 60), rel(0, 0, 40, 60));
	intersect(rel(0, 0, 10, 50), rel(0, 0, 40, 70), rel(0, 0, 40, 50));
	intersect(rel(10, 50, 100, 100), rel(-10, 20, 100, 100),
		  rel(10, 20, 100, 100));
	intersect(rel(0, 30, 50, 100), rel(30, 40, 50, 100),
		  rel(30, 30, 50, 100));
	conflict(rel(0, 0, 10, 30), alt(rel(0, 0, 0, 5), rel(0, 0, 40, 50)));
	implies(rel(0, 0, 10, 30), alt(rel(0, 0, 0, 30), rel(0, 0, 10, 40)));
	intersect(rel(0, 0, 10, 30), alt(rel(0, 0, 0, 20), rel(0, 0, 20, 40)),
		  alt(rel(0, 0, 10, 20), rel(0, 0, 20, 30)));
	same(alt(rel(0, 0, 10, 30), rel(0, 0, 50, 80)),
	     sa(rel(0, 0, 10, 30), rel(0, 0, 50, 80)));
	implies(rel(0, 0, 15, 25), alt(rel(0, 0, 10, 30), rel(0, 0, 50, 80)));
	implies(rel(0, 0, 10, 20), alt(rel(0, 0, 5, 25), rel(0, 0, 15, 30)));
	implies(alt(rel(0, 0, 15, 25), rel(0, 0, 35, 45)),
		alt(rel(0, 0, 10, 30), rel(0, 0, 30, 50)));
	intersect(alt(rel(0, 0, 10, 30), rel(0, 0, 30, 50)),
		  alt(rel(0, 0, 15, 35), rel(0, 0, 20, 45)),
		  alt(rel(0, 0, 15, 30), rel(0, 0, 30, 45)));
	intersect(alt(rel(0, 0, 10, 30), rel(0, 0, 30, 50)),
		  alt(rel(0, 0, 20, 40), rel(0, 0, 0, 15)),
		  alt(rel(0, 0, 20, 30), rel(0, 0, 30, 40),
		      rel(0, 0, 10, 15)));
	intersect(alt(rel(0, 0, 10, 30), rel(0, 0, 40, 60)),
		  alt(rel(0, 0, 20, 35), rel(0, 0, 50, 75)),
		  alt(rel(0, 0, 20, 30), rel(0, 0, 50, 60)));
	intersect(alt(rel(0, 0, 10, 30), rel(0, 0, 40, 60)),
		  alt(rel(0, 0, 20, 35), rel(0, 0, 0, 15)),
		  alt(rel(0, 0, 20, 30), rel(0, 0, 10, 15)));
	conflict(alt(rel(0, 0, 10, 30), rel(0, 0, 70, 80)),
		 alt(rel(0, 0, 0, 5), rel(0, 0, 40, 50)));
	same(rel(-10, 0, 10, 50).makeAbsolute(30), abs(20, 30, 40, 80));
	same(((RelativeTimeConstraint)alt(rel(-10, 0, 10, 50),
					  rel(0, 5, 60, 90))).makeAbsolute(30),
	     alt(abs(20, 30, 40, 80), abs(30, 35, 90, 120)));
	same(rel(Long.MIN_VALUE, 0, 1, Long.MAX_VALUE).makeAbsolute(
							      Long.MIN_VALUE),
	     abs(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1, -1));
	same(rel(Long.MIN_VALUE, 0, 1, Long.MAX_VALUE).makeAbsolute(
							      Long.MAX_VALUE),
	     abs(-1, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
	iae(DelegationRelativeTime.class, 20, 10, 30, 40);
	iae(DelegationRelativeTime.class, 10, 20, 15, 40);
	iae(DelegationRelativeTime.class, 10, 20, 30, 25);
	iae(DelegationRelativeTime.class, -30, -20, -10, 0);

	// DelegationAbsoluteTime
	copy(abs(-10, 0, 10, 30));
	implies(ClientAuthentication.NO, abs(-10, 0, 10, 30));
	implies(Delegation.NO, abs(-10, 0, 10, 50));
	same(abs(-10, 0, 10, 50), abs(-10, 0, 10, 50));
	same(abs(-100, -10, 0, 500), abs(-100, -10, 0, 500));
	conflict(abs(0, 0, 10, 50), abs(0, 0, 60, 100));
	implies(abs(0, 0, 10, 50), abs(0, 0, 0, 60));
	implies(abs(0, 0, 40, 40), abs(0, 0, 40, 60));
	implies(abs(0, 0, 60, 60), abs(0, 0, 40, 60));
	intersect(abs(0, 0, 10, 50), abs(0, 0, 40, 70), abs(0, 0, 40, 50));
	intersect(abs(10, 50, 100, 100), abs(-10, 20, 100, 100),
		  abs(10, 20, 100, 100));
	intersect(abs(0, 30, 50, 100), abs(30, 40, 50, 100),
		  abs(30, 30, 50, 100));
	conflict(abs(0, 0, 10, 30), alt(abs(0, 0, 0, 5), abs(0, 0, 40, 50)));
	implies(abs(0, 0, 10, 30), alt(abs(0, 0, 0, 30), abs(0, 0, 10, 40)));
	intersect(abs(0, 0, 10, 30), alt(abs(0, 0, 0, 20), abs(0, 0, 20, 40)),
		  alt(abs(0, 0, 10, 20), abs(0, 0, 20, 30)));
	same(alt(abs(0, 0, 10, 30), abs(0, 0, 50, 80)),
	     sa(abs(0, 0, 10, 30), abs(0, 0, 50, 80)));
	implies(abs(0, 0, 15, 25), alt(abs(0, 0, 10, 30), abs(0, 0, 50, 80)));
	implies(abs(0, 0, 10, 20), alt(abs(0, 0, 5, 25), abs(0, 0, 15, 30)));
	implies(alt(abs(0, 0, 15, 25), abs(0, 0, 35, 45)),
		alt(abs(0, 0, 10, 30), abs(0, 0, 30, 50)));
	intersect(alt(abs(0, 0, 10, 30), abs(0, 0, 30, 50)),
		  alt(abs(0, 0, 15, 35), abs(0, 0, 20, 45)),
		  alt(abs(0, 0, 15, 30), abs(0, 0, 30, 45)));
	intersect(alt(abs(0, 0, 10, 30), abs(0, 0, 30, 50)),
		  alt(abs(0, 0, 20, 40), abs(0, 0, 0, 15)),
		  alt(abs(0, 0, 20, 30), abs(0, 0, 30, 40),
		      abs(0, 0, 10, 15)));
	intersect(alt(abs(0, 0, 10, 30), abs(0, 0, 40, 60)),
		  alt(abs(0, 0, 20, 35), abs(0, 0, 50, 75)),
		  alt(abs(0, 0, 20, 30), abs(0, 0, 50, 60)));
	intersect(alt(abs(0, 0, 10, 30), abs(0, 0, 40, 60)),
		  alt(abs(0, 0, 20, 35), abs(0, 0, 0, 15)),
		  alt(abs(0, 0, 20, 30), abs(0, 0, 10, 15)));
	conflict(alt(abs(0, 0, 10, 30), abs(0, 0, 70, 80)),
		 alt(abs(0, 0, 0, 5), abs(0, 0, 40, 50)));
	iae(DelegationAbsoluteTime.class, 20, 10, 30, 40);
	iae(DelegationAbsoluteTime.class, 10, 20, 15, 40);
	iae(DelegationAbsoluteTime.class, 10, 20, 30, 25);

	// ConnectionRelativeTime
	copy(rel(30));
	same(rel(50), rel(50));
	same(alt(rel(30), rel(80)), sa(rel(30), rel(80)));
	same(rel(50).makeAbsolute(30), abs(80));
	same(rel(1).makeAbsolute(Long.MAX_VALUE), abs(Long.MAX_VALUE));
	same(rel(Long.MAX_VALUE).makeAbsolute(Long.MAX_VALUE),
	     abs(Long.MAX_VALUE));
	same(rel(Long.MAX_VALUE).makeAbsolute(Long.MIN_VALUE), abs(-1));
	same(((RelativeTimeConstraint)alt(rel(50),
					  rel(90))).makeAbsolute(30),
	     alt(abs(80), abs(120)));
	iae(ConnectionRelativeTime.class, -1);

	// ConnectionAbsoluteTime
	copy(abs(30));
	same(abs(50), abs(50));
	same(abs(-100), abs(-100));
	same(alt(abs(30), abs(80)), sa(abs(30), abs(80)));

	// InvocationConstraints
	same(sc(sa(rel(-10, 0, 10, 50), Delegation.YES,
		   ClientAuthentication.NO),
		null),
	     sa(rel(-10, 0, 10, 50), Delegation.YES, ClientAuthentication.NO),
	     none, false);
	same(sc(sa(cmin(TestPrin1.class), cmax(pa(p2("rws"), p3("bob"))),
		   cmin(p3("bob"))),
		   null),
	     sa(cmin(TestPrin1.class), cmax(pa(p2("rws"), p3("bob"))),
		cmin(p3("bob"))),
	     none, false);
	same(sc(sa(cmin(TestPrin3.class), cmax(pa(p2("rws"), p3("bob"))),
		   cmin(p2("rws"))),
		   null),
	     sa(cmin(TestPrin3.class), cmax(pa(p2("rws"), p3("bob"))),
		cmin(p2("rws"))),
	     none, false);
	same(sc(sa(cmin(TestPrin1.class), cmin(p2("rws")),
		   cmin(TestPrin3.class), cmin(p3("bob"))),
		null),
	     sa(cmin(TestPrin1.class), cmin(p2("rws")),
		cmin(TestPrin3.class), cmin(p3("bob"))),
	     none, false);
	same(sc(sa(cmin(TestPrin1.class), cmin(TestPrin3.class)),
		sa(cmin(ca(TestPrin1.class, TestPrin3.class)))),
	     sa(cmin(TestPrin1.class), cmin(TestPrin3.class)),
	     sa(cmin(ca(TestPrin1.class, TestPrin3.class))),
	     false);
	same(sc(sa(cmax(ca(TestPrin1.class, TestPrin3.class, TestPrin7.class)),
		   cmax(pa(p2("rws"), p4("bob"))),
		   cmax(pa(p2("rws"), p5("bob")))),
		null),
	     sa(cmax(ca(TestPrin1.class, TestPrin3.class, TestPrin7.class)),
		cmax(pa(p2("rws"), p4("bob"))),
		cmax(pa(p2("rws"), p5("bob")))),
	     none, false);
	same(sc(sa(cmax(ca(TestPrin1.class, TestPrin3.class, TestPrin4.class)),
		   cmax(ca(TestPrin3.class, TestPrin5.class)),
		   cmax(ca(TestPrin6.class, TestPrin7.class))),
		null),
	     sa(cmax(ca(TestPrin1.class, TestPrin3.class, TestPrin4.class)),
		cmax(ca(TestPrin3.class, TestPrin5.class)),
		cmax(ca(TestPrin6.class, TestPrin7.class))),
	     none, false);
	same(sc(sa(alt(cmax(p2("rws")), cmax(p3("bob"))),
		   alt(cmax(p1("rws")), cmax(p2("bob"))),
		   alt(cmax(p3("bob")), cmax(p4("rws"))),
		   alt(cmax(p6("rws")), cmax(p1("rws")))),
		null),
	     sa(alt(cmax(p2("rws")), cmax(p3("bob"))),
		alt(cmax(p1("rws")), cmax(p2("bob"))),
		alt(cmax(p3("bob")), cmax(p4("rws"))),
		alt(cmax(p6("rws")), cmax(p1("rws")))),
	     none, true);
	same(sc(sa(alt(cmax(p2("rws")), cmax(p3("bob"))),
		   alt(cmax(p1("rws")), cmax(p2("bob"))),
		   alt(cmax(p3("bob")), cmax(p4("rws"))),
		   alt(cmax(p6("rws")), cmax(p1("rws")))),
		sa(cmax(pa(p3("bob"), p4("rws"))),
		   cmax(pa(p2("bob"), p1("rws"))))),
	     sa(alt(cmax(p2("rws")), cmax(p3("bob"))),
		alt(cmax(p1("rws")), cmax(p2("bob"))),
		alt(cmax(p3("bob")), cmax(p4("rws"))),
		alt(cmax(p6("rws")), cmax(p1("rws")))),
	     sa(cmax(pa(p3("bob"), p4("rws"))),
		cmax(pa(p2("bob"), p1("rws")))),
	     true);
	same(sc(sa(cmin(p1("rws")), cmax(pa(p1("rws"), p3("bob")))),
		sa(cmin(p4("rws")), cmax(p4("rws")), cmax(TestPrin7.class))),
	     sa(cmin(p1("rws")), cmax(pa(p1("rws"), p3("bob")))),
	     sa(cmin(p4("rws")), cmax(p4("rws")), cmax(TestPrin7.class)),
	     false);
	npe(sa(null), none);
	npe(sa(Delegation.YES, null), none);
	npe(none, sa(null, Delegation.NO));
    }
}
