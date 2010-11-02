/*
 *  HeartOfTheMachine.java
 * 
 *  Created on Nov 1, 2010 7:03:57 PM by sim
 * 
 */

package com.sun.jini.qa.harness;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author sim
 */
public class HeartOfTheMachine
{
    private static String soul = null ; // System.getenv("SOUL");

    public HeartOfTheMachine()
    {
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
        if( soul == null ) {
            return ;
        }

        Thread t = new Thread( new Runnable() {

            public void run()
            {
                new HeartOfTheMachine().ticktack();
            }

        }, "no heart without soul");
        t.setDaemon(true);
        t.start();
    }
}
