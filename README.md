\# BudgetApp



A feature-rich Android personal finance and budgeting app with offline-first architecture.



\## Features

\- Track income \& expenses, monthly budget limits

\- Savings goals with progress tracking

\- Bill reminders and recurring transactions

\- Financial health score + automated insights

\- 5-channel notification system (budget alerts, bill reminders, goal reminders, spending summaries, subscription reminders)

\- PIN + biometric lock, privacy-mode balance masking

\- Export to CSV, Excel, PDF



\## Architecture

\- \*\*Language:\*\* Kotlin

\- \*\*UI:\*\* Jetpack Compose (Material 3)

\- \*\*Pattern:\*\* MVVM + Unidirectional Data Flow (StateFlow)

\- \*\*Persistence:\*\* Room v7 with TypeConverters

\- \*\*Background:\*\* WorkManager (SummaryWorker, ReminderWorker)

\- \*\*Async:\*\* Coroutines + Flow



\## Status

In active use by test users.

