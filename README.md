# Cloud Auth Platform

This repository combines three related projects:

| Directory | Description |
| --- | --- |
| `backend/` | Spring Cloud authorization and account services |
| `client-oauth/` | Java OAuth/SSO client SDK |
| `frontend/` | Vue administration console |

## Build

- Backend: run `mvn -DskipTests package` from `backend/`.
- OAuth client: run `mvn -DskipTests package` from `client-oauth/`.
- Frontend: copy `frontend/.env.example` to a local Vite environment file, install the dependencies, and run `npm run dev` from `frontend/`.

Configure database, Nacos, OAuth and other deployment credentials through environment-specific configuration. Do not commit credentials, private keys, database exports, or environment files.

The backend and frontend are based on the RuoYi ecosystem; retain the upstream notices and licenses included with those components.
