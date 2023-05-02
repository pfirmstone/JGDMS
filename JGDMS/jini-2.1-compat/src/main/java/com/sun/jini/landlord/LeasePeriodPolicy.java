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
package com.sun.jini.landlord;

import net.jini.core.lease.LeaseDeniedException;

/**
 * Provided for backward compatibility, migrate to new name space.
 * 
 */
@Deprecated
public interface LeasePeriodPolicy extends org.apache.river.landlord.LeasePeriodPolicy {
     public class Result extends org.apache.river.landlord.LeasePeriodPolicy.Result {
         public Result(long expiration, long duration) {
             super(expiration, duration);
         }
         
         private Result(org.apache.river.landlord.LeasePeriodPolicy.Result result){
             super(result.expiration, result.duration);
         }
     }
     

      public default Result grant(LeasedResource resource, long requestedDuration)
	throws LeaseDeniedException 
      {
          return new Result(
              grant(
                  (org.apache.river.landlord.LeasedResource) resource, 
                  requestedDuration
              )
          );
      }
      
      public default Result renew(LeasedResource resource, long requestedDuration)
        throws LeaseDeniedException
      {
          return new Result(
              renew(
                  (org.apache.river.landlord.LeasedResource) resource, 
                  requestedDuration
              )
          );
      }
}
