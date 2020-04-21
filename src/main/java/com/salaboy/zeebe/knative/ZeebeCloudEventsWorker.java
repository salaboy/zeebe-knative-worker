package com.salaboy.zeebe.knative;

import com.salaboy.cloudevents.helper.CloudEventsHelper;
import io.cloudevents.CloudEvent;
import io.cloudevents.extensions.ExtensionFormat;
import io.cloudevents.json.Json;
import io.cloudevents.v03.AttributesImpl;
import io.cloudevents.v03.CloudEventBuilder;
import io.zeebe.spring.client.ZeebeClientLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.zeebe.spring.client.EnableZeebeClient;
import io.zeebe.spring.client.annotation.ZeebeWorker;
import lombok.extern.slf4j.Slf4j;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableZeebeClient
@Slf4j
@RestController
public class ZeebeCloudEventsWorker {

    public static void main(String[] args) {
        SpringApplication.run(ZeebeCloudEventsWorker.class, args);
    }

    public enum WORKER_MODES {
        WAIT_FOR_CLOUD_EVENT,
        EMIT_ONLY
    }

    @Autowired
    private CloudEventsZeebeMappingsService mappingsService;



    @Autowired
    private JobClient jobClient;

    @Autowired
    private ZeebeClientLifecycle zeebeClient;

    //@TODO: refactor to worker class
    @ZeebeWorker(name = "knative-worker", type = "knative", timeout = 60 * 60 * 24 * 1000)
    public void genericKNativeWorker(final JobClient client, final ActivatedJob job) {
        logJob(job);
        //from headers
        //@TODO: deal with empty headers for HOST and MODE
        String host = job.getCustomHeaders().get(Headers.HOST);
        String mode = job.getCustomHeaders().get(Headers.MODE);
        String waitForCloudEventType = "";


        if (mode != null && mode.equals(WORKER_MODES.WAIT_FOR_CLOUD_EVENT.name())) {
            //@TODO: register here as consumer.. this is dynamic consumer
            //mappingsService.registerEventConsumer();
            //waitForCloudEventType = job.getCustomHeaders().get(Headers.CLOUD_EVENT_WAIT_TYPE);


            mappingsService.addPendingJob(String.valueOf(job.getWorkflowKey()), String.valueOf(job.getWorkflowInstanceKey()), String.valueOf(job.getKey()));
            //@TODO: notify the job client that the job was forwarded to an external system. In Node Client this is something like jobCount--;
            //jobClient.newForwardedCommand()..
            emitCloudEventHTTP(job, host);
        } else if (mode == null || mode.equals("") || mode.equals(WORKER_MODES.EMIT_ONLY.name())) {
            jobClient.newCompleteCommand(job.getKey()).send().join();
            emitCloudEventHTTP(job, host);
        }


    }
    //@TODO: refactor to helper class
    private void emitCloudEventHTTP(ActivatedJob job, String host) {
        final CloudEvent<AttributesImpl, String> myCloudEvent = CloudEventBuilder.<String>builder()
                .withId(UUID.randomUUID().toString())
                .withTime(ZonedDateTime.now())
                .withType(job.getCustomHeaders().get(Headers.CLOUD_EVENT_TYPE)) // from headers
                .withSource(URI.create("zeebe.default.svc.cluster.local"))
                .withData(job.getVariables()) // from content
                .withDatacontenttype(Headers.CONTENT_TYPE)
                .withSubject(String.valueOf(job.getWorkflowKey() + ":" + job.getWorkflowInstanceKey()) + ":" + job.getKey())
                .build();


        WebClient webClient = WebClient.builder().baseUrl(host).filter(logRequest()).build();

        WebClient.ResponseSpec postCloudEvent = CloudEventsHelper.createPostCloudEvent(webClient, "/", myCloudEvent);

        postCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
                .doOnSuccess(s -> log.info("Result -> " + s)).subscribe();
    }

