package com.salaboy.zeebe.knative;

import com.salaboy.cloudevents.helper.CloudEventsHelper;
import io.cloudevents.CloudEvent;
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

@SpringBootApplication
@EnableZeebeClient
@Slf4j
@RestController
public class ZeebeKnativeWorker {

    public static void main(String[] args) {
        SpringApplication.run(ZeebeKnativeWorker.class, args);
    }

    public enum WORKER_MODES {
        WAIT_FOR_CLOUD_EVENT,
        EMIT_ONLY
    }

    @Autowired
    private KnativeZeebeMappingsService mappingsService;


    @Autowired
    private JobClient jobClient;

    @Autowired
    private ZeebeClientLifecycle zeebeClient;


    @ZeebeWorker(name = "knative-worker", type = "knative")
    public void genericKNativeWorker(final JobClient client, final ActivatedJob job) {
        logJob(job);
        //from headers
        String host = job.getCustomHeaders().get(Headers.HOST);
        String mode = job.getCustomHeaders().get(Headers.MODE);
        String waitForCloudEventType = "";
        if (mode != null && mode.equals(WORKER_MODES.WAIT_FOR_CLOUD_EVENT)) {
            waitForCloudEventType = job.getCustomHeaders().get(Headers.CLOUD_EVENT_WAIT_TYPE);
        }


        final CloudEvent<AttributesImpl, String> myCloudEvent = CloudEventBuilder.<String>builder()
                .withId(UUID.randomUUID().toString())
                .withTime(ZonedDateTime.now())
                .withType(job.getCustomHeaders().get(Headers.CLOUD_EVENT_TYPE)) // from headers
                .withSource(URI.create("zeebe.default.svc.cluster.local"))
                .withData(job.getVariables()) // from content
                .withDatacontenttype(Headers.CONTENT_TYPE)
                .withSubject(String.valueOf(job.getWorkflowInstanceKey()) + ":" + job.getKey())
                .build();

        mappingsService.addPendingJob(String.valueOf(job.getWorkflowInstanceKey()), String.valueOf(job.getKey()));

        WebClient webClient = WebClient.builder().baseUrl(host).filter(logRequest()).build();

        WebClient.ResponseSpec postCloudEvent = CloudEventsHelper.createPostCloudEvent(webClient, "/", myCloudEvent);

        postCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
                .doOnSuccess(s -> System.out.println("Result -> " + s)).subscribe();

        if (mode == null || mode.equals("") || mode.equals(WORKER_MODES.EMIT_ONLY)) {
            jobClient.newCompleteCommand(job.getKey()).send().join();
        }
        // Else.. I should indicate to the job client that I've delegated the service call to an external component
        //jobClient.newForwardedCommand()..

    }


    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: " + clientRequest.method() + " - " + clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info(name + "=" + value)));
            return Mono.just(clientRequest);
        });
    }

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

    @GetMapping
    public void printPendingJobs() {
        System.out.println("Pending Jobs per Workflow: ");
        mappingsService.getAllPendingJobs().forEach((k, v) -> {
                    System.out.println("Workflow Instance Key: " + k + " ->  ");
                    v.forEach(j -> System.out.println("Job Key: " + j));
                }
        );
    }

    @PostMapping("/")
    public String recieveCloudEvent(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        CloudEvent<AttributesImpl, String> cloudEvent = CloudEventsHelper.parseFromRequest(headers, body);
        System.out.println("> I got a cloud event: " + cloudEvent.toString());
        System.out.println("  -> cloud event attr: " + cloudEvent.getAttributes());
        System.out.println("  -> cloud event data: " + cloudEvent.getData());


        String subject = cloudEvent.getAttributes().getSubject().get();
        String workflowInstanceKey = subject.split(":")[0];
        String jobKey = subject.split(":")[1];

        Set<String> pendingJobs = mappingsService.getPendingJobsForWorkflow(workflowInstanceKey);
        if (pendingJobs != null) {
            if (!pendingJobs.isEmpty()) {
                if (pendingJobs.contains(jobKey)) {
                    jobClient.newCompleteCommand(Long.valueOf(jobKey)).variables(cloudEvent.getData()).send().join();
                    mappingsService.removePendingJobFromWorkflow(workflowInstanceKey, jobKey);
                }
                System.out.println("Job Key: " + jobKey + " not found");
            } else {
                System.out.println("This workflow instance key: " + workflowInstanceKey + " doesn't have any pending jobs");
            }
        } else {
            System.out.println("Workflow instance key: " + workflowInstanceKey + " not found");
        }


        return "OK!";
    }


    @PostMapping("/signal")
    public String recieveCloudEventForSignal(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        CloudEvent<AttributesImpl, String> cloudEvent = CloudEventsHelper.parseFromRequest(headers, body);
        System.out.println("> I got a cloud event: " + cloudEvent.toString());
        System.out.println("  -> cloud event attr: " + cloudEvent.getAttributes());
        System.out.println("  -> cloud event data: " + cloudEvent.getData());


        String subject = cloudEvent.getAttributes().getSubject().get();
        String workflowId = subject.split(":")[0];
        String jobId = subject.split(":")[1];
        Optional<String> data = cloudEvent.getData();
        String correlationKey = (String) cloudEvent.getExtensions().get("correlationKey");

        zeebeClient.newPublishMessageCommand()
                .messageName("Cloud Event Response")
                .correlationKey(correlationKey)
                .variables(data.get())
                .send().join();


        return "OK!";
    }


}
