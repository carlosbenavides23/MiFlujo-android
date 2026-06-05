# AGENTS.md

## Project

This is a local-first Android app for monthly cash flow tracking.

App name: MiFlujo  
Subtitle: Flujo de efectivo mensual  
Repository: MiFlujo-android  
Package name: com.carlos.miflujo

Primary user: non-technical adult user.  
Main goal: register income and expenses quickly, generate a monthly cash flow summary, export reports, and protect data with local backups.

Core product principle:

```text
Open the app, register money, see how the month is going, close the app.
```

## Current state

MiFlujo already has a functional MVP released and tested on a physical device.

The current stable release is:

```text
v0.3.0
```

`v0.3.0` includes monthly report PDF export, a Settings screen, local JSON backup export, and local JSON backup restore.

Current planned work:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

`v0.3.5` is a technical baseline phase before Firebase Cloud Sync. It must audit, document, clean up, and prepare the project before cloud sync. It must not implement Firebase yet.

Post-MVP work must be guided by real user feedback, small issues, and low-risk changes.

Do not treat the project as a blank-slate app or redesign it from scratch.

## Product scope

The app must support:

- Register income.
- Register expenses.
- Select currency per movement:
  - C$
  - US$
- Classify expenses as:
  - Fixed cost: water, electricity, internet.
  - Maintenance.
  - Other.
- Add an optional but recommended detail/description to each movement.
- Generate a monthly cash flow report.
- Export the monthly report as PDF.
- Show totals separated by currency.
- Edit movements.
- Delete movements.
- Persist data locally with Room.
- Create local JSON backups.
- Save or share local JSON backups.
- Restore local JSON backups after full validation and explicit confirmation.

The app must NOT include unless explicitly requested, designed, and planned:

- Login.
- Cloud sync.
- Firebase.
- Firestore.
- Backend services.
- Bank integrations.
- OCR.
- Invoicing.
- AI features inside the app.
- Advanced accounting modules.
- Automatic currency conversion.
- Exchange rate handling.
- Mixed-currency net totals.
- Merge-based restore.
- Multi-device sync.

## Currency rules

The app supports córdobas and dollars.

Currencies must be handled separately.

Do not convert between currencies.

Do not calculate a single combined total across C$ and US$.

Reports must show:

- C$ income, C$ expenses, C$ net cash flow.
- US$ income, US$ expenses, US$ net cash flow.

## Money precision

Do not store money as `Double` or `Float`.

Store money as integer minor units using `Long`.

Examples:

```text
C$ 1,800.50 -> 180050
US$ 100.00 -> 10000
```

The user can type and see normal decimal amounts, but internal calculations must use integer values.

Formatting changes must only affect visible UI text, not stored money values.

## Design direction

Use Jetpack Compose and Material Design 3.

The UI must be:

- Simple.
- Readable.
- Fast.
- Clear for a non-technical user.
- Focused on the current month.
- Safe for data-changing operations.

The main action should be an extended floating action button:

```text
+ Agregar
```

Do not design the app like a complex accounting tool.

Do not add visual complexity unless it directly improves real user experience.

Home is for a quick overview.

Report is for detailed monthly analysis and PDF export.

Settings is for backup, restore, information, changelog, and future technical options.

## Visual color rules

MiFlujo may use Android dynamic color for the general app identity when available.

Dynamic color can apply to:

- FAB.
- Buttons.
- Navigation.
- Selected states.
- Chips.
- General Material components.

Financial meaning must not depend entirely on dynamic color.

Use stable semantic colors for financial values:

```text
Soft green -> income, positive amounts, positive net flow
Soft red   -> expenses, negative amounts, negative net flow
```

Do not turn the entire app green or red.

App identity color and financial meaning color are separate concepts.

Destructive actions such as restore confirmation or delete confirmation must be clearly communicated.

## Architecture

Use:

- Kotlin.
- Jetpack Compose.
- Material Design 3.
- Room.
- Coroutines / Flow.
- MVVM.
- Repository Pattern.

Conceptual flow:

```text
UI
↓
ViewModel
↓
Repository
↓
Room Database
```

The UI must not access Room DAOs directly.

Reports must be calculated from stored movements, not stored manually as independent report records.

Room/local is the primary source of truth in the current app.

Future Firebase Cloud Sync must be optional and layered on top of the local-first model. It must not make the app unusable offline.

## Suggested package structure

```text
app/
└── src/main/java/com/carlos/miflujo/
    ├── data/
    │   ├── local/
    │   ├── model/
    │   └── repository/
    ├── domain/
    │   ├── model/
    │   └── usecase/
    └── ui/
        ├── backup/
        ├── home/
        ├── movement/
        ├── report/
        ├── settings/
        ├── theme/
        └── components/
```

## Business rules

- Income increases the monthly income total for its currency.
- Expense increases the monthly expense total for its currency.
- Monthly net cash flow is calculated per currency:
  - net C$ = income C$ - expenses C$
  - net US$ = income US$ - expenses US$
