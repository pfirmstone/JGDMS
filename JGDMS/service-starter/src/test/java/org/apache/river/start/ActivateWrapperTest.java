/*
 * Copyright 2021 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.start;

import org.apache.river.start.ActivateWrapper.ActivateDesc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author peter
 */
public class ActivateWrapperTest {

    /**
     * Test of register method, of class ActivateWrapper.
     */
    @Test
    public void testActivateDesc() throws Exception {
        System.out.println("ActivateDesc");
        String className = "foo.Bar";
        String [] importLocation = new String[]{"file://foo/", "file://bar/"};
        String [] exportLocation = new String [] {"http://foo.com/", "http://bar.com"};
        String policy = "some.random.policy";
        String [] configurationArguments = new String []{"1", "2", "3", "4"};
        
        ActivateDesc desc = new ActivateDesc(className, importLocation,
                exportLocation, policy, configurationArguments);
        
        String [] data = desc.asArguments();
        
        ActivateDesc result = ActivateDesc.parse(data);
        assertEquals(desc, result);
    }
    
}
