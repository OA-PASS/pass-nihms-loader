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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.fedora.FedoraPassClient;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.PassEntityType;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;

/**
 *
 * @author Karen Hanson
 */
public class NihmsPassClientService {

    //private static final Logger LOG = LoggerFactory.getLogger(FedoraPassCrudClient.class);
    
    private String AWARD_NUMBER_FLD = "awardNumber";
    
    private PassClient client;

    public NihmsPassClientService() {
        this.client = new FedoraPassClient();
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

    
    public NihmsSubmission findExistingSubmission(URI grantUri, String pmid, String doi) {
        if (grantUri == null) {
            throw new RuntimeException("Grant URI cannot be null when searching for existing Submission.");
        }        
        if (pmid == null) {
            throw new RuntimeException("PMID cannot be null when searching for existing Submission.");
        }
        
        NihmsSubmission submission = findSubmissionByArticleIdAndGrant(pmid, grantUri, "pmid");
        if (submission != null) {
            return submission;
        }
        
        if (doi != null) {
            submission = this.findSubmissionByArticleIdAndGrant(doi, grantUri, "doi");
            if (submission != null) {
                return submission;
            }
        }
            
        Grant grant = this.readGrant(grantUri);
        URI grantPi = grant.getPi();
        submission = findSubmissionByPmidAndPi(pmid, grantPi);
        if (submission != null) {
            return submission;
        }
        
        submission = findSubmissionByDoiAndPi(doi, grantPi);
        if (submission != null) {
            return submission;
        }
        
        return null;
    }
    
    
    public Set<URI> findSubmissionByPmid(String pmid) {
        return client.findAllByAttribute(NihmsSubmission.class, "pmid", pmid);
    }
    
    public Set<URI> findSubmissionByDoi(String doi) {
        return client.findAllByAttribute(NihmsSubmission.class, "doi", doi);
    }
    
    /**     * 
     * Searches for Submission record using articleId and grantUri. This detects whether we are dealing
     * with a record that was already looked at previously. 
     * @param articleId
     * @param grantUri
     * @param idFieldName the name of the field on the Submission model that will be matched e.g. "pmid" or "doi"
     * @return
     */
    public NihmsSubmission findSubmissionByArticleIdAndGrant(String articleId, URI grantUri, String idFieldName) {
        if (grantUri==null) {
            throw new IllegalArgumentException("grantUri cannot be empty");
        }
        if (articleId==null || articleId.length()==0) {
            throw new IllegalArgumentException("article ID cannot be empty");
        }
        Map<String, Object> valuemap = new HashMap<String, Object>();
        valuemap.put(idFieldName, articleId);
        valuemap.put("grant", grantUri.toString());
        Set<URI> match = client.findAllByAttributes(NihmsSubmission.class, valuemap);
        if (match!=null && match.size()>1) {
            throw new RuntimeException(String.format(
                   "Search returned %s results for %s %s and grant %s when only one match should be found. Check the database for issues.", 
                   match.size(), idFieldName, articleId, grantUri.toString())
                   );
        }
        if (match!=null && match.size()==1){
            return readNihmsSubmission(match.iterator().next());
        }        
        
        return null;        
    }

    
    public NihmsSubmission findSubmissionByPmidAndPi(String pmid, URI pi) {
        Set<URI> uris = client.findAllByAttribute(NihmsSubmission.class, "pmid", pmid);
        return pickSubmissionWithSamePi(uris, pi);      
    }

    
    public NihmsSubmission findSubmissionByDoiAndPi(String doi, URI pi) {
        Set<URI> uris = client.findAllByAttribute(NihmsSubmission.class, "doi", doi);
        return pickSubmissionWithSamePi(uris, pi);      
    }
    
    
    private NihmsSubmission pickSubmissionWithSamePi(Set<URI> submissionUris, URI pi) {
        if (submissionUris!=null) {
            for (URI uri : submissionUris) {
                NihmsSubmission submission = readNihmsSubmission(uri);
                if (submission!=null) {
                    URI piUri = readGrant(submission.getGrants().get(0)).getPi();
                    if (piUri.equals(pi)) {
                        return submission;
                    }
                }
            }
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
     * Retrieve full NIHMS Submission record
     * @param submissionUri
     * @return matching submission or null if none found
     */
    public NihmsSubmission readNihmsSubmission(URI submissionUri) {
        if (submissionUri == null) {
            throw new IllegalArgumentException("submissionUri cannot be empty");            
        }
        Object submissionObj = client.readResource(submissionUri, NihmsSubmission.class);
        return (submissionObj!=null ? (NihmsSubmission) submissionObj : null); 
    }
    

    /**
     * Retrieve list of deposits associated with a submission or empty Set if none
     * @param submissionId
     * @return
     */
    public Set<Deposit> readSubmissionDeposits(URI submissionId) {
        Set<Deposit> deposits = new HashSet<Deposit>();
        Set<URI> depositIds = client.findAllByAttribute(Deposit.class, PassEntityType.SUBMISSION.getName(), submissionId);
        if (depositIds!=null) {
            for (URI id : depositIds) { 
                deposits.add(readDeposit(id));
            }
        }
        return deposits;
    }

    /**
     * @param submission
     * @return
     */
    public URI createNihmsSubmission(NihmsSubmission submission, URI grantUri) {
        URI submissionUri = client.createResource(submission);
        Grant grant = (Grant) client.readResource(grantUri, Grant.class);
        List<URI> submissionUris = grant.getSubmissions();
        submissionUris.add(submissionUri);
        grant.setSubmissions(submissionUris);
        client.updateResource(grant);
        return submissionUri;
    }
    

    /**
     * @param deposit
     * @return
     */
    public URI createDeposit(Deposit deposit) {
        URI depositUri = client.createResource(deposit);
        return depositUri;
    }

    /**
     * @param grant
     */
    public void updateGrant(Grant grant) {
        Grant origGrant = (Grant) client.readResource(grant.getId(), Grant.class);
        if (!origGrant.equals(grant)){
            client.updateResource(grant);
        }        
    }

    /**
     * @param deposit
     */
    public void updateDeposit(Deposit deposit) {
        Deposit origDeposit = (Deposit) client.readResource(deposit.getId(), Deposit.class);
        if (!origDeposit.equals(deposit)){
            client.updateResource(deposit);
        }        
    }

    /**
     * @param submission
     */
    public void updateNihmsSubmission(NihmsSubmission submission) {
        NihmsSubmission origSubmission = (NihmsSubmission) client.readResource(submission.getId(), NihmsSubmission.class);
        if (!origSubmission.equals(submission)){
            client.updateResource(submission);
        }        
    }
}
