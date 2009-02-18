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
import com.dstc.security.pki.ConsoleCATool;
import com.dstc.security.provider.DSTC;
import java.security.Security;

/**
 * Run the DSTC Certificate Authority console after installing the provider.
 * Install the provider here, rather than in the java.security file, since it
 * conflicts with the RSAJCA provider that comes with the JDK 1.3.
 */
public class CA {
    public static void main(String[] args) {
	Security.insertProviderAt(new DSTC(), 1);
	com.dstc.security.pki.ConsoleCATool.main(args);
    }
}
