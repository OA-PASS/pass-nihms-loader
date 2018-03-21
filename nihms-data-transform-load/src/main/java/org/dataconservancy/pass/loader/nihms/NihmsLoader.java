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
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.List;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.entrez.PubMedRecord;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission.Source;
import org.dataconservancy.pass.model.Submission.Status;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Karen Hanson
 * @version $Id$
 */
public class NihmsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsLoader.class);
    
    private static final String NIHMS_PASS_URI_KEY = "nihms.pass.uri"; 
    private static final String PMC_URL_TEMPLATE_KEY = "pmc.url.template"; 
    private static final String PMC_URL_TEMPLATE_DEFAULT = "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC%s/";

    private NihmsPassClientService clientService;
    
    private URI nihmsRepositoryUri;
    
    private String pmcUrlTemplate;

    public NihmsLoader(NihmsPassClientService clientService) {
        this.clientService = clientService;
        try {
            this.nihmsRepositoryUri = new URI(Utils.getSystemProperty(NIHMS_PASS_URI_KEY, "https://example.com/fedora/repositories/1"));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not retrieve repository URI for the NIHMS repository. Failed to convert to URI.", e);
        }
        this.pmcUrlTemplate = Utils.getSystemProperty(PMC_URL_TEMPLATE_KEY, PMC_URL_TEMPLATE_DEFAULT);
        
    }
    
    
    public void transformAndLoad(NihmsPublication pub) {
        try  {    
            NihmsSubmissionDTO dto = transform(pub);            
            load(dto);
            
        } catch (Exception ex){
            //catch any exceptions and goto next
            LOG.error("Error during transform and load.");
        }
    }
    
    private NihmsSubmissionDTO transform(NihmsPublication pub) {
        NihmsSubmissionDTO dto = new NihmsSubmissionDTO();

        URI grantUri = clientService.findGrantByAwardNumber(pub.getGrantNumber());
        if (grantUri==null) {
            throw new RuntimeException(String.format("No Grant matching award number \"%s\" was found. Cannot process submission with pmid %t", pub.getGrantNumber(), pub.getPmid()));
        }
        
        String pmid = pub.getPmid();
        PubMedRecord pubmedRecord = null;

        //this will be all about building up the DTO
        NihmsSubmissionDTO submissionDTO = new NihmsSubmissionDTO();
        submissionDTO.setGrantUri(grantUri);
        
        PmidLookup pmidLookup = new PmidLookup();
        pubmedRecord = pmidLookup.retrievePubmedRecord(pmid);
        
        NihmsSubmission submission = clientService.findExistingSubmission(grantUri, pmid, pubmedRecord.getDoi());

        if (submission != null) {
            submission = updateSubmissionFields(submission, pub, grantUri);
        } else {
            submission = initiateNewSubmission(pub, pubmedRecord, grantUri);
        }
                
        Deposit deposit = null;
        if (submission!=null && submission.getId()!=null) {
            deposit = pickNihmsDeposit(submission);
        }
        if (deposit!=null) {
            deposit = updateDepositFields(deposit, pub);
        } else if (needDeposit(pub)){
            deposit = initiateNewDeposit(pub);
        }
        submissionDTO.setNihmsSubmission(submission);
        submissionDTO.setDeposit(deposit);
        
        return dto;
    }
 
    private void load(NihmsSubmissionDTO dto) {
        Deposit deposit = dto.getDeposit();
        NihmsSubmission submission = dto.getNihmsSubmission();
        URI depositUri = deposit.getId();
        if (deposit!=null) {
            if (depositUri==null) {
                depositUri = clientService.createDeposit(deposit);
            } else {
                clientService.updateDeposit(deposit);     
            }
        }

        List<URI> deposituris = submission.getDeposits();
        deposituris.add(deposit.getId());
        submission.setDeposits(deposituris);
        
        if (submission!=null) {
            if (submission.getId()==null) {
                URI submissionUri = clientService.createNihmsSubmission(submission, dto.getGrantUri());
                LOG.info("A new submission was created with URI {}", submissionUri);
            } else {
                clientService.updateNihmsSubmission(submission);                    
            }
        }
    }
    
    
    private NihmsSubmission initiateNewSubmission(NihmsPublication pub, PubMedRecord pmr, URI grantUri) {
        LOG.info("No submission found for PMID \"{}\", initiating new Submission record", pub.getPmid());
        NihmsSubmission submission = new NihmsSubmission();

        submission.setPmid(pub.getPmid());
        submission.setStatus(pub.getSubmissionStatus());
        submission.setTitle(pmr.getTitle());
        submission.setDoi(pmr.getDoi());
        submission.setVolume(pmr.getVolume());
        submission.setIssue(pmr.getIssue());
        List<URI> grants = new ArrayList<URI>();
        grants.add(grantUri);
        submission.setGrants(grants);
        submission.setSource(Source.OTHER);
        URI journalUri = clientService.findJournalByIssn(pmr.getIssn());
        if (journalUri == null) {
            //try ESSN
            journalUri = clientService.findJournalByIssn(pmr.getEssn());
        }
        submission.setJournal(journalUri);
        
        return submission;
    }
    
    private NihmsSubmission updateSubmissionFields(NihmsSubmission submission, NihmsPublication pub, URI grantUri) {
        if (submission.getStatus() != pub.getSubmissionStatus()) {
            LOG.info("Updating Submission \"{}\". Status changing from {} to {}", 
                     submission.getId().toString(), submission.getStatus().getValue(), pub.getSubmissionStatus().getValue());
            submission.setStatus(pub.getSubmissionStatus());
        }
        if (submission.getPmid()==null || submission.getPmid().length()==0) {
            LOG.info("Updating Submission \"{}\". Adding PMID \"{}\"", submission.getId(), pub.getPmid());
            submission.setPmid(pub.getPmid());
        }
        List<URI> grantUris = submission.getGrants();
        if (!grantUris.contains(grantUri)) {
            LOG.info("Updating Submission \"{}\". Adding Grant URI \"{}\"", submission.getId(), grantUri);
            grantUris.add(grantUri);
            submission.setGrants(grantUris);
        }
        return submission;
    }
    
    private Deposit pickNihmsDeposit(NihmsSubmission submission) {
        //is update... look for deposit
        List<URI> deposits = submission.getDeposits();
        for (URI depositUri : deposits) {
            Deposit deposit = clientService.readDeposit(depositUri);
            if (deposit.getRepository().equals(nihmsRepositoryUri)) {
                return deposit;
            }
        }
        return null;
    }
    
    private Deposit updateDepositFields(Deposit deposit, NihmsPublication pub) {
        String pmcId = pub.getPmcId();
        String nihmsId = pub.getNihmsId();
        if (pmcId!=null && pmcId.length()>0) {
            if (!deposit.getAssignedId().equals(pmcId)) {
                LOG.info("Updating Deposit \"{}\". Changing AssignedId from \"{}\" to \"{}\"", deposit.getId(), deposit.getAssignedId(), pmcId);
                deposit.setAssignedId(pmcId);
                deposit.setAccessUrl(String.format(pmcUrlTemplate, pmcId));
            }
        } else if (nihmsId!=null && nihmsId.length()>0){
            if (!deposit.getAssignedId().equals(pmcId)) {
                LOG.info("Updating Deposit \"{}\". Changing AssignedId from \"{}\" to \"{}\"", deposit.getId(), deposit.getAssignedId(), nihmsId);
                deposit.setAssignedId(nihmsId);
            }
        }
        //if (!deposit.getStatus().equals(pub.getDepositStatus())) {
        //LOG.info("Updating Deposit \"{}\". Changing status from \"{}\" to \"{}\"", deposit.getId(), deposit.getStatus(), pub.getDepositStatus());
        //TODO: deposit.setStatus(pub.getDepositStatus());
        //}
        return deposit;
    }
    
    
    public boolean needDeposit(NihmsPublication pub) {
        if (pub.getPmcId()!=null && pub.getPmcId().length()>0) {return true;}
        if (pub.getNihmsId()!=null && pub.getNihmsId().length()>0) {return true;}
        if (pub.getSubmissionStatus().equals(Status.COMPLIANT) || pub.getSubmissionStatus().equals(Status.IN_PROGRESS)) {return true;}
        return false;
    }

    
    private Deposit initiateNewDeposit(NihmsPublication pub) {
        Deposit deposit = new Deposit();

        LOG.info("NIHMS Deposit record needed for PMID \"{}\", initiating new Deposit record", pub.getPmid());
        String pmcId = pub.getPmcId();
        String nihmsId = pub.getNihmsId();
        if (pmcId!=null && pmcId.length()>0) {
            deposit.setAssignedId(pmcId);    
            deposit.setAccessUrl(String.format(pmcUrlTemplate, pmcId));
        } else if (nihmsId!=null && nihmsId.length()>0){
            deposit.setAssignedId(nihmsId);
        }
        deposit.setRepository(nihmsRepositoryUri);
        deposit.setRequested(false);
        //TODO: deposit.setStatus(pub.getDepositStatus());
        
        return deposit;
    }
    
    
    
}
