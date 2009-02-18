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
 * RemoteMethodSetInterface
 * -extends Remote
 * -a interface that defines these methods:
 *    void     voidReturn()                  throws RemoteException
 *    void     voidReturn(int i, Object o)   throws RemoteException
 *    void     voidReturn(int i)             throws RemoteException
 *    Object   objectReturn()                throws RemoteException
 *    Object   objectReturn(Object o, int i) throws RemoteException
 *    Object   objectReturn(int i)           throws RemoteException
 *    int      intReturn()                   throws RemoteException
 *    int      intReturn(int i, Object o)    throws RemoteException
 *    int      intReturn(int i)              throws RemoteException
 *    Object[] objectArrayReturn(Object[] o) throws RemoteException
 *    byte     byteReturn(byte b)            throws RemoteException
 *    long     longReturn(long l)            throws RemoteException
 *    double   doubleReturn(double d)        throws RemoteException
 *    boolean  booleanReturn(boolean b)      throws RemoteException
 *    char     charReturn(char c)            throws RemoteException
 *    short    shortReturn(short s)          throws RemoteException
 *    float    floatReturn(float f)          throws RemoteException
 * </pre>
 */
public interface RemoteMethodSetInterface extends Remote {

    public void voidReturn() throws RemoteException;

    public void voidReturn(int i, Object o) throws RemoteException;

    public void voidReturn(int i) throws RemoteException;

    public Object objectReturn() throws RemoteException;

    public Object objectReturn(Object o, int i) throws RemoteException;

    public Object objectReturn(int i) throws RemoteException;

    public int intReturn() throws RemoteException;

    public int intReturn(int i, Object o) throws RemoteException;

    public int intReturn(int i) throws RemoteException;

    public Object[] objectArrayReturn(Object[] o) throws RemoteException;

    public byte byteReturn(byte b) throws RemoteException;

    public long longReturn(long l) throws RemoteException;

    public double doubleReturn(double d) throws RemoteException;

    public boolean booleanReturn(boolean b) throws RemoteException;

    public char charReturn(char c) throws RemoteException;

    public short shortReturn(short s) throws RemoteException;

    public float floatReturn(float f) throws RemoteException;
}
