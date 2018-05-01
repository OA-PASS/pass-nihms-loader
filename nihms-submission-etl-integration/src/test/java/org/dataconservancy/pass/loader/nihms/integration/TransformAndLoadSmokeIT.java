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
import java.net.URISyntaxException;

import org.apache.commons.io.IOUtils;

import org.junit.Before;
import org.junit.Test;

import org.dataconservancy.pass.client.PassClientDefault;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.entrez.PubMedEntrezRecord;
import org.dataconservancy.pass.loader.nihms.cli.NihmsSubmissionEtlApp;
import org.dataconservancy.pass.model.Grant;
import org.json.JSONObject;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 *
 * @author Karen Hanson
 */
public class TransformAndLoadSmokeIT {
    
    @Mock
    private PmidLookup pmidLookup = new PmidLookup();
        
    @Spy 
    private NihmsPassClientService passClientService = new NihmsPassClientService();
    
    private PassClient client = new PassClientDefault();
    
    private URI grantUri;

    static {
        if (System.getProperty("pass.fedora.baseurl") == null) {
            System.setProperty("pass.fedora.baseurl", "http://localhost:8080/fcrepo/rest/");
            System.setProperty("pass.fedora.baseurl", "http://localhost:8080/fcrepo/rest/");
        }
    }

    
    @Before   
    public void setup() throws URISyntaxException {
        MockitoAnnotations.initMocks(this);        
                
        Grant grant = new Grant();
        grant.setAwardNumber("AAAAAAAA");
        grantUri = client.createResource(grant);
    }
    
    
    @Test
    public void smokeTestLoadAndTransform() throws Exception {

        //mock the pmidLookup so we dont get real data, want to use the fake data
        String json = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("pmidrecord.json"));
        JSONObject rootObj = new JSONObject(json);
        PubMedEntrezRecord pmr = new PubMedEntrezRecord(rootObj);        
        when(pmidLookup.retrievePubMedRecord(Mockito.anyString())).thenReturn(pmr);
        
        doReturn(grantUri).when(passClientService).findGrantByAwardNumber(Mockito.anyString());
        
        String path = TransformAndLoadSmokeIT.class.getClassLoader().getResource("downloads").getPath();
        System.setProperty("nihms.downloads.dir", path);
        
        NihmsSubmissionEtlApp app = new NihmsSubmissionEtlApp(null, null, pmidLookup, passClientService);
        app.run();
        //reset file names:
        File downloadDir = new File(path);
        resetPaths(downloadDir);   
    }
    
    
    private void resetPaths(File folder)  {
        try {
            File[] listOfFiles = folder.listFiles();
            for (File filepath : listOfFiles) { 
                if (filepath.getAbsolutePath().endsWith(".done")) {
                    String fp = filepath.getAbsolutePath();
                    filepath.renameTo(new File(fp.substring(0, fp.length()-5)));                    
                }
            }
        } catch (Exception ex) {
            fail("There was a problem resetting the file names to remove '.done'. File names will need to be manually reset before testing again");
        }
    }
    
}
