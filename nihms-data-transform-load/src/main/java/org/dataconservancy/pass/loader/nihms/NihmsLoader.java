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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.entrez.PubMedRecord;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.Source;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.client.util.SubmissionStatusUtil.calcSubmissionStatus;

/**
 *
 * @author Karen Hanson
 */
public class NihmsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsLoader.class);
    
    private static final String NIHMS_PASS_URI_KEY = "nihms.pass.uri"; 
    private static final String PMC_URL_TEMPLATE_KEY = "pmc.url.template"; 
    private static final String PMC_URL_TEMPLATE_DEFAULT = "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC%s/";

    private NihmsPassClientService clientService;
    
    private URI nihmsRepositoryUri;
    
    private String pmcUrlTemplate;

    /**
     * 
     * @param clientService
     */
    public NihmsLoader(NihmsPassClientService clientService) {
        this.clientService = clientService;
        try {
            this.nihmsRepositoryUri = new URI(Utils.getSystemProperty(NIHMS_PASS_URI_KEY, "https://example.com/fedora/repositories/1"));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not retrieve repository URI for the NIHMS repository. Failed to convert to URI.", e);
        }
        this.pmcUrlTemplate = Utils.getSystemProperty(PMC_URL_TEMPLATE_KEY, PMC_URL_TEMPLATE_DEFAULT);
        
    }
    
    /**
     * 
     * @param pub
     */
    public void transformAndLoad(NihmsPublication pub) {
        try  {    
            NihmsSubmissionDTO dto = transform(pub);            
            load(dto);
            
        } catch (Exception ex){
            //catch any exceptions and goto next
            LOG.error("Error during transform and load.");
        }
    }
    
    /**
     * 
     * @param pub
     * @return
     */
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
        Deposit deposit = null;

        if (submission != null) {
            Set<Deposit> deposits = clientService.readSubmissionDeposits(submission.getId());
            
            if (submission!=null && submission.getId()!=null) {
                deposit = pickNihmsDeposit(deposits);
            }
            if (deposit!=null) {
                deposit = updateDepositFields(deposit, pub);
            } else if (needDeposit(pub)){
                deposit = initiateNewDeposit(pub);
            }            
            
            submission = updateSubmissionFields(submission, pub, grantUri, deposits);
            
        } else {
            deposit = initiateNewDeposit(pub);
            submission = initiateNewSubmission(pub, pubmedRecord, grantUri, deposit);
        }
        
        submissionDTO.setNihmsSubmission(submission);
        submissionDTO.setDeposit(deposit);
        
        return dto;
    }
 
    /**
     * 
     * @param dto
     */
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
    
    /**
     * 
     * @param pub
     * @param pmr
     * @param grantUri
     * @param deposit
     * @return
     */
    private NihmsSubmission initiateNewSubmission(NihmsPublication pub, PubMedRecord pmr, URI grantUri, Deposit deposit) {
        LOG.info("No submission found for PMID \"{}\", initiating new Submission record", pub.getPmid());
        NihmsSubmission submission = new NihmsSubmission();

        Set<Deposit> deposits = new HashSet<Deposit>();
        deposits.add(deposit);

        submission.setPmid(pub.getPmid());
               
        submission.setStatus(calcSubmissionStatus(deposits, false));
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
    
    /**
     * 
     * @param submission
     * @param pub
     * @param grantUri
     * @param deposits
     * @return
     */
    private NihmsSubmission updateSubmissionFields(NihmsSubmission submission, NihmsPublication pub, URI grantUri, Set<Deposit> deposits) {
        //TODO: setting boolean for missing deposits to false, but there is really know way to know this at the moment.
        Submission.Status newStatus = calcSubmissionStatus(deposits, false);
        
        if (submission.getStatus() != newStatus) {
            LOG.info("Updating Submission \"{}\". Status changing from {} to {}", 
                     submission.getId().toString(), submission.getStatus().name(), newStatus.name());
            submission.setStatus(newStatus);
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
        
    private Deposit pickNihmsDeposit(Set<Deposit> deposits) {
        //is update... look for deposit
        for (Deposit deposit : deposits) {
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
        Deposit.Status currDepositStatus = deposit.getStatus();
        Deposit.Status newDepositStatus = depositStatus(pub, currDepositStatus);
        if (!currDepositStatus.equals(newDepositStatus)) {
            LOG.info("Updating Deposit \"{}\". Changing status from \"{}\" to \"{}\"", deposit.getId(), deposit.getStatus(), newDepositStatus);
        }
        return deposit;
    }
    
    
    public boolean needDeposit(NihmsPublication pub) {
        if (pub.getPmcId()!=null && pub.getPmcId().length()>0) {return true;}
        if (pub.getNihmsId()!=null && pub.getNihmsId().length()>0) {return true;}
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT) || pub.getNihmsStatus().equals(NihmsStatus.IN_PROCESS)) {return true;}
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
        deposit.setStatus(depositStatus(pub, null));
        
        return deposit;
    }
    
    
    private Deposit.Status depositStatus(NihmsPublication pub, Deposit.Status currDepositStatus) {
        if (pub.hasFinalApproval()) {
            return Deposit.Status.ACCEPTED;
        }
        
        if (pub.isTaggingComplete() || pub.hasInitialApproval()) {
            return Deposit.Status.IN_PROGRESS;
        }

        if (pub.isFileDeposited()) {
            return Deposit.Status.RECEIVED;
        }
 
        // if the current status implies we are further along than we really are, roll back to submitted and log.
        if (currDepositStatus.equals(Deposit.Status.IN_PROGRESS) 
                || currDepositStatus.equals(Deposit.Status.ACCEPTED)
                || currDepositStatus.equals(Deposit.Status.RECEIVED)) {
            LOG.info("Deposit.Status was at a later stage than the current NIHMS status would imply. "
                    + "Rolled back from \"{}\" to \"submitted\" for pmid {}", currDepositStatus.getValue(), pub.getPmid());
            return Deposit.Status.SUBMITTED;
        }
        
        return currDepositStatus;
    }
    
    
}
