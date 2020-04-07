package com.salaboy.zeebe.knative;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class CloudEventsZeebeMappingsService {

    private Map<String, Set<String>> workflowsPendingJobs = new HashMap<>();

    private Map<String, Set<String>> messagesByWorkflowKey = new HashMap<>();

    public CloudEventsZeebeMappingsService() {
//        messagesByWorkflowKey.put("2251799813685322", new HashSet<String>());
//        messagesByWorkflowKey.get("2251799813685322").add("Cloud Event Response");
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

    public void addMessageForWorkflowKey(String workflowKey, String messageName) {
        if (messagesByWorkflowKey.get(String.valueOf(workflowKey)) == null) {
            messagesByWorkflowKey.put(String.valueOf(workflowKey), new HashSet<>());
        }
        messagesByWorkflowKey.get(workflowKey).add(String.valueOf(messageName));
    }

    public Map<String, Set<String>> getAllMessages() {
        return messagesByWorkflowKey;
    }

    public Set<String> getMessagesByWorkflowKey(String workflowKey) {
        return messagesByWorkflowKey.get(workflowKey);
    }
}
