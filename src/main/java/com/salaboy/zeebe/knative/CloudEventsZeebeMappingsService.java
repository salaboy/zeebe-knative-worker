package com.salaboy.zeebe.knative;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class CloudEventsZeebeMappingsService {

    private Map<String, Set<String>> workflowsPendingJobs = new HashMap<>();

    private Map<String, Set<String>> expectedBPMNMessages = new HashMap<>();

    public CloudEventsZeebeMappingsService() {
        expectedBPMNMessages.put("2251799813685322", new HashSet<String>());
        expectedBPMNMessages.get("2251799813685322").add("Cloud Event Response");
    }

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

    public void addExpectedBPMNMessage(String workflowKey, String messageName) {
        if (expectedBPMNMessages.get(String.valueOf(workflowKey)) == null) {
            expectedBPMNMessages.put(String.valueOf(workflowKey), new HashSet<>());
        }
        expectedBPMNMessages.get(workflowKey).add(String.valueOf(messageName));
    }

    public Map<String, Set<String>> getAllExpectedBPMNMessages() {
        return expectedBPMNMessages;
    }

    public Set<String> getExpectedBPMNMessagesByWorkflowKey(String workflowKey) {
        return expectedBPMNMessages.get(workflowKey);
    }
}
