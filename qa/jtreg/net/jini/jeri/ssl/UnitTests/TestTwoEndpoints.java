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
 * @summary Tests exports on one ListenEndpoint but different ServerEndpoints
 * @author Fred Oliver
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test TestUtilities
 * @run main/othervm/policy=policy TestTwoEndpoints
 */

import java.rmi.Remote;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;

public class TestTwoEndpoints extends TestUtilities implements Test {
    private static final String HOST = "foo";
    private static final int PORT = 0;

    public static void main(String[] args) throws Exception {
	test(tests);
    }

    static final Test[] tests = { new TestTwoEndpoints() };


    public String name() {
	return "TestTwoEndpoints";
    }

    public Object run() throws Exception {
	ServerEndpoint se1 = SslServerEndpoint.getInstance(PORT);
	ServerEndpoint se2 = SslServerEndpoint.getInstance(HOST, PORT);
	InvocationLayerFactory ilf = new BasicILFactory();
	Exporter e1 = new BasicJeriExporter(se1, ilf);
	Exporter e2 = new BasicJeriExporter(se2, ilf);
	e1.export(new Remote() { });
	e2.export(new Remote() { });

	Thread.currentThread().sleep(5000);

	// If we got here, success. Otherwise an exception should
	// have been thrown. (IllegalArgumentException)

	return null;
    }

    public void check(Object result) {
    }
}
