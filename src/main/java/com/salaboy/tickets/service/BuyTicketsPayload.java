package com.salaboy.tickets.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuyTicketsPayload {
    private String sessionId;
    private String ticketsType;
    private String ticketsQuantity;

    public BuyTicketsPayload() {
    }

    public BuyTicketsPayload(String sessionId, String ticketsType, String ticketsQuantity) {
        this.sessionId = sessionId;
        this.ticketsType = ticketsType;
        this.ticketsQuantity = ticketsQuantity;
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

    @Override
    public String toString() {
        return "BuyTicketsPayload{" +
                "sessionId='" + sessionId + '\'' +
                ", ticketsType='" + ticketsType + '\'' +
                ", ticketsQuantity=" + ticketsQuantity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuyTicketsPayload that = (BuyTicketsPayload) o;
        return ticketsQuantity == that.ticketsQuantity &&
                Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(ticketsType, that.ticketsType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, ticketsType, ticketsQuantity);
    }
}
