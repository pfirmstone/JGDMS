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
package au.net.zeus.jgdms.hello;

import au.net.zeus.jgdms.api.hello.HelloService;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core, standalone implementation of {@link HelloService}.
 *
 * <p>This class is a plain Java object with no Jini infrastructure
 * dependencies.  It contains the business logic; all Jini infrastructure
 * (export, discovery, lookup registration) is handled by the surrounding
 * {@link HelloWorldServiceImpl} wrapper.
 *
 * <h2>Thread safety</h2>
 * This implementation is immutable and therefore inherently thread-safe.
 *
 * @see HelloWorldServiceImpl
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class HelloServiceImpl implements HelloService {

    private static final Logger logger =
            Logger.getLogger(HelloServiceImpl.class.getName());

    /**
     * Creates a new {@code HelloServiceImpl}.
     */
    public HelloServiceImpl() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code "Hello, <name>!"}.
     */
    @Override
    public String sayHello(String name) throws RemoteException {
        if (name == null) throw new NullPointerException("name");
        String greeting = "Hello, " + name + "!";
        logger.log(Level.FINE, "Returning greeting: {0}", greeting);
        return greeting;
    }
}
