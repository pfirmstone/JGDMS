/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.resource.testspi;

/**
 * Provider for {@link FixtureSpi}, declared in {@code META-INF/services} on the test classpath.
 * It is loadable by the system/application class loader but NOT by the bootstrap loader -- exactly
 * the shape that tripped the {@code Service} null-loader asymmetry (found via
 * {@code getSystemResources}, then a failed bootstrap {@code Class.forName}).
 */
public final class FixtureSpiProvider implements FixtureSpi {
    public FixtureSpiProvider() {}

    @Override
    public String id() {
        return "fixture";
    }
}
