package com.salaboy.tickets.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salaboy.cloudevents.helper.CloudEventsHelper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
@Slf4j
public class TicketsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketsServiceApplication.class, args);
    }

    @Value("${PAYMENTS_SERVICE:http://localhost:8083}")
    private String PAYMENTS_SERVICE = "";

//    @Value("${ZEEBE_CLOUD_EVENTS_ROUTER:http://zeebe-cloud-events-router}")
//    private String ZEEBE_CLOUD_EVENTS_ROUTER;

//    @Value("${FRONT_END:http://customer-waiting-room-app.default.svc.cluster.local}")
//    private String FRONT_END;

    @Value("${K_SINK:http://broker-ingress.knative-eventing.svc.cluster.local/default/default}")
    private String K_SINK;

    private ObjectMapper objectMapper = new ObjectMapper();

    private void logCloudEvent(CloudEvent cloudEvent) {
        EventFormat format = EventFormatProvider
                .getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE);

        log.info("Cloud Event: " + new String(format.serialize(cloudEvent)));

    }

    private LinkedList<String> queue = new LinkedList<>();

    private Map<String, Reservation> reservationsMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initPaymentsChecker() {
        log.info("> PaymentChecker Init!");
        new Thread("paymentChecker") {
            public void run() {
                while (true) {
                    if (!queue.isEmpty()) {
                        String reservationId = queue.pop();

                        WebClient webClient = WebClient.builder().baseUrl(PAYMENTS_SERVICE).filter(logRequest()).build();

                        WebClient.ResponseSpec getPaymentConfirmation = webClient.get().uri("/api/" + reservationId).retrieve();
                        Boolean isPaymentProccessed = false;
                        while (!isPaymentProccessed) {
                            isPaymentProccessed = getPaymentConfirmation.bodyToMono(Boolean.class).doOnError(t -> t.printStackTrace())
                                    .doOnSuccess(b -> System.out.println("Is Reservation " + reservationId + " processed?  -> " + b)).block();
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        String paymentConfirmation = null;
                        try {
                            paymentConfirmation = objectMapper.writeValueAsString("{ \"reservationId\" : \"" + reservationId + "\", \"sessionId\" : \""+reservationsMap.get(reservationId).getSessionId()+"\" }");
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }

                        //Get pending payments requests.. and GET /payments/ID -> until it returns true
                        // and when it returns true.. send this CE:
                        CloudEventBuilder cloudEventBuilder = CloudEventBuilder.v03()
                                .withId(UUID.randomUUID().toString())
                                .withTime(OffsetDateTime.now().toZonedDateTime()) // bug-> https://github.com/cloudevents/sdk-java/issues/200
                                .withType("Tickets.PaymentsAuthorized")
                                .withSource(URI.create("payments.service.default"))
                                .withData(paymentConfirmation.getBytes())
                                .withDataContentType("application/json")
                                .withSubject("payments.service.default");

                        CloudEvent zeebeCloudEvent = ZeebeCloudEventsHelper
                                .buildZeebeCloudEvent(cloudEventBuilder)
                                .withCorrelationKey(reservationId)
                                .build();

                        logCloudEvent(zeebeCloudEvent);

                        WebClient webClientApproved = WebClient.builder().baseUrl(K_SINK).filter(logRequest()).build();

                        WebClient.ResponseSpec postApprovedCloudEvent = CloudEventsHelper.createPostCloudEvent(webClientApproved, zeebeCloudEvent);

                        postApprovedCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
                                .doOnSuccess(s -> log.info("Result -> " + s)).subscribe();

//                        webClientApproved = WebClient.builder().baseUrl(FRONT_END).filter(logRequest()).build();
//
//                        WebClient.ResponseSpec postApprovedFrontEndCloudEvent = CloudEventsHelper.createPostCloudEvent(webClientApproved, "/api/", zeebeCloudEvent);
//
//                        postApprovedFrontEndCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
//                                .doOnSuccess(s -> log.info("Result -> " + s)).subscribe();





                    } else {
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

    @PostMapping(value = "/reserve")
    public Reservation reserveTickets(@RequestHeader HttpHeaders headers, @RequestBody String body) throws JsonProcessingException {
        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        logCloudEvent(cloudEvent);
        if (!cloudEvent.getType().equals("Tickets.Reserved")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.Reserved' and got: " + cloudEvent.getType());
        }

        String data = objectMapper.readValue(new String(cloudEvent.getData()), String.class);
        ReserveTicketsPayload payload = objectMapper.readValue(data, ReserveTicketsPayload.class);
        // A reservation code is generated to keep the reserve alive and correlate with payment

        // Tickets reservation mechanism should kick in here..
        Reservation reservation = new Reservation(UUID.randomUUID().toString(), payload.getSessionId(), payload.getTicketsType(), Integer.parseInt(payload.getTicketsQuantity()));

        reservationsMap.put(reservation.getReservationId(), reservation);

//        CloudEventBuilder cloudEventBuilder = CloudEventBuilder.v03()
//                .withId(UUID.randomUUID().toString())
//                .withTime(ZonedDateTime.now())
//                .withType("Tickets.Reserved")
//                .withSource(URI.create("tickets-service.default.svc.cluster.local"))
//                .withData(objectMapper.writeValueAsString(objectMapper.writeValueAsString(reservation)).getBytes())
//                .withDataContentType("application/json")
//                .withSubject(payload.getSessionId());
//
//        CloudEvent zeebeCloudEvent = ZeebeCloudEventsHelper
//                .buildZeebeCloudEvent(cloudEventBuilder)
//                .withCorrelationKey(payload.getSessionId()).build();
//
//
//        logCloudEvent(zeebeCloudEvent);
//        WebClient webClient = WebClient.builder().baseUrl(ZEEBE_CLOUD_EVENTS_ROUTER).filter(logRequest()).build();
//
//        WebClient.ResponseSpec postCloudEvent = CloudEventsHelper.createPostCloudEvent(webClient, "/message", zeebeCloudEvent);
//
//        postCloudEvent.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
//                .doOnSuccess(s -> System.out.println("Result -> " + s)).subscribe();

        return reservation;
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: " + clientRequest.method() + " - " + clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info(name + "=" + value)));
            return Mono.just(clientRequest);
        });
    }

    @PostMapping("/checkout")
    public String checkOutTickets(@RequestHeader HttpHeaders headers, @RequestBody String body) throws JsonProcessingException {

        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        logCloudEvent(cloudEvent);
        if (!cloudEvent.getType().equals("Tickets.PaymentRequested")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.PaymentRequested' and got: " + cloudEvent.getType());
        }

        String data = objectMapper.readValue(new String(cloudEvent.getData()), String.class);
        ReserveTicketsPayload payload = objectMapper.readValue(data, ReserveTicketsPayload.class);


        int count = reservationsMap.get(payload.getReservationId()).getTicketsQuantity();
        double ticketPricePerUnit = 123.5;
        double totalAmount = count * ticketPricePerUnit;

        String subject = cloudEvent.getExtension(ZeebeCloudEventExtension.WORKFLOW_KEY) + ":" + cloudEvent.getExtension(ZeebeCloudEventExtension.WORKFLOW_INSTANCE_KEY) + ":" + cloudEvent.getExtension(ZeebeCloudEventExtension.JOB_KEY);
        WebClient paymentsWebClient = WebClient.builder().baseUrl(PAYMENTS_SERVICE).build();
        WebClient.RequestBodySpec uri = (WebClient.RequestBodySpec) paymentsWebClient.post().uri("/api/");
        String paymentPayload = "{" +
                "\"reservationId\" : \"" + payload.getReservationId() + "\", " +
                "\"amount\" : " + totalAmount + "," +
                "\"subject\": \"" + subject + "\"" +
                "}";
        WebClient.RequestHeadersSpec<?> headersSpec = uri.body(BodyInserters.fromValue(paymentPayload));
        headersSpec.header("Content-Type", new String[]{"application/json"})
                .retrieve().bodyToMono(String.class)
                .doOnError(t -> t.printStackTrace())
                .doOnSuccess(s -> System.out.println("Result -> " + s))
                .subscribe();

        //Add reservation to queue to check for payment processing
        queue.add(payload.getReservationId());

        return "OK";

    }


    @PostMapping("/tickets/emit")
    public String emitTickets(@RequestHeader HttpHeaders headers, @RequestBody String body) {
        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if (!cloudEvent.getType().equals("Tickets.Emitted")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.Checkout' and got: " + cloudEvent.getType());
        }
        log.info("This are your tickets for the event");
        return "This are your tickets for the event";
    }

    @PostMapping("/notifications")
    public String notifyCustomer(@RequestHeader HttpHeaders headers, @RequestBody Object body) {
        CloudEvent cloudEvent = ZeebeCloudEventsHelper.parseZeebeCloudEventFromRequest(headers, body);
        if (!cloudEvent.getType().equals("Notifications.Requested")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Notifications.Requested' and got: " + cloudEvent.getType());
        }
        log.info("Notification Sent!");
        return "Notification Sent!";

    }


}