    //@TODO: refactor to helper class
    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: " + clientRequest.method() + " - " + clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info(name + "=" + value)));
            return Mono.just(clientRequest);
        });
    }

    //@TODO: refactor to worker class
    private static void logJob(final ActivatedJob job) {
        log.info(
                "complete job\n>>> [type: {}, key: {}, element: {}, workflow instance: {}]\n{deadline; {}]\n[headers: {}]\n[variables: {}]",
                job.getType(),
                job.getKey(),
                job.getElementId(),
                job.getWorkflowInstanceKey(),
                Instant.ofEpochMilli(job.getDeadline()),
                job.getCustomHeaders(),
                job.getVariables());
    }

    //@TODO: create controller class
    @GetMapping("/jobs")
    public String printPendingJobs() {
        Map<String, Map<String, Set<String>>> jobs = mappingsService.getAllPendingJobs();
        return jobs.keySet().stream()
                .map(key -> key + "=" + jobs.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    //@TODO: create controller class
    @GetMapping("/messages")
    public String messages() {
        Map<String, Set<String>> allExpectedBPMNMessages = mappingsService.getAllMessages();
        return allExpectedBPMNMessages.keySet().stream()
                .map(key -> key + "=" + allExpectedBPMNMessages.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    //@TODO: create controller class
    @PostMapping("/")
    public String recieveCloudEvent(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        CloudEvent<AttributesImpl, String> cloudEvent = CloudEventsHelper.parseFromRequest(headers, body);

        final String json = Json.encode(cloudEvent);
        log.info("Cloud Event: " + json);

        String subject = cloudEvent.getAttributes().getSubject().get();
        String workflowKey = subject.split(":")[0];
        String workflowInstanceKey = subject.split(":")[1];
        String jobKey = subject.split(":")[2];

        Set<String> pendingJobs = mappingsService.getPendingJobsForWorkflowKey(workflowKey).get(workflowInstanceKey);
        if (pendingJobs != null) {
            if (!pendingJobs.isEmpty()) {
                if (pendingJobs.contains(jobKey)) {
                    //@TODO: deal with Optionals for Data
                    jobClient.newCompleteCommand(Long.valueOf(jobKey)).variables(cloudEvent.getData().get()).send().join();
                    mappingsService.removePendingJobFromWorkflow(workflowKey, workflowInstanceKey, jobKey);
                } else {
                    log.info("Job Key: " + jobKey + " not found");
                }
            } else {
                log.info("This workflow instance key: " + workflowInstanceKey + " doesn't have any pending jobs");
            }
        } else {
            log.info("Workflow instance key: " + workflowInstanceKey + " not found");
        }

        // @TODO: decide on return types
        return "OK!";
    }

    //@TODO: create controller class
    @PostMapping("/workflows")
    public void addStartWorkflowCloudEventMapping(@RequestBody WorkflowByCloudEvent wbce){

        mappingsService.registerStartWorkflowByCloudEvent(wbce.getCloudEventType(), Long.valueOf(wbce.getWorkflowKey()));
    }

    //@TODO: create controller class
    @GetMapping("/workflows")
    public Map<String, Long> getStartWorkflowCloudEventMapping(){
        return mappingsService.getStartWorkflowByCloudEvents();
    }

    //@TODO: create controller class
    @PostMapping("/workflow")
    public void startWorkflow(@RequestHeader Map<String, String> headers, @RequestBody Map<String, String> body) {
        CloudEvent<AttributesImpl, String> cloudEvent = CloudEventsHelper.parseFromRequest(headers, body);
        Long workflowKey = mappingsService.getStartWorkflowByCloudEvent(cloudEvent.getAttributes().getType());
        if(workflowKey != null) {
            //@TODO: deal with empty body for variables
            zeebeClient.newCreateInstanceCommand().workflowKey(workflowKey).variables(body).send().join();
        }
    }

    //@TODO: create controller class
    @PostMapping("/messages")
    public void addExpectedMessage(@RequestBody MessageForWorkflowKey messageForWorkflowKey) {
        //@TODO: Next step check and advertise which messages are expected by which workflows
        //       This can be scanned on Deploy Workflow, and we can use that to register the workflow as a consumer of events
        mappingsService.addMessageForWorkflowKey(messageForWorkflowKey.getWorkflowKey(), messageForWorkflowKey.getMessageName());
    }

    //@TODO: create controller class
    @PostMapping("/message")
    public String recieveCloudEventForMessage(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        ZeebeCloudEventExtension zeebeCloudEventExtension = new ZeebeCloudEventExtension();
        // I need to do the HTTP to Cloud Events mapping here, that means picking up the CorrelationKey header and add it to the Cloud Event
        String extension = headers.get(Headers.ZEEBE_CLOUD_EVENTS_EXTENSION);
        //@TODO: marshal to a type so I can use an Object here instead of a split
        String[] extensionsArray = extension.split(":");
        if(extensionsArray.length == 2) {
            zeebeCloudEventExtension.setCorrelationKey(extensionsArray[1]);
        }
        final ExtensionFormat zeebe = new ZeebeCloudEventExtension.Format(zeebeCloudEventExtension);

        CloudEvent<AttributesImpl, String> cloudEvent = CloudEventsHelper.parseFromRequestWithExtension(headers, body, zeebe);
        final String json = Json.encode(cloudEvent);
        log.info("Cloud Event: " + json);

        //@TODO: deal with empty type and no correlation key.
        String cloudEventType = cloudEvent.getAttributes().getType();
        String correlationKey = ((ZeebeCloudEventExtension) cloudEvent.getExtensions().get("zeebe")).getCorrelationKey();

        //@TODO: deal with optional for Data, for empty Data
        zeebeClient.newPublishMessageCommand()
                .messageName(cloudEventType)
                .correlationKey(correlationKey)
                .variables(cloudEvent.getData().get())
                .send().join();

        // @TODO: decide on return types
        return "OK!";
    }


}
