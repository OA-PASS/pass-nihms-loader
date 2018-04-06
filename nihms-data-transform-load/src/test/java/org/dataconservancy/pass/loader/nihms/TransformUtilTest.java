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
import org.dataconservancy.pass.model.Deposit.Status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Karen Hanson
 */
public class TransformUtilTest {

    private static final String dateStr = "12/12/2018";
    
    private static final String nihmsRepositoryUri = "https://example.com/repositories/1";
    
    
    /**
     * If the NIHMS status is COMPLIANT, the Deposit is always ACCEPTED
     * regardless of what the other columns in the spreadsheet say,
     * and independent of what the current deposit status says.
     */
    @Test
    public void testCalcDepositStatusAccepted() {
        NihmsPublication pub = newTestPub();

        //status is compliant by default for newTestPub
        
        //no current deposit, no dates in spreadsheet, and is compliant
        Status status = TransformUtil.calcDepositStatus(pub, null);
        assertEquals(Status.ACCEPTED, status);
        
        //no current deposit, dates in spreadsheet, and is compliant
        pub = newTestPub();
        pub.setFileDepositedDate(dateStr);
        pub.setFinalApprovalDate(dateStr);
        pub.setTaggingCompleteDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        status = TransformUtil.calcDepositStatus(pub, null);
        assertEquals(Status.ACCEPTED, status);
        
        //curr deposit status is "in-preparation", but spreadsheet says it's compliant
        pub = newTestPub();
        status = TransformUtil.calcDepositStatus(pub, Status.IN_PREPARATION);
        assertEquals(Status.ACCEPTED, status);
        
        //curr deposit status is "accepted", spreadsheet is compliant
        pub = newTestPub();
        status = TransformUtil.calcDepositStatus(pub, Status.ACCEPTED);
        assertEquals(Status.ACCEPTED, status);        
    }
    
    
    /**
     * The deposit has been received when there is a file deposit date
     * Check Deposit status assigned to received under appropriate conditions
     */
    @Test
    public void testCalcDepositStatusReceived() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        
        //common transition if submitted through PASS - current deposit status 
        //was submitted, and now the spreadsheets shows it was received.
        Status status = TransformUtil.calcDepositStatus(pub, Status.SUBMITTED);
        assertEquals(Status.RECEIVED, status);
        
        //status has gone out of alignment with PASS - PASS is ahead sometime. This should roll back 
        //the status to received and log a warning.
        status = TransformUtil.calcDepositStatus(pub, Status.IN_PROGRESS);
        assertEquals(Status.RECEIVED, status);      
        
        //it was received, and is still received
        status = TransformUtil.calcDepositStatus(pub, Status.RECEIVED);
        assertEquals(Status.RECEIVED, status);     
        
