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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientFactory;
import org.dataconservancy.pass.client.nihms.cache.GrantIdCache;
import org.dataconservancy.pass.client.nihms.cache.NihmsDepositIdCache;
import org.dataconservancy.pass.client.nihms.cache.NihmsRepositoryCopyIdCache;
import org.dataconservancy.pass.client.nihms.cache.PublicationIdCache;
import org.dataconservancy.pass.client.nihms.cache.UserPubSubmissionsCache;
import org.dataconservancy.pass.loader.nihms.util.ConfigUtil;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;



/**
 * NIHMS PASS client service deals with interactions with the data via the PASS client and controls local data caches
 * @author Karen Hanson
 */
public class NihmsPassClientService {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPassClientService.class);
    
    private String AWARD_NUMBER_FLD = "awardNumber";
    
    private PassClient client;

    /**
     * Local cache of publications, lookup by PMID
     */
    private PublicationIdCache publicationCache;

    /**
     * Local cache of repositoryCopies for NIHMS repo only, lookup by publicationId
     */
    private NihmsRepositoryCopyIdCache nihmsRepoCopyCache;

    /**
     * Local cache of Grants, lookup by awardNumber
     */
    private GrantIdCache grantCache;

    /**
     * Local cache of NIHMS Deposits, lookup by Submission URI
     */
    private NihmsDepositIdCache nihmsDepositCache;

    /**
     * Local cache of the Submissions associated with a User and Publication combination
     * Look up using UserId and PublicationId concatenated
     */
    private UserPubSubmissionsCache userPubSubsCache;
    
    /**
     * Store NIHMS REPO ID setting
     */
    private URI nihmsRepoId;
    
    public NihmsPassClientService() {
        this(PassClientFactory.getPassClient());
    }
    
    public NihmsPassClientService(PassClient client) {
        this.client = client;
        nihmsRepoId = ConfigUtil.getNihmsRepositoryUri();
        initCaches();
    }

    private void initCaches() {
        publicationCache = PublicationIdCache.getInstance();
        nihmsRepoCopyCache = NihmsRepositoryCopyIdCache.getInstance();    
        grantCache = GrantIdCache.getInstance();        
        nihmsDepositCache = NihmsDepositIdCache.getInstance();  
        userPubSubsCache = UserPubSubmissionsCache.getInstance();        
    }
    
    /**
     * Remove all data from cache
     */
    public void clearCache() {
        this.publicationCache.clear();
        this.nihmsRepoCopyCache.clear();
        this.grantCache.clear();
        this.nihmsDepositCache.clear();
        this.userPubSubsCache.clear();
    }
    
    /**
     * Searches for Grant record using awardNumber. Tries this first using the awardNumber as passed in,
     * then again without spaces.
     * @param awardNumber
     * @return
     */
    public Grant findMostRecentGrantByAwardNumber(String awardNumber) {
        if (nullOrEmpty(awardNumber)) {
            throw new IllegalArgumentException("awardNumber cannot be empty");
        }
        
        //if the awardNumber is in the cache, retrieve URI.
        URI grantId = grantCache.get(awardNumber);
        if (grantId!=null) {
            return readGrant(grantId);
        }
        
        // if we are here, there was nothing cached and we need to figure out which grant to return
        Set<URI> grantIds = client.findAllByAttribute(Grant.class, AWARD_NUMBER_FLD, awardNumber);
        
        //try with no spaces
        awardNumber = awardNumber.replaceAll("\\s+","");
        grantIds.addAll(client.findAllByAttribute(Grant.class, AWARD_NUMBER_FLD, awardNumber));
        
        List<Grant> grants = new ArrayList<Grant>();
        for (URI id : grantIds) {
            grants.add(readGrant(id));
        }
        
        if (grants.size()>0) {
            Grant mostRecentGrant = Collections.max(grants, Comparator.comparing(Grant::getStartDate));
            grantCache.put(awardNumber, mostRecentGrant.getId());
            return mostRecentGrant;
        }      
        
        return null;
    }

    /**
     * Looks up publication using PMID, since this is the most reliable field to match. Checks publication
     * cache first, then checks index
     * @param pmid
     * @param doi
     * @return
     */
    public Publication findPublicationById(String pmid, String doi) {
        if (pmid == null) {
            throw new RuntimeException("PMID cannot be null when searching for existing Submission.");
        }
        
        //if the pmid/publicationId pair is in the cache, retrieve it.
        URI publicationId = publicationCache.get(pmid);
        
        if (publicationId==null) {
            publicationId = findPublicationByArticleId(pmid, "pmid");
        }
        
        if (publicationId==null && doi!=null) {
            publicationId = findPublicationByArticleId(doi, "doi");
        }
        
        if (publicationId!=null) {
            Publication publication = readPublication(publicationId);
            publicationCache.put(pmid, publicationId);
            return publication;
        }
        
        return null;
    }

    /**
     * Find NIHMS RepositoryCopy record for a publicationId
     * @param pubId
     * @return
     */
    public RepositoryCopy findNihmsRepositoryCopyForPubId(URI pubId) {
        if (pubId == null) {
            throw new RuntimeException("publicationId cannot be null when searching for existing RepositoryCopy.");
        }

        //if the publicationId can be matched in the cache of NIHMS Repository Copy ID mappings, retrieve it
        URI repoCopyId = nihmsRepoCopyCache.get(pubId);

        if (repoCopyId==null) {
            Map<String,Object> attribs = new HashMap<String,Object>();
            attribs.put("publication", pubId);
            attribs.put("repository", nihmsRepoId);
            
            Set<URI> repositoryCopies = client.findAllByAttributes(RepositoryCopy.class, attribs);
            if (nullOrEmpty(repositoryCopies)) {
                return null;
            } else if (repositoryCopies!=null && repositoryCopies.size()==1) {
                repoCopyId = repositoryCopies.iterator().next();
            } else if (repositoryCopies!=null && repositoryCopies.size()>1) {
                throw new RuntimeException(String.format("There are multiple repository copies matching RepositoryId %s and PublicationId %s. "
                        + "This indicates a data corruption, please check the data and try again.", pubId, nihmsRepoId));
            }
        }
        
        if (repoCopyId!=null) {
            RepositoryCopy repoCopy = (RepositoryCopy) client.readResource(repoCopyId, RepositoryCopy.class);
            this.nihmsRepoCopyCache.put(pubId, repoCopy.getId());
            return repoCopy;
        }
        
        return null;
        
    }
    
    
    /**
     * Searches for Submissions matching a specific publication and User Id
     * @param pubId
     * @param grantId
     * @return
     */
    public List<Submission> findSubmissionsByPublicationAndUserId(URI pubId, URI userId) {
        if (pubId == null) {
            throw new RuntimeException("publicationId cannot be null when searching for existing Submissions");
        }
        if (userId == null) {
            throw new RuntimeException("userId cannot be null when searching for existing Submissions");
        }

        String userIdPubIdKey = userIdPubIdKey(userId, pubId);
        
        List<Submission> submissions = new ArrayList<Submission>();
        
        Map<String,Object> attribs = new HashMap<String,Object>();
        attribs.put("publication", pubId);
        attribs.put("user", userId);
        
        Set<URI> uris = client.findAllByAttributes(Submission.class, attribs);
        
        //in addition we will check the cache to see if it has any other Submissions that the indexer didn't detect
        Set<URI> cachedUris = userPubSubsCache.get(userIdPubIdKey);
        
        if (cachedUris!=null) {
            //merge the two sets of URIs to make sure we have all of them
            uris.addAll(cachedUris);
        }
        
        for (URI uri : uris) {
            submissions.add(readSubmission(uri));
        } 
        
        userPubSubsCache.put(userIdPubIdKey, uris);
        
        return submissions;
    }
    
    
    /**      
     * Searches for Publication record using articleIds. This detects whether we are dealing
     * with a record that was already looked at previously. 
     * @param articleId
     * @param idFieldName the name of the field on the Submission model that will be matched e.g. "pmid" or "doi"
     * @return
     */
    private URI findPublicationByArticleId(String articleId, String idFieldName) {
        if (nullOrEmpty(articleId)) {
            throw new IllegalArgumentException("article ID cannot be empty");
        }
        if (nullOrEmpty(idFieldName)) {
            throw new IllegalArgumentException("idFieldName cannot be empty");
        }
        URI match = client.findByAttribute(Publication.class, idFieldName, articleId);
        return match;
    }
    
    
    /**
     * Look up Journal URI using ISSN
     * @param issn
     * @return
     */
    public URI findJournalByIssn(String issn) {
        if (nullOrEmpty(issn)) {
            return null;      
        }
        return client.findByAttribute(Journal.class, "issn", issn);
    }

    
    /**
     * Searches for a NIHMS Deposit that matches a SubmissionID
     * @param submissionId
     * @return
     */
    public Deposit findNihmsDepositForSubmission(URI submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");            
        }
        
        //if the depositId is in the cache, retrieve it.
        URI depositId = nihmsDepositCache.get(submissionId);
        
        if (depositId==null) {
            //search for deposit
            Map<String,Object> attribs = new HashMap<String,Object>();
            attribs.put("submission", submissionId);
            attribs.put("repository", nihmsRepoId);        
            Set<URI> matches = client.findAllByAttributes(Deposit.class, attribs);
            if (matches!=null && matches.size()==1) {
                depositId = matches.iterator().next();
            } else if (!nullOrEmpty(matches)) {
                throw new RuntimeException(String.format("There are multiple Deposits matching submissionId %s and repositoryId %s. "
                        + "This indicates a data corruption, please check the data and try again.", submissionId, nihmsRepoId));
            }
        }
        
        if (depositId!=null) {
            Deposit deposit = client.readResource(depositId,Deposit.class);
            this.nihmsDepositCache.put(deposit.getSubmission(), deposit.getId());
            return deposit;
        }

        return null;
    }
    
    
     /**
     * Retrieve full grant record from database
     * @param grantId
     * @return Grant if found, or null if not found
     */
    private Grant readGrant(URI grantId){
        if (grantId == null) {
            throw new IllegalArgumentException("grantId cannot be empty");            
        }
        Object grantObj = client.readResource(grantId, Grant.class);
        return (grantObj!=null ? (Grant) grantObj : null);
    }
    
    
    /**
     * Retrieve full publication record from database
     * @param publicationId
     * @return Publication if found, or null if not found
     */
    public Publication readPublication(URI publicationId){
        if (publicationId == null) {
            throw new IllegalArgumentException("publicationId cannot be empty");            
        }
        Object publicationObj = client.readResource(publicationId, Publication.class);
        return (publicationObj!=null ? (Publication) publicationObj : null);
    }
    
    
    /**
     * Retrieve full Submission record
     * @param submissionId
     * @return matching submission or null if none found
     */
    public Submission readSubmission(URI submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");            
        }
        Object submissionObj = client.readResource(submissionId, Submission.class);
        return (submissionObj!=null ? (Submission) submissionObj : null); 
    }


    /**
     * Retrieve full deposit record from database
     * @param depositId
     * @return
     */
    public Deposit readDeposit(URI depositId){
        if (depositId == null) {
            throw new IllegalArgumentException("depositId cannot be empty");            
        }
        Object depositObj = client.readResource(depositId, Deposit.class);
        return (depositObj!=null ? (Deposit) depositObj : null);
    }

    
    /**
     * @param publication
     * @return
     */
    public URI createPublication(Publication publication) {
        URI publicationId = client.createResource(publication);
        LOG.info("New Publication created with URI {}", publicationId);   
        //add to local cache for faster lookup
        publicationCache.put(publication.getPmid(), publicationId);
        return publicationId;
    }    
    
    
    /**
     * @param submission
     * @return
     */
    public URI createSubmission(Submission submission) {
        URI submissionId = client.createResource(submission);
        LOG.info("New Submission created with URI {}", submissionId);          
        String key = userIdPubIdKey(submission.getUser(), submission.getPublication());
        userPubSubsCache.addToOrCreateEntry(key,submissionId);        
        return submissionId;
    }
    

    /**
     * @param respositoryCopy
     * @return
     */
    public URI createRepositoryCopy(RepositoryCopy repositoryCopy) {
        URI repositoryCopyId = client.createResource(repositoryCopy);
        LOG.info("New RepositoryCopy created with URI {}", repositoryCopyId);  
        nihmsRepoCopyCache.put(repositoryCopy.getPublication(), repositoryCopyId); 
        return repositoryCopyId;
    }

    /**
     * 
     * @param publication
     * @return true if record needed to be updated, false if no update
     */
    public boolean updatePublication(Publication publication) {
        Publication origPublication = (Publication) client.readResource(publication.getId(), Publication.class);
        if (!origPublication.equals(publication)){
            client.updateResource(publication);
            LOG.info("Publication with URI {} was updated ", publication.getId());   
            return true;
        }     
        return false;
    }
    
    
    /**
     * @param submission
     * @return true if record needed to be updated, false if no update
     */
    public boolean updateSubmission(Submission submission) {
        Submission origSubmission = (Submission) client.readResource(submission.getId(), Submission.class);
        if (!origSubmission.equals(submission)){
            client.updateResource(submission);
            
            //shouldnt be necessary, but just to be sure... make sure this is in cache:
            String key = userIdPubIdKey(submission.getUser(), submission.getPublication());
            userPubSubsCache.addToOrCreateEntry(key,submission.getId());
            
            LOG.info("Submission with URI {} was updated ", submission.getId());
            return true;    
        }      
        return false;  
    }
    

    /**
     * @param repositoryCopy
     * @return true if record needed to be updated, false if no update
     */
    public boolean updateRepositoryCopy(RepositoryCopy repositoryCopy) {
        RepositoryCopy origRepoCopy = (RepositoryCopy) client.readResource(repositoryCopy.getId(), RepositoryCopy.class);
        if (!origRepoCopy.equals(repositoryCopy)){
            client.updateResource(repositoryCopy);
            LOG.info("RepositoryCopy with URI {} was updated ", repositoryCopy.getId());   
            return true;
        }        
        return false;
    }
    
    /**
     * @param deposit
     * @return true if record needed to be updated, false if no update
     */
    public boolean updateDeposit(Deposit deposit) {
        Deposit origDeposit = (Deposit) client.readResource(deposit.getId(), Deposit.class);
        if (!origDeposit.equals(deposit)){
            client.updateResource(deposit);
            LOG.info("Deposit with URI {} was updated ", deposit.getId());      
            return true;
        }   
        return false;     
    }
    
    private static String userIdPubIdKey(URI userId, URI pubId) {
        return userId.toString() + pubId.toString();
    }
    
}
