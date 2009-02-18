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

do				// Missing brace
    a;
while (b);

for (a; b; c)			// Missing brace
    d;
for (a;
     b;
     c) {			// Brace on wrong line
    d;
}

if (a)				// Missing brace
    b;
if (a(
    b())) {			// Brace on wrong line
    b;
}
if (a) {
    b;
} else				// Missing brace
    c;
if (a) {
    if (b(
	c)) {			// Brace on wrong line
    } else if (d(
	e())) {			// Brace on wrong line
    } else if (f)		// Missing brace
	g;
}

switch (a)			// Missing brace
    case b: a;
switch (a(
    b())) {			// Brace on wrong line
case b:
}

synchronized (a)		// Missing brace
    b;
synchronized (a(
    b())) {			// Brace on wrong line
    c;
}

try				// Missing brace
    a;
catch (b c) {
    d;
} finally {
    e;
}
try {
    a;
} catch (b c)			// Missing brace
    d;
finally {
    e;
}
try {
    a;
} catch (b c) {
} finally			// Missing brace
    e;
try {
    a;
} catch (b
	 c) {			// Brace on wrong line
} finally {
}

while (a)			// Missing brace
    b;
while (a(
    b())) {			// Brace on wrong line
    c;
}

/* OK */

do {
    a;
} while (b);

for (a; b; c) {
    d;
}
for (a;
     b;
     c)
{
    d;
}

if (a) {
    b;
} else {
    c;
}
if (a(
    b()))
{
    c;
} else {
    d;
}

switch (a) {
case b:
}
switch (a(
    b()))
{
case b:
}

synchronized class Foo {
}
synchronized (a) {
    b;
}
    
synchronized (a(
    b()))
{
    c;
}

try {
    a;
} catch (b c) {
    d;
} finally {
    e;
}
try {
    a;
} catch (b
	 c)
{
    d;
} finally {
    e;
}

while (a) {
    b;
}
while (a(
    b()))
{
    c;
}
