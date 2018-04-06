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

import org.junit.Test;

import org.json.JSONObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Karen Hanson
 * @version $Id$
 */
public class EntrezPmidLookupTest {

    @Test
    public void testGetEntrezRecordJson() { 
        PmidLookup apiService = new PmidLookup();
        
        String pmid = "29249144";
        
        JSONObject pmr = apiService.retrievePubMedRecordAsJson(pmid);
        assertTrue(pmr.getString("source").contains("Proteome"));
        
    }
    
    @Test
    public void testGetPubMedRecord() {
        PmidLookup pmidLookup = new PmidLookup();
        String pmid = "29249144";
        PubMedEntrezRecord record = pmidLookup.retrievePubMedRecord(pmid);        
        assertEquals("https://doi.org/10.1021/acs.jproteome.7b00775", record.getDoi());
        
    }
    
}
