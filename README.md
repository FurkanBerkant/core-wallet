# Core Wallet Application

A Spring Boot-based wallet management system that enables users to create accounts, perform deposits and withdrawals, transfer funds between accounts, and maintain a complete transaction ledger for audit purposes.

## Features

- **Account Management**: Create and retrieve user accounts with unique balances
- **Deposit Transactions**: Add funds to an account
- **Withdraw Transactions**: Remove funds from an account with balance validation
- **Fund Transfers**: Transfer money between accounts with automatic balance updates
- **Transaction Ledger**: Maintain a complete history of all transactions organized by account
- **Error Handling**: Comprehensive exception handling for invalid operations (insufficient balance, account not found)
- **RESTful API**: Clean and intuitive REST endpoints for all operations

## Technologies Used

- **Java 17+**
- **Spring Boot 3.x**
- **Spring Data JPA**
- **PostgreSQL Database**
- **Maven**
- **Docker & Docker Compose**
- **JUnit 5**
- **Mockito**

## How to Run

### Prerequisites

- Java 17 or higher
- Maven
- Docker and Docker Compose
- Port 8080 and 5432 available on your machine

### Using Docker Compose

1. **Start the application with database**:
   ```bash
   docker-compose up
   ```
   This command will:
   - Start a PostgreSQL database container
   - Build and start the Spring Boot application
   - Automatically create required tables

2. **Access the application**:
   ```
   http://localhost:8080/api/accounts
   ```

3. **Stop the application**:
   ```bash
   docker-compose down
   ```

### Using Maven (Local Development)

1. **Start the database only** (if you have Docker):
   ```bash
   docker-compose up -d postgres
   ```
   Or configure your own PostgreSQL instance and update `application.properties`

2. **Build the project**:
   ```bash
   ./mvnw clean install
   ```

3. **Run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Run tests**:
   ```bash
   ./mvnw test
   ```

5. **Access the application**:
   ```
   http://localhost:8080/api/accounts
   ```

## API Endpoints

- `POST /api/accounts` - Create a new account
- `GET /api/accounts/{id}` - Get account details
- `POST /api/accounts/{id}/deposit` - Deposit funds
- `POST /api/accounts/{id}/withdraw` - Withdraw funds
- `POST /api/accounts/transfer` - Transfer funds between accounts
- `GET /api/accounts/{id}/ledger` - Get transaction history for an account

## Project Structure

```
src/
├── main/java/com/wallet/core/
│   ├── controller/      # REST API endpoints
│   ├── service/         # Business logic
│   ├── repository/      # Database access
│   ├── model/          # Entity classes
│   ├── dto/            # Data transfer objects
│   └── exception/      # Custom exceptions
└── test/java/          # Unit and integration tests
```

