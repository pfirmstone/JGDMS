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
    private static String soulfile = System.getProperty("soul");

    public HeartOfTheMachine()
    {
    }

    private boolean hasReasonToLive()
    {
        File f = new File(soulfile);
        return f.exists();
    }

    private void ticktack()
    {
        try {
            while( hasReasonToLive() ) {
                Thread.sleep( TimeUnit.SECONDS.toMillis(30) );
            }
            Runtime.getRuntime().halt(999);
        } catch( InterruptedException e ) {
            //Runtime.getRuntime().halt(999);
        } finally {
        }
    }

    public static void start()
    {
        if( soulfile == null ) {
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
