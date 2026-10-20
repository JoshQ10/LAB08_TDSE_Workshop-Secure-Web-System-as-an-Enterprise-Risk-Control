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
│   ├── src/test/java/.../RiskControllerTest.java   # Unit test of the endpoint
│   ├── src/test/java/.../SecurityConfigTest.java   # 401 / 200 / health / deny-all
│   ├── mvnw / mvnw.cmd
│   └── pom.xml
├── frontend/                        # Vite + React SPA
│   ├── src/
│   │   ├── main.jsx                 # Auth0Provider setup
│   │   ├── App.jsx                  # Login, logout, protected API call
│   │   └── index.css
│   ├── .env.example                 # Committable template (no secrets)
│   ├── .env.local                   # Local values (git-ignored)
│   ├── .env.production.local        # Production build values (git-ignored)
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

The **API** represents the protected backend (the *resource*); the **Single Page
Application** represents the browser client. The API and its permission are created
first so that the permission can be explicitly granted to the SPA afterwards.

### 1. Create the API

**Applications → APIs → Create API**

| Field               | Value                         |
| ------------------- | ----------------------------- |
| Name                | `Enterprise Risk Lab API`     |
| Identifier (audience) | `https://enterprise-risk-api` |
| Signing algorithm   | `RS256`                       |

The audience is the unique identifier of the backend that accepts the token. It must
match **exactly** in Auth0, in `VITE_AUTH0_AUDIENCE` (React), and in
`spring.security.oauth2.resourceserver.jwt.audiences` (Spring Boot).

### 2. Define the permission

Open the API → **Permissions** and add:

| Permission  | Description                     |
| ----------- | ------------------------------- |
| `read:risk` | Read the protected risk status  |

The API is the resource; `read:risk` is one allowed action on that resource. The backend
in this lab only requires an *authenticated* token, but declaring the permission is the
first step towards scope-based authorization (e.g. `hasAuthority("SCOPE_read:risk")`).

### 3. Create and configure the browser client

**Applications → Applications → Create Application → Single Page Application**, named
`Enterprise Risk Lab Frontend`. In **Settings** (replace the hostname with yours):

| Setting               | Value                                                        |
| --------------------- | ------------------------------------------------------------ |
| Allowed Callback URLs | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org`    |
| Allowed Logout URLs   | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org`    |
| Allowed Web Origins   | `http://localhost:5173,https://YOUR-SUBDOMAIN.duckdns.org`    |

These tell Auth0 where it may safely redirect to, log out to, and accept requests from.
No wildcards are used.

### 4. Grant the SPA access to the API (User-Delegated Access)

**Applications → Applications → Enterprise Risk Lab Frontend → APIs** → find
`Enterprise Risk Lab API` → **Edit** → under **User-Delegated Access** choose
**Grant Access**, select `read:risk` (or *All*), and save.

> **Why User-Delegated Access?** The SPA acts *on behalf of a signed-in person*. The
> green check under **Client Access** is for machine-to-machine applications and does
> **not** authorize a browser login. Without it Auth0 returns
> `Client "..." is not authorized to access resource server "https://enterprise-risk-api"`.

### 5. Record the client configuration and create a test user

Values the frontend may contain (they are public by design — **never** a client secret):

```
AUTH0_DOMAIN=YOUR_TENANT.us.auth0.com
AUTH0_CLIENT_ID=...
AUTH0_AUDIENCE=https://enterprise-risk-api
```

Create a test account in **Authentication → Database → Users** (`Username-Password-Authentication`
connection) and make sure that connection is enabled for the SPA
(**Applications → Enterprise Risk Lab Frontend → Connections**). Use this account to log in
through the React application.

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
- `callApi()` reads the response as **text first** and only then parses it as JSON. Spring
  Security answers a rejected token with `401` and an **empty body**, so calling
  `response.json()` directly would surface a misleading
  `JSON.parse: unexpected end of data`. Instead the UI shows
  `Request failed: API returned 401`, which points straight at the real cause.

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

Run the automated tests (no Auth0 connection needed — JWTs are mocked):

```bash
./mvnw test
```

`SecurityConfigTest` checks that `/api/risk-status` returns **401** without a token and
**200** with a JWT, that `/actuator/health` is public, and that undeclared paths are denied.

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
To keep it updated, add a cron job on the server (the token stays on the server, never in
the repo):

```bash
mkdir -p ~/duckdns && echo 'echo url="https://www.duckdns.org/update?domains=YOUR-SUBDOMAIN&token=YOUR_DUCKDNS_TOKEN&ip=" | curl -k -o ~/duckdns/duck.log -K -' > ~/duckdns/duck.sh
chmod 700 ~/duckdns/duck.sh
(crontab -l 2>/dev/null; echo "*/5 * * * * ~/duckdns/duck.sh >/dev/null 2>&1") | crontab -
nslookup YOUR-SUBDOMAIN.duckdns.org      # must return the server's public IP
```

