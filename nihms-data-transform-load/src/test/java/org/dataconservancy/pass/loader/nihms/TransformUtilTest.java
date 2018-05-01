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

import java.net.URI;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.joda.time.DateTime;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Karen Hanson
 */
public class TransformUtilTest {
    
    private static final String nihmsRepositoryUri = "https://example.com/repositories/1";
    
    private static String dateStr = "12/11/2018";
    
    
    /**
     * Confirms that the correct deposit is selected based on matching repository 
     * from a list of deposits
     * @throws Exception
     */
    @Test
    public void testPickDepositForRepositoryHasMatch() throws Exception {
        
        Set<Deposit> deposits = new HashSet<Deposit>();
        
        Deposit deposit1 = new Deposit();
        deposit1.setRepository(new URI("wrong:repo1"));
        deposits.add(deposit1);

        Deposit deposit2 = new Deposit();
        deposit2.setRepository(new URI("wrong:repo2"));
        deposits.add(deposit2);

        //this is the one we want to get returned
        Deposit deposit3 = new Deposit();
        URI repositoryUri = new URI(nihmsRepositoryUri);
        deposit3.setRepository(repositoryUri);
        deposits.add(deposit3);

        Deposit deposit4 = new Deposit();
        deposit4.setRepository(new URI("wrong:repo3"));
        deposits.add(deposit4);
        
        Deposit match = TransformUtil.pickDepositForRepository(deposits, repositoryUri);
        
        assertEquals(deposit3, match);
        assertEquals(repositoryUri, match.getRepository());
        
    }
    
    /**
     * Confirms that pickDepositForRepository returns null rather than throws exception if no match found
     * @throws Exception
     */
    @Test
    public void testPickDepositForRepositoryHasNoMatch() throws Exception {
        
        Set<Deposit> deposits = new HashSet<Deposit>();
        
        Deposit deposit1 = new Deposit();
        deposit1.setRepository(new URI("wrong:repo1"));
        deposits.add(deposit1);

        Deposit deposit2 = new Deposit();
        deposit2.setRepository(new URI("wrong:repo2"));
        deposits.add(deposit2);

        Deposit deposit3 = new Deposit();
        deposit3.setRepository(new URI("wrong:repo3"));
        deposits.add(deposit3);

        URI repositoryUri = new URI(nihmsRepositoryUri);
        Deposit match = TransformUtil.pickDepositForRepository(deposits, repositoryUri);
        
        assertEquals(null, match);
        
    }

    /** 
     * Check formatDate util is working to convert date as formatted in NIHMS spreadsheet to DateTime
     */
    @Test
    public void testFormatDate() {
        DateTime newDate = TransformUtil.formatDate(dateStr);
        assertEquals(12, newDate.getMonthOfYear());
        assertEquals(11, newDate.getDayOfMonth());
        assertEquals(2018, newDate.getYear());
        assertEquals(0, newDate.getMinuteOfHour());
        assertEquals(0, newDate.getSecondOfMinute());
        assertEquals(0, newDate.getMillisOfSecond());
    }
    
    
    /**
     * If the NIHMS status is COMPLIANT, the RepositoryCopy status is 
     * always COMPLETE regardless of what the other columns in the spreadsheet say
     * and independent of the current CopyStatus
     */
    @Test
    public void testCalCopyStatusComplete() {
        NihmsPublication pub = newTestPub();

        //status is compliant by default for newTestPub
        
        //no info other than is compliant, return complete
        CopyStatus status = TransformUtil.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.COMPLETE, status);
        
        //dates in spreadsheet and is compliant, return complete
        pub = newTestPub();
        pub.setFileDepositedDate(dateStr);
        pub.setFinalApprovalDate(dateStr);
        pub.setTaggingCompleteDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        status = TransformUtil.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.COMPLETE, status);
        
        //curr deposit status is "accepted", but spreadsheet says it's compliant, change to complete
        pub = newTestPub();
        status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.COMPLETE, status);
             
    }
    
    
    /**
     * The RepositoryCopy is accepted if there is a file deposit date and no other
     * indication of progress. If RepositoryCopy status says complete, it will move it
     * back to be aligned with what NIHMS says.
     */
    @Test
    public void testCalcCopyStatusAccepted() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        
        //Current status is null, now we have an indication that it has been accepted (deposit date)
        CopyStatus status = TransformUtil.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.ACCEPTED, status);
        
        //status has gone out of alignment with PASS - PASS status is saying complete. This should roll back 
        //the status to accepted and log a warning.
        status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.COMPLETE);
        assertEquals(CopyStatus.ACCEPTED, status);      
        
        //it was accepted, and is still accepted
        status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.ACCEPTED, status);     
        
        //checks being non-compliant doesn't affect status of the Repository Copy  
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        pub.setFileDepositedDate(dateStr);
        status = TransformUtil.calcRepoCopyStatus(pub, null);
        assertEquals(CopyStatus.ACCEPTED, status);        
    }

    
    /**
     * The deposit has been received and it is being reviewed, metadata added etc.
     * The presence of an initial approval or tagging date indicates in-progress status
     * Check in-progress is appropriate assigned to CopyStatus in relevant conditions
     */
    @Test
    public void testCalcCopyStatusInProgress() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        
        //last update we saw the file was accepted, now it should be in progress as there is an initial approval date
        CopyStatus status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.IN_PROGRESS, status);
        
        //status has gone out of alignment with PASS - PASS is ahead sometime. This should roll back 
        //the status to received and log a warning.
        status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.COMPLETE);
        assertEquals(CopyStatus.IN_PROGRESS, status);
        
        //this time, the submission has been tagged since it was accepted.
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        pub.setTaggingCompleteDate(dateStr);
        status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.IN_PROGRESS, status);
    }

    
    /**
     * Tests the scenarios where the NIHMS spreadsheet does not have a definitive indication
     * that the deposit has had anything done to it
     */
    @Test
    public void testCalcDepositStatusNoStatusFromNihms() {
        
        //the file was submitted, there is nothing to indicate anything has been done with 
        //it yet so status should stay the same.
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        CopyStatus status = TransformUtil.calcRepoCopyStatus(pub, null);
        assertEquals(null, status);
        
        //PASS system says file was in-preparation, but NIHMS says non compliant... 
        //presumably NIHMS submission did not take place yet hence non-compliant.
        //it shouldn't touch status NIHMS is unaware of submission yet
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        status = TransformUtil.calcRepoCopyStatus(pub, null);
        assertEquals(null, status);
        
        //PASS system says file was ready-to-submit, but NIHMS says non compliant... 
        //presumably NIHMS submission did not take place yet hence non-compliant.
        //it shouldn't touch status NIHMS is unaware of submission yet
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        status = TransformUtil.calcRepoCopyStatus(pub, CopyStatus.ACCEPTED);
        assertEquals(CopyStatus.ACCEPTED, status);
        
    }

    
    private NihmsPublication newTestPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, "123456", "AB 12345", null, null, null, null, null, null);
    }
    
    
}
