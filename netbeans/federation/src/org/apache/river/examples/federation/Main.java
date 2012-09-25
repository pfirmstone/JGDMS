/*
 *  Main.java
 * 
 */

package org.apache.river.examples.federation;

import org.apache.river.federation.Federation;

/**
 *
 */
public class Main
{

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception
    {
        new Main().launch();
    }

    private void launch() throws Exception
    {
        Federation.start();
        
        launchServer();
        launchClient();        
    }

    private void launchServer()
    {
        DemoServiceImpl dsi = new DemoServiceImpl();
        
        Federation.register(dsi);
    }

    private void launchClient() throws Exception
    {
        DemoService ds = Federation.lookup(DemoService.class);
        
        System.out.println( ds.hello("there") );
    }
}