Then allow inbound TCP **80** and **443** only — **never** expose 8080 — both in the cloud
provider's security group / firewall and on the host:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

### 2. Server packages (Ubuntu)

```bash
sudo apt update
sudo apt install -y apache2 certbot python3-certbot-apache openjdk-21-jre
```

Harden the host first: change default credentials, create a non-root deployment user, and
enable the firewall.

### 3. Build artifacts (on a trusted machine or CI)

For the production build the frontend must call the API through Apache on the same HTTPS
origin. Create `frontend/.env.production.local` (git-ignored; Vite gives it priority over
`.env.local` when running `npm run build`):

```
VITE_AUTH0_DOMAIN=YOUR_TENANT.us.auth0.com
VITE_AUTH0_CLIENT_ID=YOUR_CLIENT_ID
VITE_AUTH0_AUDIENCE=https://enterprise-risk-api
VITE_API_URL=https://YOUR-SUBDOMAIN.duckdns.org
```

```bash
(cd frontend && npm run build)      # → frontend/dist/
(cd backend  && ./mvnw clean package)   # → backend/target/*.jar
```

Transfer the build outputs to the server over a secure channel (e.g. `scp`/`rsync` over SSH):

```bash
scp backend/target/enterprise-risk-api-0.0.1-SNAPSHOT.jar deploy/* USER@SERVER_IP:~/
scp -r frontend/dist USER@SERVER_IP:~/
```

### 4. Backend service

Edit `deploy/enterprise-risk-api.service` first: set your Auth0 tenant in the issuer URI and
your Duck DNS hostname in `APP_CORS_ALLOWED_ORIGINS`. Compared with the base template, the
unit also sets `SERVER_ADDRESS=127.0.0.1` so the API **only listens on loopback** (defence in
depth on top of the firewall), and adds basic systemd hardening (`NoNewPrivileges`,
`PrivateTmp`, `ProtectSystem`, `ProtectHome`).

```bash
sudo mkdir -p /opt/enterprise-risk-api
sudo cp ~/enterprise-risk-api-0.0.1-SNAPSHOT.jar /opt/enterprise-risk-api/app.jar
sudo useradd --system --no-create-home riskapi
sudo chown -R riskapi:riskapi /opt/enterprise-risk-api

sudo cp ~/enterprise-risk-api.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now enterprise-risk-api
curl http://127.0.0.1:8080/actuator/health          # → {"status":"UP"}
sudo ss -tlnp | grep 8080                           # → 127.0.0.1:8080, not 0.0.0.0
```

### 5. Apache + TLS

Replace `YOUR-SUBDOMAIN` in `deploy/enterprise-risk.conf`, then:

```bash
sudo mkdir -p /var/www/enterprise-risk-frontend
sudo cp -r ~/dist/* /var/www/enterprise-risk-frontend/

sudo a2enmod ssl proxy proxy_http rewrite headers
sudo cp ~/enterprise-risk.conf /etc/apache2/sites-available/
sudo a2ensite enterprise-risk.conf
sudo a2dissite 000-default.conf          # optional: remove the default Apache site
sudo apachectl configtest
sudo systemctl reload apache2

sudo certbot --apache -d YOUR-SUBDOMAIN.duckdns.org
sudo certbot renew --dry-run
```

Certbot creates `/etc/apache2/sites-available/enterprise-risk-le-ssl.conf` (the `*:443`
virtual host with the certificate) and, if you accept the redirect option, turns the `*:80`
host into an HTTP→HTTPS redirect. In the generated `-le-ssl.conf` you can change
`RequestHeader set X-Forwarded-Proto "http"` to `"https"` so the backend knows the original
scheme.

Finally, make sure the frontend was built with `VITE_API_URL=https://YOUR-SUBDOMAIN.duckdns.org`,
redeploy `dist/` if needed, and confirm the same HTTPS origin is in the Auth0 callback,
logout, and web origin settings.

### 6. Verify from outside the server

