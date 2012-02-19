/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.river.impl.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.AllPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

//import org.apache.felix.moduleloader.IContent;


/**
 * A cache for local permissions. Local permissions are read from a given bundle
 * and cached for later lookup. See core spec 9.2.1.
 */
// TODO: maybe use bundle events to clean thing up or weak/soft references
public final class LocalPermissions
{
    private static final PermissionInfo[] ALL_PERMISSION = new PermissionInfo[] { new PermissionInfo(
        AllPermission.class.getName(), "", "") };

    private static final String perm = "META-INF/permissions.perm";

    public LocalPermissions()
    {
        
    }

    public PermissionInfo[] getPerms(URL content)
    {
        PermissionInfo[] permissions = null;
        InputStream in = null;
        try
        {

//                    in = content.("META-INF/permissions.perm");
            if (in != null)
            {
                ArrayList perms = new ArrayList();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, "UTF-8"));
                for (String line = reader.readLine(); line != null; line = reader
                    .readLine())
                {
                    String trim = line.trim();
                    if (trim.startsWith("#") || trim.startsWith("//")
                        || (trim.length() == 0))
                    {
                        continue;
                    }
                    perms.add(new PermissionInfo(line));
                }

                permissions = (PermissionInfo[]) perms
                    .toArray(new PermissionInfo[perms.size()]);
            }
        }
        catch (Exception ex)
        {
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException ex)
                {
                    // TODO Auto-generated catch block
                    ex.printStackTrace();
                }
            }
        }
        return permissions; //possibly null.
    }
}
