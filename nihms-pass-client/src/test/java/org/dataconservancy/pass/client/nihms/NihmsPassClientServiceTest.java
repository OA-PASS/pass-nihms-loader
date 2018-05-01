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
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
    private static final String sUserUri = "https://example.com/fedora/users/1";
    private static final String sDepositUri = "https://example.com/fedora/deposits/1";
    private static final String sRepositoryUri = "https://example.com/fedora/repositories/1";
    private static final String sPublicationUri = "https://example.com/fedora/publications/1";
    private static final String sRepositoryCopyUri = "https://example.com/fedora/repositoryCopies/1";
    private static final String pmid = "12345678";
    private static final String doi = "https://doi.org/10.000/abcde";
    private static final String awardNumber = "RH 1234";
    private static final String title = "Article Title";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    
    @Mock
    private PassClient mockClient;
        
    private NihmsPassClientService clientService;
    
    private URI grantUri;
    private URI userUri;
    private URI submissionUri;
    private URI depositUri;
    private URI repositoryUri;
    private URI publicationUri;
    private URI repositoryCopyUri;
    
    @Before 
    public void initMocks() throws Exception{
        MockitoAnnotations.initMocks(this);
        clientService = new NihmsPassClientService(mockClient);
        grantUri = new URI(sGrantUri);
        userUri = new URI(sUserUri);
        submissionUri = new URI(sSubmissionUri);
        depositUri = new URI(sDepositUri);
        repositoryUri = new URI(sRepositoryUri);
        publicationUri = new URI(sPublicationUri);
        repositoryCopyUri = new URI(sRepositoryCopyUri);
    }
    
    
    /**
     * Checks that it findGrantByAwardNumber searches the database with and without space
     * returns null when non found
     */
    @Test
    public void testFindGrantByAwardNumberNoMatch() {
        
        ArgumentCaptor<String> awardNumCaptor = ArgumentCaptor.forClass(String.class);
        
        when(mockClient.findByAttribute(eq(Grant.class), eq("awardNumber"), eq(awardNumber))).thenReturn(null);

        URI grant = clientService.findGrantByAwardNumber(awardNumber);
        
        verify(mockClient, times(2)).findByAttribute(eq(Grant.class), eq("awardNumber"), awardNumCaptor.capture()); 
        
        assertEquals(awardNumber, awardNumCaptor.getAllValues().get(0));
        assertEquals(awardNumber.replace(" ", ""), awardNumCaptor.getAllValues().get(1));
        assertEquals(null, grant);
        
    }
    
    
    /**
     * Checks that it findGrantByAwardNumber returns URI when one found
     */
    @Test
    public void testFindGrantByAwardNumberHasMatch() throws Exception {
                
        when(mockClient.findByAttribute(eq(Grant.class), eq("awardNumber"), eq(awardNumber))).thenReturn(grantUri);

        URI matchedGrantUri = clientService.findGrantByAwardNumber(awardNumber);
        verify(mockClient).findByAttribute(eq(Grant.class), eq("awardNumber"), eq(awardNumber)); 
        assertEquals(grantUri, matchedGrantUri);
        
    }

    
    /**
     * Checks that it findPublicationById returns match based on PMID
     */
    @Test
    public void testFindPublicationByIdPmidMatch() throws Exception {
        Publication publication = new Publication();
        publication.setId(publicationUri);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setPmid(pmid);
        
        when(mockClient.findByAttribute(eq(Publication.class), eq("pmid"), eq(pmid))).thenReturn(publicationUri);
        when(mockClient.readResource(eq(publicationUri), eq(Publication.class))).thenReturn(publication);
        
        Publication matchedPublication = clientService.findPublicationById(pmid, doi);
        
        assertEquals(publication, matchedPublication);         
    }

    
    /**
     * Checks that it findPublicationById returns match based on DOI
     */
    @Test
    public void testFindPublicationByIdDoiMatch() throws Exception {
        Publication publication = new Publication();
        publication.setId(publicationUri);
        publication.setDoi(doi);
        publication.setTitle(title);
        publication.setPmid(pmid);
        
        when(mockClient.findByAttribute(Publication.class, "pmid", pmid)).thenReturn(null);
        when(mockClient.findByAttribute(Publication.class, "doi", doi)).thenReturn(publicationUri);
        when(mockClient.readResource(publicationUri, Publication.class)).thenReturn(publication);
        
        Publication matchedPublication = clientService.findPublicationById(pmid, doi);
        
        verify(mockClient, times(2)).findByAttribute(eq(Publication.class), any(), any());
        assertEquals(publication, matchedPublication);         
    }

    
    /**
     * Checks that it findPublicationById returns null when no match
     */
    @Test
    public void testFindPublicationByIdNoMatch() throws Exception {
       
        when(mockClient.findByAttribute(Publication.class, "pmid", pmid)).thenReturn(null);
        when(mockClient.findByAttribute(Publication.class, "doi", doi)).thenReturn(null);
        
        Publication matchedPublication = clientService.findPublicationById(pmid, doi);
        
        verify(mockClient, times(2)).findByAttribute(eq(Publication.class), Mockito.anyString(), any());
        assertNull(matchedPublication);         
    }  

    
    /**
     * Checks that it findRepositoryCopyByRepoAndPubId returns match based on repository and publication
     */
    @Test
    public void testFindRepositoryCopyByRepoAndPubIdHasMatch() throws Exception {
        RepositoryCopy repoCopy = new RepositoryCopy();
        repoCopy.setId(repositoryCopyUri);
        repoCopy.setPublication(publicationUri);
        
        Set<URI> repositoryCopyUris = new HashSet<URI>();
        repositoryCopyUris.add(repositoryCopyUri);
        
        when(mockClient.findAllByAttributes(eq(RepositoryCopy.class), any())).thenReturn(repositoryCopyUris);
        when(mockClient.readResource(eq(repositoryCopyUri), eq(RepositoryCopy.class))).thenReturn(repoCopy);
        
        RepositoryCopy matchedRepoCopy = clientService.findRepositoryCopyByRepoAndPubId(repositoryUri, publicationUri);
        
        assertEquals(repoCopy, matchedRepoCopy);       
    }

    
    /**
     * Checks that it findRepositoryCopyByRepoAndPubId returns null when no match
     */
    @Test
    public void testFindRepositoryCopyByRepoAndPubIdNoMatch() throws Exception {
        when(mockClient.findAllByAttributes(eq(RepositoryCopy.class), any())).thenReturn(null);
        
        RepositoryCopy matchedRepoCopy = clientService.findRepositoryCopyByRepoAndPubId(repositoryUri, publicationUri);
        
        assertNull(matchedRepoCopy);       
    }
    
    
    
    /**
     * Tests the scenario where a match is found right away using publication+grant 
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionPubGrantHasMatch() throws Exception {
        
        URI submissionUri2 = new URI(sSubmissionUri + "2");
        
        Set<URI> submissions = new HashSet<URI>();
        submissions.add(submissionUri);
        submissions.add(submissionUri2);

        Submission submission = new Submission();
        submission.setId(submissionUri);

        Submission submission2 = new Submission();
        submission.setId(submissionUri2);
        
        Grant grant = new Grant();
        grant.setId(grantUri);
        grant.setPi(userUri);

        when(mockClient.findAllByAttributes(eq(Submission.class), any())).thenReturn(submissions);
        when(mockClient.readResource(any(), eq(Submission.class))).thenReturn(submission).thenReturn(submission2);
        
        List<Submission> matchedSubmissions = clientService.findSubmissionsByPublicationAndUserId(publicationUri, userUri);
        
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(mockClient).findAllByAttributes(eq(Submission.class), argumentCaptor.capture());
        
        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(userUri, argumentCaptor.getValue().get("user"));
        assertEquals(publicationUri, argumentCaptor.getValue().get("publication"));
        
        assertEquals(submission, matchedSubmissions.get(0));
        assertEquals(submission2, matchedSubmissions.get(1));
        
    }
    

    /**
     * Tests the scenario where no match is found using publication+grant 
     * @throws Exception
     */
    @Test
    public void testFindExistingSubmissionPubGrantNoMatch() throws Exception {
        //returns null first time, then the submission the second time when search using doi
        
        when(mockClient.findAllByAttributes(eq(Submission.class), any())).thenReturn(new HashSet<URI>());
        
        List<Submission> matchedSubmissions = clientService.findSubmissionsByPublicationAndUserId(publicationUri, userUri);
        
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(mockClient).findAllByAttributes(eq(Submission.class), argumentCaptor.capture());
        
        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(userUri, argumentCaptor.getValue().get("user"));
        assertEquals(publicationUri, argumentCaptor.getValue().get("publication"));
        
        assertTrue(matchedSubmissions.size()==0);
        
    }
    
    
    /**
     * Test that given a submission containing some Deposit URIs, they are all retrieved and returned as a list
     * @throws Exception
     */
    @Test
    public void testFindDepositBySubmissionAndRepositoryIdHasMatch() throws Exception {
        Set<URI> depositUris = new HashSet<URI>();
        depositUris.add(depositUri);
        
        Deposit deposit = new Deposit();
        deposit.setId(depositUri);
        deposit.setSubmission(submissionUri);
        deposit.setRepository(repositoryUri);

        when(mockClient.findAllByAttributes(eq(Deposit.class), any())).thenReturn(depositUris);
        when(mockClient.readResource(eq(depositUri), eq(Deposit.class))).thenReturn(deposit);
        
        Deposit matchedDeposit = clientService.findDepositBySubmissionAndRepositoryId(submissionUri, repositoryUri);
        
        verify(mockClient).findAllByAttributes(eq(Deposit.class), any());
        verify(mockClient).readResource(any(), eq(Deposit.class));
        
        assertEquals(deposit, matchedDeposit);
    }
    
    /**
     * Checks that exception thrown if too many deposit URIs are returned when finding Deposits related to a Submission
     * and Repository combination
     * @throws Exception
     */
    @Test(expected=RuntimeException.class)
    public void testFindDepositBySubmissionAndRepositoryIdExtraMatch() throws Exception {
        URI depositUri2 = new URI(sDepositUri + "2");
        Set<URI> depositUris = new HashSet<URI>();
        depositUris.add(depositUri);
        depositUris.add(depositUri2);

        when(mockClient.findAllByAttributes(eq(Deposit.class), any())).thenReturn(depositUris);
        
        clientService.findDepositBySubmissionAndRepositoryId(submissionUri, repositoryUri);
        
    }
    
        
    /**
     * Checks that createSubmission works as expected
     */
    @Test
    public void testCreateSubmission() {
        Submission submission = new Submission();
        
        when(mockClient.createResource(eq(submission))).thenReturn(submissionUri);
        
        URI newSubmissionUri = clientService.createSubmission(submission);
        
        verify(mockClient).createResource(eq(submission));
        
        assertEquals(submissionUri, newSubmissionUri);
        
    }
    
    
    /**
     * Checks that if there are changes an update happens in updateSubmission
     */
    @Test
    public void testUpdateSubmissionHasChanges() {
        Submission submission = new Submission();
        submission.setId(submissionUri);
        submission.setSubmitted(false);

        //make a submission that is different
        Submission submissionEdited = new Submission();
        submissionEdited.setId(submissionUri);
        submissionEdited.setSubmitted(true);
        
        when(mockClient.readResource(eq(submissionUri), eq(Submission.class))).thenReturn(submission);
        clientService.updateSubmission(submissionEdited);
        verify(mockClient).updateResource(eq(submissionEdited));
        
    }
    

    /**
     * Checks that if there are no changes an update does not happen in updateSubmission
     */
    @Test
    public void testUpdateSubmissionNoChanges() {
        Submission submission = new Submission();
        submission.setId(submissionUri);

        when(mockClient.readResource(eq(submissionUri), eq(Submission.class))).thenReturn(submission);
        //try to update submission with no changes
        clientService.updateSubmission(submission);
        verify(mockClient, never()).updateResource(any());
    }
    
    
}
