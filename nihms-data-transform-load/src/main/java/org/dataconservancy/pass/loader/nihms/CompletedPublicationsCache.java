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
package org.dataconservancy.pass.loader.nihms;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import org.dataconservancy.pass.loader.nihms.util.ConfigUtil;
import org.dataconservancy.pass.loader.nihms.util.FileUtil;

import static org.dataconservancy.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

/**
 * This controls a simple local text file containing a list of compliant "pmid|grantNumber" combinations
 * that are considered DONE and therefore require no re-processing. Only compliant records with a PMCID already
 * assigned should be added to this list. The list is used as a lookup during processing to avoid the excessive 
 * database interactions that are required to re-process completed nihms data. 
 * 
 * @author Karen Hanson
 */
public class CompletedPublicationsCache {    

    private static final String CACHEPATH_KEY = "nihmsetl.loader.cachepath";
    
    private static final String CACHEPATH_DEFAULT = "/cache/compliant-cache.data";
        
    private File cacheFile = null;
    
    private Set<String> processed;
    
    /**
     * Check for cache file and load data into memory
     */
    @SuppressWarnings("unchecked")
    public CompletedPublicationsCache() {
        String sCacheFile = ConfigUtil.getSystemProperty(CACHEPATH_KEY, FileUtil.getCurrentDirectory() + CACHEPATH_DEFAULT);
        try {
            File cacheFile = new File(sCacheFile);
            if (!cacheFile.exists()) {
                cacheFile.getParentFile().mkdirs();
                cacheFile.createNewFile();
            }
            this.cacheFile = cacheFile;
            // read in cached values
            processed = new HashSet<String>(FileUtils.readLines(cacheFile));
        } catch (Exception ex) {
            throw new RuntimeException("Could not create cache file to hold compliant records at path " + sCacheFile, ex);
        }
    }
    
    /**
     * Check if file contains record
     * @param pmid
     * @param awardNumber
     * @return
     */
    public boolean contains(String pmid, String awardNumber) {
        if (!nullOrEmpty(pmid) && !nullOrEmpty(awardNumber)) {
            String cachevalue = pmid + "|" + awardNumber;
            return processed.contains(cachevalue);
            
        }
        return false;
    }
    
    /**
     * Add a record to the cache file
     * @param pmid
     * @param awardNumber
     */
    public void add(String pmid, String awardNumber) {
        if (!nullOrEmpty(pmid) && !nullOrEmpty(awardNumber) 
                && !contains(pmid, awardNumber)) {
            String cachevalue = pmid + "|" + awardNumber;
            try (PrintWriter output = new PrintWriter(new FileWriter(cacheFile, true))){
                processed.add(cachevalue);
                output.println(cachevalue);
            } catch (Exception ex) {
                throw new RuntimeException("Problem writing cachevalue: " + cachevalue + " to cache");
            }
        }
    }
    
}
