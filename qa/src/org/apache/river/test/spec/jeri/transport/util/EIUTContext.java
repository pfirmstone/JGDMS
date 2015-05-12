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
package org.apache.river.test.spec.jeri.transport.util;

//jeri imports
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;

//import java.io
import java.io.IOException;

//harness imports
import org.apache.river.qa.harness.TestException;

//java.util
import java.util.ArrayList;

public class EIUTContext implements ListenContext {

    private ArrayList results = new ArrayList();

    public ListenCookie addListenEndpoint(ListenEndpoint endpoint)
        throws IOException {
        try {
            ListenHandle lh = endpoint.listen(new SETRequestHandler());
            results.add(new Boolean(false));
            return lh.getCookie();
        } catch (IOException e) {
            results.add(new Boolean(true));
            throw e;
            //This listen context expects an IOException
        }
    }

    public ArrayList getResults() {
        return results;
    }
}
