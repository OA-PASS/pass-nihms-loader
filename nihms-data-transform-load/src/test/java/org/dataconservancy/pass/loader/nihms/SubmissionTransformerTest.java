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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.entrez.PubMedEntrezRecord;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission.Source;
import org.dataconservancy.pass.model.Submission.Status;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NIHMS Transformer code
 * @author Karen Hanson
 */
public class SubmissionTransformerTest {

    private static final String sGrantUri = "https://example.com/fedora/grants/1";
    private static final String sSubmissionUri = "https://example.com/fedora/submissions/1";
    private static final String sRepositoryUri = "https://example.com/fedora/repositories/1";
    private static final String sNihmsRepositoryUri = "https://example.com/fedora/repositories/2";
    private static final String sDepositUri = "https://example.com/fedora/deposits/1";
    private static final String sNihmsDepositUri = "https://example.com/fedora/deposits/2";
    private static final String sJournalUri = "https://example.com/fedora/journals/1";
    
    private static final String nihmsId = "abcdefg";
    private static final String pmcId = "9876543";
    private static final String depositDate = "12/12/2018";
    
    //PubMedEntrezRecord fields
    private static final String doi = "https://doi.org/10.001/0101ab";
    private static final String issn = "1234-5678";
    private static final String pmid = "123456";
    private static final String title = "Test Title";
    private static final String issue = "3";
    private static final String volume = "5";
    
    private static final String pmcIdTemplateUrl = "https://example.com/pmc/pmc%s";
    
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    
    @Mock
    private NihmsPassClientService clientServiceMock;

    @Mock
    private PmidLookup pmidLookupMock;
    
    @Mock
    private PubMedEntrezRecord pubMedRecordMock;
    
    private SubmissionTransformer transformer;
    
    @Before 
    public void init() {
        System.setProperty("nihms.pass.uri", sNihmsRepositoryUri);
        System.setProperty("pmc.url.template", pmcIdTemplateUrl);
        MockitoAnnotations.initMocks(this);
        transformer = new SubmissionTransformer(clientServiceMock, pmidLookupMock);
    }
    
    /**
     * Tests the scenario where there is no current Submission in PASS for the article, and no 
     * need for a new deposit. The returned object should have a Submission object without a URI
     * and no Deposit records.
     */
    @Test
    public void testTransformNewSubmissionNoDeposit() throws Exception {
       
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.NON_COMPLIANT); //is the only status that won't initiate a deposit
        
        //Mocking that we have a valid grant URI, PMID, and DOI to use.
        when(clientServiceMock.findGrantByAwardNumber(Mockito.anyString())).thenReturn(new URI(sGrantUri));
        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();
        
        //when it looks for existing submission, return null... we want it to initiate a new one.
        when(clientServiceMock.findExistingSubmission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(null);
        when(clientServiceMock.findJournalByIssn(Mockito.any())).thenReturn(new URI(sJournalUri));
        
        NihmsSubmissionDTO dto = transformer.transform(pub);        

        verify(clientServiceMock, never()).readSubmissionDeposits(Mockito.anyObject()); 

        checkPmrValues(dto);
        assertEquals(null, dto.getNihmsSubmission().getId());
        assertEquals(Status.NON_COMPLIANT_NOT_STARTED, dto.getNihmsSubmission().getStatus());
        assertEquals(0, dto.getNihmsSubmission().getDeposits().size());
        assertEquals(null, dto.getDeposit());
    }
    
    /**
     * Tests the scenario where there is no current Submission in PASS for the article, and a 
     * new Deposit is needed. The returned object should have a Submission object without a URI
     * and a Deposit object without a URI
     */
    @Test
    public void testTransformNewSubmissionWithNewInProcessDeposit() throws Exception {
       
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS); //status will initiate a Deposit
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        pub.setInitialApprovalDate(depositDate);
        pub.setTaggingCompleteDate(depositDate);
        
        //Mocking that we have a valid grant URI, PMID, and DOI to use.
        when(clientServiceMock.findGrantByAwardNumber(Mockito.anyString())).thenReturn(new URI(sGrantUri));
        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();
        
        //when it looks for existing submission, return null... we want it to initiate a new one.
        when(clientServiceMock.findExistingSubmission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(null);
        when(clientServiceMock.findJournalByIssn(Mockito.any())).thenReturn(new URI(sJournalUri));
        
        NihmsSubmissionDTO dto = transformer.transform(pub);        

        verify(clientServiceMock, never()).readSubmissionDeposits(Mockito.anyObject()); 

        checkPmrValues(dto);
        assertEquals(null, dto.getNihmsSubmission().getId());
        assertEquals(Status.COMPLIANT_IN_PROGRESS, dto.getNihmsSubmission().getStatus());
        assertEquals(0, dto.getNihmsSubmission().getDeposits().size());
        
        assertEquals(null, dto.getDeposit().getId());
        assertEquals(nihmsId, dto.getDeposit().getAssignedId());
        assertEquals(sNihmsRepositoryUri, dto.getDeposit().getRepository().toString());
        assertEquals(null, dto.getDeposit().getAccessUrl());
        assertEquals(false, dto.getDeposit().getRequested());
        assertEquals(false, dto.getDeposit().getUserActionRequired());
        assertEquals(Deposit.Status.IN_PROGRESS, dto.getDeposit().getStatus());
    }

    
    /**
     * Tests the scenario where there is no current Submission in PASS for the article, and a 
     * new Deposit is needed. The returned object should have a Submission object without a URI
     * and a Deposit object without a URI
     */
    @Test
    public void testTransformNewSubmissionWithNewCompliantDeposit() throws Exception {
       
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.COMPLIANT); //status will initiate a Deposit
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        pub.setFileDepositedDate(depositDate);
        pub.setPmcId(pmcId);
        
