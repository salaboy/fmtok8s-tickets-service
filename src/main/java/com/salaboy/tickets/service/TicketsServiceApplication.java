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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@SpringBootApplication
@RestController
@Slf4j
public class TicketsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketsServiceApplication.class, args);
    }

    @Value("${PAYMENTS_SERVICE:http://localhost:8083}")
    private String PAYMENTS_SERVICE = "";
    

    @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public double selectTickets(@RequestHeader Map<String, String> headers, @RequestBody String body) {
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
