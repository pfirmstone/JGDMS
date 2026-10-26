/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench;

import org.openjdk.jmh.annotations.Fork;

/**
 * {@link ContextCaptureSplit} on one virtual thread, SecurityManager installed.
 * Compare with {@link ContextCaptureSplitPlatform} to see which stage makes a
 * check on a virtual thread cost more.
 * Written with AI assistance (Claude); see the README.
 */
@Fork(value = 2,
        jvmArgsPrepend = {ContextCaptureSplit.ADD_OPENS_1, ContextCaptureSplit.ADD_OPENS_2},
        jvmArgsAppend = {"-Djmh.executor=VIRTUAL", "-Djava.security.manager=default"})
public class ContextCaptureSplitVirtual extends ContextCaptureSplit {
}
