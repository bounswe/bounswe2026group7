# Frontend-Mobile Feature Parity Tracker

## Purpose

Living checklist tracking feature parity between web (frontend) and mobile apps. This is a coordination document for parity gaps; implementation work happens in feature issues.

## Status Legend

- ✅ Implemented and connected
- 🟡 Partial, mock, or limited
- ❌ Missing
- ⚪ Unknown / needs verification

## Parity Checklist

| Feature | Web | Mobile | Notes | Linked Issue |
| --- | --- | --- | --- | --- |
| Auth (login/register) | ✅ | ✅ | Screens exist on both; validate flows. |  |
| Auth (forgot/reset) | ✅ | ✅ | Mobile flow tracked in mobile demo checklist. |  |
| Email verification | ✅ | ✅ | Verify email screens exist on both. |  |
| Profile (self) | ✅ | ✅ | Web: Profile page; Mobile: Profile tab. |  |
| Profile (public/mentor) | ✅ | ✅ | Web: User profile; Mobile: mentor public profile. |  |
| Recommendations / Explore | ✅ | ✅ | Web: Explore; Mobile: Explore tab. |  |
| Messaging | ✅ | ✅ | Web: Messages; Mobile: Messages tab. |  |
| Notifications | ✅ | ✅ | Web: Notifications; Mobile: notifications screen. |  |
| Scheduling / Meetings | ✅ | 🟡 | Mobile uses mock/static data per checklist. | #261 |
| Availability | ✅ | ✅ | Web: Availability; Mobile: availability-scheduling. |  |
| Tasks | ✅ | 🟡 | Mobile task-tracker uses mock/static content per checklist. | #263 |
| Milestones | ✅ | ✅ | Mobile milestones screen exists; verify backend. |  |
| Blogs | ✅ | 🟡 | Mobile blog flow not in final demo scope. | #365, #366 |
| Social feed | ✅ | 🟡 | Mobile social feed not in final demo scope. | TBD |
| Reporting | ✅ | ❌ | Mobile reporting not connected per checklist. | #428 |
| Admin | ✅ | ❌ | No mobile admin surface found. | #429 |

## Sources

- Mobile demo coverage and known limitations: [docs/mobile-final-demo-checklist.md](mobile-final-demo-checklist.md)
- Web pages: [frontend/src/pages](../frontend/src/pages)
- Mobile screens: [mobile-app/app](../mobile-app/app)
