package com.salaboy.zeebe.knative;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class KnativeZeebeMappingsService {

    private Map<String, Set<String>> workflowsPendingJobs = new HashMap<>();

    private Map<String, Set<String>> expectedBPMNMessages = new HashMap<>();


    public void addPendingJob(String workflowInstanceKey, String jobKey) {
        if (workflowsPendingJobs.get(String.valueOf(workflowInstanceKey)) == null) {
            workflowsPendingJobs.put(String.valueOf(workflowInstanceKey), new HashSet<>());
        }
        workflowsPendingJobs.get(workflowInstanceKey).add(String.valueOf(jobKey));
    }

    public Set<String> getPendingJobsForWorkflow(String workflowInstanceKey) {
        return workflowsPendingJobs.get(workflowInstanceKey);
    }

    public Map<String, Set<String>> getAllPendingJobs() {
        return workflowsPendingJobs;
    }


    public void removePendingJobFromWorkflow(String workflowInstanceKey, String jobKey) {
        workflowsPendingJobs.get(workflowInstanceKey).remove(jobKey);
    }

    public void addExpectedBPMNMessage(String workflowKey, String messageName){

    }
}
