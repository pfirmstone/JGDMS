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
/* @test %W% %E%
 * @summary Tests the WeakSoftTable class.
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @build TestUtilities
 * @run main/othervm/policy=policy TestWeakSoftTable
 */

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.*;
import com.sun.jini.collection.WeakSoftTable;

public abstract class TestWeakSoftTable extends TestUtilities {

    static class Key {
	private static int nextIndex;
	private final String name;
	private final int index = ++nextIndex;
	Key(String name) { this.name = name; }
	public int hashCode() { return name.hashCode(); }
	public boolean equals(Object o) {
	    return (o instanceof Key) && name.equals(((Key) o).name);
	}
	public String toString() {
	    return "key" + name + "[" + index + "]";
	}
    }

    static class WeakKey extends WeakSoftTable.WeakKey {
	WeakKey(Object key) { super(key); }
	private WeakKey(WeakKey weakKey, ReferenceQueue queue) {
	    super(weakKey, queue);
	}
	public WeakSoftTable.RemovableReference copy(ReferenceQueue queue) {
	    return new WeakKey(this, queue);
	}
	public String toString() {
	    return String.valueOf(get());
	}
    }

    static class SoftValue extends WeakSoftTable.SoftValue {
	SoftValue(WeakKey key, Object value) { super(key, value); }
	private SoftValue(SoftValue softValue, ReferenceQueue queue) {
	    super(softValue, queue);
	}
	public WeakSoftTable.RemovableReference copy(ReferenceQueue queue) {
	    return new SoftValue(this, queue);
	}
	public String toString() {
	    return String.valueOf(get());
	}
    }

    public static Collection tests = new ArrayList();

    static final Key keyA = new Key("A");
    static final String valA = "valA";
    static final Key keyA2 = new Key("A");
    static final String valA2 = "valA2";
    static final Key keyB = new Key("B");
    static final String valB = "valB";
    static final String valB2 = "valB2";
    static final Key keyX = new Key("X");

    static final LazyField hash = new LazyField(
	"com.sun.jini.collection", "WeakSoftTable", "hash");

    public static void main(String[] args) {
	test(tests);
    }

    static Object[] array(Object a) {
	return new Object[] { a };
    }

    static Object[] array(Object a, Object b) {
	return new Object[] { a, b };
    }

    static Object[] array(Object a, Object b, Object c, Object d) {
	return new Object[] { a, b, c, d };
    }

    static boolean contains(Collection c, Object o) {
	for (Iterator iter = c.iterator(); iter.hasNext(); ) {
	    if (iter.next() == o) {
		return true;
	    }
	}
	return false;
    }

    static abstract class LocalTest extends BasicTest {
	WeakSoftTable table;
	Object[] keysAndValues;
	Collection clear;

	LocalTest(String name,
		  Object[] keysAndValues,
		  Object[] clear,
		  Object result)
	{
	    super(name +
		  "\n  keysAndValues = " + toString(keysAndValues) +
		  (clear == null ? "" :
		   "\n  clear = " + toString(clear)),
		  result);
	    /* Hold reference to the keys and values so they don't get GC'ed */
	    this.keysAndValues = (keysAndValues == null) ? new Object[0]
		: keysAndValues;
	    this.clear = (clear == null) ? Collections.EMPTY_LIST
		: Arrays.asList(clear);
	}

	Object get(Object key, int index) {
	    SoftValue softValue =
		(SoftValue) table.get(new WeakKey(key), index);
	    return softValue == null ? null : softValue.get();
	}

	void add(Object key, Object value) {
	    WeakKey weakKey = new WeakKey(key);
	    table.add(weakKey, new SoftValue(weakKey, value));
	}
    
	Object remove(Object key, int index) {
	    SoftValue softValue =
		(SoftValue) table.remove(new WeakKey(key), index);
	    return softValue == null ? null : softValue.get();
	}

	void maybeClear(boolean force) {
	    if (clear == null) {
		return;
	    }
	    for (Iterator iter = getHash().entrySet().iterator();
		 iter.hasNext(); )
	    {
		Map.Entry entry = (Map.Entry) iter.next();
		Reference key = (Reference) entry.getKey();
		if (contains(clear, key.get())) {
		    key.clear();
		    key.enqueue();
		}
		List list = (List) entry.getValue();
		for (int i = list.size(); --i >= 0; ) {
		    Reference value = (Reference) list.get(i);
		    if (contains(clear, value.get())) {
			value.clear();
			value.enqueue();
		    }
		}
	    }

	    /* Force processing queue */
	    if (force) {
		remove(keyX, 0);
	    }
	}

