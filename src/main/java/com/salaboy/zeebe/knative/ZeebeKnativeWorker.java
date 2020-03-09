package com.salaboy.zeebe.knative;

import com.salaboy.cloudevents.helper.CloudEventsHelper;
import io.cloudevents.CloudEvent;
import io.cloudevents.v03.AttributesImpl;
import io.cloudevents.v03.CloudEventBuilder;
import org.checkerframework.checker.units.qual.A;
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

    private Map<String, Set<String>> workflowsPendingJobs = new HashMap<>();

    @Autowired
    private JobClient jobClient;

    @ZeebeWorker(name = "knative-worker", type = "knative")
    public void genericKNativeWorker(final JobClient client, final ActivatedJob job) {
        logJob(job);

        String host = job.getCustomHeaders().get("host"); //from headers
        final CloudEvent<AttributesImpl, String> myCloudEvent = CloudEventBuilder.<String>builder()
                .withId(UUID.randomUUID().toString())
                .withTime(ZonedDateTime.now())
                .withType(job.getCustomHeaders().get("type")) // from headers
                .withSource(URI.create("zeebe.default.svc.cluster.local"))
                .withData(job.getVariables()) // from content
                .withDatacontenttype("application/json")
                .withSubject(String.valueOf(job.getWorkflowInstanceKey()))
                .build();

        if (workflowsPendingJobs.get(String.valueOf(job.getWorkflowInstanceKey())) == null) {
            workflowsPendingJobs.put(String.valueOf(job.getWorkflowInstanceKey()), new HashSet<>());
        }
        workflowsPendingJobs.get(String.valueOf(job.getWorkflowInstanceKey())).add(String.valueOf(job.getKey()));

        WebClient webClient = WebClient.builder().baseUrl(host).filter(logRequest()).build();

        WebClient.ResponseSpec postCloudEvent = CloudEventsHelper.createPostCloudEvent(webClient, "/", myCloudEvent);

        postCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
                .doOnSuccess(s -> System.out.println("Result -> " + s)).subscribe();
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
        System.out.println(workflowsPendingJobs);
    }

    @PostMapping("/")
    public String recieveCloudEvent(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        CloudEvent<AttributesImpl, String> cloudEvent = CloudEventsHelper.parseFromRequest(headers, body);
        System.out.println("> I got a cloud event: " + cloudEvent.toString());
        System.out.println("  -> cloud event attr: " + cloudEvent.getAttributes());
        System.out.println("  -> cloud event data: " + cloudEvent.getData());
        String workflowId = cloudEvent.getAttributes().getSubject().get();
        Set<String> pendingJobs = workflowsPendingJobs.get(workflowId);
        if (!pendingJobs.isEmpty()) {
            Iterator<String> it = pendingJobs.iterator();
            if (it.hasNext()) {
                String jobId = it.next();
                jobClient.newCompleteCommand(Long.valueOf(jobId)).variables(cloudEvent.getData()).send().join();

                pendingJobs.remove(jobId);
            }
        }


        return "OK!";
    }


}
