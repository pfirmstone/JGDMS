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
 * @summary test BasicMethodConstraints.getConstraints
 * 
 * @run main/othervm GetConstraints
 */
import java.lang.reflect.Method;
import net.jini.constraint.BasicMethodConstraints.MethodDesc;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;

public class GetConstraints {

    static final InvocationConstraints sc =
			new InvocationConstraints(Integrity.YES, null);

    static MethodDesc desc(String n) {
	return new MethodDesc(n, sc);
    }

    static MethodDesc desc(String n, Class[] t) {
	return new MethodDesc(n, t, sc);
    }

    static void match(Method m, MethodDesc desc) {
	BasicMethodConstraints mc =
	    new BasicMethodConstraints(new MethodDesc[]{desc});
	if (mc.getConstraints(m) != desc.getConstraints()) {
	    throw new RuntimeException("failed to match");
	}
    }

    static void nomatch(Method m, MethodDesc desc) {
	BasicMethodConstraints mc =
	    new BasicMethodConstraints(new MethodDesc[]{desc});
	if (!mc.getConstraints(m).isEmpty()) {
	    throw new RuntimeException("incorrectly matched");
	}
    }

    public static void main(String[] args) throws Exception {
	Method m = String.class.getMethod("length", null);
	match(m, desc("length", new Class[0]));
	match(m, desc("length"));
	match(m, desc("*length"));
	match(m, desc("length*"));
	match(m, desc("*th"));
	match(m, desc("le*"));
	match(m, new MethodDesc(sc));
	nomatch(m, desc("lengt", new Class[0]));
	nomatch(m, desc("ength", new Class[0]));
	nomatch(m, desc("length", new Class[]{String.class}));
	nomatch(m, desc("get*"));
	nomatch(m, desc("*bar"));
	Class[] t = new Class[]{int.class, String.class, int.class, int.class};
	m = String.class.getMethod("regionMatches", t);
	match(m, desc("regionMatches", t));
	match(m, desc("regionMatches"));
	match(m, desc("*Matches"));
	match(m, desc("region*"));
	match(m, new MethodDesc(sc));
	nomatch(m, desc("regionMatches", new Class[0]));
	nomatch(m, desc("regionMatches",
			new Class[]{boolean.class, String.class,
				    int.class, int.class}));
    }
}
