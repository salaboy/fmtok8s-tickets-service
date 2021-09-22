# FMTOK8s Tickets Service
For more information check [http://github.com/salaboy/from-monolith-to-k8s](http://github.com/salaboy/from-monolith-to-k8s)

Youtube example of a similar system: https://www.youtube.com/watch?time_continue=45&v=iI7O4iq-Lcc&feature=emb_logo

## Example Flow

Send Reserve Tickets CloudEvent: 

```
curl -X POST http://localhost:8082/reserve -H "Content-Type: application/json" -H "ce-type: Tickets.Reserved"  -H "ce-id: 123"  -H "ce-specversion: 1.0" -H "ce-source: curl-command" -d '{"sessionId" : "123", "ticketsType": "standing", "ticketsQuantity": "2", "reservationId": "456" }' 

```

Once tickets are reserved, you can checkout and proceed to payment: 

```
curl -X POST http://localhost:8082/checkout -H "Content-Type: application/json" -H "ce-type: Tickets.PaymentRequested"  -H "ce-id: 123"  -H "ce-specversion: 1.0" -H "ce-source: curl-command" -d '{"sessionId" : "123", "ticketsType": "standing", "ticketsQuantity": "2", "reservationId": "456" }' 

```



## Build and Release

```
mvn package
```

```
docker build -t salaboy/fmtok8s-c4p-rest:0.1.0
docker push salaboy/fmtok8s-c4p-rest:0.1.0
```

```
cd charts/fmtok8s-c4p-rest
helm package .
```

Copy tar to http://github.com/salaboy/helm and push