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
        NihmsSubmission submission = findSubmissionByPmidAndGrant(pmid, grantUri);
        if (submission != null) {
            return submission;
        }

        submission = this.findSubmissionByDoiAndGrant(doi, grantUri);
        if (submission != null) {
            return submission;
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
    
    /**
     * Searches for Submission record using pmid and grantUri. This detects whether we are dealing
     * with a record that was already looked at previously
     * @param awardNumber
     * @return
     */
    public NihmsSubmission findSubmissionByPmidAndGrant(String pmid, URI grantUri) {
        if (grantUri==null) {
            throw new IllegalArgumentException("grantUri cannot be empty");
        }
        if (pmid==null || pmid.length()==0) {
            throw new IllegalArgumentException("pmid cannot be empty");
        }
        Map<String, Object> valuemap = new HashMap<String, Object>();
        valuemap.put("pmid", pmid);
        valuemap.put("grant", grantUri.toString());
        Set<URI> match = client.findAllByAttributes(NihmsSubmission.class, valuemap);
        if (match!=null && match.size()>1) {
            throw new RuntimeException(String.format("Search returned %r results for pmid %s and "
                                                    + "grant %t when only one match should be found. Check the database for issues.", pmid, grantUri.toString()));
        }
        if (match!=null && match.size()==1){
            return this.readNihmsSubmission(match.iterator().next());
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
    
    
    private NihmsSubmission pickSubmissionWithSamePi(Set<URI> uris, URI pi) {
        for (URI uri : uris) {
            NihmsSubmission submission = readNihmsSubmission(uri);
            if (submission!=null) {
                URI piUri = readGrant(submission.getGrants().get(0)).getPi();
                if (piUri.equals(pi)) {
                    return submission;
                }
            }
        }
        return null;   
    }
    
    
    /**
     * Searches for Submission record using pmid and grantUri. This detects whether we are dealing
     * with a record that was already looked at previously
     * @param awardNumber
     * @return
     */
    public NihmsSubmission findSubmissionByDoiAndGrant(String doi, URI grantUri) {
        if (grantUri==null) {
            throw new IllegalArgumentException("grantUri cannot be empty");
        }
        if (doi==null || doi.length()==0) {
            throw new IllegalArgumentException("doi cannot be empty");
        }
        Map<String, Object> valuemap = new HashMap<String, Object>();
        valuemap.put("doi", doi);
        valuemap.put("grant", grantUri.toString());
        Set<URI> match = client.findAllByAttributes(NihmsSubmission.class, valuemap);
        if (match!=null && match.size()>1) {
            throw new RuntimeException(String.format("Search returned %r results for pmid %s and "
                                                    + "grant %t when only one match should be found. Check the database for issues.", doi, grantUri.toString()));
        }
        if (match!=null && match.size()==1){
            return this.readNihmsSubmission(match.iterator().next());
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
    
    
    public Grant readGrant(URI grantUri){
        if (grantUri == null) {
            throw new IllegalArgumentException("grantUri cannot be empty");            
        }
        return (Grant) client.readResource(grantUri, Grant.class);
    }

    
    public Deposit readDeposit(URI depositUri){
        if (depositUri == null) {
            throw new IllegalArgumentException("depositUri cannot be empty");            
        }
        return (Deposit) client.readResource(depositUri, Deposit.class);
    }
    
    
    public Set<Deposit> readSubmissionDeposits(URI submissionId) {
        Set<Deposit> deposits = new HashSet<Deposit>();
        Set<URI> depositIds = client.findAllByAttribute(Deposit.class, "Submission", submissionId);
        for (URI id : depositIds) { 
            deposits.add(readDeposit(id));
        }
        return deposits;
    }
    
    
    public NihmsSubmission readNihmsSubmission(URI submissionUri) {
        if (submissionUri == null) {
            throw new IllegalArgumentException("submissionUri cannot be empty");            
        }
        return (NihmsSubmission) client.readResource(submissionUri, NihmsSubmission.class);
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
    public void updateResource(Grant grant) {
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