        //Mocking that we have a valid grant URI, PMID, and DOI to use.
        when(clientServiceMock.findGrantByAwardNumber(Mockito.anyString())).thenReturn(new URI(sGrantUri));
        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);

        pmrMockWhenValues();
        
        //when it looks for existing submission, return null... we want it to initiate a new one.
        when(clientServiceMock.findExistingSubmission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(null);
        when(clientServiceMock.findJournalByIssn(Mockito.any())).thenReturn(new URI(sJournalUri));
        
        NihmsSubmissionDTO dto = transformer.transform(pub);        

        verify(clientServiceMock, never()).readSubmissionDeposits(Mockito.anyObject()); 

        checkPmrValues(dto);
        assertEquals(null, dto.getNihmsSubmission().getId());
        assertEquals(Status.COMPLIANT_COMPLETE, dto.getNihmsSubmission().getStatus());
        assertEquals(0, dto.getNihmsSubmission().getDeposits().size());
        
        assertEquals(null, dto.getDeposit().getId());
        assertEquals(pmcId, dto.getDeposit().getAssignedId());
        assertEquals(sNihmsRepositoryUri, dto.getDeposit().getRepository().toString());
        assertEquals(String.format(pmcIdTemplateUrl, pmcId), dto.getDeposit().getAccessUrl());
        assertEquals(false, dto.getDeposit().getRequested());
        assertEquals(false, dto.getDeposit().getUserActionRequired());
        assertEquals(Deposit.Status.ACCEPTED, dto.getDeposit().getStatus());
        
    }
    

    /**
     * Tests the scenario where there is already a Submission in PASS for the article, but 
     * a new Deposit is needed. The returned object should have a Submission object with a URI
     * and a Deposit object without a URI
     */
    @Test
    public void testTransformUpdateSubmissionWithNewInProcessDeposit() throws Exception {
       
        NihmsPublication pub = newTestPub();
        pub.setNihmsStatus(NihmsStatus.IN_PROCESS); 
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        
        //Mocking that we have a valid grant URI, PMID, and DOI to use.
        when(clientServiceMock.findGrantByAwardNumber(Mockito.anyString())).thenReturn(new URI(sGrantUri));
        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);
        pmrMockWhenValues();
        
        NihmsSubmission submission = newTestNihmsSubmission();
        
        //when it looks for existing submission, return the test submission... we want it to update this.
        when(clientServiceMock.findExistingSubmission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(submission);
        
        //lets add an existing deposit, but not the one that is for this repository
        Set<Deposit> deposits = new HashSet<Deposit>();
        Deposit deposit = new Deposit();
        deposit.setId(new URI(sDepositUri));
        deposit.setRepository(new URI(sRepositoryUri));
        deposit.setStatus(Deposit.Status.IN_PROGRESS);
        deposit.setSubmission(submission.getId());
        deposits.add(deposit);
        when(clientServiceMock.readSubmissionDeposits(Mockito.any())).thenReturn(deposits);
        
        NihmsSubmissionDTO dto = transformer.transform(pub);        

        //check the values in the dto
        checkPmrValues(dto);
        assertEquals(submission, dto.getNihmsSubmission()); //submission shouldn't have needed changes
        
        assertEquals(null, dto.getDeposit().getId()); //because it should be new
        assertEquals(nihmsId, dto.getDeposit().getAssignedId());
        assertEquals(sNihmsRepositoryUri, dto.getDeposit().getRepository().toString());
        assertEquals(null, dto.getDeposit().getAccessUrl());
        assertEquals(false, dto.getDeposit().getRequested());
        assertEquals(false, dto.getDeposit().getUserActionRequired());
        assertEquals(Deposit.Status.RECEIVED, dto.getDeposit().getStatus());
        
    }


    /**
     * Tests the scenario where there is already a Submission in PASS for the article, and 
     * a Deposit for NIHMS, but the status has changed. The returned object should have a 
     * Submission object with a URI and a Deposit object with a URI
     */
    @Test
    public void testTransformUpdateSubmissionWithUpdateDeposit() throws Exception {
       
        NihmsPublication pub = newTestPub(); //compliant by default
        pub.setNihmsId(nihmsId);
        pub.setFileDepositedDate(depositDate);
        pub.setInitialApprovalDate(depositDate);
        pub.setTaggingCompleteDate(depositDate);
        pub.setFinalApprovalDate(depositDate);
        pub.setPmcId(pmcId);
        
        //Mocking that we have a valid grant URI, PMID, and DOI to use.
        when(clientServiceMock.findGrantByAwardNumber(Mockito.anyString())).thenReturn(new URI(sGrantUri));
        when(pmidLookupMock.retrievePubMedRecord(Mockito.anyString())).thenReturn(pubMedRecordMock);
        pmrMockWhenValues();
        
        NihmsSubmission submission = newTestNihmsSubmission(); //compliant-in-progress by default
        
        //when it looks for existing submission, return the test submission... we want it to update this.
        when(clientServiceMock.findExistingSubmission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(submission);
        
        //lets add an existing deposit, but not the one that is for this repository
        Set<Deposit> deposits = new HashSet<Deposit>();
        Deposit deposit = new Deposit();
        deposit.setId(new URI(sDepositUri));
        deposit.setRepository(new URI(sRepositoryUri));
        deposit.setStatus(Deposit.Status.IN_PROGRESS);
        deposit.setSubmission(submission.getId());
        deposits.add(deposit);
        
        Deposit deposit2 = new Deposit();
        deposit2.setId(new URI(sNihmsDepositUri));
        deposit2.setRepository(new URI(sNihmsRepositoryUri));
        deposit2.setStatus(Deposit.Status.RECEIVED);
        deposit2.setSubmission(submission.getId());
        deposits.add(deposit2);
        when(clientServiceMock.readSubmissionDeposits(Mockito.any())).thenReturn(deposits);
        
        NihmsSubmissionDTO dto = transformer.transform(pub);        

        //check the values in the dto
        checkPmrValues(dto);
        assertEquals(submission, dto.getNihmsSubmission()); //submission shouldn't have needed changes
        
        assertEquals(sNihmsDepositUri, dto.getDeposit().getId().toString()); //because it should be new
        assertEquals(pmcId, dto.getDeposit().getAssignedId());
        assertEquals(sNihmsRepositoryUri, dto.getDeposit().getRepository().toString());
        assertEquals(String.format(pmcIdTemplateUrl, pmcId), dto.getDeposit().getAccessUrl());
        assertEquals(Deposit.Status.ACCEPTED, dto.getDeposit().getStatus());
        
    }
    
    @Test
    public void testTransformNoMatchingGrantThrowsException() {

        when(clientServiceMock.findGrantByAwardNumber(Mockito.anyObject())).thenReturn(null);
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("No Grant matching award number");
        
        NihmsPublication pub = newTestPub();
        
        transformer.transform(pub);
        
    }
    
    private void checkPmrValues(NihmsSubmissionDTO dto) {
        assertEquals(sGrantUri,dto.getGrantUri().toString());
        assertEquals(title, dto.getNihmsSubmission().getTitle());
        assertEquals(volume, dto.getNihmsSubmission().getVolume());
        assertEquals(issue, dto.getNihmsSubmission().getIssue());
        assertEquals(pmid, dto.getNihmsSubmission().getPmid());
        assertEquals(doi, dto.getNihmsSubmission().getDoi());
        assertEquals(Source.OTHER, dto.getNihmsSubmission().getSource());
        assertEquals(sJournalUri, dto.getNihmsSubmission().getJournal().toString());     
    }

        
    private void pmrMockWhenValues() {
        when(pubMedRecordMock.getPmid()).thenReturn(pmid);
        when(pubMedRecordMock.getDoi()).thenReturn(doi);
        when(pubMedRecordMock.getIssn()).thenReturn(issn);
        when(pubMedRecordMock.getEssn()).thenReturn(null);
        when(pubMedRecordMock.getIssue()).thenReturn(issue);
        when(pubMedRecordMock.getVolume()).thenReturn(volume);
        when(pubMedRecordMock.getTitle()).thenReturn(title);        
    }
    
    
    private NihmsSubmission newTestNihmsSubmission() throws Exception {
        NihmsSubmission submission = new NihmsSubmission();
        
        submission.setId(new URI(sSubmissionUri));
        submission.setPmid(pmid);
        submission.setDoi(doi);
        submission.setTitle(title);
        submission.setJournal(new URI(sJournalUri));
        submission.setVolume(volume);
        submission.setIssue(issue);
        List<URI> grants = new ArrayList<URI>();
        grants.add(new URI(sGrantUri));
        submission.setGrants(grants);
        submission.setSource(Source.OTHER);
        submission.setStatus(Status.COMPLIANT_IN_PROGRESS);
        List<URI> deposits = new ArrayList<URI>();
        deposits.add(new URI(sDepositUri));
        submission.setDeposits(deposits);
        
        return submission;
    }
    
    
    private NihmsPublication newTestPub() {
        return new NihmsPublication(NihmsStatus.COMPLIANT, pmid, "AB 12345", null, null, null, null, null, null);
    }
    
}
