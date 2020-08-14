package com.salaboy.tickets.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReserveTicketsPayload {
    private String sessionId;
    private String ticketsType;
    private String ticketsQuantity;
    private String reservationId;

    public ReserveTicketsPayload() {
    }

    public ReserveTicketsPayload(String sessionId, String ticketsType, String ticketsQuantity, String reservationId) {
        this.sessionId = sessionId;
        this.ticketsType = ticketsType;
        this.ticketsQuantity = ticketsQuantity;
        this.reservationId = reservationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTicketsType() {
        return ticketsType;
    }

    public void setTicketsType(String ticketsType) {
        this.ticketsType = ticketsType;
    }

    public String getTicketsQuantity() {
        return ticketsQuantity;
    }

    public void setTicketsQuantity(String ticketsQuantity) {
        this.ticketsQuantity = ticketsQuantity;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    @Override
    public String toString() {
        return "ReserveTicketsPayload{" +
                "sessionId='" + sessionId + '\'' +
                ", ticketsType='" + ticketsType + '\'' +
                ", ticketsQuantity='" + ticketsQuantity + '\'' +
                ", reservationId='" + reservationId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReserveTicketsPayload that = (ReserveTicketsPayload) o;
        return Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(ticketsType, that.ticketsType) &&
                Objects.equals(ticketsQuantity, that.ticketsQuantity) &&
                Objects.equals(reservationId, that.reservationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, ticketsType, ticketsQuantity, reservationId);
    }
}
