package com.salaboy.tickets.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentConfirmation {

    private String sessionId;
    private String reservationId;

    public PaymentConfirmation() {
    }

    public PaymentConfirmation(String sessionId, String reservationId) {
        this.sessionId = sessionId;
        this.reservationId = reservationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }
}
