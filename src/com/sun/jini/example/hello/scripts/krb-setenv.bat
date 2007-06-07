@rem /*
@rem Licensed to the Apache Software Foundation (ASF) under one
@rem or more contributor license agreements.  See the NOTICE file
@rem distributed with this work for additional information
@rem regarding copyright ownership. The ASF licenses this file
@rem to you under the Apache License, Version 2.0 (the
@rem "License"); you may not use this file except in compliance
@rem with the License. You may obtain a copy of the License at
@rem 
@rem      http://www.apache.org/licenses/LICENSE-2.0
@rem 
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem */

@rem Script to set environment variables used by other Kerberos scripts.
@rem You should modify the variable values to match your environment 
@rem setup.

@rem Client principal, excluding the realm
@rem Example: set CLIENT=client
if "%CLIENT%"=="" set CLIENT=client

@rem Server principal, including server name and instance, but not the realm
@rem Example: set SERVER=server or
@rem          set SERVER=server/server1.xyz.com
if "%SERVER%"=="" set SERVER=server

@rem Phoenix principal, including phoenix name and instance, but not 
@rem the realm
@rem Example: set PHOENIX=phoenix or
@rem          set PHOENIX=phoenix/server2.xyz.com
if "%PHOENIX%"=="" set PHOENIX=phoenix

@rem Reggie principal, including reggie name and instance, but not 
@rem the realm
@rem Example: set REGGIE=reggie or
@rem          set REGGIE=reggie/server2.xyz.com
if "%REGGIE%"=="" set REGGIE=reggie

@rem Default realm used by KDC and all principals in this example
@rem Example: set REALM=REALM1.XYZ.COM
if "%REALM%"=="" set REALM=your_default_realm

@rem Host on which the KDC server is running
@rem Example: set KDC_HOST=server3.xyz.com
if "%KDC_HOST%"=="" set KDC_HOST=your_kdc_host
