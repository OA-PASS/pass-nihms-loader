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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.dataconservancy.pass.client.nihms.NihmsPassClientService;
import org.dataconservancy.pass.entrez.PmidLookup;
import org.dataconservancy.pass.entrez.PubMedEntrezRecord;
import org.dataconservancy.pass.loader.nihms.model.NihmsPublication;
import org.dataconservancy.pass.loader.nihms.model.NihmsStatus;
import org.dataconservancy.pass.loader.nihms.util.ConfigUtil;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.loader.nihms.util.ProcessingUtil.formatDate;
import static org.dataconservancy.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

/**
 * Does the heavy lifting of data transform work, converting a NihmsPublication to a 
 * NihmsSubmissionDTO (submission + deposits) for loading to the database
 * @author Karen Hanson
 */
public class NihmsPublicationToSubmission {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPublicationToSubmission.class);
    
    private static final String PMC_URL_TEMPLATE_KEY = "nihmsetl.pmcurl.template"; 
    private static final String PMC_URL_TEMPLATE_DEFAULT = "https://www.ncbi.nlm.nih.gov/pmc/articles/%s/";
    
    private static final String NIHMS_CSV_DATE_PATTERN = "MM/dd/yyyy";

    
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
    public NihmsPublicationToSubmission() {
        this.pmidLookup = new PmidLookup();
        this.clientService = new NihmsPassClientService();
        setProperties();
    }
    
    /**
     * Constructor initiates with the required NIHMS Client Service and PMID lookup
     * @param clientService
     */
    public NihmsPublicationToSubmission(NihmsPassClientService clientService, PmidLookup pmidLookup) {
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
        this.nihmsRepositoryUri = ConfigUtil.getNihmsRepositoryUri();
    }    

    
    /**
     * Does the heavy lifting of converting a NihmsPublication record into the NihmsSubmissionDTO 
     * that is needed for the NihmsLoader to write it to Fedora
     * @param pub
     * @return
     */
    public SubmissionDTO transform(NihmsPublication pub) {
        
        //matching grant uri is a requirement for all nihms submissions
        Grant grant = clientService.findGrantByAwardNumber(pub.getGrantNumber());
        if (grant==null) {
            throw new RuntimeException(String.format("No Grant matching award number \"%s\" was found. Cannot process submission with pmid %s", pub.getGrantNumber(), pub.getPmid()));
        }
        
        //this stage will be all about building up the DTO
        SubmissionDTO submissionDTO = new SubmissionDTO();
        submissionDTO.setGrantId(grant.getId());
        
        Publication publication = retrieveOrCreatePublication(pub);
        submissionDTO.setPublication(publication);
        
        RepositoryCopy repoCopy = retrieveOrCreateRepositoryCopy(pub, publication.getId());
        submissionDTO.setRepositoryCopy(repoCopy);

        Submission submission = retrieveOrCreateSubmission(publication.getId(), grant, (repoCopy!=null), pub.getFileDepositedDate());
        submissionDTO.setSubmission(submission);
        
        return submissionDTO;
    }
 

    
    //****************************************************
    //
    //  Deals with Publication
    //
    //****************************************************
    
    private Publication retrieveOrCreatePublication(NihmsPublication nihmsPub) {
        //use pmid to get additional metadata from Entrez. Need this for DOI, maybe other fields too
        String pmid = nihmsPub.getPmid();
        String doi = null;

        //NOTE: if this returns null, the request succeeded, but Entrez could not match the record so we should proceed without one.
        //A RuntimeException would be thrown and the transform would fail if there was e.g. a config or connection problem.
        PubMedEntrezRecord pubmedRecord = pmidLookup.retrievePubMedRecord(pmid);
        if (pubmedRecord != null) {
            doi = pubmedRecord.getDoi();
        } 
        Publication publication = clientService.findPublicationById(pmid, doi);
        if (publication==null){
            publication = initiateNewPublication(nihmsPub, pubmedRecord);
        }  else {
            if (nullOrEmpty(publication.getDoi()) && !nullOrEmpty(doi)) {
                publication.setDoi(doi);
            }
            if (nullOrEmpty(publication.getPmid())) {
                publication.setPmid(pmid);
            }
        }
        
        return publication;
    }

    /**
     * Ideally, uses the record from Entrez to populate publication details, but in the absence of that
     * this will create a publication with what we have in the NihmsPublication
     * @param nihmsPub
     * @param pmr
     * @return
     */
    private Publication initiateNewPublication(NihmsPublication nihmsPub, PubMedEntrezRecord pmr) {
        LOG.info("No existing publication found for PMID \"{}\", initiating new Publication record", nihmsPub.getPmid());
        Publication publication = new Publication();
        
        publication.setPmid(nihmsPub.getPmid());
        
        if (pmr!=null) {            
            publication.setTitle(pmr.getTitle());
            publication.setDoi(pmr.getDoi());
            publication.setVolume(pmr.getVolume());
            publication.setIssue(pmr.getIssue());
            
            URI journalUri = clientService.findJournalByIssn(pmr.getIssn());
            if (journalUri == null) {
                //try ESSN
                journalUri = clientService.findJournalByIssn(pmr.getEssn());
            }
            publication.setJournal(journalUri);
        } else {
            publication.setTitle(nihmsPub.getArticleTitle());
        }
        
        return publication;
    }


    
    //****************************************************
    //
    //  Deals with RepositoryCopy
    //
    //****************************************************
        
    private RepositoryCopy retrieveOrCreateRepositoryCopy(NihmsPublication pub, URI publicationId) {
        RepositoryCopy repoCopy = null;
        if (publicationId != null) {
            repoCopy = clientService.findNihmsRepositoryCopyForPubId(publicationId);
        }
        if (repoCopy==null && !nullOrEmpty(pub.getNihmsId())) { //only create if there is at least a nihms ID indicating something is started
            repoCopy = initiateNewRepositoryCopy(pub, publicationId);
        }
        return repoCopy;
    }
    
    
    private RepositoryCopy initiateNewRepositoryCopy(NihmsPublication pub, URI publicationId) {
        RepositoryCopy repositoryCopy = new RepositoryCopy();

        LOG.info("NIHMS RepositoryCopy record needed for PMID \"{}\", initiating new RepositoryCopy record", pub.getPmid());

        repositoryCopy.setPublication(publicationId);
        repositoryCopy.setCopyStatus(calcRepoCopyStatus(pub, null));
        repositoryCopy.setRepository(nihmsRepositoryUri);
        
        List<String> externalIds = new ArrayList<String>();
        String pmcId = pub.getPmcId();
        if (!nullOrEmpty(pmcId)){
            externalIds.add(pmcId);    
            repositoryCopy.setAccessUrl(createAccessUrl(pmcId));        
        }
        if (!nullOrEmpty(pub.getNihmsId())) {
            externalIds.add(pub.getNihmsId());            
        }
        repositoryCopy.setExternalIds(externalIds);
                        
        return repositoryCopy;
    }

    private URI createAccessUrl(String pmcId) {
        URI accessUrl = null;
        try {
            accessUrl = new URI(String.format(pmcUrlTemplate, pmcId));
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Could not create PMCID URL from using ID {}", pmcId), ex);
        }
        return accessUrl;        
    }
    

    
    //****************************************************
    //
    //  Deals with Submission
    //
    //****************************************************
    
    private Submission retrieveOrCreateSubmission(URI publicationUri, Grant grant, boolean hasRepoCopy, String depositedDate) {
        Submission submission = null;
        URI grantId = grant.getId();

        //no point in doing this unless there was previously a publication - no publication, no existing submission!
        if (publicationUri != null) {
            List<Submission> submissions = clientService.findSubmissionsByPublicationAndUserId(publicationUri, grant.getPi());
            
            if (!nullOrEmpty(submissions)){
                // is there already a nihms submission in the system for this publication? if so add to it instead of making a new one
                List<Submission> nihmsSubmissions = submissions.stream()
                            .filter(s -> s.getRepositories().contains(nihmsRepositoryUri))
                            .collect(Collectors.toList());
                
                if (nihmsSubmissions.size()==1) {
                    submission = nihmsSubmissions.get(0);
                } else if (nihmsSubmissions.size()>1) { //something wrong with the data
                    String msg = String.format("2 or more submissions, including %s and %s, contain a reference to the NIHMS repository. "
                                                + "Only one Submission should contain a reference. Please check the data before "
                                                + "reloading the record.", nihmsSubmissions.get(0).getId(), nihmsSubmissions.get(1).getId());
                    throw new RuntimeException(msg);
                }
                    
                //no existing submission for nihms repo, lets see if we can find an appropriate submission 
                // to add repository to instead of creating a new one. First one found will do
                if (submission == null) {
                    submission = submissions.stream().filter(s -> !s.getSubmitted()).findFirst().orElse(null);
                }
                
            } 
        }
        
        if (submission==null) {
            submission = initiateNewSubmission(grant, publicationUri);    
        }
        
        // if we have a repository copy, but the submission is not marked as submitted, set it as submitted and use file deposit date
        // when a submission is set to submitted by this transform process, it becomes Source.OTHER regardless of where it started
        if (submission.getRepositories().size()==1
                && hasRepoCopy 
                && !submission.getSubmitted()) {
            submission.setSubmitted(true);
            submission.setSource(Source.OTHER);
            // in the absence of an alternative submittedDate, use the file deposited date from NIHMS data
            if (!nullOrEmpty(depositedDate)) {
                submission.setSubmittedDate(formatDate(depositedDate, NIHMS_CSV_DATE_PATTERN));
            }
        }

        // finally, make sure grant is in the list of the chosen submission
        List<URI> grants = submission.getGrants();
        if (!grants.contains(grantId)) {
            grants.add(grantId);
            submission.setGrants(grants);
        }
                
        return submission;
    }
    
    private Submission initiateNewSubmission(Grant grant, URI publicationUri) {
        LOG.info("No Submission to Repository {} found for Grant {}", nihmsRepositoryUri, grant.getId());
        Submission submission = new Submission();

        submission.setPublication(publicationUri);
        
        List<URI> repositories = new ArrayList<URI>();
        repositories.add(nihmsRepositoryUri);
        submission.setRepositories(repositories);
        
        List<URI> grants = new ArrayList<URI>();
        grants.add(grant.getId());
        submission.setGrants(grants);
                
        submission.setSource(Source.OTHER);
        submission.setAggregatedDepositStatus(null);
        submission.setSubmitted(false); // false by default, changes to true if there is a repoCopy

        submission.setUser(grant.getPi());
                
        return submission;
    }
    
    /**
     * Determines a new deposit status based on various dates populated in the NIHMS publication
     * If the status registered in PASS is further along than NIHMS thinks we are, roll back to
     * what NIHMS says and log the fact as a warning. If this calc returns NULL, a repository copy should
     * not have been created 
     * 
     * @param pub
     * @param currDepositStatus
     * @return
     */
    public static CopyStatus calcRepoCopyStatus(NihmsPublication pub, CopyStatus currCopyStatus) {
        if (pub.getNihmsStatus().equals(NihmsStatus.COMPLIANT)) {
            return CopyStatus.COMPLETE;
        }
        
        CopyStatus newStatus = null;
        
        if (pub.getNihmsStatus().equals(NihmsStatus.NON_COMPLIANT) && !nullOrEmpty(pub.getNihmsId())) {
            newStatus = CopyStatus.STALLED;
        } else if (pub.isTaggingComplete() || pub.hasInitialApproval()) {
            newStatus = CopyStatus.IN_PROGRESS;
        } else if (!nullOrEmpty(pub.getNihmsId())) {
            newStatus = CopyStatus.ACCEPTED;
        }
        
        // if the current status in PASS implies we are further along than we really are, use the status according to 
        // NIHMS but log a warning
        if (currCopyStatus!=null) {
             if (newStatus==null
                 || currCopyStatus.equals(CopyStatus.COMPLETE)
                 || (currCopyStatus.equals(CopyStatus.IN_PROGRESS) && newStatus.equals(CopyStatus.ACCEPTED))) {
                LOG.warn("The status of the RepositoryCopy in PASS was at a later stage than the current NIHMS status would imply. "
                            + "Rolled back from \"{}\" to \"{}\" for pmid {}", currCopyStatus.toString(), (newStatus==null ? "(null)" : newStatus.toString()), pub.getPmid());
             } 
             
        }
        
        return newStatus;
    }
    
        
}
