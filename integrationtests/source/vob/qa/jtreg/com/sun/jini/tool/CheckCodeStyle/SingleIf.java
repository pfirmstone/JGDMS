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
/* Bad */

if (a()
    || b())
    c();			// Missing brace
if (a())			// Missing brace
    if (b())
	c();
if (a())			// Missing brace
    while (b()) {
    }
if (a())
    b();
else				// Missing brace
    c();
if (a())			// Missing brace
    b(
	c());
if (a())
    b();
else {				// Missing brace
    c();
}
if (a())
    b;
else if (c()) {			// Missing brace
    d;
}
if (a()) {
    b();
} else				// Missing brace
    c();
}
if (a)				// Missing brace
    b(c(
    d()));
if (a) b;			// Missing brace

/* OK */

if (a)
    b();
if (a) {
    if (b)
	c;
} else {
    d;
}

