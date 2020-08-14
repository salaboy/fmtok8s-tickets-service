package com.salaboy.tickets.service;

public class Reservation {
    private String reservationId;
    private String sessionId;

    private String ticketsType;
    private int ticketsQuantity;


    public Reservation() {
    }

    public Reservation( String reservationId, String sessionId, String ticketsType, int ticketsQuantity) {
        this.reservationId = reservationId;
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

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
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
