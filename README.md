# CPEN321_26W1_ProjectName

_Keep this README up to date with the steps required to build and run the frontend and backend (including any scripts, config files, and environment variables). TAs ill follow these instructions._

## Requirements

Install the following before the frontend or backend setup steps:

- [git](https://git-scm.com/install/)


--- 

## Frontend Setup

### Requirements

- [Android Studio](https://developer.android.com/studio) (latest version)
- [Java 17](https://adoptium.net/temurin/releases/?version=17)
- [Android SDK](https://developer.android.com/studio#command-tools) with API level 36+ (Android 16)

### Setup

1. **Open project**: Open the `frontend/` directory in Android Studio
2. **Sync Gradle**: Android Studio will automatically prompt you to sync the project. Click "Sync Now". You can also manually run `cd frontend && ./gradlew build` to trigger the sync and download the necessary dependencies.
3. **Configure Android SDK**: Ensure you have Android SDK 36 installed.
4. **Set up emulator/device**:
   - Create a new AVD (Android Virtual Device) by selecting Pixel 9 as the device and Android Baklava (API level 36) as the system image.
   - Alternatively, connect a physical Android device running Android 16 (API level 36).
5. **Setup app config**: Copy the example file, then fill in local values:
   ```bash
   cp frontend/local.properties.example frontend/local.properties
   ```
   Set at least:
   - `sdk.dir`: path to your Android SDK. Android Studio usually writes this the first time you open `frontend/`. On Mac it is often `sdk.dir=/Users/<username>/Library/Android/sdk`.
   - `API_BASE_URL`: backend URL baked into the APK (rebuild the app after changing it).
     - **Local backend** (plain HTTP, no Caddy): `http://10.0.2.2:3000` for the emulator (`10.0.2.2` is the host machine). For a physical device on the same Wi-Fi, use `http://<your-lan-ip>:3000`.
     - **Server backend** (HTTPS through Caddy): `https://<SERVER_IP>.sslip.io`, e.g. `https://1.2.3.123.sslip.io`.
   - `GOOGLE_CLIENT_ID`: Google OAuth client ID used for Google sign in.
   - `MAPS_API_KEY`: Google Maps API key (Maps SDK for Android enabled) for the location picker.
6. **Firebase config**: put `google-services.json` (Firebase console → Project settings → your Android app) in `frontend/app/`. Without it the app still builds, but push notifications are disabled.


### Build and Run

- **Debug build**: Click the green play button in the toolbar, to compile the code, package a debug APK, and install it on the connected device or running emulator. Alternatively, from the project root, run `./scripts/run-frontend.sh`.
- **Release build**: Go to Build -> Generate Signed App Bundle or APK -> APK. Follow the on-screen instructions to create a key, and select the "release" build variant. You will then have to manually install the generated APK on your device or the running emulator.


### Backend Configuration

Ensure the backend server is running and update the base URL in the app configuration if needed.

---
## Backend Setup

The backend runs with Docker Compose (backend + MySQL, plus Caddy for HTTPS on the server). It uses the `backend/.env` and root `.env` files (see below).

### Environment configuration

From the project root:

```bash
cp backend/.env.example backend/.env
cp .env.example .env
```

Set at least:
- `JWT_SECRET`: a long random string used to sign auth tokens.
- `GOOGLE_CLIENT_ID`: same Google OAuth client ID as the frontend, used to verify sign ins.
- `GOOGLE_APPLICATION_CREDENTIALS=./firebase-service-account.json`: Firebase service account key for sending push notifications. Download it from Firebase console → Project settings → Service accounts → Generate new private key, and save it as `backend/firebase-service-account.json` (it is a secret: never commit it).
- `PORT` (optional): defaults to `3000` if unset.
- In the root `.env`: `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` (database credentials), and `SERVER_IP`: public IP of the server (Caddy serves `https://<SERVER_IP>.sslip.io`). Locally `SERVER_IP` is unused but must still be set to any value.


### Run with Docker Compose

**Requirements:** 
- [Docker](https://docs.docker.com/desktop/setup/install) and [Docker Compose](https://docs.docker.com/desktop/setup/install) v2.24+
- [curl](https://curl.se/download.html)

**Local (no Caddy, plain HTTP)**, from the project root:

```bash
docker compose up --build -d backend
```

This starts the backend and MySQL only. The API is at `http://localhost:3000` (Postman) and `http://10.0.2.2:3000` (emulator), so use the HTTP `API_BASE_URL` in `local.properties`.

**Server (with Caddy, HTTPS)**, from the project root on the server:

```bash
./scripts/run-backend.sh
```

Or run Compose directly:

```bash
docker compose up --build -d
```

This also starts Caddy, which gets an HTTPS certificate for `https://<SERVER_IP>.sslip.io` automatically. Ports 80 and 443 must be open on the server, and the frontend uses the HTTPS `API_BASE_URL`.

**Stop**:

```bash
docker compose down
```

Don't add `-v` unless you mean to wipe data: it deletes the MySQL database and Caddy's certificates (Let's Encrypt only issues 5 certificates per domain per week).

## Additional Setup

_Please specify any other additional setup steps non-specific to either frontend nor backend_