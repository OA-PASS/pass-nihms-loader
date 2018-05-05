/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.loader.nihms.integration;

import java.io.File;

import java.net.URI;

import org.junit.Before;
import org.junit.Test;

import org.dataconservancy.pass.loader.nihms.cli.NihmsTransformLoadApp;
import org.dataconservancy.pass.model.Grant;

/**
 * Smoke tests loads in some tests data
 * @author Karen Hanson
 */
public class TransformAndLoadSmokeIT extends TransformAndLoadITBase {
    
    @Before   
    public void setup() throws Exception {   
        //check grant data is available
        URI grantUri = client.findByAttribute(Grant.class, "awardNumber", checkableAwardNumber);
        if (grantUri==null) {
            preLoadGrants();
        }  
    }
    
    
    @Test
    public void smokeTestLoadAndTransform() throws Exception {
        
        NihmsTransformLoadApp app = new NihmsTransformLoadApp(null);
        app.run();
        //reset file names:
        File downloadDir = new File(path);
        resetPaths(downloadDir);   
    }
    
}
