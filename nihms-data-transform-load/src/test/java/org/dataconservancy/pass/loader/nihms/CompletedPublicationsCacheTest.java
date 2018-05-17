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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.dataconservancy.pass.loader.nihms.util.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests CompletedPublicationsCache class
 * @author Karen Hanson
 */
public class CompletedPublicationsCacheTest {

    
    String cachepath = null;

    @Before
    public void startup() {
        cachepath = FileUtil.getCurrentDirectory() +"/cache/compliant-cache.data";
        System.setProperty("nihmsetl.loader.cachepath", cachepath);
    }
    
    @After
    public void cleanup() {
        File cachefile = new File(cachepath);
        if (cachefile.exists()) {
            cachefile.delete();
        }
    }    

    /**
     * Makes sure cache contains items after you add them
     * Also ensure that when you create a new cache object 
     * that it reads in the existing values correctly
     */
    @Test
    public void testAddThenFindMatch() {
        CompletedPublicationsCache cache = new CompletedPublicationsCache();
        String pmid1 = "123456";
        String pmid2 = "987654";
        String awardNum1 = "AB1 FI12345";
        String awardNum2 = "AB2 RF21355";
        
        assertFalse(cache.contains(pmid1, awardNum1));
        assertFalse(cache.contains(pmid2, awardNum2));
        
        cache.add(pmid1, awardNum1);
        cache.add(pmid2, awardNum2);
        
        assertTrue(cache.contains(pmid1, awardNum1));
        assertTrue(cache.contains(pmid2, awardNum2));
        
        cache = null;
        //recreate cache object and check we can still find the cached items
        CompletedPublicationsCache cache2 = new CompletedPublicationsCache();
        assertTrue(cache2.contains(pmid1, awardNum1));
        assertTrue(cache2.contains(pmid2, awardNum2));        
    }

    /**
     * Makes sure adding duplicate data does not add rows to the cache file
     * @throws Exception 
     */
    @Test
    public void testDoesNotAddDuplicates() throws Exception {
        CompletedPublicationsCache cache = new CompletedPublicationsCache();
        String pmid1 = "123456";
        String pmid2 = "987654";
        String awardNum1 = "AB1 FI12345";
        String awardNum2 = "AB2 RF21355";
        
        assertFalse(cache.contains(pmid1, awardNum1));
        assertFalse(cache.contains(pmid2, awardNum2));
        
        cache.add(pmid1, awardNum1);
        cache.add(pmid2, awardNum2);
        cache.add(pmid1, awardNum1);
        cache.add(pmid2, awardNum2);
        cache.add(pmid1, awardNum1);
        cache.add(pmid2, awardNum2);
        
        assertTrue(cache.contains(pmid1, awardNum1));
        assertTrue(cache.contains(pmid2, awardNum2));

        @SuppressWarnings("unchecked")
        List<String> processed = new ArrayList<String>(FileUtils.readLines(new File(cachepath)));
        assertEquals(2, processed.size());
    }
    
    
    
}
