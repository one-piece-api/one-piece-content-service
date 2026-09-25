# one-piece-content-service

Backend applicativo (Spring Boot) per il workflow editoriale dei contenuti One Piece — bozze
private per autore, coda di revisione con presa in carico, pubblicazione con storico
versioni. Primo caso d'uso concreto: **Devil Fruit Type**.

- **Flussi/regole di prodotto:** `docs/user-flows/authentication-and-user-management.md` (repo `one-piece-api`).
- **Piano di implementazione:** `docs/implementation-plan-content.md` (repo `one-piece-api`).
- **Stack:** vedi `docs/technology-stack.md` (repo `one-piece-api`).
- **Decisioni architetturali di questo servizio:** `docs/adr/`.

## Sviluppo locale

Prerequisito: il cluster `kind` locale attivo (`./scripts/setup.sh` nel repo
`onepiece-infrastructure`) e questi due port-forward, in due terminali separati:

```bash
kubectl port-forward svc/keycloak-http -n auth 8080:8080
kubectl port-forward svc/one-piece-postgresql -n data 5433:5432
```

**Da IntelliJ IDEA:** apri il progetto (Gradle lo importa automaticamente),
poi esegui la run configuration generata per `ContentServiceApplication`
(`src/main/java/dev/onepieceapi/contentservice/ContentServiceApplication.java`).
Il profilo Spring `local` è già quello attivo di default
(`application.properties`), quindi non serve impostare nessuna variabile
d'ambiente o VM option: parte già puntando ai port-forward sopra. Il servizio
risponde su `http://localhost:8082/api/content/...`
(`http://localhost:8082/api/content/actuator/health` per verificare che sia su).

**Da riga di comando**, equivalente:

```bash
./gradlew bootRun
```

**Test e formattazione:**

```bash
./gradlew check            # test + verifica formattazione (spring-javaformat)
./gradlew format           # applica la formattazione automaticamente
```

**API: spec OpenAPI, Swagger UI, Bruno** (vedi `docs/adr/0001-openapi-contract-and-bruno-collection.md`):

- Swagger UI: `http://localhost:4180/api/content/swagger-ui.html` (dopo il login; in remoto stesso path sull'IP pubblico).
- Spec committata: `openapi/openapi.yaml`. Dopo una modifica alle API:

  ```bash
  ./gradlew updateOpenApiSpec              # rigenera openapi/openapi.yaml (il test fallisce se è disallineata)
  ./scripts/generate-bruno-collection.sh   # rigenera bruno/ dalla spec (richiede Node.js)
  ```

- Bruno: "Open Collection" su `bruno/`, environment `local` o `remote` (per `remote` copia
  `bruno/.env.example` in `bruno/.env` e imposta `REMOTE_HOST`). Il primo invio apre il
  login Keycloak (client `bruno`, PKCE).

**Verifica nel cluster `kind`** (build immagine reale, non solo il processo
locale): `scripts/deploy-local.sh` (build immagine + `kind load` + rollout
restart del Deployment).
