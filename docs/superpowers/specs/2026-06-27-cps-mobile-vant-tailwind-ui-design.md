# CPS Mobile Vant Tailwind UI Design

## Scope

Refine the existing Vue3 mobile app UI with Vant components and TailwindCSS utility styling. The backend contract and workflow behavior stay unchanged.

## Visual Direction

The palette should match CPS inspection, quality issue tracking, and factory workflow closure. The UI should feel youthful and fresh without looking like a consumer marketing app.

Color tokens:

```text
Primary teal: #14B8A6
Trust blue: #2563EB
Warning orange: #F97316
Success green: #22C55E
Danger red: #EF4444
Soft teal background: #F0FDFA
Neutral app background: #F8FAFC
Primary text: #0F172A
Secondary text: #64748B
Border: #CCFBF1
```

## Component Direction

Use Vant for mobile-native controls:

```text
NavBar, Button, Field, Cell, CellGroup, Tabs, Tab, Tag, Uploader,
Form, Picker/Popup where practical, Loading, Toast, Empty, Divider
```

Use TailwindCSS for page layout, spacing, responsive constraints, color rhythm, and small visual composition.

## Page Direction

`IssueCreateView` should feel like a guided mobile report form: photo upload first, then location, category, AI suggestion, description, feedback handler, and submit.

`IssueListView` should use Vant Tabs and tappable issue cards with status tags, overdue warning, location/category metadata, and current handler.

`IssueDetailView` should use section cards for basic info, images, AI suggestions, workflow action form, and flow timeline. Action forms remain status-driven.

## UX Requirements

Touch targets must stay at least 44px high. Required workflow actions must remain controlled by backend `availableActions`. Color must not be the only indicator for overdue, closed, reject, or success states.
