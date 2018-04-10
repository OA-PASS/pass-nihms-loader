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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.client.util.ConfigUtil;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.entrez.PubMedEntrezRecord;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.Source;
import org.dataconservancy.pass.model.ext.nihms.NihmsSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.client.util.SubmissionStatusUtil.calcSubmissionStatus;
import static org.dataconservancy.pass.loader.nihms.TransformUtil.calcDepositStatus;
import static org.dataconservancy.pass.loader.nihms.TransformUtil.isDepositUserActionRequired;
import static org.dataconservancy.pass.loader.nihms.TransformUtil.needNihmsDeposit;
import static org.dataconservancy.pass.loader.nihms.TransformUtil.pickDepositForRepository;

/**
 * Does the heavy lifting of data transform work, converting a NihmsPublication to a 
 * NihmsSubmissionDTO (submission + deposits) for loading to the database
 * @author Karen Hanson
 */
public class SubmissionTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionTransformer.class);
    
    private static final String NIHMS_PASS_URI_KEY = "nihms.pass.uri"; 
    private static final String PMC_URL_TEMPLATE_KEY = "pmc.url.template"; 
    private static final String PMC_URL_TEMPLATE_DEFAULT = "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC%s/";

    /**
     * The Nihms client service communicates with the Pass Client to perform database interactions for the
     * NIHMS loader
     */
    private NihmsPassClientService clientService;
    
    /** 
     * Service for looking up PMID 
     */
    private PmidLookup pmidLookup;
    
    /**
     * URI for the NIHMS repository, this should be set as a property
     */
    private URI nihmsRepositoryUri;
    
    /**
     * PMC URL template is used with String.format() to pass a PMCID into.
     * This in turn forms the accessUrl for the deposited item
     */
    private String pmcUrlTemplate;

    /**
     * Constructor uses defaults for client service and pmid lookup
     * @param clientService
     */
    public SubmissionTransformer() {
        this.pmidLookup = new PmidLookup();
        this.clientService = new NihmsPassClientService();
        setProperties();
    }
    
    /**
     * Constructor initiates with the required NIHMS Client Service and PMID lookup
     * @param clientService
     */
    public SubmissionTransformer(NihmsPassClientService clientService, PmidLookup pmidLookup) {
        if (clientService==null) {
            throw new RuntimeException("NihmsPassClientService cannot be null");
        }
        if (pmidLookup==null) {
            throw new RuntimeException("PmidLookup cannot be null");
        }
        this.pmidLookup = pmidLookup;
        this.clientService = clientService;     
        setProperties();
    }
    
    
    private void setProperties() {
        this.pmcUrlTemplate = ConfigUtil.getSystemProperty(PMC_URL_TEMPLATE_KEY, PMC_URL_TEMPLATE_DEFAULT);   
        try {
            this.nihmsRepositoryUri = new URI(ConfigUtil.getSystemProperty(NIHMS_PASS_URI_KEY, "https://example.com/fedora/repositories/1"));
        } catch (URISyntaxException e) {
            throw new RuntimeException("NIHMS repository property is not a valid URI, please check the nihms.pass.uri property is populated correctly.", e);
        }
    }    

    
    /**
     * Does the heavy lifting of converting a NihmsPublication record into the NihmsSubmissionDTO 
     * that is needed for the NihmsLoader to write it to Fedora
     * @param pub
     * @return
     */
    public NihmsSubmissionDTO transform(NihmsPublication pub) {
        
        //matching grant uri is a requirement for all nihms submissions
        URI grantUri = clientService.findGrantByAwardNumber(pub.getGrantNumber());
        if (grantUri==null) {
            throw new RuntimeException(String.format("No Grant matching award number \"%s\" was found. Cannot process submission with pmid %s", pub.getGrantNumber(), pub.getPmid()));
        }
        
        //this will be all about building up the DTO
        NihmsSubmissionDTO submissionDTO = new NihmsSubmissionDTO();
        submissionDTO.setGrantUri(grantUri);

        //use pmid to get additional metadata from Entrez. Need this for DOI, maybe other fields too
        String pmid = pub.getPmid();
        String doi = null;

        //NOTE: if this returns null, the request succeeded, but Entrez could not match the record so we should proceed without one.
        //A RuntimeException would be thrown and the transform would fail if there was e.g. a config or connection problem.
        PubMedEntrezRecord pubmedRecord = pmidLookup.retrievePubMedRecord(pmid);
        if (pubmedRecord != null) {
            doi = pubmedRecord.getDoi();
        }
        
        //are we looking at an update or new submission? see if an submission exists
        NihmsSubmission submission = clientService.findExistingSubmission(grantUri, pmid, doi);
        Deposit nihmsDeposit = null;

        if (submission != null) {
            // first see if a nihms deposit exists for this submission
            Set<Deposit> deposits = clientService.readSubmissionDeposits(submission.getId());
            nihmsDeposit = pickDepositForRepository(deposits, nihmsRepositoryUri);
            
            if (nihmsDeposit!=null) {
                nihmsDeposit = updateNihmsDepositFields(nihmsDeposit, pub);
                for(Iterator<Deposit> iterator = deposits.iterator(); iterator.hasNext(); ) {
                    if(iterator.next().getId() == nihmsDeposit.getId()) {
                        iterator.remove();
                    }
                }
                deposits.add(nihmsDeposit);
            } else if (needNihmsDeposit(pub)) { //if no deposit has been started we may not need a deposit record... 
                nihmsDeposit = initiateNewDeposit(pub);
                deposits.add(nihmsDeposit);
            }       
            
            submission = updateSubmissionFields(submission, pub, grantUri, deposits);
            
        } else {
            if (needNihmsDeposit(pub)) {
                nihmsDeposit = initiateNewDeposit(pub);
            }
            submission = initiateNewSubmission(pubmedRecord, grantUri, nihmsDeposit);
        }
        
        submissionDTO.setNihmsSubmission(submission);
        submissionDTO.setDeposit(nihmsDeposit);
        
        return submissionDTO;
    }
 
    
    /**
     * 
     * @param pub
     * @param pmr
     * @param grantUri
     * @param deposit
     * @return
     */
    private NihmsSubmission initiateNewSubmission(PubMedEntrezRecord pmr, URI grantUri, Deposit deposit) {
        LOG.info("No submission found for PMID \"{}\", initiating new Submission record", pmr.getPmid());
        NihmsSubmission submission = new NihmsSubmission();

        Set<Deposit> deposits = new HashSet<Deposit>();
        if (deposit != null) {
            deposits.add(deposit);
        }

        submission.setPmid(pmr.getPmid());
               
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
        //TODO: setting boolean for missing deposits to false, but there is really no way to know this at the moment unless there is a policy service
        Submission.Status newStatus = calcSubmissionStatus(deposits, false);
        if (!submission.getStatus().equals(newStatus)) {
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

        deposit.setUserActionRequired(isDepositUserActionRequired(pub));
        
        deposit.setUserActionRequired(false);
        
        deposit.setStatus(calcDepositStatus(pub, null));
        
        return deposit;
    }
    
    
    private Deposit updateNihmsDepositFields(Deposit deposit, NihmsPublication pub) {
        String pmcId = pub.getPmcId();
        String nihmsId = pub.getNihmsId();
        if (pmcId!=null && pmcId.length()>0) {
            if (deposit.getAssignedId()==null || !deposit.getAssignedId().equals(pmcId)) {
                LOG.info("Updating Deposit \"{}\". Changing AssignedId from \"{}\" to \"{}\"", deposit.getId(), deposit.getAssignedId(), pmcId);
                deposit.setAssignedId(pmcId);
                deposit.setAccessUrl(String.format(pmcUrlTemplate, pmcId));
            }
        } else if (nihmsId!=null && nihmsId.length()>0){
            if (deposit.getAssignedId()==null || !deposit.getAssignedId().equals(nihmsId)) {
                LOG.info("Updating Deposit \"{}\". Changing AssignedId from \"{}\" to \"{}\"", deposit.getId(), deposit.getAssignedId(), nihmsId);
                deposit.setAssignedId(nihmsId);
            }
        }
        Deposit.Status currDepositStatus = deposit.getStatus();
        Deposit.Status newDepositStatus = calcDepositStatus(pub, currDepositStatus);
        if (!currDepositStatus.equals(newDepositStatus)) {
            LOG.info("Updating Deposit \"{}\". Changing status from \"{}\" to \"{}\"", deposit.getId(), deposit.getStatus(), newDepositStatus);
            deposit.setStatus(newDepositStatus);
        }
        return deposit;
    }
    

    

}
