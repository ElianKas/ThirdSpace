---
inclusion: always
---

# Reusable UI components (Swift)

> Records the Workstream-B UI-component convention. Companion to `30-native-ui-conventions.md`.

Reusable small UI elements are shared, token-styled SwiftUI components in `iosApp/iosApp/AppControls.swift` (+ the sheet building blocks in `SheetComponents.swift`): `AppTextField`/`AppSecureField`/`AppTextEditorField` + `.appFieldSurface()` + `FieldLabel`/`LabeledField` (inputs), `AppButton`(.primary/.secondary/.soft/.destructive/.destructiveQuiet)/`AppButtonLabel`/`AppIconButton` (buttons), `AppListRow`/`AppChip`/`EmptyStateView` (rows/pills/empty states). Views COMPOSE these rather than re-styling raw SwiftUI controls — no ad-hoc text fields/buttons/rows. All styling uses only the `AppColor`/`AppDimens`/`AppTypography`/`AppLayout` tokens — no hardcoded hex or magic numbers in views. Input placeholders use `AppColor.placeholder` (#6B6760, WCAG-AA readable) — never the system default or `textFaint`. `SheetActionButton`/`SheetWideButton`/`AuthPrimaryButton`/`CategoryPill`/`FilterChipView` remain as thin wrappers over the new components. The one documented exception: SwiftUI `.alert` text fields can't be custom-styled.
