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

## Non-negotiable MVP scope

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

The app must NOT include in the MVP:

- Login.
- Cloud sync.
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

## Architecture

Use:

- Kotlin.
- Jetpack Compose.
- Material Design 3.
- Room.
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

- Do not add features outside the MVP unless explicitly requested.
- Do not introduce networking.
- Do not introduce authentication.
- Do not introduce automatic currency conversion.
- Do not mix currency totals.
- Do not hard-code UI text deep inside business logic.
- Prefer small, testable functions.
- Keep financial logic easy to review.
- Add tests for business rules when possible.

## Documentation rules

Before coding, read:

- `AGENTS.md`
- `docs/product-spec.md`
- `docs/mvp-scope.md`
- `docs/business-rules.md`
- `docs/data-model.md`
- `docs/ui-design.md`
- `docs/decisions.md`

When a product or architecture decision changes, update `docs/decisions.md`.
