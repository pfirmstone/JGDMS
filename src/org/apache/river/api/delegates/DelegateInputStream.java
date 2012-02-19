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
import java.io.InputStream;
import java.security.Guard;

/**
 *
 * @author peter
 */
class DelegateInputStream extends InputStream {
    
    private final Guard g;
    private final InputStream i;
    
    DelegateInputStream(InputStream in, Guard g){
        this.g = g;
        i = in;
    }

    @Override
    public int read() throws IOException {
        g.checkGuard(this);
        return i.read();
    }
    
    public int read(byte b[], int off, int len) throws IOException {
	g.checkGuard(this);
        return i.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        g.checkGuard(this);
        return i.skip(n);
    }

    
    public int available() throws IOException {
        g.checkGuard(this);
        return i.available();
    }

    
    public void close() throws IOException {
        g.checkGuard(this);
        i.close();
    }

    public void mark(int readlimit) {
        g.checkGuard(this);
        i.mark(readlimit);
    }

    
    public void reset() throws IOException {
        g.checkGuard(this);
        i.reset();
    }

    
    public boolean markSupported() {
        g.checkGuard(this);
        return i.markSupported();
    }
}
