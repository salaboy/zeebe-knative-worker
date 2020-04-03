# Zeebe Knative / Cloud Events Worker

This project focus in providing a bridge between Zeebe + BPMN to Knative and Cloud Events to provide Cloud Events orchestration using Zeebe Workflows. 



# Service Task Properties

Properties
- HOST
- TYPE
- MODE: 
  - EMIT_ONLY: 
  - WAIT_FOR_CLOUD_EVENT: 
- WAIT_TYPE: Cloud Event Type to wait 

The worker has two modes:
- EMIT ONLY: It will emit a Cloud Event and complete the Job
- WAIT FOR CLOUD EVENT: It will wait to receive Cloud Event with a specific Type which will be correlated by the workflow and job key to complete the Service Task. 
 

# Endpoints

The worker expose HTTP Endpoints to recieve Cloud Events that can be propagated to workflows. 

- / POST - > Receive Cloud Event via HTTP
- /signal POST -> Receive a Cloud Event that will be forwarded as a BPMN Signal for an Intermediate Catch Event 


# Examples

> zbctl deploy emit-wait.bpmn --insecure
>
> zbctl create instance EMIT_WAIT  --insecure
>
> curl -X POST localhost:8080/ -H "Content-Type: application/json" -H "Ce-Id: 536808d3" -H "Ce-Type: <WAIT_TYPE>" -H "Ce-Source: curl" -H "Ce-Subject: <WORKFLOW_INSTANCE_KEY>:<JOB_KEY>"  -d '{"name":"salaboy"}'  -v
>

EMIT AND CONTINUE:
> zbctl deploy emit-and-continue.bpmn --insecure
> zbctl create instance EMIT_AND_CONTINUE --variables "{\"myVarId\" : \"123\"}" --insecure
>
