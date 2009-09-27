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

@rem Batch file to run confirming Kerberos server

@rem Get the environment variables setup
call scripts/krb-setenv.bat

java -Djava.security.manager= ^
     -Djava.security.policy=config\krb-server.policy ^
     -Djava.security.auth.login.config=config\krb-server.login ^
     -Djava.protocol.handler.pkgs=net.jini.url ^
     -Djava.security.properties=config\dynamic-policy.security-properties ^
     -Djava.rmi.server.RMIClassLoaderSpi=com.sun.jini.example.hello.MdClassAnnotationProvider ^
     -Dexport.codebase.source.app=lib ^
     -Dexport.codebase.app="httpmd://%computername%:8080/server-dl.jar;sha=0" ^
     -Dexport.codebase.source.jsk=..\..\lib-dl ^
     -Dexport.codebase.jsk="httpmd://%computername%:8080/jsk-dl.jar;sha=0" ^
     -DclientPrincipal=%CLIENT%@%REALM% ^
     -DserverPrincipal=%SERVER%@%REALM% ^
     -DreggiePrincipal=%REGGIE%@%REALM% ^
     -Djava.security.krb5.realm=%REALM% ^
     -Djava.security.krb5.kdc=%KDC_HOST% ^
     -jar lib\server.jar ^
     config\confirming-krb-server.config
