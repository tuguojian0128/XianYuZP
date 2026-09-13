# XianYuZP

[简体中文](README.md) | **English**

![Version](https://img.shields.io/badge/version-0.0.1-2f6f5e)
![Java](https://img.shields.io/badge/Java-21-2f6f5e)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.5-2f6f5e)
![Vue](https://img.shields.io/badge/Vue-3-2f6f5e)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-2f6f5e)

> An operations platform for Xianyu virtual products, orders, delivery, messaging, and automation.

XianYuZP supports fixed-content and card-key delivery, order processing, automated delivery, message replies, review follow-ups, product management, inventory management, and operational diagnostics. Accounts, products, orders, inventory, and settings are isolated by tenant, and persistent tasks can recover after a restart.

Current version: **0.0.1**

## Features

- Xianyu account connections, cookies, and login-state maintenance
- Product synchronization, publishing, and local product management
- Fixed-content, card-key, and external-supplier automatic delivery
- Delivery credentials and buyer private-chat delivery channels
- Keyword replies, AI knowledge base, and manual takeover
- Receipt guidance and active reviews after order completion
- Orders, inventory, buyers, messages, and operation logs
- Email, Webhook, and other notification channels
- Health checks, failure retries, and manual review

## Quick start

Copy the environment template and start Docker Compose:

```bash
cp .env.example .env
docker compose up -d --build
```

```bash
docker compose ps
docker compose logs -f app
```

Open <http://localhost:12400> after startup. The default application image is `xianyuzp:0.0.1`; override it with `APP_IMAGE` when needed.

## Configuration

Runtime settings are provided through environment variables or `.env`. Keep `.env` only on the deployment host and never commit it. See [.env.example](.env.example) and [compose.yaml](compose.yaml).

Email notifications support common SMTP providers such as QQ, Gmail, and NetEase. Users only need a sender address, an authorization code, and a recipient address; never put authorization codes in source code or commits.

## Development build

The backend requires Java 21 and the frontend requires Node.js 20.19+:

```bash
./mvnw test
cd vue-code
npm ci
npm run build
```

The Dockerfile builds Java and frontend artifacts inside the container:

```bash
docker compose build app
```

## Layout

- `src/main/java`: Spring Boot backend
- `src/main/resources`: migrations and frontend build artifacts
- `vue-code/src`: Vue 3 administration interface
- `deploy`: Nginx and service deployment examples
- `docs`: design and implementation notes

## Data and security

MySQL data is stored in a Docker volume. Removing the application container does not remove the database volume. Use HTTPS, strong passwords, a host firewall, and regular backups in production; do not expose MySQL directly.

Xianyu APIs, cookies, and risk controls may change. Follow applicable laws and platform rules, and review exceptional tasks shown in the console.

## Version and license

The version is unified at `0.0.1`, corresponding to Git tag `v0.0.1`.

This project is licensed under [PolyForm Noncommercial License 1.0.0](LICENSE). See [DISCLAIMER.md](DISCLAIMER.md) for usage restrictions and disclaimers.
