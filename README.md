# one-piece-content-service

Backend applicativo (Spring Boot) per il workflow editoriale dei contenuti One Piece — bozze
private per autore, coda di revisione con presa in carico, pubblicazione con storico
versioni. Primo caso d'uso concreto: **Devil Fruit Type**.

- **Flussi/regole di prodotto:** `docs/user-flows/authentication-and-user-management.md` (repo `one-piece-api`).
- **Piano di implementazione:** `docs/implementation-plan-content.md` (repo `one-piece-api`).
- **Stack:** vedi `docs/technology-stack.md` (repo `one-piece-api`).

## Sviluppo locale

```bash
./gradlew bootRun          # richiede Keycloak + content-postgresql raggiungibili via port-forward
./gradlew check            # test + checkstyle + format
./gradlew spotlessApply    # o l'equivalente format task, se le differenze di formattazione bloccano la build
```

Deploy sul cluster `kind` locale: `scripts/deploy-local.sh` (build immagine + `kind load` + rollout).
