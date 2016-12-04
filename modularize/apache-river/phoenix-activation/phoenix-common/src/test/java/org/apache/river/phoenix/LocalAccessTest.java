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
package org.apache.river.phoenix;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import junit.framework.TestCase;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientHost;

public class LocalAccessTest extends TestCase {

    public void testNoServerContext() {
        try {
            LocalAccess.check();
        }
        catch(AccessControlException e) {
            fail("unexpected: " + e);
        }
    }

    public void testNoClientHost() {
        ServerContext.doWithServerContext(new Runnable() {
            public void run() {
                try {
                    LocalAccess.check();
                }
                catch(AccessControlException e) {
                    fail("unexpected: " + e);
                }
            }
        }, new ArrayList(0));
        
    }

    public void testLocalClientHost() throws UnknownHostException {
        Collection col = new ArrayList(1);
        col.add(new ClientHost() {
            public InetAddress getClientHost() {
                try {
                    return InetAddress.getLocalHost();
                }
                catch(UnknownHostException e) {
                    return null;
                }
            }
        });
        ServerContext.doWithServerContext(new Runnable() {
            public void run() {
                try {
                    LocalAccess.check();
                }
                catch(AccessControlException e) {
                    fail("unexpected: " + e);
                }
            }
        }, col);
    }

    public void testLoopBackClientHost() {
        Collection col = new ArrayList(1);
        col.add(new ClientHost() {
            public InetAddress getClientHost() {
                try {
                    return InetAddress.getByName("127.0.1.1");
                }
                catch(UnknownHostException e) {
                    return null;
                }
            }
        });
        ServerContext.doWithServerContext(new Runnable() {
            public void run() {
                try {
                    LocalAccess.check();
                }
                catch(AccessControlException e) {
                    fail("unexpected: " + e);
                }
            }
        }, col);
        
    }

    public void testRemoteClientHost() {
        Collection col = new ArrayList(1);
        col.add(new ClientHost() {
            public InetAddress getClientHost() {
                try {
                    return InetAddress.getByName("www.apache.org");
                }
                catch(UnknownHostException e) {
                    return null;
                }
            }
        });
        ServerContext.doWithServerContext(new Runnable() {
            public void run() {
                try {
                    LocalAccess.check();
                }
                catch(AccessControlException e) {
                    return;
                }
                fail("expected AccessControlException not thrown");
            }
        }, col);
        
    }

}
