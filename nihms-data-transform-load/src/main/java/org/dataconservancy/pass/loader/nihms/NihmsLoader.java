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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.fedora.FedoraPassCrudClient;
import org.dataconservancy.pass.entrez.EntrezPmidLookup;
import org.dataconservancy.pass.entrez.PubMedRecord;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 * @version $Id$
 */
public class NihmsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(FedoraPassCrudClient.class);
    
    private PassClient client;
    
    private String AWARD_NUMBER_FLD = "awardNumber";
    
    public NihmsLoader(PassClient client) {
        this.client = client;
    }
    
    public void transformAndLoad(NihmsPublication pub) {
        try  {       
                    
            URI grantUri = findGrantByAwardNumber(pub.getGrantNumber());
            if (grantUri==null) {
                LOG.error("No Grant matching award number \"{}\" was found. Cannot process submission with pmid {}", pub.getGrantNumber(), pub.getPmid());
                return;
            }

            String pmid = pub.getPmid();  
            NihmsSubmission submission = findSubmissionByPmidAndGrant(pmid, grantUri);
            if (submission!=null) {
                //already have submission... just need to update status
                if (submission.getStatus() != pub.getSubmissionStatus()) {
                    LOG.info("Updating Submission \"{}\". Status changing from {} to {}", 
                             submission.getId().toString(), submission.getStatus().getValue(), pub.getSubmissionStatus().getValue());
                    
                    submission.setStatus(pub.getSubmissionStatus());
                    client.updateResource(submission);                    
                }
                //TODO: figure out if deposit needs to change?
                return;
            } 
            
            EntrezPmidLookup pmidLookup = new EntrezPmidLookup();
            PubMedRecord record = pmidLookup.retrievePubmedRecord(pmid);
            
            submission = findSubmissionByDoiAndGrant(record.getDoi(), grantUri);
            if (submission!=null) {
                //update submission
                submission.setPmid(pmid);
                LOG.info("Updating Submission \"{}\". Adding PMID \"{}\"", submission.getId().toString(), pmid);
                if (submission.getStatus() != pub.getSubmissionStatus()) {
                    LOG.info("Updating Submission \"{}\". Status changing from {} to {}", 
                             submission.getId().toString(), submission.getStatus().getValue(), pub.getSubmissionStatus().getValue());
                    submission.setStatus(pub.getSubmissionStatus());  
                }
                client.updateResource(submission);   
                //TODO: figure out if deposit needs to change?               
                return;                
            }
            
            //if you're at this point, no submission found yet... need to create it.
            //TODO: make a "toSubmission(PubMedRecord...);
            submission = new NihmsSubmission();
            submission.setDoi(record.getDoi());
            
        } catch (Exception ex){
            //catch any exceptions and goto next
            LOG.error("Error during transform and load.");
        }
    }
    
    /**
     * Searches for Grant record using awardNumber. Tries this first using the awardNumber as passed in,
     * then again without spaces.
     * @param awardNumber
     * @return
     */
    private URI findGrantByAwardNumber(String awardNumber) {
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

    
    /**
     * Searches for Submission record using pmid and grantUri. This detects whether we are dealing
     * with a record that was already looked at previously
     * @param awardNumber
     * @return
     */
    private NihmsSubmission findSubmissionByPmidAndGrant(String pmid, URI grantUri) {
        if (grantUri==null) {
            throw new IllegalArgumentException("grantUri cannot be empty");
        }
        if (pmid==null || pmid.length()==0) {
            throw new IllegalArgumentException("pmid cannot be empty");
        }
        NihmsSubmission submission = null;
        Map<String, Object> valuemap = new HashMap<String, Object>();
        valuemap.put("pmid", pmid);
        valuemap.put("grant", grantUri.toString());
        Set<URI> match = client.findAllByAttributes(NihmsSubmission.class, valuemap);
        if (match!=null && match.size()>1) {
            throw new RuntimeException(String.format("Search returned %r results for pmid %s and "
                                                    + "grant %t when only one match should be found. Check the database for issues.", pmid, grantUri.toString()));
        }
        if (match!=null && match.size()==1){
            submission = (NihmsSubmission) client.readResource(match.iterator().next(), NihmsSubmission.class);
        }
        
        return submission;        
    }

    
    /**
     * Searches for Submission record using pmid and grantUri. This detects whether we are dealing
     * with a record that was already looked at previously
     * @param awardNumber
     * @return
     */
    private NihmsSubmission findSubmissionByDoiAndGrant(String doi, URI grantUri) {
        if (grantUri==null) {
            throw new IllegalArgumentException("grantUri cannot be empty");
        }
        if (doi==null || doi.length()==0) {
            throw new IllegalArgumentException("doi cannot be empty");
        }
        NihmsSubmission submission = null;
        Map<String, Object> valuemap = new HashMap<String, Object>();
        valuemap.put("doi", doi);
        valuemap.put("grant", grantUri.toString());
        Set<URI> match = client.findAllByAttributes(NihmsSubmission.class, valuemap);
        if (match!=null && match.size()>1) {
            throw new RuntimeException(String.format("Search returned %r results for pmid %s and "
                                                    + "grant %t when only one match should be found. Check the database for issues.", doi, grantUri.toString()));
        }
        if (match!=null && match.size()==1){
            submission = (NihmsSubmission) client.readResource(match.iterator().next(), NihmsSubmission.class);
        }
        
        return submission;        
    }
    
}
