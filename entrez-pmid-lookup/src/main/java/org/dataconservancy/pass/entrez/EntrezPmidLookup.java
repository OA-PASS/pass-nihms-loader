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
package org.dataconservancy.pass.entrez;

import java.io.IOException;
import java.io.InputStream;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.IOUtils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import org.json.JSONObject;

/**
 * Service to retrieve a PMID records from Entrez.
 * @author Karen Hanson
 * @version $Id$
 */
public class EntrezPmidLookup {

    private static final String DEFAULT_ENTREZ_PATH="https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&retmode=json&rettype=abstract&id=%s";
    private static final String ENTREZ_PATH_KEY="entrez.pmid.path";
    
    private String entrezPath;
    
    private static final String JSON_RESULT_KEY = "result";
    
    public EntrezPmidLookup() {
        entrezPath = System.getProperty(ENTREZ_PATH_KEY, DEFAULT_ENTREZ_PATH);
    }
    

    /**
     * Retrieve PubMedRecord object for PMID record from NIH's Entrez API service. 
     * @param pmid
     * @return
     */
    public PubMedRecord retrievePubmedRecord(String pmid) {
        JSONObject jsonObj = retrievePubmedRecordJson(pmid);
        return (jsonObj != null ? new PubMedRecord(jsonObj) : null);
    }


    /**
     * Retrieve JSON for PMID record from NIH's Entrez API service. Returns JSON object containing the record.
     * @param pmid
     * @return
     */
    public JSONObject retrievePubmedRecordJson(String pmid) {
        
        JSONObject jsonRecord = null;
        
        try {
            URI path = new URI(String.format(entrezPath, pmid));
            HttpClient client = HttpClientBuilder.create().build();
            HttpGet httpget = new HttpGet(path);
            HttpResponse response = client.execute(httpget);
            InputStream inputstream = response.getEntity().getContent();
            String jsonEntrezRecord =IOUtils.toString(inputstream);
            JSONObject root = new JSONObject(jsonEntrezRecord);
            if (root.has(JSON_RESULT_KEY)){
                JSONObject result = root.getJSONObject(JSON_RESULT_KEY);
                if (result.has(pmid)) {
                    jsonRecord = result.getJSONObject(pmid);
                }
            }
            
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return jsonRecord;
    }
    
    
    
}