```bash
curl -i https://YOUR-SUBDOMAIN.duckdns.org/api/risk-status     # → 401 (expected)
curl -m 5 http://YOUR-SUBDOMAIN.duckdns.org:8080/api/risk-status   # → timeout / refused
```

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
| Auth0 redirects back with `Client "..." is not authorized to access resource server` | The SPA lacks **User-Delegated Access** to `https://enterprise-risk-api` (see [Auth0 step 4](#4-grant-the-spa-access-to-the-api-user-delegated-access)). Granting only *Client Access* does not authorize a browser login. |
| `Request failed: JSON.parse: unexpected end of data` | The API returned an empty error response (token rejected). The current `callApi()` reads the body as text and shows the real HTTP status instead. |
| `Request failed: API returned 401`               | Check that `issuer-uri` includes `https://` **and the trailing slash**, that `jwt.audiences` equals exactly `https://enterprise-risk-api`, that the SPA requests that same `VITE_AUTH0_AUDIENCE`, and restart Spring Boot after editing `application.properties`. |
| Login works but the API call fails from `localhost:5173` (CORS error in console) | `http://localhost:5173` missing from `app.cors.allowed-origins`; fix and restart the backend. |
| No user can sign in                               | Create a test user in **Authentication → Database → Users**, or enable a connection for the SPA. Roles are not required for this lab. |
| Login redirect / callback mismatch error          | Callback, logout or web origin missing from the Auth0 application settings.        |
| `502 Bad Gateway` from Apache                     | Backend not running or not listening on `127.0.0.1:8080` (`journalctl -u enterprise-risk-api`). |
| SPA routes 404 on refresh                         | Apache rewrite to `index.html` not enabled (`a2enmod rewrite`).                    |
| Certbot fails validation                          | Port 80 blocked, or Duck DNS not pointing at the server's public IP.               |
| Frontend env changes ignored                      | Vite inlines `VITE_*` at build/dev-start time: restart `npm run dev` or rebuild.   |

## Evidence

proceso crear API: <img width="2505" height="1321" alt="image" src="https://github.com/user-attachments/assets/9f000b9b-e10b-4f32-b836-3980d1a3aae9" />
añadir permisos: <img width="1301" height="1126" alt="image" src="https://github.com/user-attachments/assets/635d9529-9b24-4e7b-8381-5cff152173e4" />
creacion nueva aplicacion: <img width="1175" height="900" alt="image" src="https://github.com/user-attachments/assets/123f96c4-ef06-4d25-b8cb-38cb2acdf72a" />
dominio nuevo añadido: <img width="1326" height="425" alt="image" src="https://github.com/user-attachments/assets/23f435f2-0ff0-4295-9d11-5cca0574de87" />
prueba unauthenticated request API: <img width="1337" height="261" alt="image" src="https://github.com/user-attachments/assets/d82f63db-5340-4946-a670-a3af223522d2" />
prueba login funcional: <img width="2182" height="1087" alt="image" src="https://github.com/user-attachments/assets/0fb6823a-9db2-477b-a817-d9d9da060f12" />
prueba call protected API despues del login: <img width="601" height="438" alt="image" src="https://github.com/user-attachments/assets/1954b932-b630-4dfb-b8cd-6ff082f886ed" />
firewall activo ubuntu: <img width="617" height="576" alt="image" src="https://github.com/user-attachments/assets/fd39266c-e2c7-4015-b2e6-b9ab08faccae" />
prueba pagina funcional ubuntu(DNS, firewall funcionales): <img width="2171" height="1344" alt="image" src="https://github.com/user-attachments/assets/4ccdb3b0-01de-420b-8ca7-18db2b9b83c6" />
terminal ubuntu: <img width="728" height="400" alt="image" src="https://github.com/user-attachments/assets/00e17660-889d-4782-b366-3069f7cf1f35" />
prueba registr del correo: <img width="675" height="173" alt="image" src="https://github.com/user-attachments/assets/df6f9b21-00d5-4dee-b76e-c2cc844d8620" />


captura obligatoria1: <img width="1763" height="1007" alt="image" src="https://github.com/user-attachments/assets/87008c9b-4ee2-42ce-8199-e18bd6a426db" />
captira obligatoria2, certificado valido: <img width="1475" height="870" alt="image" src="https://github.com/user-attachments/assets/8402355a-f46d-4877-8d75-575071f22305" />




Add your evidence before submitting:

| Deliverable                          | Path                              |
| ------------------------------------ | --------------------------------- |
| Screenshot: authenticated API call   | `docs/authenticated-api-call.png` |
| Screenshot: valid certificate        | `docs/valid-certificate.png`      |

<!-- Uncomment once the images exist:
![Authenticated API call](docs/authenticated-api-call.png)
![Valid certificate](docs/valid-certificate.png)
-->

The certificate can also be evidenced with command output:

```bash
openssl s_client -connect YOUR-SUBDOMAIN.duckdns.org:443 -servername YOUR-SUBDOMAIN.duckdns.org </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
sudo certbot certificates
```

## References

- [Auth0 React SDK quickstart](https://auth0.com/docs/quickstart/spa/react)
- [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [OAuth 2.0 for Browser-Based Apps (PKCE)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
- [Certbot documentation](https://eff-certbot.readthedocs.io/en/stable/)
- [Duck DNS](https://www.duckdns.org/)
- [OWASP API Security Top 10](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
