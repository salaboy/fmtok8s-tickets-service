package com.salaboy.tickets.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuyTicketsPayload {
    private String sessionId;
    private String ticketsType;
    private int ticketsQuantity;

    public BuyTicketsPayload() {
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

    public int getTicketsQuantity() {
        return ticketsQuantity;
    }

    public void setTicketsQuantity(int ticketsQuantity) {
        this.ticketsQuantity = ticketsQuantity;
    }
}
