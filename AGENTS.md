# AGENTS.md

## Project

This is a local-first Android app for monthly cash flow tracking.

App name: MiFlujo  
Subtitle: Flujo de efectivo mensual  
Repository: MiFlujo-android  
Package name: com.carlos.miflujo

Primary user: non-technical adult user.  
Main goal: register income and expenses quickly and generate a monthly cash flow summary.

Core product principle:

```text
Open the app, register money, see how the month is going, close the app.
```

## Current state

MiFlujo already has a functional MVP released and tested on a physical device.

Current work is post-MVP maintenance and incremental improvement.

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
- Show totals separated by currency.
- Edit movements.
- Delete movements.
- Persist data locally with Room.

The app must NOT include unless explicitly requested and planned:

- Login.
- Cloud sync.
- Backend services.
- Bank integrations.
- OCR.
- Invoicing.
- AI features inside the app.
- Advanced accounting modules.
- Automatic currency conversion.
- Exchange rate handling.
- Mixed-currency net totals.

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

The main action should be an extended floating action button:

```text
+ Agregar
```

Do not design the app like a complex accounting tool.

Do not add visual complexity unless it directly improves real user experience.

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
        ├── home/
        ├── movement/
        ├── report/
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

## Code rules

- Do not add features outside the current issue unless explicitly requested.
- Prefer small, localized changes.
- Do not introduce networking.
- Do not introduce authentication.
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

- `.jks`
- `.keystore`
- `local.properties`
- debug APKs
- secrets
- tokens

## Validation rules

For code changes, run:

```bash
./gradlew :app:assembleDebug
```

For changes affecting business rules, also run relevant tests or add tests when possible.

For UI changes, validate manually on a physical device when possible.

For release changes, test update behavior without uninstalling the previous release.

## Documentation rules

Before coding, read:

- `AGENTS.md`
- `docs/product-spec.md`
- `docs/mvp-scope.md`
- `docs/business-rules.md`
- `docs/data-model.md`
- `docs/ui-design.md`
- `docs/decisions.md`

For release work, also read:

- `docs/release-process.md`

When a product, architecture, workflow, visual identity, or release decision changes, update `docs/decisions.md`.

When the release process changes, update `docs/release-process.md`.
