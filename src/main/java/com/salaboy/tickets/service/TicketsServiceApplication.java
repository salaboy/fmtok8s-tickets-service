package com.salaboy.tickets.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.zeebe.cloudevents.ZeebeCloudEventExtension;
import io.zeebe.cloudevents.ZeebeCloudEventsHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@SpringBootApplication
@RestController
@Slf4j
public class TicketsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketsServiceApplication.class, args);
    }

    @Value("${PAYMENTS_SERVICE:http://localhost:8083}")
    private String PAYMENTS_SERVICE = "";

    private ObjectMapper objectMapper = new ObjectMapper();

    private void logCloudEvent(CloudEvent cloudEvent) {
        EventFormat format = EventFormatProvider
                .getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE);

        log.info("Cloud Event: " + new String(format.serialize(cloudEvent)));

    }

    @PostMapping(value = "/checkout")
    public double selectTickets(@RequestHeader HttpHeaders headers, @RequestBody String body) throws JsonProcessingException {
        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        logCloudEvent(cloudEvent);
        if(!cloudEvent.getType().equals("Tickets.CheckedOut")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.CheckedOut' and got: " + cloudEvent.getType() );
        }
        String subject = cloudEvent.getExtension(ZeebeCloudEventExtension.WORKFLOW_KEY) + ":" + cloudEvent.getExtension(ZeebeCloudEventExtension.WORKFLOW_INSTANCE_KEY) + ":" + cloudEvent.getExtension(ZeebeCloudEventExtension.JOB_KEY);
        String data = objectMapper.readValue(new String(cloudEvent.getData()), String.class);
        BuyTicketsPayload payload = objectMapper.readValue(data, BuyTicketsPayload.class);


        int count = Integer.valueOf(payload.getTicketsQuantity());
        double ticketPricePerUnit = 123.5;
        double totalAmount = count * ticketPricePerUnit;


        log.info("> Delegate to Payment Service to collect the payment.");

        WebClient paymentsWebClient = WebClient.builder().baseUrl(PAYMENTS_SERVICE).build();
        WebClient.RequestBodySpec uri = (WebClient.RequestBodySpec)paymentsWebClient.post().uri("");
        String paymentPayload = "{" +
                                    "\"paymentId\" : \"ABC-123\", " +
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
    public String emitTickets(@RequestHeader HttpHeaders headers, @RequestBody String body){
        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if(!cloudEvent.getType().equals("Tickets.Emitted")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.Checkout' and got: " + cloudEvent.getType() );
        }
        log.info("This are your tickets for the event");
        return "This are your tickets for the event";
    }

    @PostMapping("/notifications")
    public String notifyCustomer(@RequestHeader HttpHeaders headers, @RequestBody Object body){
        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if(!cloudEvent.getType().equals("Notifications.Requested")){
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Notifications.Requested' and got: " + cloudEvent.getType() );
        }
        log.info("Notification Sent!");
        return "Notification Sent!";

    }


}
