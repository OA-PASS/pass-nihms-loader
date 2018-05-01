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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.dataconservancy.pass.model.Submission;
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
 * Unit tests for Nihms SubmissionLoader
 * @author Karen Hanson
 */
public class SubmissionLoaderTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    
    @Mock
    private NihmsPassClientService clientServiceMock;
    
    private static final String sSubmissionUri = "https://example.com/fedora/submissions/1";
    private static final String sRepositoryCopyUri = "https://example.com/fedora/repositoryCopies/1";
    private static final String sDepositUri = "https://example.com/fedora/deposits/1";
    private static final String sPublicationUri = "https://example.com/fedora/publications/1";
    private static final String sUserUri = "https://example.com/fedora/users/1";

    private static final String title = "A Title";
    private static String pmid = "12345678";
    
    @Before 
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }
    
    /**
     * Check that if a Submission is provided with new Publication, new Submission and no 
     * RepositoryCopy, it will do createPublication, createSubmission and not touch RepositoryCopy data
     * 
     * @throws Exception
     */
    @Test
    public void testLoadNewPubNewSubmissionNoRepositoryCopy() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        
        Publication publication = new Publication();
        publication.setTitle(title);
        publication.setPmid(pmid);
        
        Submission submission = new Submission();
        submission.setPublication(new URI(sPublicationUri));
        submission.setUser(new URI(sUserUri));

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);

        when(clientServiceMock.createPublication(publication)).thenReturn(new URI(sPublicationUri));
        when(clientServiceMock.createSubmission(submission)).thenReturn(new URI(sSubmissionUri));
        
        loader.load(dto);
        
        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(clientServiceMock).createPublication(publicationCaptor.capture());
        assertEquals(publication, publicationCaptor.getValue());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).createSubmission(submissionCaptor.capture()); 
        submission.setPublication(new URI(sPublicationUri));
        assertEquals(submission, submissionCaptor.getValue());

        //no repo copy so shouldn't touch RepositoryCopy create/update
        verify(clientServiceMock, never()).createRepositoryCopy(Mockito.anyObject());    
        verify(clientServiceMock, never()).updateRepositoryCopy(Mockito.anyObject());       
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());      
    }
    
    
    /**
     * Check that if a Submission is provided with existing Publication, new Submission and no 
     * RepositoryCopy, it will do updatePublication, createSubmission and not touch RepositoryCopy data
     * 
     * @throws Exception
     */
    @Test
    public void testLoadUpdatePubNewSubmissionNoRepositoryCopy() throws Exception {
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        
        Publication publication = new Publication();
        publication.setId(new URI(sPublicationUri));
        publication.setTitle(title);
        publication.setPmid(pmid);
        
        Submission submission = new Submission();
        submission.setUser(new URI(sUserUri));
        submission.setPublication(publication.getId());

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);
                
        loader.load(dto);
        
        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(clientServiceMock).updatePublication(publicationCaptor.capture());
        assertEquals(publication, publicationCaptor.getValue());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).createSubmission(submissionCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());

        //no repo copy so shouldn't touch RepositoryCopy create/update
        verify(clientServiceMock, never()).createRepositoryCopy(Mockito.anyObject());    
        verify(clientServiceMock, never()).updateRepositoryCopy(Mockito.anyObject());       
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());      
    }
    

    /**
     * Check that if a Submission is provided with existing Publication, existing Submission and no 
     * RepositoryCopy, it will do updatePublication, updateSubmission and not touch RepositoryCopy data
     * 
     * @throws Exception
     */
    @Test
    public void testLoadUpdatePubUpdateSubmissionNoRepoCopy() throws Exception {
        
        Publication publication = new Publication();
        publication.setId(new URI(sPublicationUri));
        publication.setTitle(title);
        publication.setPmid(pmid);
        
        Submission submission = new Submission();
        submission.setId(new URI(sSubmissionUri));
        submission.setPublication(new URI(sPublicationUri));
        submission.setUser(new URI(sUserUri));

        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);
                
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);
        
        loader.load(dto);
        
        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(clientServiceMock).updatePublication(publicationCaptor.capture());
        assertEquals(publication, publicationCaptor.getValue());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).updateSubmission(submissionCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());

        //no repo copy so shouldn't touch RepositoryCopy create/update
        verify(clientServiceMock, never()).createRepositoryCopy(Mockito.anyObject());    
        verify(clientServiceMock, never()).updateRepositoryCopy(Mockito.anyObject());       
        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());     
    }

    /**
     * Check that if Submission has existing publication, new submission, new RepositoryCopy
     * does updatePublication, createSubmission, createRepositoryCopy
     * 
     * @throws Exception
     */
    @Test
    public void testLoadUpdatePublicationNewSubmissionNewRepoCopy() throws Exception {

        Publication publication = new Publication();
        publication.setId(new URI(sPublicationUri));
        publication.setTitle(title);
        publication.setPmid(pmid);
        
        Submission submission = new Submission();
        submission.setPublication(publication.getId());
        submission.setUser(new URI(sUserUri));
        
        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setPublication(publication.getId());
        repositoryCopy.setRepository(TransformUtil.getNihmsRepositoryUri());
        repositoryCopy.setCopyStatus(CopyStatus.ACCEPTED);
        
        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);
        dto.setRepositoryCopy(repositoryCopy);
        
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);

        URI submissionUri = new URI(sSubmissionUri);
        when(clientServiceMock.createSubmission(submission)).thenReturn(submissionUri);
        
        //run it
        loader.load(dto);     
        
        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(clientServiceMock).updatePublication(publicationCaptor.capture()); 
        assertEquals(publication, publicationCaptor.getValue());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).createSubmission(submissionCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());

        ArgumentCaptor<RepositoryCopy> repoCopyCaptor = ArgumentCaptor.forClass(RepositoryCopy.class);
        verify(clientServiceMock).createRepositoryCopy(repoCopyCaptor.capture()); 
        assertEquals(repositoryCopy, repoCopyCaptor.getValue());

        verify(clientServiceMock, never()).updateDeposit(Mockito.anyObject());     
    }

    
    /**
     * Check that if Submission has existing publication, existing submission, new RepositoryCopy,
     * existing Deposit that has no RepoCopy that it does updatePublication, updateSubmission, 
     * createRepositoryCopy, and updates Deposit link
     * @throws Exception
     */
    @Test
    public void testLoadNewPubNewSubmissionNewRepoCopy() throws Exception {

        Publication publication = new Publication();
        publication.setId(new URI(sPublicationUri));
        publication.setTitle(title);
        publication.setPmid(pmid);

        Submission submission = new Submission();
        submission.setId(new URI(sSubmissionUri));
        submission.setPublication(publication.getId());
        submission.setUser(new URI(sUserUri));
        
        RepositoryCopy repositoryCopy = new RepositoryCopy();
        repositoryCopy.setPublication(publication.getId());
        repositoryCopy.setRepository(TransformUtil.getNihmsRepositoryUri());
        repositoryCopy.setCopyStatus(CopyStatus.ACCEPTED);
        
        Deposit deposit = new Deposit();
        deposit.setId(new URI(sDepositUri));
        deposit.setDepositStatus(DepositStatus.ACCEPTED);
        deposit.setRepository(repositoryCopy.getRepository());
        
        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();
        dto.setPublication(publication);
        dto.setSubmission(submission);
        dto.setRepositoryCopy(repositoryCopy);
        
        SubmissionLoader loader = new SubmissionLoader(clientServiceMock);

        URI repositoryCopyUri = new URI(sRepositoryCopyUri);
        when(clientServiceMock.createRepositoryCopy(repositoryCopy)).thenReturn(repositoryCopyUri);
        when(clientServiceMock.findDepositBySubmissionAndRepositoryId(submission.getId(), repositoryCopy.getRepository())).thenReturn(deposit);
        
        //run it
        loader.load(dto);     
        
        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(clientServiceMock).updatePublication(publicationCaptor.capture()); 
        assertEquals(publication, publicationCaptor.getValue());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(clientServiceMock).updateSubmission(submissionCaptor.capture()); 
        assertEquals(submission, submissionCaptor.getValue());

        ArgumentCaptor<RepositoryCopy> repoCopyCaptor = ArgumentCaptor.forClass(RepositoryCopy.class);
        verify(clientServiceMock).createRepositoryCopy(repoCopyCaptor.capture()); 
        assertEquals(repositoryCopy, repoCopyCaptor.getValue());

        ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(clientServiceMock).updateDeposit(depositCaptor.capture()); 
        deposit.setRepositoryCopy(repositoryCopyUri);
        assertEquals(deposit, depositCaptor.getValue());
        
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
    
}
