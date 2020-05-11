package com.salaboy.tickets.service;

import com.salaboy.cloudevents.helper.CloudEventsHelper;
import io.cloudevents.CloudEvent;
import io.zeebe.cloudevents.*;
import io.cloudevents.json.Json;
import io.cloudevents.v03.AttributesImpl;
import io.cloudevents.v03.CloudEventBuilder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

@SpringBootApplication
@RestController
@Slf4j
public class TicketsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketsServiceApplication.class, args);
    }

    @Value("${ZEEBE_CLOUD_EVENTS_ROUTER:http://localhost:8080}")
    private String ZEEBE_CLOUD_EVENTS_ROUTER;
    @Value("${PAYMENTS_SERVICE:http://localhost:8083}")
    private String PAYMENTS_SERVICE = "";
    private LinkedList<TicketPurchaseSession> queue = new LinkedList<>();
    private int concurrentUsers = 1;

    @PostConstruct
    public void initQueue() {
        log.info("> Queue Init!");
        new Thread("queue") {
            public void run() {
                while (true) {
                    if (!queue.isEmpty()) {
                        TicketPurchaseSession session = queue.pop();
                        log.info("You are next: " + session);

                        CloudEventBuilder<String> cloudEventBuilder = CloudEventBuilder.<String>builder()
                                .withId(UUID.randomUUID().toString())
                                .withTime(ZonedDateTime.now())
                                .withType("Tickets.Reserved")
                                .withSource(URI.create("tickets.service.default"))
                                .withData("{\"tickets\" : " + 2 + "}")
                                .withDatacontenttype("application/json")
                                .withSubject(session.getSessionId());


                        CloudEvent<AttributesImpl, String> zeebeCloudEvent = ZeebeCloudEventsHelper
                                .buildZeebeCloudEvent(cloudEventBuilder)
                                .withCorrelationKey(session.getSessionId()).build();

                        String cloudEventJson = Json.encode(zeebeCloudEvent);
                        log.info("Before sending Cloud Event: " + cloudEventJson);
                        WebClient webClient = WebClient.builder().baseUrl(ZEEBE_CLOUD_EVENTS_ROUTER).filter(logRequest()).build();

                        WebClient.ResponseSpec postCloudEvent = CloudEventsHelper.createPostCloudEvent(webClient, "/message", zeebeCloudEvent);

                        postCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
                                .doOnSuccess(s -> System.out.println("Result -> " + s)).subscribe();
                       log.info("Queue Size: " + queue.size());
                    }else{
                        log.info("The Queue is empty!");
                    }
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }


    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: " + clientRequest.method() + " - " + clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info(name + "=" + value)));
            return Mono.just(clientRequest);
        });
    }

    @PostMapping("/queue")
    public String queueForTicket(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        CloudEvent<AttributesImpl, String> cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if(!cloudEvent.getAttributes().getType().equals("Tickets.CustomerQueueJoined")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.CustomerQueueJoined' and got: " + cloudEvent.getAttributes().getType() );
        }
        String data = cloudEvent.getData().get();
        TicketPurchaseSession session = Json.decodeValue(data, TicketPurchaseSession.class);
        String clientId = UUID.randomUUID().toString();
        session.setClientId(clientId);
        log.info("> New Customer in Queue: " + session);
        queue.add(session);
        return session.toString();
    }

    @PostMapping("/checkout")
    public double selectTickets(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
        CloudEvent<AttributesImpl, String> cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if(!cloudEvent.getAttributes().getType().equals("Tickets.CheckedOut")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.CheckedOut' and got: " + cloudEvent.getAttributes().getType() );
        }
        log.info("Cloud Event Data at Checkout : " + cloudEvent.getData().get()) ;
        String subject = "";
        ZeebeCloudEventExtension zcee = (ZeebeCloudEventExtension)cloudEvent.getExtensions().get(Headers.ZEEBE_CLOUD_EVENTS_EXTENSION);
        if(zcee != null){
            subject = zcee.getWorkflowKey() + ":" + zcee.getWorkflowInstanceKey() + ":" + zcee.getJobKey();
        }
        int count = 0; // get from payload
        double ticketPricePerUnit = 123.5;
        double totalAmount = count * ticketPricePerUnit;


        log.info("> Delegate to Payment Service to collect the payment.");

        WebClient paymentsWebClient = WebClient.builder().baseUrl(PAYMENTS_SERVICE).build();
        WebClient.RequestBodySpec uri = (WebClient.RequestBodySpec)paymentsWebClient.post().uri("");
        String paymentPayload = "{" +
                                    "\"paymentId\" : \"123\", " +
                                    "\"amount\" : " +totalAmount + "," +
                                    "\"subject\": \"" + subject + "\"" +
                                "}";
        WebClient.RequestHeadersSpec<?> headersSpec = uri.body(BodyInserters.fromValue(paymentPayload));
        headersSpec.header("Content-Type", new String[]{"application/json"})
                .retrieve().bodyToMono(String.class)
                .doOnError(t -> t.printStackTrace())
                .doOnSuccess(s -> System.out.println("Result -> " + s))
                .subscribe();


        return totalAmount;
    }

    @PostMapping("/tickets/emit")
    public String emitTickets(@RequestHeader Map<String, String> headers, @RequestBody Object body){
        CloudEvent<AttributesImpl, String> cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if(!cloudEvent.getAttributes().getType().equals("Tickets.Emitted")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.Checkout' and got: " + cloudEvent.getAttributes().getType() );
        }
        log.info("This are your tickets for the event");
        return "This are your tickets for the event";
    }

    @PostMapping("/notifications")
    public String notifyCustomer(@RequestHeader Map<String, String> headers, @RequestBody Object body){
        CloudEvent<AttributesImpl, String> cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if(!cloudEvent.getAttributes().getType().equals("Notifications.Requested")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Notifications.Requested' and got: " + cloudEvent.getAttributes().getType() );
        }
        log.info("Notification Sent!");
        return "Notification Sent!";

    }


}
