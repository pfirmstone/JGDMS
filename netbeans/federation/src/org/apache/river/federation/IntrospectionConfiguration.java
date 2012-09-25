/*
 *  IntrospectionConfiguration.java
 * 
 */

package org.apache.river.federation;

import java.rmi.Remote;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;

/**
 * TODO: implement
 */
public class IntrospectionConfiguration
    implements Configuration
{
    private EmptyConfiguration empty = EmptyConfiguration.INSTANCE ;

    public IntrospectionConfiguration(Remote svc)
    {
        // TODO
    }

    @Override
    public Object getEntry(String component, String name, Class type, Object defaultValue, Object data)
            throws ConfigurationException
    {
        return empty.getEntry(component, name, type, defaultValue, data);
    }

    @Override
    public Object getEntry(String component, String name, Class type, Object defaultValue)
            throws ConfigurationException
    {
        return empty.getEntry(component, name, type, defaultValue);
    }

    @Override
    public Object getEntry(String component, String name, Class type)
            throws ConfigurationException
    {
        return empty.getEntry(component, name, type);
    }
    
    
}
