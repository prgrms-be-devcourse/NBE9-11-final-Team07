# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Popspot** is a Spring Boot backend for a popup store platform. Users can browse/reserve popup stores, purchase goods, and manage coupons. Two roles exist: `USER` (customers) and `ORGANIZER` (store operators).

## Build & Run Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Run tests (all)
./gradlew test

# Run a single test class
./gradlew test --tests "com.back.popspot.domain.payment.entity.PaymentTest"

# Run a single test method
./gradlew test --tests "com.back.popspot.domain.SomeTest.methodName"

# Check code style
./gradlew checkstyleMain
```

## Tech Stack

- **Java 25**, **Spring Boot 4.1.0**
- **Spring Data JPA** with **MySQL** (prod) / **H2** (dev/test)
- **Spring Security** + **OAuth2 Client** (social login)
- **Lombok**, **Bean Validation**

## Architecture

### Package Structure

```
src/main/java/com/back/popspot/
├── domain/               # Feature domains
│   ├── coupon/           # Coupons issued per popup store
│   ├── goods/            # Goods (products) sold at popup stores
│   ├── oauthAccount/     # Social login accounts linked to users
│   ├── payment/          # Payments for reservations and goods orders
│   ├── popupStore/       # Popup stores and reservation time slots
│   ├── reservation/      # User reservations for popup store slots
│   └── user/             # User accounts
└── global/
    ├── entity/           # BaseEntity (id, createdAt, modifiedAt)
    ├── exception/        # BusinessException, ErrorCode enum, GlobalExceptionHandler
    └── response/         # CommonApiResponse<T>
```

### Domain Model Relationships

- `User` → `OauthAccount` (1:N) — a user can have multiple OAuth providers
- `User` (as ORGANIZER) → `PopupStore` (1:N)
- `PopupStore` → `ReservationSlot` (1:N) — time slots with capacity
- `User` + `ReservationSlot` → `Reservation` (unique constraint per user+slot)
- `PopupStore` → `Goods` (1:N) — products sold at the store
- `PopupStore` → `Coupon` (1:N) — discount coupons per store
- `User` + `Coupon` → `UserCoupon` — coupon issuance records
- `User` → `GoodsOrder` (1:N) — order with optional coupon discount and shipping info
- `GoodsOrder` → `GoodsOrderItem` (1:N)
- `Payment` — covers both `POPUP` type (linked to `Reservation`) and `GOODS` type (linked to `GoodsOrder`)

### Key Conventions

**Response wrapper**: All controllers return `CommonApiResponse<T>`. Use:
- `CommonApiResponse.success(data)` — 200 with data
- `CommonApiResponse.successMessage("message")` — 200 no data
- `CommonApiResponse.created("message", data)` — 201 with data

**Exceptions**: Throw `BusinessException(ErrorCode)` for domain errors. `GlobalExceptionHandler` maps `ErrorCode` enum values to HTTP responses. Add new error codes to `ErrorCode` enum.

**Base entity**: All entities extend `BaseEntity` which provides `id` (auto-increment), `createdAt`, `modifiedAt` via JPA auditing.

**Soft delete**: `Goods` uses `deletedAt` for soft deletion (set timestamp instead of deleting rows).

**Code style**: Naver Java coding convention (Checkstyle). Max line length 120, LF line endings, UTF-8, tabs=4 spaces.

### Domain Packages

Each domain currently only has an `entity/` subpackage. When adding features, follow the layered pattern: `entity/` → `repository/` → `service/` → `controller/`, with `dto/` for request/response objects.
