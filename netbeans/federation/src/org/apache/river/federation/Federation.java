/*
 *  Federation.java
 * 
 *  Created on 20-Sep-2012 14:44:38 by sim
 * 
 */

package org.apache.river.federation;

import java.rmi.Remote;

/**
 *
 * @author sim
 */
public class Federation
{
    private static Remote dummy;

    public static void start()
    {
        //TODO
    }

    public static void register(Remote svc)
    {
        //TODO
        dummy = svc ;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Remote> T lookup(Class<T> svcif)
    {
        //TODO
        return (T)dummy ;
    }

    private Federation()
    {
    }
    
}