	void initTable() {
	    table = new WeakSoftTable();
	    for (int i = 0; i < keysAndValues.length; i += 2) {
		add(keysAndValues[i], keysAndValues[i+1]);
	    }
	}

	Map getHash() {
	    return (Map) hash.get(table);
	}
    }

    static {
	tests.add(TestGet.localtests);
    }

    public static class TestGet extends LocalTest {
	static Test[] localtests = {
	    new TestGet(keyA, 0, null, null, null),
	    new TestGet(keyA, 33, null, null, null),
	    new TestGet(keyA, 0, array(keyA, valA), null, valA),
	    new TestGet(keyA, 1, array(keyA, valA), null, null),
	    new TestGet(keyA, 0, array(keyA2, valA), null, null),
	    new TestGet(keyA, 0, array(keyB, valA), null, null),
	    new TestGet(keyA, 0, array(keyA, valA), array(keyA), null),
	    new TestGet(keyA, 0, array(keyA, valA), array(valA), null),
	    new TestGet(keyA, 0, array(keyA, valA, keyA, valA2), null, valA),
	    new TestGet(keyA, 1, array(keyA, valA, keyA, valA2), null, valA2),
	    new TestGet(keyA, 0, array(keyA, valA, keyA, valA2), array(keyA),
			null),
	    new TestGet(keyA, 0, array(keyA, valA, keyA, valA2), array(valA),
			valA2),
	    new TestGet(keyA, 0, array(keyA, valA, keyA, valA2), array(valA2),
			valA),
	    new TestGet(keyA, 0, array(keyA, valA, keyA, valA2),
			array(valA, valA2), null),
	    new TestGet(keyA, 1, array(keyA, valA, keyA, valA2), array(valA),
			null),
	    new TestGet(keyA, 0, array(keyA, valA, keyB, valB), array(keyB),
			valA)
	};

	final Object key;
	final int index;

	TestGet(Object key,
		int index,
		Object[] keysAndValues,
		Object[] clear,
		Object result)
	{
	    super("get(" + key + ", " + index + ")",
		  keysAndValues, clear, result);
	    this.key = key;
	    this.index = index;
	}

	public Object run() {
	    initTable();
	    maybeClear(true);
	    return get(key, index);
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    super.check(get(key, index));
	}
    }

    static {
	tests.add(TestAdd.localtests);
    }

    public static class TestAdd extends LocalTest {
	static Test[] localtests = {
	    new TestAdd(keyA, valA, null, null, 0),
	    new TestAdd(keyA, valA2, array(keyA, valA), null, 1),
	    new TestAdd(keyA, valA2, array(keyA, valA), array(keyA), 0),
	    new TestAdd(keyA, valA2, array(keyA, valA), array(valA), 0)
	};

	final Object key;
	final int index;

	TestAdd(Object key,
		Object value,
		Object[] keysAndValues,
		Object[] clear,
		int index)
	{
	    super("add(" + key + ", " + value + ")",
		  keysAndValues, clear, value);
	    this.key = key;
	    this.index = index;
	}

	public Object run() {
	    initTable();
	    maybeClear(false);
	    add(key, getCompareTo());
	    return get(key, index);
	}
    }

    static {
	tests.add(TestRemove.localtests);
    }

    public static class TestRemove extends LocalTest {
	static Test[] localtests = {
	    new TestRemove(keyA, 0, null, null, null, null),
	    new TestRemove(keyA, 0, array(keyA, valA), null, valA, null),
	    new TestRemove(keyA, 0, array(keyA, valA, keyA, valA2), null,
			   valA, valA2)
	};

	final Object key;
	final int index;
	final Object nextResult;

	TestRemove(Object key,
		   int index,
		   Object[] keysAndValues,
		   Object[] clear,
		   Object result,
		   Object nextResult)
	{
	    super("remove(" + key + ", " + index + ")",
		  keysAndValues, clear, result);
	    this.key = key;
	    this.index = index;
	    this.nextResult = nextResult;
	}

	public Object run() {
	    initTable();
	    maybeClear(false);
	    return remove(key, index);
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    Object get = get(key, index);
	    if (get != nextResult) {
		throw new FailedException("Wrong next value, found " + get +
					  ", expected " + nextResult);
	    }
	}
    }
}
