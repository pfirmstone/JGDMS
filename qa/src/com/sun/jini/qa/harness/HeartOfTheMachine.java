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

package com.sun.jini.qa.harness;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *
 * 
 */
public class HeartOfTheMachine
{
    private String soul ;

    private HeartOfTheMachine()
    {
        soul = System.getenv("SOUL");

        if( soul == null ) {
            return ;
        }

        Thread t = new Thread( new Runnable() {

            public void run()
            {
                ticktack();
            }

        }, "no heart without soul");
        t.setDaemon(true);
        t.start();
    }

    private boolean hasReasonToLive()
    {
        File f = new File(soul);
        return f.exists();
    }

    private void ticktack()
    {
        try {
            while( hasReasonToLive() ) {
                Thread.sleep( TimeUnit.SECONDS.toMillis(10) );
            }
            Runtime.getRuntime().halt(100);
        } catch( InterruptedException e ) {
            // ignore.
        } finally {
        }
    }

    public static void start()
    {
        try {
            new HeartOfTheMachine();
        } catch( Throwable t ) {
            //System.out.println("Heart NOT started");
            Logger.getAnonymousLogger().severe("Heart NOT started");
        }
    }

}
