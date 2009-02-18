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
package com.sun.jini.test.spec.activation.util;
import java.rmi.Remote;
import java.rmi.activation.ActivationException;
import java.rmi.RemoteException;
import java.io.IOException;


/**
 * <pre>
 * ExceptionThrowingInterface
 *   - an interface which declares <code>throwsException<\code> method
 *    which throws exception ponted in call of
 *    <code>exceptionForThrow<\code> method
 *   - extend {@link Remote} interface
 * </pre>
 */
public interface ExceptionThrowingInterface extends Remote {

    public void exceptionForThrow(Throwable testedException)
            throws RemoteException;

    public void throwsException() throws Throwable;
}
