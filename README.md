# PokerDice Web Application

Multiplayer dice game application with distributed architecture, developed in **Kotlin (Spring Boot)** and **React**, orchestrated via **Docker Compose**.

## 📋 Prerequisites

To run this project on a new computer, you only need to have installed:

* **Git** (To clone the repository)
* **Docker Desktop** (Must be running)

*Note: You don't need to install Java, Gradle, or Node.js locally. The build process is completely isolated inside containers (Multi-Stage Build).*

---

## 🚀 Installation and Execution (Quick Start)

### 1. Clone the Repository
Open the terminal and execute:
```bash
git clone <REPOSITORY_URL>
cd pokerdice
```

### 2. Start the Infrastructure
This command compiles the code (Backend and Frontend), builds the images, and starts all services (Database, Backend Cluster, and Nginx).
```bash
docker compose up -d --build
```

(Wait a few minutes on the first run while dependencies are downloaded).

### 3. Verify Status
Confirm that all containers are active (`Up`):
```bash
docker ps
```

---

## 🖥️ Application Access

The single entry point is Nginx (Load Balancer), which manages both static traffic and the API.

* **Web App**: http://localhost:8080
* **API Endpoint**: `http://localhost:8080/api`

---

## 📊 Monitoring and Logs

To monitor traffic being distributed between backend replicas, use this specific command that filters only the application server logs:
```bash
docker compose logs -f backend-1 backend-2
```

Docker assigns different colors to each container, allowing you to visualize the Round-Robin algorithm in action.

Other useful commands:
* View all logs (DB, Nginx, Backends): `docker compose logs -f`
* View Frontend/Proxy logs: `docker logs -f pokerdice-nginx`

---

## 🛑 Stop and Clean

To shut down the application and remove created containers and networks (Clean Slate):
```bash
docker compose down
```

---

## 🏗️ Environment Architecture

The `docker-compose.yml` sets up the following topology:

1. **pokerdice-nginx (8080)**:
   * Serves React static files (SPA).
   * Load balances `/api` requests to the backends.

2. **pokerdice-backend-1 (8081)**: Node 1 of the Spring Boot server.

3. **pokerdice-backend-2 (8082)**: Node 2 of the Spring Boot server.

4. **db-pokerdice-dev (5432)**: Shared PostgreSQL database.

5. **pokerdice-ubuntu**: Utility container (`dig`, `curl`) for internal network debugging.
