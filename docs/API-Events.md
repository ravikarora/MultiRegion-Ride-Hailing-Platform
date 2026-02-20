# APIs & Events Specification

## REST API

All write APIs require `Idempotency-Key: <uuid>` header (enforced by API Gateway filter).

Base URL: `http://localhost:8080/api/v1`

---

### Driver Location Service

#### `POST /locations` — Driver location update
**Rate limit:** 10,000 req/s (tunable)

**Request:**
```json
{
  "driverId": "drv_123",
  "latitude": 12.9716,
  "longitude": 77.5946,
  "status": "IDLE",
  "tier": "ECONOMY",
  "rating": 4.7,
  "regionId": "ap-south-1"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "data": null
}
```

---

### Dispatch Service

#### `POST /rides` — Create ride request
**Headers:** `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "riderId": "usr_456",
  "pickupLat": 12.9716,
  "pickupLng": 77.5946,
  "destinationLat": 12.9352,
  "destinationLng": 77.6245,
  "tier": "ECONOMY",
  "paymentMethod": "CARD",
  "regionId": "ap-south-1"
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "data": {
    "rideId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "riderId": "usr_456",
    "status": "DISPATCHING",
    "assignedDriverId": null,
    "tier": "ECONOMY",
    "estimatedSurgeMultiplier": 1.0,
    "createdAt": "2026-02-19T10:00:00Z"
  }
}
```

#### `GET /rides/{rideId}` — Poll ride status

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "rideId": "3fa85f64-...",
    "status": "ACCEPTED",
    "assignedDriverId": "drv_123"
  }
}
```

#### `POST /rides/{rideId}/accept?driverId=drv_123`
**Headers:** `Idempotency-Key: <uuid>`

#### `POST /rides/{rideId}/decline?driverId=drv_123`

#### `POST /rides/{rideId}/cancel?requesterId=usr_456`

---

### Trip Service

#### `POST /trips` — Start trip
**Headers:** `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "dispatchRequestId": "3fa85f64-...",
  "driverId": "drv_123",
  "riderId": "usr_456",
  "pickupLat": 12.9716,
  "pickupLng": 77.5946,
  "destinationLat": 12.9352,
  "destinationLng": 77.6245,
  "regionId": "ap-south-1"
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "data": {
    "id": "7f1e3a2b-...",
    "status": "STARTED",
    "startedAt": "2026-02-19T10:05:00Z"
  }
}
```

#### `POST /trips/{tripId}/end`
**Headers:** `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "endLat": 12.9352,
  "endLng": 77.6245,
  "distanceKm": 8.4
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id": "7f1e3a2b-...",
    "status": "ENDED",
    "distanceKm": 8.40,
    "baseFare": "16.10",
    "surgeMultiplier": 1.3,
    "fareAmount": "20.93",
    "currency": "USD",
    "endedAt": "2026-02-19T10:23:00Z"
  }
}
```

#### `POST /trips/{tripId}/pause` — Pause trip (waiting)

#### `GET /trips/{tripId}` — Get trip details

---

### Surge Pricing Service

#### `GET /surge/{geoCell}` — By H3 cell ID

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "geoCell": "872a1072fffffff",
    "surgeMultiplier": 1.8
  }
}
```

#### `GET /surge?lat=12.9716&lng=77.5946` — By coordinates

---

## Kafka Event Schemas

### `driver.location.updated`
Partitioned by `driverId`. Published by Driver Location Service.

```json
{
  "driverId": "drv_123",
  "latitude": 12.9716,
  "longitude": 77.5946,
  "regionId": "ap-south-1",
  "status": "IDLE",
  "tier": "ECONOMY",
  "timestamp": "2026-02-19T10:00:00.000Z"
}
```

---

### `ride.requested`
Partitioned by `rideId`. Published by Dispatch Service.

```json
{
  "rideId": "3fa85f64-...",
  "riderId": "usr_456",
  "pickupLat": 12.9716,
  "pickupLng": 77.5946,
  "destinationLat": 12.9352,
  "destinationLng": 77.6245,
  "tier": "ECONOMY",
  "paymentMethod": "CARD",
  "regionId": "ap-south-1",
  "idempotencyKey": "ik-abc-123",
  "requestedAt": "2026-02-19T10:00:00.000Z"
}
```

---

### `driver.offer.sent`
Partitioned by `rideId`.

```json
{
  "rideId": "3fa85f64-...",
  "driverId": "drv_123",
  "attemptNumber": 1,
  "ttlSeconds": 15,
  "offeredAt": "2026-02-19T10:00:01.000Z"
}
```

---

### `ride.accepted` / `ride.declined` / `ride.cancelled` / `ride.no_driver_found`
Partitioned by `rideId`.

```json
{
  "rideId": "3fa85f64-...",
  "riderId": "usr_456",
  "driverId": "drv_123",
  "status": "ACCEPTED",
  "reason": null,
  "changedAt": "2026-02-19T10:00:08.000Z"
}
```

---

### `trip.started` / `trip.ended` / `trip.paused`
Partitioned by `tripId`.

```json
{
  "tripId": "7f1e3a2b-...",
  "rideId": "3fa85f64-...",
  "driverId": "drv_123",
  "riderId": "usr_456",
  "status": "ENDED",
  "fareAmount": 20.93,
  "surgeMultiplier": 1.3,
  "durationSeconds": 1080,
  "distanceKm": 8.4,
  "eventTime": "2026-02-19T10:23:00.000Z"
}
```

---

### `payment.initiated` / `payment.captured` / `payment.failed`
Partitioned by `tripId`.

```json
{
  "paymentId": "pay_...",
  "tripId": "7f1e3a2b-...",
  "riderId": "usr_456",
  "amount": 20.93,
  "currency": "USD",
  "paymentMethod": "CARD",
  "pspReference": "PSP-A1B2C3D4",
  "status": "CAPTURED",
  "failureReason": null,
  "eventTime": "2026-02-19T10:23:05.000Z"
}
```

---

### `supply.demand.snapshot`
Partitioned by `geoCell`. Published by Driver Location Service every 10s.

```json
{
  "geoCell": "872a1072fffffff",
  "regionId": "ap-south-1",
  "activeDrivers": 42,
  "pendingRides": 67,
  "demandMultiplier": 0.0,
  "computedAt": "2026-02-19T10:00:10.000Z"
}
```

---

## Topic Configuration

| Topic | Partitions | Retention | Consumer Groups |
|-------|-----------|-----------|----------------|
| `driver.location.updated` | 32 | 1 hour | surge-pricing-service |
| `ride.requested` | 8 | 24 hours | notification-service |
| `driver.offer.sent` | 8 | 24 hours | notification-service |
| `ride.accepted/declined/cancelled` | 8 | 24 hours | notification-service |
| `trip.started/ended` | 8 | 24 hours | payment-service, notification-service |
| `payment.*` | 4 | 7 days | notification-service |
| `supply.demand.snapshot` | 16 | 1 hour | surge-pricing-service |
