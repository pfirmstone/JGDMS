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
/*
 * @test
 * @summary Tests UnicodeEscapesDecodingReader
 * @author Tim Blackman
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm/policy=policy TestUnicodeEscapesDecodingReader
 */

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** Tests UnicodeEscapesDecodingReader. */
public class TestUnicodeEscapesDecodingReader extends UnitTestUtilities {

    static final String src;

    static {
        String root = System.getProperty("test.root", ".");  
        StringBuilder sb = new StringBuilder();
        sb.append(root).append(File.separator).append("net").append(File.separator);
        sb.append("jini").append(File.separator).append("config");
        sb.append(File.separator).append("TestAPI").append(File.separator);
        src = sb.toString();
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    static List tests = new ArrayList();

    static Constructor cons;

    public static void main(String[] args) {
	test(tests);
    }

    static Reader createReader(Reader reader) {
	try {
	    if (cons == null) {
		Class cl = Class.forName(
		    "net.jini.config." +
		    "UnicodeEscapesDecodingReader");
		cons = cl.getDeclaredConstructor(
		    new Class[] { Reader.class });
		cons.setAccessible(true);
	    }
	    return (Reader) cons.newInstance(new Object[] { reader });
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    if (cause instanceof RuntimeException) {
		throw (RuntimeException) cause;
	    } else {
		throw unexpectedException(cause);
	    }
	} catch (RuntimeException e) {
	    throw e;
	} catch (Exception e) {
	    throw unexpectedException(e);
	}
    }

    /* -- Test reading Unicode escapes, one char at a time -- */

    static {
	tests.add(TestEscapes.localtests);
    }

    public static class TestEscapes extends BasicTest {
	static Test[] localtests = {
	    t("", ""),
	    t("abc123", "abc123"),
	    t("\\", "\\"),
	    t("\\a", "\\a"),
	    t("\\\\u", "\\\\u"),
	    t("\\\\ux", "\\\\ux"),
	    t("\\u0061\\u004c", "aL"),
	    t("\\u1234\\uABCD", "\u1234\uabcd"),
	    t("\\\\\\uuuuuuu007e", "\\\\~"),
	    t("\\u0061\\aubc", "a\\aubc"),
	    t("\\u", IOException.class),
	    t("\\uuu", IOException.class),
	    t("\\u1", IOException.class),
	    t("\\u12", IOException.class),
	    t("\\u123", IOException.class),
	    t("\\u-123", IOException.class),
	    t("\\u123x", IOException.class)
	};

	private final String input;
	private Reader reader;

	static Test t(String input, Object result) {
	    return new TestEscapes(input, result);
	}

	private TestEscapes(String input, Object result) {
	    super(input, result);
	    this.input = input;
	}

	public Object run() throws Exception {
	    reader = createReader(new StringReader(input));
	    try {
		StringBuffer result = new StringBuffer();
		while (true) {
		    int c = reader.read();
		    if (c < 0) {
			break;
		    }
		    result.append((char) c);
		}
		return result.toString();
	    } catch (Exception e) {
		return e;
	    }
	}

	public void check(Object result) throws IOException {
	    reader.close();
	    try {
		reader.read();
		throw new FailedException("No exception reading after close");
	    } catch (IOException e) {
	    }
	    Object compareTo = getCompareTo();
	    if (safeEquals(result, compareTo) ||
		(compareTo instanceof Class &&
		 result != null &&
		 result.getClass() == compareTo) ||
		(compareTo instanceof Throwable &&
		 result instanceof Throwable &&
		 safeEquals(((Throwable) compareTo).getMessage(),
			    ((Throwable) result).getMessage())))
	    {
		return;
	    }
	    throw new FailedException("Should be: " + compareTo);
	}
    }

    /* -- Test passing null arguments -- */

    static {
	tests.add(
	    new BasicTest("null reader", NullPointerException.class) {
		public Object run() {
		    try {
			return createReader(null);
		    } catch (Exception e) {
			return e;
		    }
		}
		public void check(Object result) throws Exception {
		    super.check(result.getClass());
		}
	    });
	tests.add(
	    new BasicTest("null char buffer", NullPointerException.class) {
		public Object run() throws IOException {
		    Reader reader = createReader(new StringReader("hello"));
		    try {
			reader.read(null, 0, 0);
			return null;
		    } catch (Exception e) {
			return e;
		    }
		}
		public void check(Object result) throws Exception {
		    super.check(result != null ? result.getClass() : null);
		}
	    });
    }

    /* -- Test arbitrary calls to read with a char array -- */

    static {
	tests.add(ReadTest.localtests);
    }

    public static class ReadTest extends BasicTest {
	static Test[] localtests = {
	    new ReadTest(-1, 2, "abcd", "wxyz", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(5, 0, "abcd", "wxyz", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(1, 0, "abcd", "", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(2, -1, "abcd", "wxyz", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(0, 5, "abcd", "wxyz", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(0, 1, "abcd", "", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(3, 3, "abcd", "wxyz", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(3, Integer.MAX_VALUE, "abcd", "wxyz", 0,
			 IndexOutOfBoundsException.class),
	    new ReadTest(0, 0, "abcd", "xyzw", 0, "xyzw"),
	    new ReadTest(0, 0, "abcd", "", 0, ""),
	    new ReadTest(0, 1, "", "xyzw", -1, "xyzw"),
	    new ReadTest(1, 4, "abcd", "uvwxyz", 4, "uabcdz"),
	    new ReadTest(4, 2, "abcd", "uvwxyz", 2, "uvwxab"),
	    new ReadTest(2, 4, "ab", "uvwxyz", 2, "uvabyz")
	};

	private final int off;
	private final int len;
	private final String source;
	private final String sink;
	private final int nchars;
	private Reader reader;

	ReadTest(int off, int len, String source, String sink, int nchars,
		 Object shouldBe)
	{
	    super(off + ", " + len + ", " + source + ", " + sink, shouldBe);
	    this.off = off;
	    this.len = len;
	    this.source = source;
	    this.sink = sink;
	    this.nchars = nchars;
	}

	public Object run() throws IOException {
	    reader = createReader(new StringReader(source));
	    char[] into = sink.toCharArray();
	    int got;
	    try {
		got = reader.read(into, off, len);
	    } catch (RuntimeException e) {
		return e;
	    }
	    if (got != nchars) {
		throw new FailedException(
		    "Got " + got + " chars, expected " + nchars);
	    }
	    return new String(into);
	}

	public void check(Object result) {
	    Object compareTo = getCompareTo();
	    if (safeEquals(result, compareTo) ||
		(compareTo instanceof Class &&
		 result != null &&
		 result.getClass() == compareTo) ||
		(compareTo instanceof Throwable &&
		 result instanceof Throwable &&
		 safeEquals(((Throwable) compareTo).getMessage(),
			    ((Throwable) result).getMessage())))
	    {
		return;
	    }
	    throw new FailedException("Should be: " + compareTo);
	}
    }
}
