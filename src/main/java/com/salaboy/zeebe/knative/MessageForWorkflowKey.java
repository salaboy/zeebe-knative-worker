package com.salaboy.zeebe.knative;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageForWorkflowKey {
    private String workflowKey;
    private String messageName;

    public MessageForWorkflowKey() {
    }

    public MessageForWorkflowKey(String workflowKey, String messageName) {
        this.workflowKey = workflowKey;
        this.messageName = messageName;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }

    public String getMessageName() {
        return messageName;
    }

    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }
}