        //checks being non-compliant doesn't affect status of deposit, it should only affect submissions status        
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        pub.setFileDepositedDate(dateStr);
        status = TransformUtil.calcDepositStatus(pub, Status.SUBMITTED);
        assertEquals(Status.RECEIVED, status);        
    }

    
    /**
     * The deposit has been received and it is being reviewed, metadata added etc.
     * The presence of an initial approval or tagging date indicates in-progress status
     * Check in-progress is appropriate assigned to Deposit in relevant conditions
     */
    @Test
    public void testCalcDepositStatusInProgress() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        
        //last update we saw the file was received, now it should be in progress as there is an initial approval date
        Status status = TransformUtil.calcDepositStatus(pub, Status.RECEIVED);
        assertEquals(Status.IN_PROGRESS, status);
        
        //status has gone out of alignment with PASS - PASS is ahead sometime. This should roll back 
        //the status to received and log a warning.
        status = TransformUtil.calcDepositStatus(pub, Status.ACCEPTED);
        assertEquals(Status.IN_PROGRESS, status);
        
        //this time, the submission has been tagged since it was submitted.
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        pub.setFileDepositedDate(dateStr);
        pub.setInitialApprovalDate(dateStr);
        pub.setTaggingCompleteDate(dateStr);
        status = TransformUtil.calcDepositStatus(pub, Status.SUBMITTED);
        assertEquals(Status.IN_PROGRESS, status);
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
        Status status = TransformUtil.calcDepositStatus(pub, Status.SUBMITTED);
        assertEquals(Status.SUBMITTED, status);
        
        //PASS system says file was in-preparation, but NIHMS says non compliant... 
        //presumably NIHMS submission did not take place yet hence non-compliant.
        //it shouldn't touch status NIHMS is unaware of submission yet
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        status = TransformUtil.calcDepositStatus(pub, Status.IN_PREPARATION);
        assertEquals(Status.IN_PREPARATION, status);
        
        //PASS system says file was ready-to-submit, but NIHMS says non compliant... 
        //presumably NIHMS submission did not take place yet hence non-compliant.
        //it shouldn't touch status NIHMS is unaware of submission yet
        pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        status = TransformUtil.calcDepositStatus(pub, Status.READY_TO_SUBMIT);
        assertEquals(Status.READY_TO_SUBMIT, status);
        
    }
    
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
     * Confirms that compliant publication with initial approval date does not
     * require user action
     */
    @Test
    public void testIsDepositUserActionRequiredFalseOnCompliant() {
        NihmsPublication pub = newTestPub();
        pub.setFileDepositedDate("12/12/2017");
        boolean userActionReq = TransformUtil.isDepositUserActionRequired(pub);
        assertEquals(false, userActionReq);
    }

    /**
     * Confirms that a non-compliant publication with an initial approval date DOES
     * require user action
     */
    @Test
    public void testIsDepositUserActionRequiredTrueOnNonCompliantWithDate() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        pub.setFileDepositedDate("12/12/2017");
        boolean userActionReq = TransformUtil.isDepositUserActionRequired(pub);
        assertEquals(true, userActionReq);
    }


    /**
     * Confirms that a non-compliant publication with no initial approval date 
     * DOES NOT require user action
     */
    @Test
    public void testIsDepositUserActionRequiredFalseOnNonCompliantNoDate() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        boolean userActionReq = TransformUtil.isDepositUserActionRequired(pub);
        assertEquals(false, userActionReq);
    }
    
    /**
     * Should do a NIHMS deposit if there is a PMC ID
     */
    @Test
    public void testNeedNihmsDepositTrueWhenPmcId() {
        NihmsPublication pub = newTestPub();
        //in process or compliant should always create a deposit, so switch to non-compliant
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        pub.setPmcId("9876543");
        boolean needDeposit = TransformUtil.needNihmsDeposit(pub);
        assertTrue(needDeposit);
    }

    /**
     * Should do NIHMS deposit if there is a NIHMS ID
     */
    @Test
    public void testNeedNihmsDepositTrueWhenNihmsId() {
        NihmsPublication pub = newTestPub();
        //in process or compliant should always create a deposit, so switch to non-compliant
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        pub.setNihmsId("7654321");
        boolean needDeposit = TransformUtil.needNihmsDeposit(pub);
        assertTrue(needDeposit);
    }

    /**
     * Should do NIHMS deposit if the Submission is compliant
     */
    @Test
    public void testNeedNihmsDepositTrueWhenCompliant() {
        NihmsPublication pub = newTestPub();
        //status is compliant by default for newTestPub
        boolean needDeposit = TransformUtil.needNihmsDeposit(pub);
        assertTrue(needDeposit);
    }


    /**
     * Should do NIHMS deposit if Submission is in-process
     */
    @Test
    public void testNeedNihmsDepositTrueWhenInProcess() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS);
        boolean needDeposit = TransformUtil.needNihmsDeposit(pub);
        assertTrue(needDeposit);        
    }

    /**
     * Should not do NIHMS deposit if status is non-compliant and we have
     * no information to go on the Deposit record.
     */
    @Test
    public void testNeedNihmsDepositFalseWhenNoData() {
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT);
        boolean needDeposit = TransformUtil.needNihmsDeposit(pub);
        assertFalse(needDeposit);                
    }
        
    
    private NihmsPublication newTestPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, "123456", "AB 12345", null, null, null, null, null, null);
    }
    
    
}
