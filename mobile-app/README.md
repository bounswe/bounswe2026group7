# MentorMatch Mobile App

React Native / Expo application for the MentorMatch platform (CMPE 354 Group 7).

---

## Prerequisites

- Node.js 18+
- npm 9+
- [Expo Go](https://expo.dev/go) installed on your physical device **or** an Android/iOS emulator

---

## Development Setup

### 1. Install dependencies

```bash
cd mobile-app
npm install
```

### 2. Configure environment

```bash
cp .env.example .env
```

Open `.env` and set `EXPO_PUBLIC_API_URL` to point at the backend:

| Scenario | Value |
|---|---|
| Android emulator (AVD) | `http://10.0.2.2:8080` |
| Physical device on same Wi-Fi | `http://<your-machine-ip>:8080` |
| Production server | `http://<server-ip-or-domain>:8080` |

> **Why not `localhost`?** On a physical device or Android emulator, `localhost` refers to the device itself, not your development machine. Use the machine's LAN IP instead (e.g. `192.168.x.x`).

### 3. Start the backend

The mobile app requires the backend to be running. From the project root:

```bash
cp .env.example .env   # configure backend env vars
docker compose up --build
```

Backend will be available at `http://localhost:8080`.

### 4. Start the Expo dev server

```bash
npx expo start
```

- Scan the QR code with **Expo Go** on your phone, or
- Press `a` to open on a connected Android device/emulator, or
- Press `i` to open on an iOS simulator (macOS only).

---

## Production Build (APK)

### Prerequisites

- [EAS CLI](https://docs.expo.dev/build/setup/): `npm install -g eas-cli`
- An [Expo account](https://expo.dev/signup)

### Build

```bash
cd mobile-app
eas build -p android --profile production
```

This submits a cloud build on Expo's servers and produces a signed `.apk` / `.aab`. The download link is shown in the EAS dashboard when the build completes.

### Local APK (without EAS)

If you have the Android SDK and Java 17+ installed:

```bash
npx expo run:android --variant release
```

The APK will be output to `android/app/build/outputs/apk/release/app-release.apk`.

---

## Network Configuration Summary

The app reads `EXPO_PUBLIC_API_URL` at bundle time. All API calls go to `$EXPO_PUBLIC_API_URL/api`.

- **Do not** use `localhost` — it resolves to the device, not the host machine.
- **Android emulator**: the special alias `10.0.2.2` maps to the host machine's loopback.
- **Physical device**: find your machine's IP with `ipconfig` (Windows) or `ifconfig` (macOS/Linux) and use that.
- **Production**: set the variable to the deployed server's address before building the APK.

---

## Default Credentials

| Role | Username / Email | Password |
|---|---|---|
| Admin | admin@group7.com | Admin1234! |
| Mentor | mentor@group7.com | Mentor1234! |
| Mentee | mentee@group7.com | Mentee1234! |

---

## Running Tests

```bash
cd mobile-app
npm test
```
