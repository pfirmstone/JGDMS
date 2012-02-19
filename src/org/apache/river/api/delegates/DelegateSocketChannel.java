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

package org.apache.river.api.delegates;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketPermission;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.Guard;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 *
 * @author peter
 */
class DelegateSocketChannel extends SocketChannel {

    private volatile Guard g;
    private final SocketChannel c;
    
    DelegateSocketChannel(SocketChannel sc, Guard guard){
        super(SelectorProvider.provider());
        c = sc;
        g = guard;
    }
    
    @Override
    public Socket socket() {
        return new DelegateSocket(c.socket(), g);
    }

    @Override
    public boolean isConnected() {
        return c.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return c.isConnectionPending();
    }

    @Override
    public boolean connect(final SocketAddress remote) throws IOException {
        // Access is not allowed to a new connection if the caller is not
        // allowed to connect to existing.
        g.checkGuard(this);
        Guard ng = DelegatePermission.get(new SocketPermission(remote.toString(), "CONNECT"));
        ng.checkGuard(this); // Make sure this will succeed.
        // caller probably does not have SocketPermission.
        Boolean result = Boolean.FALSE;
        try {
            result = AccessController.doPrivileged( 
                new PrivilegedExceptionAction<Boolean>(){
                    public Boolean run() throws IOException{
                        return c.connect(remote);
                    }
                }
            );
           synchronized (c) {     
                g = ng;
           }
        } catch (PrivilegedActionException ex) {
            Exception e = ex.getException();
            if ( e instanceof IOException) throw (IOException)e;
            throw new IOException("ConnectionFailed: " + remote.toString(), e);
        }
        return result.booleanValue();
    }

    @Override
    public boolean finishConnect() throws IOException {
        return c.finishConnect();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        g.checkGuard(this);
        return c.read(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        g.checkGuard(this);
        return c.read(dsts, offset, length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        g.checkGuard(this);
        return c.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        g.checkGuard(this);
        return c.write(srcs, offset, length);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void implCloseSelectableChannel() throws IOException {
        g.checkGuard(this);
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction(){
                public Object run() throws IOException{
                    try {
                        Method m = c.getClass().getMethod("implCloseSelectableChannel");
                        m.setAccessible(true);
                        m.invoke(c);
                    } catch (InvocationTargetException ex){
                        Throwable e = ex.getTargetException();
                        if (e instanceof IOException) throw (IOException) e;
                        throw new IOException(e);
                    } catch (Exception ex){
                        throw new IOException(ex);
                    }
                    return null;
                }
            });
        }catch (PrivilegedActionException ex){
            Exception e = ex.getException();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void implConfigureBlocking(final boolean block) throws IOException {
        g.checkGuard(this);
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction(){
                public Object run() throws IOException{
                    try {
                        Method m = c.getClass().getMethod("implConfigureBlocking", boolean.class);
                        m.setAccessible(true);
                        m.invoke(c, block);
                    } catch (InvocationTargetException ex){
                        Throwable e = ex.getTargetException();
                        if (e instanceof IOException) throw (IOException) e;
                        throw new IOException(e);
                    } catch (Exception ex){
                        throw new IOException(ex);
                    }
                    return null;
                }
            });
        }catch (PrivilegedActionException ex){
            Exception e = ex.getException();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }
    
}
