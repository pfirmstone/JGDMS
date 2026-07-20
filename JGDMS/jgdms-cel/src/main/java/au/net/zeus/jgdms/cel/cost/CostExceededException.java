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

package au.net.zeus.jgdms.cel.cost;

/**
 * Thrown by {@link CostModel} when the statically computed cost {@code C(E)}
 * exceeds {@code maxExprCost}, <em>or</em> when any intermediate checked-long
 * arithmetic term used to compute it overflows (JGDMS-STD-011 §10.6: an
 * accumulator overflow MUST itself be treated as {@code C(E) > maxExprCost}
 * -- reject -- never allowed to wrap silently into a small or zero value).
 */
public class CostExceededException extends Exception {

    private static final long serialVersionUID = 1L;

    public CostExceededException(String message) {
        super(message);
    }
}
