# Secure Web System as an Enterprise Risk Control

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white)
![Auth0](https://img.shields.io/badge/Auth0-OIDC-EB5424?logo=auth0&logoColor=white)
![Apache](https://img.shields.io/badge/Apache-2.4-D22128?logo=apache&logoColor=white)
![TLS](https://img.shields.io/badge/TLS-Let's%20Encrypt-003A70?logo=letsencrypt&logoColor=white)

A minimal, production-shaped full-stack system that demonstrates how **identity
management, authorization, encryption in transit, and audit logging** function as
concrete **enterprise risk controls**. The project pairs a React single-page application
authenticated with **Auth0** against a **Spring Boot** backend protected as an **OAuth 2.0
Resource Server**, and deploys both behind **Apache with a valid Let's Encrypt
certificate** on a public **Duck DNS** hostname.

> **Deployed address**
>
> `https://YOUR-SUBDOMAIN.duckdns.org` — replace with your live URL before submitting.

---

## Table of contents

1. [Overview](#overview)
2. [Learning objectives](#learning-objectives)
3. [Architecture](#architecture)
4. [Core concepts](#core-concepts)
5. [Technology stack](#technology-stack)
6. [Repository structure](#repository-structure)
7. [Auth0 configuration](#auth0-configuration)
8. [Backend](#backend)
9. [Frontend](#frontend)
10. [Running locally](#running-locally)
11. [Deployment](#deployment)
12. [Verification checklist](#verification-checklist)
13. [Risk-control reflection](#risk-control-reflection)
14. [Threat model and security notes](#threat-model-and-security-notes)
15. [Troubleshooting](#troubleshooting)
16. [Evidence](#evidence)
17. [References](#references)

---

## Overview

Enterprise applications rarely fail because of a single technical defect; they fail when
trust is placed somewhere it should not be. This project is deliberately small so that the
security decisions are visible end to end:

- A **user authenticates once** with Auth0; the SPA never sees or stores a password.
- The SPA receives a short-lived **JWT access token** scoped to a specific **audience**.
- The backend, acting purely as an **OAuth 2.0 Resource Server**, validates the token's
  **signature, issuer, and audience** before serving any data.
- All traffic crosses the internet over **TLS**, and the application port is **never
  exposed directly** — Apache terminates TLS and reverse-proxies to the API.
- Every request is recorded by **systemd/journald**, providing the audit trail needed for
  incident investigation.

The result is a system where each control reduces a specific, nameable risk.

## Learning objectives

By completing this workshop you should be able to:

- Explain the difference between **authentication** (who the user is) and
  **authorization** (what the user may do).
- Protect an HTTP API with a **JWT access token** issued by Auth0 and validated by Spring
  Security.
- Understand **OAuth 2.0**, **OpenID Connect**, and the role of the **audience** claim in
  preventing token misuse across APIs.
- Deploy an application behind **HTTPS** with a legitimate, automatically renewed
  **TLS certificate**.
- Identify the enterprise risks controlled by **identity management, authorization,
  encryption in transit, and central logging**.

## Architecture

```
        ┌────────────┐
        │  Browser   │  login + access token
        └─────┬──────┘
              │
        ┌─────▼──────┐        Bearer token        ┌──────────────────┐
        │   Auth0    │                            │  Spring Boot API │
        │  (OIDC)    │                            │ resource server  │
        └─────┬──────┘                            └────────▲─────────┘
              │  id_token + access_token                    │
        ┌─────▼──────────────────────────┐                  │
        │  React SPA (frontend)          │                  │
        └────────────────────────────────┘                  │
                                                            │
   Internet ──► Duck DNS ──► Apache + TLS ──► /api/ ────────┘
                                  │
                            (static SPA files)
```

**Request flow**

1. The user clicks **Log in**; the Auth0 SPA SDK redirects to the Auth0 Universal Login.
2. Auth0 authenticates the user and redirects back with an authorization **code**.
3. The SDK exchanges the code (PKCE) for an **ID token** and an **access token** scoped to
   `https://enterprise-risk-api`.
4. When the user calls the API, the SPA attaches the access token in
   `Authorization: Bearer <token>`.
5. Spring Security validates the token's signature, `iss` (issuer), `aud` (audience) and
   expiry, then authorizes the request.

## Core concepts

### Authentication vs. authorization

- **Authentication** answers *“Who is this principal?”* — handled by Auth0 during login.
  The result is an identity (`sub`) plus an ID token for the client.
- **Authorization** answers *“Is this principal allowed to perform this action?”* — handled
  by the backend via Spring Security rules and the access token's claims.

Keeping these separate is what lets the SPA be untrusted for access decisions: it may ask,
but only the resource server grants.

### OAuth 2.0, OpenID Connect, and the SPA

OAuth 2.0 is an **authorization** framework that issues access tokens. OpenID Connect (OIDC)
layers **authentication** on top via the ID token. A browser SPA is a **public client** and
therefore uses the **Authorization Code Flow with PKCE** — it can hold a **client ID** but
must **never** hold a client secret. Anything shipped to the browser is public.

### JSON Web Tokens (JWT)

A JWT is a signed, self-contained token with three parts: header, payload (claims), and
signature.

- The **signature** (RS256, asymmetric) proves the token was issued by Auth0 and not
  tampered with. The resource server verifies it using Auth0's public **JWKS**.
- The **`iss`** claim must match the configured issuer, so tokens from another tenant are
  rejected.
- The **`aud`** claim must contain the API identifier (`https://enterprise-risk-api`), so a
  token minted for *another* API cannot be replayed against this one.
- The **`exp`** claim bounds the token's lifetime, limiting the window of a leaked token.

### Resource Server pattern

The backend never issues tokens and never handles credentials. It only **verifies** tokens —
a clean separation of duties that shrinks the attack surface: there is no session store, no
password database, and no custom crypto.

### Encryption in transit (TLS/HTTPS)

A bearer token is a credential; whoever holds it can use it until it expires. **TLS**
protects tokens and user data from interception and tampering on the network. Certbot issues
and renews a free, publicly trusted certificate so browsers show a valid lock icon without
warnings.

### Reverse proxy and network exposure

Apache terminates TLS, serves the static SPA, and forwards only `/api/` to the backend on
`127.0.0.1:8080`. Because the API binds to the loopback interface and the firewall allows
only ports **80** and **443**, the application port is not reachable from the internet.

### CORS

CORS is a browser-enforced policy. The backend allows only the exact development and
production origins, so a malicious site cannot read API responses using the user's browser
context.

### Least privilege and audit logging

Server processes run as a dedicated, non-login system user (`riskapi`) with no shell and
minimal filesystem access. systemd captures all service output into journald, producing a
centralized, timestamped record for incident response.

## Technology stack

| Layer            | Technology                                            |
| ---------------- | ----------------------------------------------------- |
| Frontend         | React 18, Vite 5, `@auth0/auth0-react` 2.x            |
| Backend          | Java 21, Spring Boot 3.3, Spring Security, OAuth2 Resource Server |
| Identity         | Auth0 (Universal Login, RS256, audience-scoped tokens) |
| Web server       | Apache HTTP Server 2.4 (`ssl`, `proxy`, `proxy_http`, `rewrite`, `headers`) |
| TLS              | Let's Encrypt via Certbot                             |
| Public DNS       | Duck DNS                                              |
| Process manager  | systemd                                               |
| Build tooling    | Maven (wrapper) + npm                                 |

## Repository structure

```
.
├── backend/                         # Spring Boot OAuth 2.0 Resource Server
│   ├── .mvn/                        # Maven wrapper
│   ├── src/main/java/com/example/enterpriserisk/
│   │   ├── EnterpriseRiskApiApplication.java
│   │   ├── config/SecurityConfig.java       # Security, CORS, resource server
│   │   └── controller/RiskController.java   # Protected /api/risk-status
│   ├── src/main/resources/application.properties
│   ├── src/test/java/.../RiskControllerTest.java
│   ├── mvnw / mvnw.cmd
│   └── pom.xml
├── frontend/                        # Vite + React SPA
│   ├── src/
│   │   ├── main.jsx                 # Auth0Provider setup
│   │   ├── App.jsx                  # Login, logout, protected API call
│   │   └── index.css
│   ├── .env.example                 # Committable template (no secrets)
│   ├── .env.local                   # Local values (git-ignored)
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
├── deploy/
│   ├── enterprise-risk-api.service  # systemd unit template
│   └── enterprise-risk.conf         # Apache virtual host template
├── .gitignore
└── README.md
```

## Auth0 configuration

Create a tenant, then provision two objects:

1. **Application → Single Page Application**, named `Enterprise Risk Lab Frontend`.
2. **API → name** `Enterprise Risk Lab API`, **identifier (audience)**
   `https://enterprise-risk-api`, **signing algorithm** `RS256`.

Configure the SPA (replace the placeholder hostname with your deployed one):

| Setting               | Value                                                        |
| --------------------- | ------------------------------------------------------------ |
| Allowed Callback URLs | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org`    |
| Allowed Logout URLs   | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org`    |
| Allowed Web Origins   | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org`    |

Values to record (never commit them, and **never** a client secret for an SPA):

```
AUTH0_DOMAIN=YOUR_TENANT.us.auth0.com
AUTH0_CLIENT_ID=...
AUTH0_AUDIENCE=https://enterprise-risk-api
```

## Backend

A Spring Boot application that behaves strictly as a **resource server**.

### Security configuration (`SecurityConfig.java`)

- CSRF is disabled because the API is **stateless** and authenticated by bearer tokens
  rather than cookies.
- CORS allows only the configured origins and the `Authorization` / `Content-Type` headers.
- `/actuator/health` is public (useful for probes); `/api/**` requires authentication;
  every other path is denied by default.
- `oauth2ResourceServer().jwt()` enables JWT validation, including issuer and audience.

### Endpoint

| Method | Path                | Access        | Description                        |
| ------ | ------------------- | ------------- | ---------------------------------- |
| GET    | `/api/risk-status`  | Authenticated | Returns the token subject and control status |
| GET    | `/actuator/health`  | Public        | Health probe                       |

Sample authenticated response:

```json
{
  "status": "protected",
  "subject": "auth0|64f...",
  "control": "JWT validation and authenticated access"
}
```

### Configuration (`application.properties`)

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://YOUR_TENANT.us.auth0.com/
spring.security.oauth2.resourceserver.jwt.audiences=https://enterprise-risk-api
server.port=8080
management.endpoints.web.exposure.include=health
app.cors.allowed-origins=http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org
```

## Frontend

A Vite React SPA that delegates all authentication to Auth0.

- `main.jsx` wraps the app in `<Auth0Provider>` and requests an access token for the
  configured **audience** on each authorization.
- `App.jsx` exposes **Log in**, **Call protected API**, and **Log out**. Tokens are obtained
  on demand with `getAccessTokenSilently()` and sent only in the `Authorization` header;
  they are never displayed or persisted manually.

Environment variables (`.env.local`, git-ignored):

```
VITE_AUTH0_DOMAIN=YOUR_TENANT.us.auth0.com
VITE_AUTH0_CLIENT_ID=YOUR_CLIENT_ID
VITE_AUTH0_AUDIENCE=https://enterprise-risk-api
VITE_API_URL=http://localhost:8080
```

> Vite only exposes variables prefixed with `VITE_`, and everything it exposes is public.
> Do not place secrets in any frontend variable.

## Running locally

**Prerequisites:** Java 21, Node.js 18+, and an Auth0 tenant/API.

### 1. Backend

```bash
cd backend
# set issuer + audience in src/main/resources/application.properties first
./mvnw spring-boot:run
```

Verify the security baseline:

```bash
curl -i http://localhost:8080/api/risk-status      # → 401 Unauthorized (expected)
curl -i http://localhost:8080/actuator/health      # → 200 OK
```

### 2. Frontend

```bash
cd frontend
copy .env.example .env.local      # Windows (cp on macOS/Linux) — then fill in values
npm install
npm run dev
```

Open `http://localhost:5173`, log in, and click **Call protected API**.

## Deployment

### 1. Duck DNS

Create a subdomain (e.g. `team4-risk.duckdns.org`) and point it at the server's public IP.
Confirm it resolves, then allow inbound TCP **80** and **443** only — **never** expose 8080.

### 2. Server packages (Ubuntu)

```bash
sudo apt update
sudo apt install -y apache2 certbot python3-certbot-apache openjdk-21-jre
```

Harden the host first: change default credentials, create a non-root deployment user, and
enable the firewall.

### 3. Build artifacts (on a trusted machine or CI)

```bash
(cd frontend && npm run build)      # → frontend/dist/
(cd backend  && ./mvnw clean package)   # → backend/target/*.jar
```

Transfer the build outputs to the server over a secure channel (e.g. `scp`/`rsync` over SSH).

### 4. Backend service

```bash
sudo mkdir -p /opt/enterprise-risk-api
sudo cp backend/target/*.jar /opt/enterprise-risk-api/app.jar
sudo useradd --system --no-create-home riskapi
sudo chown -R riskapi:riskapi /opt/enterprise-risk-api

sudo cp deploy/enterprise-risk-api.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now enterprise-risk-api
curl http://127.0.0.1:8080/actuator/health
```

### 5. Apache + TLS

```bash
sudo mkdir -p /var/www/enterprise-risk-frontend
sudo cp -r frontend/dist/* /var/www/enterprise-risk-frontend/

sudo a2enmod ssl proxy proxy_http rewrite headers
sudo cp deploy/enterprise-risk.conf /etc/apache2/sites-available/
sudo a2ensite enterprise-risk.conf
sudo apachectl configtest
sudo systemctl reload apache2

sudo certbot --apache -d YOUR-SUBDOMAIN.duckdns.org
sudo certbot renew --dry-run
```

Finally, rebuild the frontend with `VITE_API_URL=https://YOUR-SUBDOMAIN.duckdns.org`,
redeploy `dist/`, and add the same HTTPS origin to the Auth0 callback, logout, and web
origin settings.

## Verification checklist

- [ ] Unauthenticated requests to `/api/risk-status` return **401**.
- [ ] A logged-in user can call `/api/risk-status` from the frontend.
- [ ] The backend validates the JWT **issuer** and **audience**.
- [ ] The application is available at `https://YOUR-SUBDOMAIN.duckdns.org`.
- [ ] The browser shows a **valid certificate** and HTTPS lock icon.
- [ ] Port **8080** is **not** reachable from the public internet.
- [ ] `sudo certbot renew --dry-run` succeeds.

## Risk-control reflection

| Control                     | Risk reduced                                 | Evidence                              |
| --------------------------- | -------------------------------------------- | ------------------------------------- |
| Auth0 authentication        | Impersonation and weak credential handling   | Auth0 login flow and user identity    |
| JWT validation at the API   | Unauthorized API access                      | 401 without a valid token             |
| Audience validation         | Token reuse against another API              | API accepts only its intended audience |
| HTTPS with a valid cert     | Credential or token exposure in transit      | Browser certificate details           |
| Apache reverse proxy        | Direct exposure of the application port      | Only ports 80 and 443 are public      |
| Central service logs        | Delayed incident investigation               | `journalctl -u enterprise-risk-api`   |

## Threat model and security notes

**What each control mitigates**

- *Token theft via network sniffing* → TLS on all public traffic.
- *Token replay against a different API* → strict **audience** validation.
- *Token forgery* → RS256 signature verification against Auth0's JWKS.
- *Token overreach over time* → short-lived tokens; short `exp` limits impact.
- *Direct backend exploitation* → API bound to loopback; only 80/443 public.
- *Cross-origin data theft* → CORS restricted to known origins.
- *Credential compromise in the SPA* → no client secret in the browser; PKCE instead.
- *Privilege escalation on the host* → dedicated non-login system user; least privilege.

**Operational rules**

- Never commit `.env.local`, server credentials, Auth0 secrets, or Duck DNS tokens.
- An SPA uses the Auth0 **client ID**, never a client secret.
- Restrict CORS to the exact development and production origins.
- Treat access tokens as sensitive: do not display, log, or manually store them.
- Use least privilege for server accounts and apply OS updates regularly.

## Troubleshooting

| Symptom                                          | Likely cause / fix                                                                 |
| ------------------------------------------------ | ---------------------------------------------------------------------------------- |
| `401` even after logging in                       | Audience mismatch — the SPA must request `VITE_AUTH0_AUDIENCE`.                     |
| CORS error in the browser console                 | Origin not listed in `app.cors.allowed-origins`.                                    |
| `invalid issuer` at startup                       | `issuer-uri` tenant typo or missing trailing slash.                                 |
| Login redirect error                              | Callback/logout/web origin missing from the Auth0 application.                      |
| `502 Bad Gateway` from Apache                     | Backend not running or not listening on `127.0.0.1:8080`.                           |
| SPA routes 404 on refresh                         | Apache rewrite to `index.html` not enabled.                                          |
| Certbot fails validation                          | Port 80 blocked, or DNS not pointing at the server.                                 |

## Evidence

Add your evidence before submitting:

| Deliverable                          | Path                              |
| ------------------------------------ | --------------------------------- |
| Screenshot: authenticated API call   | `docs/authenticated-api-call.png` |
| Screenshot: valid certificate        | `docs/valid-certificate.png`      |

## References

- [Auth0 React SDK quickstart](https://auth0.com/docs/quickstart/spa/react)
- [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [OAuth 2.0 for Browser-Based Apps (PKCE)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
- [Certbot documentation](https://eff-certbot.readthedocs.io/en/stable/)
- [Duck DNS](https://www.duckdns.org/)
- [OWASP API Security Top 10](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
