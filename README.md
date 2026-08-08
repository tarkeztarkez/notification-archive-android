# Notification Archive

 ![primo_piano.png](img/primo_piano.png)

<br>

<p float="middle">
  <img src="img/home.png" width="200" />
  <img src="img/graph.png" width="200" />
  <img src="img/search_screen.png" width="200" />
  <img src="img/settings_screen.png" width="200" />
</p>

<h2> Description </h2>

This private fork keeps the original on-device notification history and adds a reliable Android → HTTPS API → PostgreSQL archive. Every sync event is stored in ObjectBox before WorkManager attempts a network request. UUID-based inserts make retries idempotent.

## Archive setup

In **Settings → Private notification archive**, configure:

1. the HTTPS server URL;
2. a bearer API token;
3. a device name and optional Wi-Fi-only mode;
4. additional comma-separated excluded package names;
5. **Sync enabled**.

The API token is stored with Android Keystore-backed encrypted preferences. Banking, authenticator, and password-manager package defaults are excluded from server sync. The existing app blacklist remains available for broader capture exclusions.

The server lives in [`api/`](api/) and exposes:

- `GET /api/v1/health`
- `POST /api/v1/notifications/batch`
- `GET /api/v1/notifications?q=&package_name=&device_id=&event_type=&since=&limit=`
- `GET /api/v1/sync/status`

All notification endpoints require `Authorization: Bearer <API_TOKEN>`. PostgreSQL is reached only by the API and uses idempotent `event_id` inserts. The schema and indexes are applied on API startup.

### Server environment

```text
DATABASE_URL=postgresql://USER:PASSWORD@HOST:5432/notification_archive
API_TOKEN=<at-least-32-character-random-token>
MAX_REQUEST_BYTES=1048576
RATE_LIMIT_PER_MINUTE=120
LOG_LEVEL=INFO
```

Build the API with the root `Dockerfile`. Back up and restore the database with the platform's PostgreSQL backup tooling; test restores into a separate database before relying on them.

<h2>📲 Features</h2>

- **✅ Save Notifications** – Automatically stores notifications received on your Android device.  
- **🔍 Search Notifications** – Easily find past notifications using keywords.  
- **📂 Organized History** – View notifications grouped by apps for better readability.  
- **🕒 Detailed Log** – See timestamps, app names, and notification content.  
- **📊 Graphs** – See some notifications' stats from graph.  
- **🔔 See Deleted Notifications** – See notifications that were accidentally or automatically deleted.  
- **🌓 Dark Mode** – Enjoy a sleek UI with dark mode support.
- **🔒 Secure Access** – Enable password or fingerprint authentication to access the app.

<h2> Requirements </h2>

* Android 7.0+

<h2> Info </h2>

This project use <a href="https://github.com/PhilJay/MPAndroidChart">MPAndroidChart</a> that is licensed under <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache LICENSE-2.0</a>

<h2> Download </h2>

<p align="center">
    <a href="https://apt.izzysoft.de/fdroid/index/apk/com.alftendev.notlistener">
        <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="240" height="80">
    </a>
    <a href="https://play.google.com/store/apps/details?id=com.alftendev.notlistener">
        <img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" width="240" height="80">
    </a>
    <a href="https://github.com/Alfio010/notification-listener-android/releases/latest">
        <img src="img/get-it-on-github.png" alt="Get it on GitHub" width="240" height="80">
    </a>
</p>