- A movement belongs to a month based on its date.
- Amounts must be positive.
- Currency is required.
- Movement type is required.
- Expense category is required.
- Fixed cost subcategory is required when the category is fixed cost.
- Detail is recommended but not required.
- Backup JSON must be validated before restore.
- Restore must not modify data until the user confirms explicitly.
- Local restore currently replaces all movements inside a Room transaction.

## PDF, backup, and restore rules

PDF export and JSON backup are different features.

PDF:

- Human-readable monthly report.
- Used for sharing or reviewing.
- Must not be treated as a technical backup.

JSON backup:

- Machine-readable local backup.
- Used for restoring movements.
- Must include schema version and app identifier.
- Must preserve real movement fields.

Restore:

- Must validate the entire file before confirmation.
- Must reject invalid backups without modifying data.
- Must require explicit confirmation.
- Current local restore replaces all movements.
- Current local restore preserves positive unique IDs from the backup.
- Future cloud-safe restore behavior must be documented before Firebase sync.

## Code rules

- Do not add features outside the current issue unless explicitly requested.
- Prefer small, localized changes.
- Do not introduce networking.
- Do not introduce authentication.
- Do not introduce Firebase.
- Do not introduce Firestore.
- Do not introduce cloud sync.
- Do not introduce automatic currency conversion.
- Do not mix currency totals.
- Do not hard-code UI text deep inside business logic.
- Prefer small, testable functions.
- Keep financial logic easy to review.
- Add tests for business rules when possible.
- Do not modify data/domain/persistence layers for UI-only issues.
- Do not change Room schema without planning a migration.
- Do not introduce new dependencies unless explicitly allowed.
- Do not change backup schema without updating tests and docs.
- Do not assume local Room IDs are valid cloud identities.

## Pre-Firebase v0.3.5 rules

For `v0.3.5`, the goal is to reduce risk before Firebase Cloud Sync.

Allowed work:

- Documentation updates.
- Technical audit documentation.
- Cloud sync strategy documentation.
- Restore cloud-safe behavior documentation.
- Android Auto Backup policy documentation.
- Room schema export preparation.
- Centralizing movement validation.
- Adding UUID only after strategy and migration plan are documented.
- Backup schema v2 only after UUID strategy is documented.

Forbidden in `v0.3.5` unless explicitly approved later:

- Firebase setup.
- Login.
- Firestore collections.
- Remote writes.
- Remote reads.
- Sync engine.
- Conflict resolution implementation.
- Large UI redesign.
- PRs mixing unrelated issues.

Recommended order:

1. `#96` docs: guardar auditoría técnica pre-Firebase.
2. `#97` docs: definir estrategia de identidad y sincronización cloud.
3. `#102` docs: definir comportamiento cloud-safe para restauración de backups.
4. `#103` chore: definir política de Android Auto Backup para datos financieros.
5. `#98` chore: habilitar Room schema export antes de migraciones sync.
6. `#101` refactor: centralizar validación de reglas de negocio de movimientos.
7. `#99` feature: agregar UUID estable a movimientos.
8. `#100` feature: crear backup schema v2 con UUID.

## Post-MVP workflow

Work should follow this flow:

```text
Real feedback -> GitHub issue -> small branch -> implementation -> review -> PR -> dev -> main -> release
```

Branch strategy:

```text
main      -> stable releases
dev       -> integration branch
feature/* -> new functionality
fix/*     -> bug fixes
style/*   -> UI or visual changes
chore/*   -> maintenance tasks
docs/*    -> documentation
```

Prefer one issue per branch.

Avoid mixing unrelated bugfixes, UI changes, and features in the same branch.

## Release rules

Before every release APK:

- Merge tested changes into `main`.
- Update `versionCode`.
- Update `versionName`.
- Generate the signed APK from `main`.
- Use the same release keystore as previous releases.
- Test updating over the previous release without uninstalling.
- Confirm Room data persists after update.
- Create a Git tag matching the release version.
- Publish a GitHub Release with the APK asset.

Never commit or upload:

- `.jks`.
- `.keystore`.
- `local.properties`.
- debug APKs.
- secrets.
- tokens.

## Validation rules

For documentation-only changes:

- Review the rendered Markdown.
- Check that the docs do not contradict current code or release state.
- No Gradle command is required unless code changed.

For code changes, run:

```bash
./gradlew :app:assembleDebug
```

For changes affecting business rules, also run relevant tests or add tests when possible.

For backup, restore, or report logic, run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

For UI changes, validate manually on a physical device when possible.

For release changes, test update behavior without uninstalling the previous release.

## Documentation rules

Before coding, read:

- `AGENTS.md`.
- `docs/product-spec.md`.
- `docs/mvp-scope.md`.
- `docs/business-rules.md`.
- `docs/data-model.md`.
- `docs/ui-design.md`.
- `docs/decisions.md`.

For release work, also read:

- `docs/release-process.md`.

For pre-Firebase work, also read:

- `docs/audit-pre-firebase.md`.

When a product, architecture, workflow, visual identity, data model, backup/restore behavior, or release decision changes, update `docs/decisions.md`.

When the release process changes, update `docs/release-process.md`.
