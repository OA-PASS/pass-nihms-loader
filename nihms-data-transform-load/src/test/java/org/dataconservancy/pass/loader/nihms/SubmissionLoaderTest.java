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
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.PassEntityType;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NihmsLoader
 * @author Karen Hanson
 */
public class SubmissionLoaderTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    
    @Mock
    private NihmsPassClientService clientServiceMock;
    
    private static final String sGrantUri = "https://example.com/fedora/grants/1";
    private static final String sSubmissionUri = "https://example.com/fedora/submissions/1";
    private static final String sRepositoryUri = "https://example.com/fedora/repositories/1";
    private static final String sDepositUri = "https://example.com/fedora/deposits/1";
    
    @Before 
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }
    
    /**
     * Check that if a Submission is provided with no ID and no deposit, it will do a 
     * createSubmission and not touch Deposit data
     * @throws Exception
     */
    @Test
    public void testLoadNewSubmissionNoNewDeposit() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        
        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(null);
        submission.setTitle("test");

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        URI grantUri = new URI(sGrantUri);
        dto.setGrantUri(grantUri);        
        dto.setNihmsSubmission(submission);
                
        loader.load(dto);

        ArgumentCaptor<NihmsSubmission> submissionCaptor = ArgumentCaptor.forClass(NihmsSubmission.class);
        ArgumentCaptor<URI> grantUriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(clientServiceMock).createNihmsSubmission(submissionCaptor.capture(), grantUriCaptor.capture()); 
        
        assertEquals(PassEntityType.SUBMISSION.getName(), submissionCaptor.getValue().getType());
        assertEquals("test", submissionCaptor.getValue().getTitle());
        assertEquals(0, submissionCaptor.getValue().getDeposits().size());
        
        assertEquals(grantUri, grantUriCaptor.getValue());
        
        //no deposits so shouldn't touch deposit create/update
        verify(clientServiceMock, never()).createDeposit(Mockito.anyObject());    
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());      
    }

    /**
     * Check that if a Submission has an ID but no Deposit, it will do an update
     * and not touch Deposit data.
     * @throws Exception
     */
    @Test
    public void testLoadUpdateSubmissionNoNewDeposit() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);

        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(new URI(sSubmissionUri));

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        URI grantUri = new URI(sGrantUri);
        dto.setGrantUri(grantUri);        
        dto.setNihmsSubmission(submission);
        
        loader.load(dto);

        ArgumentCaptor<NihmsSubmission> submissionCaptor = ArgumentCaptor.forClass(NihmsSubmission.class);
        verify(clientServiceMock).updateNihmsSubmission(submissionCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());

        //no deposits so shouldn't touch deposit create/update
        verify(clientServiceMock, never()).createDeposit(Mockito.anyObject());    
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());     
    }

    /**
     * Check that if Submission has an ID and a Deposit, it will create a new 
     * Deposit, add the new URI to the Submission Deposits list and do a new Submission.
     * @throws Exception
     */
    @Test
    public void testLoadNewSubmissionNewDeposit() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);

        //initiate test objects
        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(null);

        Deposit deposit = new Deposit();
        deposit.setRepository(new URI(sRepositoryUri));
        
        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        URI grantUri = new URI(sGrantUri);
        dto.setGrantUri(grantUri);        
        dto.setNihmsSubmission(submission);
        dto.setDeposit(deposit);
        
        //ensure calls to mock create methods return a URI
        URI submissionUri = new URI(sSubmissionUri);
        when(clientServiceMock.createNihmsSubmission(submission, grantUri)).thenReturn(submissionUri);
        when(clientServiceMock.readNihmsSubmission(Mockito.anyObject())).thenReturn(submission);
        URI depositUri = new URI(sDepositUri);        
        when(clientServiceMock.createDeposit(Mockito.anyObject())).thenReturn(depositUri);
        
        //run it
        loader.load(dto);     
        
        //capture create submission arguments and validate
        ArgumentCaptor<NihmsSubmission> submissionCaptor = ArgumentCaptor.forClass(NihmsSubmission.class);
        ArgumentCaptor<URI> grantUriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(clientServiceMock).createNihmsSubmission(submissionCaptor.capture(), grantUriCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());
        assertEquals(grantUri, grantUriCaptor.getValue());

        //capture create deposit arguments, make local deposit look like the one we expect, and validate
        ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(clientServiceMock).createDeposit(depositCaptor.capture());
        deposit.setSubmission(submissionUri);
        assertEquals(deposit, depositCaptor.getValue());
        
        //for create submission & deposit, need to go back and update deposit list of submission. 
        //create a list of deposits as we expect it, capture submission from update and validate
        List<URI> deposits = new ArrayList<URI>();
        deposits.add(depositUri);
                
        ArgumentCaptor<NihmsSubmission> captor = ArgumentCaptor.forClass(NihmsSubmission.class);
        verify(clientServiceMock).updateNihmsSubmission(captor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());
        assertEquals(deposits.get(0), captor.getValue().getDeposits().get(0));
        assertEquals(deposits.size(), captor.getValue().getDeposits().size());
        
        //check update deposit wasn't touched
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());    
    }

    /**
     * Check that when you do an update submission and add a deposit, that 
     * deposit list is created and Submissions.deposits list is updated accurately
     * @throws Exception
     */
    @Test
    public void testLoadUpdateSubmissionNewDeposit() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);

        //initiate test objects
        NihmsSubmission submission = new NihmsSubmission();
        URI submissionUri = new URI(sSubmissionUri);
        submission.setId(submissionUri);

        Deposit deposit = new Deposit();
        deposit.setRepository(new URI(sRepositoryUri));

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        URI grantUri = new URI(sGrantUri);
        dto.setGrantUri(grantUri);        
        dto.setNihmsSubmission(submission);
        dto.setDeposit(deposit);
        
        //ensure calls to mock create methods return a URI
        URI depositUri = new URI(sDepositUri);        
        when(clientServiceMock.createDeposit(Mockito.anyObject())).thenReturn(depositUri);

        //run it
        loader.load(dto);     

        //capture create deposit arguments, make local deposit look like the one we expect, and validate
        ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(clientServiceMock).createDeposit(depositCaptor.capture());
        deposit.setSubmission(submissionUri);
        assertEquals(deposit, depositCaptor.getValue());
        
        //capture update submission arguments, check submission and deposit uri list are as expected
        ArgumentCaptor<NihmsSubmission> submissionCaptor = ArgumentCaptor.forClass(NihmsSubmission.class);
        verify(clientServiceMock).updateNihmsSubmission(submissionCaptor.capture()); 
        List<URI> deposits = new ArrayList<URI>();
        deposits.add(depositUri);
        submission.setDeposits(deposits);
        assertEquals(submission, submissionCaptor.getValue());
        assertEquals(deposits.get(0), submissionCaptor.getValue().getDeposits().get(0));
        assertEquals(deposits.size(), submissionCaptor.getValue().getDeposits().size());

        //check create submission and update deposit weren't touched
        verify(clientServiceMock, never()).createNihmsSubmission(Mockito.anyObject(), Mockito.anyObject()); 
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject()); 
    }

    /**
     * Check that if you update a submission and a deposit but create no new objects, that
     * deposit and submission are passed as expected, and no creates happen
     * @throws Exception
     */
    @Test
    public void testLoadUpdateSubmissionUpdateDeposit() throws Exception {

        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);

        //initiate test objects
        NihmsSubmission submission = new NihmsSubmission();
        URI submissionUri = new URI(sSubmissionUri);
        submission.setId(submissionUri);

        Deposit deposit = new Deposit();
        URI depositUri = new URI(sDepositUri);
        deposit.setId(depositUri);
        deposit.setRepository(new URI(sRepositoryUri));
        
        List<URI> deposits = new ArrayList<URI>(0);
        deposits.add(depositUri);
        submission.setDeposits(deposits);

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        URI grantUri = new URI(sGrantUri);
        dto.setGrantUri(grantUri);        
        dto.setNihmsSubmission(submission);
        dto.setDeposit(deposit);
        
        //run it
        loader.load(dto);     

        //capture update deposit arguments, make local deposit look like the one we expect, and validate
        ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(clientServiceMock).updateDeposit(depositCaptor.capture());
        assertEquals(deposit, depositCaptor.getValue());
        
        //capture update submission arguments, check submission and deposit uri list are as expected
        ArgumentCaptor<NihmsSubmission> submissionCaptor = ArgumentCaptor.forClass(NihmsSubmission.class);
        verify(clientServiceMock).updateNihmsSubmission(submissionCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());

        //check create submission and create deposit weren't touched
        verify(clientServiceMock, never()).createNihmsSubmission(Mockito.anyObject(), Mockito.anyObject()); 
        verify(clientServiceMock, never()).createDeposit(Mockito.anyObject()); 
    }

    /**
     * Checks an exception is thrown when a null DTO is passed into the loader
     */
    @Test
    public void testLoadThrowExceptionWhenNullDTO() {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("A null Submission object was passed to the loader.");
        
        loader.load(null);

        verifyZeroInteractions(clientServiceMock); 
    }

    /**
     * Checks an exception is thrown when a DTO is passed without the Submission object
     */
    @Test
    public void testLoadThrowExceptionWhenNoSubmission() {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("A null Submission object was passed to the loader.");
        
        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();        
        loader.load(dto);
        
        verifyZeroInteractions(clientServiceMock); 
    }

    /**
     * Checks an exception is thrown when a DTO is passed without a GrantUri
     */
    @Test
    public void testLoadThrowExceptionWhenNoGrantUri() {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        NihmsSubmission submission = new NihmsSubmission();
        submission.setPmid("12345");
        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        dto.setNihmsSubmission(submission);
        dto.setGrantUri(null);
        
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage(String.format("No Grant URI was provided for the Submission with PMID: %s.", dto.getNihmsSubmission().getPmid()));
        
        loader.load(dto);
        
        verifyZeroInteractions(clientServiceMock); 
    }
    
}
