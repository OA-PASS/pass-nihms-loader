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

import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Karen Hanson
 */
public class PubMedRecord {

    private static final String DOI_PREFIX = "https://doi.org/";
    private static final String VALID_DOI_CONTAINS = "10.";
    private static final String JSON_ARTICLEIDS_KEY = "articleids";
    private static final String JSON_IDTYPE_KEY = "idtype";
    private static final String JSON_IDTYPE_DOI = "doi";
    private static final String JSON_IDVALUE_KEY = "value";    
    
    
    private JSONObject entrezJson;
    
    public PubMedRecord(JSONObject entrezJson) {
        this.entrezJson = entrezJson;
    }
    

    /**
     * Extract DOI from Entrez JSON as https://doi.org/10....
     * @return
     */
    public String getDoi() {

        String doi = null;
        JSONObject jsonPmidRecord = this.entrezJson;
        if (jsonPmidRecord!=null) {
            JSONArray ids = jsonPmidRecord.getJSONArray(JSON_ARTICLEIDS_KEY);
            for (Object oid : ids) {
                JSONObject id = (JSONObject) oid;
                if (id.getString(JSON_IDTYPE_KEY).equals(JSON_IDTYPE_DOI)) {
                    doi = id.getString(JSON_IDVALUE_KEY);
                    if (doi!=null && doi.length()>0 && doi.contains(VALID_DOI_CONTAINS)) {
                        doi = doi.trim();
                        doi = DOI_PREFIX + doi;
                    }
                }                    
            }
        }
        
        return doi;
    }
    
}
