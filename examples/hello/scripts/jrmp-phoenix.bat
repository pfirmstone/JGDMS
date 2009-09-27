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

@rem Batch file to run JRMP Phoenix

java -Djava.security.manager= ^
     -Djava.security.policy=config\phoenix.policy ^
     -Djava.rmi.server.codebase="http://%computername%:8080/phoenix-dl.jar http://%computername%:8080/jsk-dl.jar" ^
     -DserverHost=%computername% ^
     -jar ..\..\lib\phoenix.jar ^
     config\jrmp-phoenix.config
