/*
 *  Federation.java
 * 
 */

package org.apache.river.federation;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.core.lookup.ServiceID;
import net.jini.lookup.JoinManager;

/**
 *
 */
public class Federation
{
    private static Remote dummy;
    
    private static Map<Remote,JoinManager> joins = new HashMap<>();
    
    public static void start()
    {
        //TODO
    }

    public static class DummyServiceProxy 
        implements Serializable
    {

        private DummyServiceProxy(Remote svc)
        {
        }

    }
    
    public static void register(Remote svc) throws RemoteException, IOException, ConfigurationException
    {
        //TODO
        dummy = svc ;
        
        Configuration config = getConfiguration(svc);
        
        Object svcproxy = new DummyServiceProxy(svc); // TODO
        
        JoinManager joinMgr = new JoinManager(svcproxy,null,(ServiceID)null,null,null,config); // TODO

        joins.put( svc, joinMgr );
    }

    /**
     * Debatable if whe need the service here.
     */
    private static Configuration getConfiguration(Remote svc)
    {
        return new IntrospectionConfiguration(svc);
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
