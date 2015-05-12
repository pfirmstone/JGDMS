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
 * @summary Test HTTP connection reuse.
 * @build Echo Test CountedSocketFactory
 * @run main/othervm Test
 * @run main/othervm -Dorg.apache.river.jeri.http.idleConnectionTimeout=0 Test
 */

import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.http.HttpServerEndpoint;

public class Test {

    private static final long DEFAULT_IDLE_CONNECTION_TIMEOUT = 15000;

    public static void main(String[] args) throws Exception {
	long connTimeout =
	    Long.getLong("org.apache.river.jeri.http.idleConnectionTimeout",
			 DEFAULT_IDLE_CONNECTION_TIMEOUT).longValue();

	CountedSocketFactory csf = new CountedSocketFactory();
	Exporter exporter = new BasicJeriExporter(
	    HttpServerEndpoint.getInstance(null, 0, csf, null),
	    new BasicILFactory());
	EchoImpl impl = new EchoImpl();
	Echo stub = (Echo) exporter.export(impl);

	try {
	    final int REPS = 3;
	    for (int i = 0; i < REPS; i++) {
		if (stub.echo(i) != i) {
		    throw new Error();
		}
	    }

	    int expectedSocketsCreated = (connTimeout > 0) ? 1 : REPS;
	    if (csf.clientSocketsCreated != expectedSocketsCreated) {
		throw new Error(
		    "wrong number of sockets created: " +
		    csf.clientSocketsCreated + " instead of " +
		    expectedSocketsCreated);
	    }

	    if (connTimeout > 0) {
		for (int i = 0; i < REPS; i++) {
		    sleep(connTimeout);
		    if (stub.echo(i) != i) {
			throw new Error();
		    }
		}
		expectedSocketsCreated += REPS;
		if (csf.clientSocketsCreated != expectedSocketsCreated) {
		    throw new Error(
			"wrong number of sockets created: " +
			csf.clientSocketsCreated + " instead of " +
			expectedSocketsCreated);
		}
	    }
	} finally {
	    exporter.unexport(true);
	}
    }

    private static void sleep(long millis) {
	long now = System.currentTimeMillis();
	long end = now + millis;
	do {
	    try {
		Thread.sleep(end - now);
	    } catch (InterruptedException e) {
		System.err.println("swallowed " + e);
	    }
	    now = System.currentTimeMillis();
	} while (now < end);
    }

    static class EchoImpl implements Echo {
	public int echo(int val) throws java.rmi.RemoteException { 
	    return val;
	}
    }
}
