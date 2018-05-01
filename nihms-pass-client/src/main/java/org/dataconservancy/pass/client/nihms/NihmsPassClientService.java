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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientDefault;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 */
public class NihmsPassClientService {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPassClientService.class);
    
    private String AWARD_NUMBER_FLD = "awardNumber";
    
    private PassClient client;

    public NihmsPassClientService() {
        this.client = new PassClientDefault();
    }
    
    public NihmsPassClientService(PassClient client) {
        this.client = client;
    }

    
    /**
     * Searches for Grant record using awardNumber. Tries this first using the awardNumber as passed in,
     * then again without spaces.
     * @param awardNumber
     * @return
     */
    public URI findGrantByAwardNumber(String awardNumber) {
        if (awardNumber==null || awardNumber.length()==0) {
            throw new IllegalArgumentException("awardNumber cannot be empty");
        }
        URI grantUri = client.findByAttribute(Grant.class, AWARD_NUMBER_FLD, awardNumber);
        if (grantUri==null) {
            //try with no spaces
            awardNumber = awardNumber.replaceAll("\\s+","");
            grantUri = client.findByAttribute(Grant.class, AWARD_NUMBER_FLD, awardNumber);
        }
        
        return grantUri;        
    }

    
    public Publication findPublicationById(String pmid, String doi) {
        if (pmid == null) {
            throw new RuntimeException("PMID cannot be null when searching for existing Submission.");
        }
        
        Publication publication = findPublicationByArticleId(pmid, "pmid");
        if (publication != null) {
            return publication;
        }
        
        if (doi != null) {
            publication = findPublicationByArticleId(doi, "doi");
            if (publication != null) {
                return publication;
            }
        }
        
        return null;
    }

    
    public RepositoryCopy findRepositoryCopyByRepoAndPubId(URI repoId, URI pubId) {
        if (repoId == null) {
            throw new RuntimeException("repositoryId cannot be null when searching for existing RepositoryCopy.");
        }
        if (pubId == null) {
            throw new RuntimeException("publicationId cannot be null when searching for existing RepositoryCopy.");
        }
        
        Map<String,Object> attribs = new HashMap<String,Object>();
        attribs.put("publication", pubId);
        attribs.put("repository", repoId);
        
        Set<URI> repositoryCopies = client.findAllByAttributes(RepositoryCopy.class, attribs);
        if (repositoryCopies==null || repositoryCopies.size()==0) {
            return null;
        } else if (repositoryCopies.size()==1) {
            return (RepositoryCopy) client.readResource(repositoryCopies.iterator().next(), RepositoryCopy.class);
        } else {
            throw new RuntimeException(String.format("There are multiple repository copies matching RepositoryId %s and PublicationId %s. "
                    + "This indicates a data corruption, please check the data and try again.", pubId, repoId));
        }
    }
    
    
    /**
     * Searches for Submissions matching a specific publication and User Id
     * @param publicationId
     * @param grantId
     * @return
     */
    public List<Submission> findSubmissionsByPublicationAndUserId(URI publicationId, URI userId) {
        if (publicationId == null) {
            throw new RuntimeException("PublicationId cannot be null when searching for existing Submissions");
        }
        if (userId == null) {
            throw new RuntimeException("UserId cannot be null when searching for existing Submissions");
        }
        List<Submission> submissions = new ArrayList<Submission>();
        
        Map<String,Object> attribs = new HashMap<String,Object>();
        attribs.put("publication", publicationId);
        attribs.put("user", userId);
        
        Set<URI> uris = client.findAllByAttributes(Submission.class, attribs);
        for (URI uri : uris) {
            if (uri!=null) {
                submissions.add(readSubmission(uri));
            }
        } 
        
        return submissions;
    }
    
    /**      
     * Searches for Publication record using articleIds. This detects whether we are dealing
     * with a record that was already looked at previously. 
     * @param articleId
     * @param grantUri
     * @param idFieldName the name of the field on the Submission model that will be matched e.g. "pmid" or "doi"
     * @return
     */
    public Publication findPublicationByArticleId(String articleId, String idFieldName) {
        if (articleId==null || articleId.length()==0) {
            throw new IllegalArgumentException("article ID cannot be empty");
        }
        if (idFieldName==null || idFieldName.length()==0) {
            throw new IllegalArgumentException("idFieldName cannot be empty");
        }
        URI match = client.findByAttribute(Publication.class, idFieldName, articleId);
        if (match!=null) {
            return readPublication(match);
        } 
        
        return null;        
    }
    
    
    /**
     * Look up Journal URI using ISSN
     * @param issn
     * @return
     */
    public URI findJournalByIssn(String issn) {
        if (issn == null) {
            throw new IllegalArgumentException("issn cannot be empty");            
        }
        return client.findByAttribute(Journal.class, "issn", issn);
    }

    
    /**
     * Searches for a Deposit that matches a Submission and Repository ID combination
     * @param submissionId
     * @param repositoryId
     * @return
     */
    public Deposit findDepositBySubmissionAndRepositoryId(URI submissionId, URI repositoryId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");            
        }
        if (repositoryId == null) {
            throw new IllegalArgumentException("repositoryId cannot be empty");            
        }
        Map<String,Object> attribs = new HashMap<String,Object>();
        attribs.put("submission", submissionId);
        attribs.put("repository", repositoryId);        
        Set<URI> matches = client.findAllByAttributes(Deposit.class, attribs);
        if (matches==null || matches.size()==0) {
            return null;
        }
        if (matches.size()==1) {
            Deposit deposit = client.readResource(matches.iterator().next(),Deposit.class);
            return deposit;
        } else {
            throw new RuntimeException(String.format("There are multiple Deposits matching submissionId %s and repositoryId %s. "
                    + "This indicates a data corruption, please check the data and try again.", submissionId, repositoryId));
        }
    }
    
    
     /**
     * Retrieve full grant record from database
     * @param grantUri
     * @return Grant if found, or null if not found
     */
    public Grant readGrant(URI grantUri){
        if (grantUri == null) {
            throw new IllegalArgumentException("grantUri cannot be empty");            
        }
        Object grantObj = client.readResource(grantUri, Grant.class);
        return (grantObj!=null ? (Grant) grantObj : null);
    }
    
    
    /**
     * Retrieve full publication record from database
     * @param publicationUri
     * @return Publication if found, or null if not found
     */
    public Publication readPublication(URI publicationUri){
        if (publicationUri == null) {
            throw new IllegalArgumentException("publicationUri cannot be empty");            
        }
        Object publicationObj = client.readResource(publicationUri, Publication.class);
        return (publicationObj!=null ? (Publication) publicationObj : null);
    }
    
    
    /**
     * Retrieve full Submission record
     * @param submissionUri
     * @return matching submission or null if none found
     */
    public Submission readSubmission(URI submissionUri) {
        if (submissionUri == null) {
            throw new IllegalArgumentException("submissionUri cannot be empty");            
        }
        Object submissionObj = client.readResource(submissionUri, Submission.class);
        return (submissionObj!=null ? (Submission) submissionObj : null); 
    }


    /**
     * Retrieve full deposit record from database
     * @param depositUri
     * @return
     */
    public Deposit readDeposit(URI depositUri){
        if (depositUri == null) {
            throw new IllegalArgumentException("depositUri cannot be empty");            
        }
        Object depositObj = client.readResource(depositUri, Deposit.class);
        return (depositObj!=null ? (Deposit) depositObj : null);
    }

    
    /**
     * @param publication
     * @return
     */
    public URI createPublication(Publication publication) {
        URI publicationUri = client.createResource(publication);
        LOG.info("New Publication created with URI {}", publicationUri);   
        return publicationUri;
    }    
    
    
    /**
     * @param submission
     * @return
     */
    public URI createSubmission(Submission submission) {
        URI submissionUri = client.createResource(submission);
        LOG.info("New Submission created with URI {}", submissionUri);   
        return submissionUri;
    }
    

    /**
     * @param respositoryCopy
     * @return
     */
    public URI createRepositoryCopy(RepositoryCopy repositoryCopy) {
        URI repositoryCopyUri = client.createResource(repositoryCopy);
        LOG.info("New RepositoryCopy created with URI {}", repositoryCopyUri);   
        return repositoryCopyUri;
    }


    /**
     * @param publication
     */
    public void updatePublication(Publication publication) {
        Publication origPublication = (Publication) client.readResource(publication.getId(), Publication.class);
        if (!origPublication.equals(publication)){
            client.updateResource(publication);
            LOG.info("Publication with URI {} was updated ", publication.getId());    
        }        
    }
    
    
    /**
     * @param submission
     */
    public void updateSubmission(Submission submission) {
        Submission origSubmission = (Submission) client.readResource(submission.getId(), Submission.class);
        if (!origSubmission.equals(submission)){
            client.updateResource(submission);
            LOG.info("Submission with URI {} was updated ", submission.getId());    
        }        
    }
    

    /**
     * @param repositoryCopy
     */
    public void updateRepositoryCopy(RepositoryCopy repositoryCopy) {
        RepositoryCopy origRepoCopy = (RepositoryCopy) client.readResource(repositoryCopy.getId(), RepositoryCopy.class);
        if (!origRepoCopy.equals(repositoryCopy)){
            client.updateResource(repositoryCopy);
            LOG.info("RepositoryCopy with URI {} was updated ", repositoryCopy.getId());   
        }        
    }
    
    /**
     * @param deposit
     */
    public void updateDeposit(Deposit deposit) {
        Deposit origDeposit = (Deposit) client.readResource(deposit.getId(), Deposit.class);
        if (!origDeposit.equals(deposit)){
            client.updateResource(deposit);
            LOG.info("Deposit with URI {} was updated ", deposit.getId());    
        }        
    }
}
