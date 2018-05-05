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

import java.net.URISyntaxException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.function.Consumer;

import org.junit.Test;

import org.dataconservancy.pass.loader.nihms.model.NihmsPublication;
import org.dataconservancy.pass.loader.nihms.model.NihmsStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that the CSVProcessor pulls records and consumes them as NihmsPublications
 * Also checks that badly formatted headings in the CSV are caught as exception
 * @author Karen Hanson
 */
public class NihmsCsvProcessorTest {

    private int count = 0;
    
    /**
     * Check the Iterator reads in CSV
     * @throws URISyntaxException 
     */
    @Test
    public void testReadCsv() throws URISyntaxException {
        Path resource = Paths.get(NihmsCsvProcessorTest.class.getResource("/compliant_NihmsData.csv").toURI());

        NihmsCsvProcessor processor = new NihmsCsvProcessor(resource, NihmsStatus.COMPLIANT);
        
        Consumer<NihmsPublication> consumer = pub -> {
            assertTrue(pub != null);
            count = count + 1;
            if (count == 1) {
                assertEquals("12345678", pub.getPmid());
                assertEquals("PMC3453453", pub.getPmcId()); 
                assertEquals("NIHMS678678", pub.getNihmsId());
                assertEquals("A12 BC000001", pub.getGrantNumber());
                assertEquals("4/4/2016", pub.getFileDepositedDate());
                assertEquals("4/5/2016", pub.getInitialApprovalDate());
                assertEquals("4/12/2016", pub.getTaggingCompleteDate());
                assertEquals("4/12/2016", pub.getFinalApprovalDate());
                assertTrue(pub.hasFinalApproval());
                assertTrue(pub.hasInitialApproval());
            }
            if (count == 2) {
                assertEquals("34567890", pub.getPmid());
                assertEquals("PMC4564564", pub.getPmcId()); 
                assertEquals("NIHMS789789", pub.getNihmsId());
                assertEquals("B23 DE000002", pub.getGrantNumber());  
                assertEquals("7/6/2017", pub.getFileDepositedDate());
                assertEquals("7/6/2017", pub.getInitialApprovalDate());
                assertEquals("7/14/2017", pub.getTaggingCompleteDate());
                assertEquals("7/14/2017", pub.getFinalApprovalDate());
                assertTrue(pub.hasFinalApproval());
                assertTrue(pub.hasInitialApproval());
            }
            if (count == 3) {
                assertEquals("34567890", pub.getPmid());
                assertEquals("PMC4564564", pub.getPmcId()); 
                assertEquals("NIHMS789789", pub.getNihmsId());
                assertEquals("R15 LM239488", pub.getGrantNumber()); 
                assertEquals("7/6/2017", pub.getFileDepositedDate());
                assertEquals("7/6/2017", pub.getInitialApprovalDate());
                assertEquals("7/14/2017", pub.getTaggingCompleteDate());
                assertEquals("7/14/2017", pub.getFinalApprovalDate());   
                assertTrue(pub.hasFinalApproval());
                assertTrue(pub.hasInitialApproval());              
            }
            if (count > 3) {
                fail("Should have only processed 3 records");
            }
        };
        
        processor.processCsv(consumer);
        
        if (count != 3) {
            fail("Count should be 3 by the end of the test");
        }
        
    }
    
    /**
     * Check an exception is thrown when there is a bad heading in the spreadsheet. We
     * do not want to process badly formatted data
     */
    @Test(expected=RuntimeException.class)
    public void testBadHeadingDetection() {
        String filename = "/compliant_BadHeadings.csv";
        String filepath = NihmsCsvProcessorTest.class.getResource(filename).toString();
        new NihmsCsvProcessor(Paths.get(filepath), NihmsStatus.COMPLIANT);        
    }

    /**
     * Check a file path that doesn't exist is provided
     */
    @Test(expected=RuntimeException.class)
    public void testBadPath() {
        String filename = "/compliant_DoesntExist.csv";
        String filepath = NihmsCsvProcessorTest.class.getResource(filename).toString();
        new NihmsCsvProcessor(Paths.get(filepath), NihmsStatus.COMPLIANT);        
    }
    
}
