# one-piece-content-service

Backend applicativo (Spring Boot) per il workflow editoriale dei contenuti One Piece — catena
lineare di versioni per contenuto, revisione con presa in carico, pubblicazione, ritiro e
ripristino, su un framework generico per entità. Entità: **Devil Fruit Type** e **Devil Fruit**
(con relazione al tipo e immagine) e la struttura narrativa — **Saga, Arco, Volume** (con
copertina PNG), **Capitolo** ed **Episodio** (relazioni verso arco/volume, date per paese) e il
**Bounty** (un manifesto di taglia, chiave `posterName` + `amount`) e l'**Affiliazione**
(ciurma, organizzazione, chiave `romaji`). Espone:

- **Catalogo lingue** (`/languages`): lettura, aggiunta, rimozione di una lingua non in uso.
- **Devil Fruit Type** (`/devil-fruit-types`): lista paginata, catena di versioni,
  contenuto e cronologia di una versione, bozze (crea, modifica, scarta) e tutte le
  transizioni del workflow (invio, presa in carico, approvazione, rifiuto, pubblicazione,
  archiviazione, ritiro, ripristino, nuova versione), con le azioni consentite al chiamante.
- **Devil Fruit** (`/devil-fruits`): le stesse operazioni, più il tipo (relazione verso un
  tipo approvato) e l'immagine PNG (salvataggio multipart, validata e normalizzata). Un frutto
  va online solo con il suo tipo online; un tipo con frutti online non si ritira.
- **Prima apparizione** (frutto, tipo, sottocategoria; ADR-0008): `firstAppearance.chapter`
  e/o `firstAppearance.episode`, verso un capitolo o un episodio approvato
  (`/chapters/linkable`, `/episodes/linkable`). Frutto e tipo la richiedono per la revisione e
  vanno online solo se ciò che indicano è online (`FIRST_APPEARANCE_MISSING`,
  `RELATED_NOT_ONLINE`); un capitolo o un episodio in cui compaiono elementi online non si
  ritira (`ONLINE_ITEMS_LINKED`).
- **Struttura narrativa** (`/sagas`, `/arcs`, `/volumes`, `/chapters`, `/episodes`): stesse
  operazioni, chiave naturale `number`; filtri `?saga=`/`?arc=`/`?volume=`. Un figlio va online
  solo con il padre online (`RELATED_NOT_ONLINE`); un padre con figli online non si ritira
  (`ONLINE_ITEMS_LINKED`).
- **Bounty** (`/bounties`): un elemento per manifesto, chiave composta `posterName` + `amount` (slug
  `monkey-d-luffy-3000000000`; un duplicato nomina entrambi i campi), `condition`, flag `inactive`,
  descrizione per lingua, poster PNG opzionale, prima apparizione. `previous` è il bounty sostituito
  (`/bounties/linkable`): al massimo un sostituto e nessun ciclo, serializzati da un lock
  (`PREVIOUS_ALREADY_REPLACED`, `BOUNTY_CHAIN_CYCLE`). La `validity` (`ACTIVE`, `SUPERSEDED`,
  `INACTIVE`) è derivata, mai salvata; si filtra con `?validity=` e `?condition=` e si ordina per `amount`.
- **Affiliazione** (`/affiliations`): chiave `romaji` (slug `mugiwara-no-ichimi`), nome unico per
  lingua e descrizione, emblema PNG opzionale (quadrato, sfondo trasparente), prima apparizione.
  `kind` (`PIRATE_CREW`, `NAVY`, `WORLD_GOVERNMENT`, `REVOLUTIONARY_ARMY`, `OTHER`): obbligatorio dalla
  prossima sottomissione (le versioni esistenti restano a `null`), filtro `?kind=`.
  `GET /affiliations/linkable` elenca le affiliazioni con una versione approvata, col `kind` (per i
  personaggi); ogni riga della lista dice quanti personaggi online ne fanno parte (`memberCount`,
  senza chi l'ha lasciata). Non si ritira con personaggi online che la indicano (`ONLINE_ITEMS_LINKED`,
  voce `characters`), né va online con un `kind` diverso da `NAVY` mentre personaggi online vi hanno un
  grado (`MARINE_RANK_IN_USE`). Un capitolo o un episodio con affiliazioni online come prima
  apparizione non si ritira (`ONLINE_ITEMS_LINKED`, voce `affiliations`).
- **Specie** (`/species`): la razza di un personaggio. Chiave `romaji` (slug `gyojin`), nome unico per
  lingua e descrizione, immagine PNG opzionale (ritratto 4:5, senza trasparenza richiesta), prima
  apparizione. `GET /species/linkable` elenca le specie con una versione approvata (per i personaggi).
  Un capitolo o un episodio con specie online come prima apparizione non si ritira
  (`ONLINE_ITEMS_LINKED`, voce `species`), né una specie indicata da personaggi online.
- **Personaggio** (`/characters`): chiave `romaji` (slug `nami`), nome unico per lingua e
  descrizione, `japaneseName`, `status`, `gender`, `birthday` (`--07-03`, opzionale), liste ordinate
  di epiteti (per lingua), alias e specie (`/species/linkable`, al massimo 20 epiteti e 20 alias),
  ritratto PNG opzionale (4:5, senza trasparenza), prima apparizione. Appartenenze (`affiliations`,
  lista ordinata: affiliazione, `role` per lingua, `marineRank` solo se l'affiliazione è `NAVY`,
  `former`). Filtri `?characterStatus=`, `?species=`, `?affiliation=` e `?affiliationKind=` (solo
  appartenenze attuali). Va online solo con prima apparizione, ogni specie e ogni affiliazione online
  (`RELATED_NOT_ONLINE`, `speciesIndex`/`affiliationsIndex`) e un grado solo in un'affiliazione online
  `NAVY` (`MARINE_RANK_NOT_ALLOWED`); un capitolo o un episodio con personaggi online non si ritira (voce `characters`).
- **Dashboard** (`/dashboard`): contatori per stato, attività recente del chiamante
  (dall'audit log) e pagine per stato trasversali alle entità.

Visibilità e azioni dipendono dai permessi `content:*` del token, mai dal nome del ruolo.

- **Flussi/regole di prodotto:** `docs/user-flows/content-editorial-workflow.md` (repo `one-piece-api`).
- **Piano di implementazione:** `docs/implementation-plan-content.md` (repo `one-piece-api`).
- **Stack:** vedi `docs/technology-stack.md` (repo `one-piece-api`).
- **Decisioni architetturali di questo servizio:** `docs/adr/`.

## Sviluppo locale

Prerequisito: credenziali per GitHub Packages (da cui arriva `one-piece-exception`), cioè un
[personal access token classic](https://github.com/settings/tokens) con il solo scope
`read:packages`, in `~/.gradle/gradle.properties`:

```properties
gpr.user=<utente GitHub>
gpr.token=<token>
```

Poi il cluster `kind` locale attivo (`./scripts/setup.sh` nel repo
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

- Bruno: "Open Collection" su `bruno/`, environment `dev` (servizio avviato da IntelliJ,
  `localhost:8082`, senza proxy), `local` (cluster `kind` via oauth2-proxy) o `remote` (`app.onepieceapi.dev`, solo dall'IP del proprietario).
  Il primo invio apre il login Keycloak (client `bruno`, PKCE).

**Verifica nel cluster `kind`** (build immagine reale, non solo il processo
locale): `scripts/deploy-local.sh` (build immagine + `kind load` + rollout
restart del Deployment).
