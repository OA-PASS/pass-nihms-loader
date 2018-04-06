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
package org.dataconservancy.pass.client.nihms;

import java.net.URI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.PassEntityType;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests many of the public methods in NihmsPassClientService. Does not currently test 
 * methods that consist of only a null check and then a call to the fedora client
 * @author Karen Hanson
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class NihmsPassClientServiceTest {
    
    private static final String sGrantUri = "https://example.com/fedora/grants/1";
    private static final String sSubmissionUri = "https://example.com/fedora/submissions/1";
    private static final String sPersonUri = "https://example.com/fedora/people/1";
    private static final String sDepositUri = "https://example.com/fedora/deposits/1";
    private static final String sRepositoryUri = "https://example.com/fedora/repositories/1";
    private static final String pmid = "12345678";
    private static final String doi = "https://doi.org/10.000/abcde";
    private static final String awardNumber = "RH 1234";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    
    @Mock
    private PassClient mockClient;
        
    private NihmsPassClientService clientService;
    
    private URI grantUri;
    private URI personUri;
    private URI submissionUri;
    private URI depositUri;
    private URI repositoryUri;
    
    @Before 
    public void initMocks() throws Exception{
        MockitoAnnotations.initMocks(this);
        clientService = new NihmsPassClientService(mockClient);
        grantUri = new URI(sGrantUri);
        personUri = new URI(sPersonUri);
        submissionUri = new URI(sSubmissionUri);
        depositUri = new URI(sDepositUri);
        repositoryUri = new URI(sRepositoryUri);
    }
    
    
    /**
     * Checks that it findGrantByAwardNumber searches the database with and without space
     * returns null when non found
     */
    @Test
    public void testFindGrantByAwardNumberNoMatch() {
        
        ArgumentCaptor<String> awardNumCaptor = ArgumentCaptor.forClass(String.class);
        
        when(mockClient.findByAttribute(Mockito.any(), Mockito.anyString(), Mockito.any())).thenReturn(null);

        URI grant = clientService.findGrantByAwardNumber(awardNumber);
        
        verify(mockClient, times(2)).findByAttribute(Mockito.any(), Mockito.anyString(), awardNumCaptor.capture()); 
        
        assertEquals(awardNumber, awardNumCaptor.getAllValues().get(0));
        assertEquals(awardNumber.replace(" ", ""), awardNumCaptor.getAllValues().get(1));
        assertEquals(null, grant);
        
    }
    
    
    /**
     * Checks that it findGrantByAwardNumber returns URI when one found
     */
    @Test
    public void testFindGrantByAwardNumberHasMatch() throws Exception {
                
        when(mockClient.findByAttribute(any(), Mockito.anyString(), any())).thenReturn(grantUri);

        URI matchedGrantUri = clientService.findGrantByAwardNumber(awardNumber);
        verify(mockClient).findByAttribute(any(), Mockito.anyString(), any()); 
        assertEquals(grantUri, matchedGrantUri);
        
    }


    /**
     * Tests the scenario where a match is found right away using pmid+grant 
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionGrantPmidHasMatch() throws Exception {
        
        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionUri);

        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(submissionUri);

        when(mockClient.findAllByAttributes(eq(NihmsSubmission.class), any())).thenReturn(submissions);
        when(mockClient.readResource(eq(submissionUri), eq(NihmsSubmission.class))).thenReturn(submission);
        
        Submission matchedSubmission = clientService.findExistingSubmission(grantUri, pmid, null);
        
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(mockClient).findAllByAttributes(eq(NihmsSubmission.class), argumentCaptor.capture());
        
        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(sGrantUri, argumentCaptor.getValue().get("grant"));
        assertEquals(pmid, argumentCaptor.getValue().get("pmid"));
        
        assertEquals(submission, matchedSubmission);
        
    }
    

    /**
     * Tests the scenario where no match is found using pmid+grant but it finds
     * a match using doi+grant
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionGrantDoiHasMatch() throws Exception {
        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionUri);

        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(submissionUri);
        
        //returns null first time, then the submission the second time when search using doi
        when(mockClient.findAllByAttributes(eq(NihmsSubmission.class), any())).thenReturn(null).thenReturn(submissions);
        when(mockClient.readResource(eq(submissionUri), eq(NihmsSubmission.class))).thenReturn(submission);
        
        Submission matchedSubmission = clientService.findExistingSubmission(grantUri, pmid, doi);
        
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(mockClient, times(2)).findAllByAttributes(eq(NihmsSubmission.class), argumentCaptor.capture());
        
        verify(mockClient).readResource(eq(submissionUri), eq(NihmsSubmission.class));
                
        assertEquals(2, argumentCaptor.getAllValues().get(0).size());
        assertEquals(sGrantUri, argumentCaptor.getAllValues().get(0).get("grant"));
        assertEquals(pmid, argumentCaptor.getAllValues().get(0).get("pmid"));

        assertEquals(2, argumentCaptor.getAllValues().get(1).size());
        assertEquals(sGrantUri, argumentCaptor.getAllValues().get(1).get("grant"));
        assertEquals(doi, argumentCaptor.getAllValues().get(1).get("doi"));
        
        assertEquals(submission, matchedSubmission);
        
    }


    /**
     * Tests the scenario where no match is found using pmid+grant or doi+grant, 
     * but it finds a match on pmid+pi.
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionPmidPiHasMatch() throws Exception {
        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionUri);

        Grant grant = new Grant();
        grant.setId(grantUri);
        grant.setPi(personUri);
        List<URI> grants = new ArrayList<URI>();
        grants.add(grantUri);
        
        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(submissionUri);
        submission.setGrants(grants);
        
        //returns null first two times, then the submissions list the third time, the call will be pmid only
        //and should return submissions match
        when(mockClient.findAllByAttributes(eq(NihmsSubmission.class), any()))
                                .thenReturn(null)
                                .thenReturn(null);
        when(mockClient.findAllByAttribute(eq(NihmsSubmission.class), eq("pmid"), eq(pmid))).thenReturn(submissions);
        
        when(mockClient.readResource(eq(submissionUri), eq(NihmsSubmission.class))).thenReturn(submission);
        when(mockClient.readResource(eq(grantUri), eq(Grant.class))).thenReturn(grant);
        
        Submission matchedSubmission = clientService.findExistingSubmission(grantUri, pmid, doi);
        
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(mockClient, times(2)).findAllByAttributes(eq(NihmsSubmission.class), argumentCaptor.capture());
        verify(mockClient).findAllByAttribute(eq(NihmsSubmission.class), eq("pmid"), eq(pmid));
        verify(mockClient, times(2)).readResource(eq(grantUri), eq(Grant.class));
        verify(mockClient).readResource(eq(submissionUri), eq(NihmsSubmission.class));
                
        assertEquals(2, argumentCaptor.getAllValues().get(0).size());
        assertEquals(sGrantUri, argumentCaptor.getAllValues().get(0).get("grant"));
        assertEquals(pmid, argumentCaptor.getAllValues().get(0).get("pmid"));

        assertEquals(2, argumentCaptor.getAllValues().get(1).size());
        assertEquals(sGrantUri, argumentCaptor.getAllValues().get(1).get("grant"));
        assertEquals(doi, argumentCaptor.getAllValues().get(1).get("doi"));
        
        assertEquals(submission, matchedSubmission);
        
    }
    

    /**
     * Tests the scenario where no match is found using pmid+grant, doi+grant, 
     * or pmid+pi, now it should move onto doi+pi.  In this test, there is an 
     * extra submission match with a different PI to make sure the private
     * NihmsPassClientService.pickSubmissionWithSamePi() is behaving as it should
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionDoiPiHasMatch() throws Exception {

        List<URI> grants1 = new ArrayList<URI>();        
        Grant grant1 = new Grant();
        grant1.setId(grantUri);
        grant1.setPi(personUri);
        grants1.add(grantUri);

        List<URI> grants2 = new ArrayList<URI>();    
        Grant grant2 = new Grant();
        URI grantUri2 = new URI(sGrantUri + "2");
        URI personUri2 = new URI(sPersonUri + "2");
        grant2.setId(grantUri2);
        grant2.setPi(personUri2);
        grants2.add(grantUri2);
        
        NihmsSubmission submission1 = new NihmsSubmission();
        submission1.setId(submissionUri);
        submission1.setGrants(grants1);
        
        NihmsSubmission submission2 = new NihmsSubmission();
        URI submissionUri2 = new URI(sSubmissionUri + "2");
        submission2.setId(submissionUri2);
        submission2.setGrants(grants2);

        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionUri);
        submissions.add(submissionUri2); 
        
        //returns null first three times, then the submission list the fourth time, the call will be doi only
        //and should return submissions match
        when(mockClient.findAllByAttributes(eq(NihmsSubmission.class), any()))
                                .thenReturn(null)
                                .thenReturn(null);
        when(mockClient.findAllByAttribute(eq(NihmsSubmission.class), eq("pmid"), eq(pmid))).thenReturn(null);
        when(mockClient.findAllByAttribute(eq(NihmsSubmission.class), eq("doi"), eq(doi))).thenReturn(submissions);
        
        when(mockClient.readResource(eq(submissionUri), eq(NihmsSubmission.class))).thenReturn(submission1); 
        when(mockClient.readResource(eq(submissionUri2), eq(NihmsSubmission.class))).thenReturn(submission2); 
        
        when(mockClient.readResource(eq(grantUri), eq(Grant.class))).thenReturn(grant1).thenReturn(grant1);
        when(mockClient.readResource(eq(grantUri2), eq(Grant.class))).thenReturn(grant2);
        
        //going to look for grant2 match to make sure it bypasses the first URI in the set when finding a match
        Submission matchedSubmission = clientService.findExistingSubmission(grantUri2, pmid, doi);
        
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockClient, times(2)).findAllByAttributes(eq(NihmsSubmission.class), argumentCaptor.capture());

        ArgumentCaptor<URI> grantUriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(mockClient, times(3)).readResource(grantUriCaptor.capture(), eq(Grant.class));
        
        verify(mockClient).findAllByAttribute(eq(NihmsSubmission.class), eq("pmid"), eq(pmid));
        verify(mockClient).findAllByAttribute(eq(NihmsSubmission.class), eq("doi"), eq(doi));
        verify(mockClient).readResource(eq(submissionUri), eq(NihmsSubmission.class));
                
        assertEquals(2, argumentCaptor.getAllValues().get(0).size());
        assertEquals(grantUri2.toString(), argumentCaptor.getAllValues().get(0).get("grant"));
        assertEquals(pmid, argumentCaptor.getAllValues().get(0).get("pmid"));

        assertEquals(2, argumentCaptor.getAllValues().get(1).size());
        assertEquals(grantUri2.toString(), argumentCaptor.getAllValues().get(1).get("grant"));
        assertEquals(doi, argumentCaptor.getAllValues().get(1).get("doi"));
        
        assertEquals(grantUri2, grantUriCaptor.getAllValues().get(0));
        assertEquals(grantUri, grantUriCaptor.getAllValues().get(1));
        assertEquals(grantUri2, grantUriCaptor.getAllValues().get(2));
        
        assertEquals(submission2, matchedSubmission);
        
    }
    
    
    /**
     * The findSubmissionByArticleIdAndGrant() method is fairly well exercised within 
     * the findExistingSubmission() tests. This just makes sure that an error is thrown
     * if 2 submissions are found with the same grant and articleId
     */
    @Test(expected=RuntimeException.class) 
    public void testFindSubmissionByArticleIdAndGrantMultiMatches() throws Exception {
        URI submissionUri2 = new URI(sSubmissionUri + "2");
        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionUri);
        submissions.add(submissionUri2); 
        //returns multiple matches, should not do this! Suggests error in the db, so go no further to
        //avoid additional damage
        when(mockClient.findAllByAttributes(eq(NihmsSubmission.class), any())).thenReturn(submissions);
        clientService.findSubmissionByArticleIdAndGrant(pmid, grantUri, "pmid");
    }
    
    /**
     * Test that given a submission containing some Deposit URIs, they are all retrieved and returned as a list
     * @throws Exception
     */
    @Test
    public void testReadSubmissionDeposits() throws Exception {
        URI depositUri2 = new URI(sDepositUri + "2");
        Set<URI> depositUris = new HashSet<URI>();
        depositUris.add(depositUri);
        depositUris.add(depositUri2);
        
        Deposit deposit1 = new Deposit();
        deposit1.setId(depositUri);
        deposit1.setSubmission(submissionUri);
        deposit1.setRepository(repositoryUri);

        Deposit deposit2 = new Deposit();
        deposit2.setId(depositUri2);
        deposit2.setSubmission(submissionUri);
        
        when(mockClient.findAllByAttribute(eq(Deposit.class), eq(PassEntityType.SUBMISSION.getName()), eq(submissionUri))).thenReturn(depositUris);
        when(mockClient.readResource(eq(depositUri), eq(Deposit.class))).thenReturn(deposit1);
        when(mockClient.readResource(eq(depositUri2), eq(Deposit.class))).thenReturn(deposit2);
        
        Set<Deposit> deposits = clientService.readSubmissionDeposits(submissionUri);
        
        verify(mockClient).findAllByAttribute(eq(Deposit.class), eq(PassEntityType.SUBMISSION.getName()), eq(submissionUri));
        verify(mockClient, times(2)).readResource(any(), eq(Deposit.class));
        
        assertEquals(2, deposits.size());
        assertTrue(deposits.contains(deposit1));
        assertTrue(deposits.contains(deposit2));
        
    }
    
    /**
     * Checks that createNihmsSubmission works as expected, it should add the 
     * submission to the grant.submissions lists at the end.
     */
    @Test
    public void testCreateNihmsSubmission() {
        NihmsSubmission submission = new NihmsSubmission();
        Grant grant = new Grant();
        grant.setId(grantUri);
        
        when(mockClient.createResource(eq(submission))).thenReturn(submissionUri);
        when(mockClient.readResource(eq(grantUri), eq(Grant.class))).thenReturn(grant);
        
        URI newSubmissionUri = clientService.createNihmsSubmission(submission, grantUri);
        
        verify(mockClient).createResource(eq(submission));
        verify(mockClient).readResource(eq(grantUri),eq(Grant.class));
        ArgumentCaptor<Grant> grantCaptor = ArgumentCaptor.forClass(Grant.class);
        verify(mockClient).updateResource(grantCaptor.capture());
        
        assertEquals(grantUri, grantCaptor.getValue().getId());
        assertEquals(submissionUri, grantCaptor.getValue().getSubmissions().get(0));
        assertEquals(submissionUri, newSubmissionUri);
        
    }
    
    
    /**
     * Checks that if there are changes an update happens in updateNihmsSubmission
     */
    @Test
    public void testUpdateNihmsSubmissionHasChanges() {
        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(submissionUri);

        //make a submission that is different
        NihmsSubmission submissionEdited = new NihmsSubmission();
        submissionEdited.setId(submissionUri);
        submissionEdited.setDoi(doi);
        
        when(mockClient.readResource(eq(submissionUri), eq(NihmsSubmission.class))).thenReturn(submission);
        clientService.updateNihmsSubmission(submissionEdited);
        verify(mockClient).updateResource(eq(submissionEdited));
        
    }
    

    /**
     * Checks that if there are no changes an update does not happen in updateNihmsSubmission
     */
    @Test
    public void testUpdateNihmsSubmissionNoChanges() {
        NihmsSubmission submission = new NihmsSubmission();
        submission.setId(submissionUri);

        when(mockClient.readResource(eq(submissionUri), eq(NihmsSubmission.class))).thenReturn(submission);
        //try to update submission with no changes
        clientService.updateNihmsSubmission(submission);
        verify(mockClient, never()).updateResource(any());
    }
    
    
}
