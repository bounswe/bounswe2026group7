# Mobile Final Demo Checklist

## Objective

This checklist summarizes the mobile app flows that should be validated before the Final Milestone demo and records the currently known limitations that should not surprise the team during the presentation.

## Demo-Ready Flows

### Authentication and session flow

- [ ] Login works with demo mentor and mentee accounts
- [ ] Logout returns the user to the login screen
- [ ] Session persists after reopening the app
- [ ] Forgot password screen submits successfully
- [ ] Reset password screen validates token and updates password

### Mentor discovery and request flow

- [ ] Mentee can browse mentor cards from the Explore screen
- [ ] Mentee can open mentor public profile
- [ ] Mentee can submit a mentorship request
- [ ] Mentor can open incoming request details
- [ ] Mentor can accept or reject a request

### Active mentorship flow

- [ ] Active mentorship cards appear for the correct role
- [ ] User can open active mentorship details
- [ ] Shared goal is visible in connection details
- [ ] Shared goal can be updated from the connection screen

### Messaging flow

- [ ] Mentor-mentee conversation list loads from backend
- [ ] Mentor-mentee message history opens correctly
- [ ] Users can send text messages
- [ ] Users can attach and open files in chat
- [ ] Unread messaging state is visible
- [ ] Mentor-to-mentor messaging works for mentor users

### Notifications

- [ ] Notification badge appears on the home screen when unread items exist
- [ ] Notifications screen lists unread and read items correctly
- [ ] Individual notifications can be marked as read
- [ ] Mark-all-read flow works

### Availability

- [ ] Mentor availability screen loads existing data
- [ ] Mentor can save recurring availability
- [ ] Mentee availability screen loads and saves correctly

## Manual Validation Notes

These checks should be repeated with final demo accounts before the presentation:

- [ ] Fresh session validation after deleting local auth/session data
- [ ] Mentor demo account validation
- [ ] Mentee demo account validation
- [ ] One complete end-to-end request lifecycle from mentee request to mentor response
- [ ] One complete chat validation with a real message send
- [ ] One notification validation after a user-facing action

## Known Limitations

The following areas are not considered demo-ready and should not be relied on during the final presentation unless they are completed separately:

- `meetings-sessions` still uses mock/static mobile data rather than real scheduling endpoints
- `task-tracker` still uses mock/static task content rather than backend-driven task/submission/feedback flows
- mobile mentorship cancellation/end flow is not connected to a completed backend lifecycle endpoint
- mobile reporting flow is not connected to a completed reporting backend
- mobile social feed/blog features are not part of the validated final mobile demo scope
- native push notifications are not part of the validated mobile demo scope

## Recommended Demo Path

Use the mobile demo primarily for the flows that are already integrated and stable:

1. Login as mentee
2. Browse mentors from Explore
3. Open a mentor profile
4. Submit a mentorship request
5. Show notifications or request state
6. Open messages and show a real conversation
7. Switch to mentor account if needed for mentor-only messaging or request handling

## References

- Mobile authentication and recovery screens in `mobile-app/app`
- Mobile messaging flow in `mobile-app/app/(tabs)/messages.tsx`
- Notifications flow in `mobile-app/app/notifications.tsx`
- Mentorship request flow in `mobile-app/app/mentorship-requests.tsx`
- Active mentorship flow in `mobile-app/app/connection-profile.tsx`
