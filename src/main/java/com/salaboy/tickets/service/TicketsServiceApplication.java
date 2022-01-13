package com.salaboy.tickets.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.spring.http.CloudEventHttpUtils;
import io.cloudevents.spring.webflux.CloudEventHttpMessageReader;
import io.cloudevents.spring.webflux.CloudEventHttpMessageWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.net.URI;
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


    @Value("${K_SINK:http://broker-ingress.knative-eventing.svc.cluster.local/default/default}")
    private String K_SINK;


    private void logCloudEvent(CloudEvent cloudEvent) {
        EventFormat format = EventFormatProvider
                .getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE);

        log.info("Cloud Event: " + new String(format.serialize(cloudEvent)));

    }

    @Configuration
    public static class CloudEventHandlerConfiguration implements CodecCustomizer {

        @Override
        public void customize(CodecConfigurer configurer) {
            configurer.customCodecs().register(new CloudEventHttpMessageReader());
            configurer.customCodecs().register(new CloudEventHttpMessageWriter());
        }

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
                        PaymentConfirmation paymentConfirmation = new PaymentConfirmation(reservationsMap.get(reservationId).getSessionId(), reservationId );


                        //Get pending payments requests.. and GET /payments/ID -> until it returns true
                        // and when it returns true.. send this CE:
                        CloudEventBuilder cloudEventBuilder = CloudEventBuilder.v1()
                                .withId(UUID.randomUUID().toString())
                                .withType("Tickets.PaymentsAuthorized")
                                .withSource(URI.create("payments.service.default"))
                                .withExtension("correlationkey", reservationId)
                                .withDataContentType("application/json; charset=UTF-8");

                        CloudEvent cloudEvent = cloudEventBuilder.build();
                        logCloudEvent(cloudEvent);

                        WebClient webClientApproved = WebClient.builder().baseUrl(K_SINK).filter(logRequest()).build();

                        HttpHeaders outgoing = CloudEventHttpUtils.toHttp(cloudEvent);

                        webClientApproved.post().headers(httpHeaders -> httpHeaders.putAll(outgoing)).bodyValue(paymentConfirmation).retrieve().bodyToMono(String.class).doOnError(t -> t.printStackTrace())
                                .doOnSuccess(s -> log.info("Result -> " + s)).subscribe();


                    } else {
                        log.info("The Payment Checker queue is empty!");
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

    @GetMapping("reservations")
    public  Map<String, Reservation> getAllReservations(){
        return reservationsMap;
    }

    @PostMapping(value = "/reserve")
    public void reserveTickets(@RequestHeader HttpHeaders headers, @RequestBody ReserveTicketsPayload reserveTicketsPayload) throws JsonProcessingException {
        CloudEvent cloudEvent = CloudEventHttpUtils.fromHttp(headers).build();
        logCloudEvent(cloudEvent);
        if (!cloudEvent.getType().equals("Tickets.Reserved")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.Reserved' and got: " + cloudEvent.getType());
        }

        // A reservation code is generated to keep the reserve alive and correlate with payment

        // Tickets reservation mechanism should kick in here..
        Reservation reservation = new Reservation(reserveTicketsPayload.getReservationId(),
                reserveTicketsPayload.getSessionId(),
                reserveTicketsPayload.getTicketsType(),
                Integer.parseInt(reserveTicketsPayload.getTicketsQuantity()));

        reservationsMap.put(reservation.getReservationId(), reservation);


    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: " + clientRequest.method() + " - " + clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info(name + "=" + value)));
            return Mono.just(clientRequest);
        });
    }

    @PostMapping("/checkout")
    public String checkOutTickets(@RequestHeader HttpHeaders headers, @RequestBody ReserveTicketsPayload payload) throws JsonProcessingException {

        CloudEvent cloudEvent = CloudEventHttpUtils.fromHttp(headers).build();
        logCloudEvent(cloudEvent);
        if (!cloudEvent.getType().equals("Tickets.PaymentRequested")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.PaymentRequested' and got: " + cloudEvent.getType());
        }

        int count = reservationsMap.get(payload.getReservationId()).getTicketsQuantity();
        double ticketPricePerUnit = 123.5;
        double totalAmount = count * ticketPricePerUnit;

        String subject = cloudEvent.getExtension("statemachineid") + ":" + cloudEvent.getExtension("correlationkey") ;
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
        CloudEvent cloudEvent = CloudEventHttpUtils.fromHttp(headers).build();
        logCloudEvent(cloudEvent);
        if (!cloudEvent.getType().equals("Tickets.Emitted")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Tickets.Checkout' and got: " + cloudEvent.getType());
        }
        log.info("This are your tickets for the event");
        return "This are your tickets for the event";
    }

    @PostMapping("/notifications")
    public String notifyCustomer(@RequestHeader HttpHeaders headers, @RequestBody Object body) {
        CloudEvent cloudEvent = CloudEventHttpUtils.fromHttp(headers).build();
        logCloudEvent(cloudEvent);
        if (!cloudEvent.getType().equals("Notifications.Requested")) {
            throw new IllegalStateException("Wrong Cloud Event Type, expected: 'Notifications.Requested' and got: " + cloudEvent.getType());
        }
        log.info("Notification Sent!");
        return "Notification Sent!";

    }



}
