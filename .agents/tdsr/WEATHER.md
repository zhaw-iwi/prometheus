# Reusable Weather Integration Spec

## Purpose

Use this document to ask Codex to add location-based current weather to any application. The implementation should let the application use a default location, optionally let a user or tenant save a custom location, fetch current weather from a provider, normalize provider-specific data into a small stable app contract, and cache results so the provider is not called on every page load.

This spec is intentionally framework-agnostic. Codex should translate the contracts below into the target project's language, routing style, persistence layer, and UI patterns.

## Core Behavior

1. Define a default weather location in configuration.
2. Resolve free-text location input through a geocoding provider before saving it.
3. Probe current weather for the resolved coordinates before persisting the preference.
4. Return only normalized weather data to the rest of the app.
5. Cache current weather per rounded coordinate for a short time, commonly 15 minutes.
6. Keep failures explicit with stable error codes.
7. Never save a new location, spend user resources, or replace the active preference unless both geocoding and the weather probe succeed.

## Recommended Provider

Open-Meteo is a good default provider because it supports geocoding and current forecast conditions without an API key for many uses. Check the current Open-Meteo terms before production use, especially for commercial projects.

Reference endpoints:

- Geocoding: `https://geocoding-api.open-meteo.com/v1/search`
- Forecast/current weather: `https://api.open-meteo.com/v1/forecast`
- Docs: `https://open-meteo.com/en/docs` and `https://open-meteo.com/en/docs/geocoding-api`

Suggested geocoding request:

```text
GET https://geocoding-api.open-meteo.com/v1/search?name={query}&count=1&language=en&format=json
```

Persist the first result only after validating that it includes usable `name`, `country`, `latitude`, `longitude`, and `timezone` fields. If the provider omits timezone, use `auto` or the app default.

Suggested current weather request:

```text
GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=weather_code,precipitation,rain,showers,snowfall,cloud_cover,wind_speed_10m,wind_gusts_10m,is_day&timezone={timezone}
```

Use server-side HTTP requests with short timeouts, for example 3 seconds to connect and 5 seconds total. Prefer HTTPS. Only add an HTTP fallback if the target project explicitly accepts that tradeoff for non-secret public weather data.

## Components To Build

### WeatherProvider

Create a small provider interface so the app can use a fake provider in tests and change providers later.

```text
WeatherProvider.geocode(query) -> WeatherLocation | null
WeatherProvider.current(latitude, longitude, timezone) -> RawCurrentWeather
```

`WeatherLocation` should contain:

```json
{
  "name": "Zurich",
  "country": "Switzerland",
  "latitude": 47.3769,
  "longitude": 8.5417,
  "timezone": "Europe/Zurich"
}
```

Provider implementations should convert network, HTTP, and invalid JSON failures into a stable app-level weather error such as `WEATHER_UNAVAILABLE`.

### WeatherRepository

Create a repository or persistence boundary for preferences and cache rows. Keep it separate from controllers and provider HTTP code.

Recommended tables or collections:

```sql
CREATE TABLE weather_preferences (
    owner_id VARCHAR(128) NOT NULL PRIMARY KEY,
    location_query VARCHAR(120) NOT NULL,
    resolved_name VARCHAR(160) NOT NULL,
    country VARCHAR(96) NOT NULL,
    latitude DECIMAL(9, 6) NOT NULL,
    longitude DECIMAL(9, 6) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE weather_cache (
    cache_key VARCHAR(128) NOT NULL PRIMARY KEY,
    weather_json TEXT NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Adapt `owner_id` to the target app: `user_id`, `tenant_id`, organization id, workspace id, or a singleton settings key. If the app has no authenticated owner, skip preferences and use only a configured default location plus cache.

### WeatherService

The service owns the application rules:

- Trim and validate submitted locations, commonly 2 to 120 characters.
- Load the saved location for the owner or fall back to the configured default.
- Resolve a submitted location through `WeatherProvider.geocode`.
- Fetch current weather through `WeatherProvider.current` before saving a preference.
- Normalize raw provider weather into the app contract.
- Save preference and cache in one transaction when the app has a database transaction facility.
- Apply optional product rules, such as a paid setting, entitlement, or quota, inside the same transaction after validation succeeds.
- Do not charge, save, or replace the current preference on geocoding failure or weather probe failure.

### API Or Controller

Expose endpoints that match the target app's routing conventions. A common JSON shape:

```text
GET /api/weather
GET /api/weather-preference
POST /api/weather-preference
```

`GET /api/weather` should return current normalized weather for the saved owner location or default location:

```json
{
  "ok": true,
  "weather": {
    "location": {
      "name": "Zurich",
      "country": "Switzerland",
      "label": "Zurich, Switzerland",
      "latitude": 47.3769,
      "longitude": 8.5417,
      "timezone": "Europe/Zurich"
    },
    "weather": {
      "condition": "rain",
      "intensity": "medium",
      "wind": "calm",
      "is_day": true,
      "cloud_cover": 72,
      "updated_at": "2026-06-20T12:00:00+00:00"
    }
  }
}
```

`GET /api/weather-preference` should return whether the owner has a custom location:

```json
{
  "ok": true,
  "preference": {
    "has_custom_location": true,
    "location": {
      "name": "Zurich",
      "country": "Switzerland",
      "label": "Zurich, Switzerland",
      "latitude": 47.3769,
      "longitude": 8.5417,
      "timezone": "Europe/Zurich"
    }
  }
}
```

`POST /api/weather-preference` should accept:

```json
{
  "location": "Zurich"
}
```

On success, return the saved preference and the freshly normalized weather. If the project has related state that changes, such as billing, quota, or user profile state, return the refreshed state in the same response.

### Client

The client should treat weather as a normalized read model:

- Load `GET /api/weather` on app startup.
- Refresh weather in the background at the same cadence as the cache TTL, commonly every 15 minutes.
- Keep background refresh failures silent unless the user explicitly requested the action.
- Render all effects, badges, text, or theme changes from the normalized contract, not provider-specific fields.
- On location form submit, disable the submit control, call `POST /api/weather-preference`, update the displayed preference and current weather on success, and reload the previous preference after failure.

## Normalized Weather Contract

Use a deliberately small contract so provider changes do not affect the UI.

```json
{
  "condition": "clear",
  "intensity": "none",
  "wind": "calm",
  "is_day": true,
  "cloud_cover": 10,
  "updated_at": "2026-06-20T12:00:00+00:00"
}
```

Recommended values:

- `condition`: `clear`, `cloudy`, `rain`, `snow`, `fog`, `storm`
- `intensity`: `none`, `light`, `medium`, `heavy`
- `wind`: `calm`, `windy`
- `is_day`: boolean
- `cloud_cover`: integer from 0 to 100
- `updated_at`: ISO 8601 timestamp generated by the app when it normalized the provider response

Recommended Open-Meteo normalization:

```text
snow:
  snowfall > 0
  or weather_code in [71, 73, 75, 77, 85, 86]

storm:
  weather_code in [95, 96, 99]

rain:
  rain + showers > 0
  or weather_code in [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82]

fog:
  weather_code in [45, 48]

cloudy:
  weather_code in [2, 3]

clear:
  fallback
```

Intensity should apply only to `rain`, `snow`, and `storm`:

```text
precipitation >= 4.0 -> heavy
precipitation >= 1.0 -> medium
otherwise -> light
```

For precipitation, use the maximum of provider `precipitation`, `rain + showers`, and `snowfall`. If another provider uses different units, convert to millimeters per hour before applying these thresholds or tune the thresholds for that provider.

Wind should be:

```text
max(wind_speed_10m, wind_gusts_10m) >= 25 km/h -> windy
otherwise -> calm
```

If another provider returns miles per hour, meters per second, or knots, convert to km/h or use equivalent thresholds.

## Cache Rules

Use a coordinate cache key so all owners sharing a location can reuse the same weather response:

```text
weather:{latitude rounded to 4 decimals}:{longitude rounded to 4 decimals}
```

Recommended TTL: 900 seconds.

On `GET /api/weather`:

1. Resolve the active location.
2. Read cache by coordinate key and freshness cutoff.
3. Return cached normalized weather if fresh.
4. Otherwise fetch, normalize, save cache, and return it.

Avoid caching provider errors as successful weather. If the provider is unavailable, return a stable error and let the previous UI state remain visible if the client already has one.

## Error Contract

Use stable machine-readable codes and human-readable messages:

```json
{
  "ok": false,
  "error_code": "WEATHER_LOCATION_NOT_FOUND",
  "message": "Could not find weather for that location. Your previous weather location is still being used."
}
```

Recommended codes:

- `AUTH_REQUIRED`: the preference endpoint requires an authenticated owner.
- `INVALID_WEATHER_LOCATION`: missing, non-string, too short, or too long location input.
- `WEATHER_LOCATION_NOT_FOUND`: geocoding returned no usable result.
- `WEATHER_UNAVAILABLE`: provider request failed or returned unusable current weather.
- `WEATHER_STORAGE_FAILED`: database or persistence failure.
- `WEATHER_SETTING_NOT_ALLOWED`: optional project-specific entitlement, quota, billing, or permission failure.

Recommended HTTP status mapping:

- `401` for unauthenticated preference access.
- `422` for invalid input or not-found geocoding.
- `409` for project-specific entitlement, quota, billing, or resource conflicts.
- `503` for provider outage or missing weather infrastructure.
- `500` for unexpected storage failures.

## Security And Reliability

- Do not expose provider credentials to browser code. Keep provider calls server-side unless the provider is intentionally public and the project accepts client-side calls.
- Use short network timeouts.
- Do not follow redirects unless the target project explicitly allows them.
- Do not store raw provider payloads unless needed for debugging or analytics.
- Store only normalized weather and resolved location fields needed by the app.
- Avoid logging precise user-entered locations if they may be sensitive.
- Check provider license and attribution requirements for the target use case.
- Keep the app's default behavior working when no custom location has been saved.

## Test Plan

Use a fake `WeatherProvider` and deterministic clock. Do not make live network calls in automated tests.

Minimum tests:

1. Default location returns normalized weather.
2. The second weather read within the TTL uses cache instead of calling the provider.
3. Valid preference save geocodes, probes current weather, saves normalized location fields, saves cache, and returns normalized weather.
4. Unknown location returns `WEATHER_LOCATION_NOT_FOUND` and does not save a preference.
5. Weather probe failure returns `WEATHER_UNAVAILABLE` and keeps the previous/default location.
6. Optional billing, quota, or entitlement failure does not save a preference.
7. Cache expiry causes a fresh provider call.
8. Normalization covers clear, cloudy, rain, snow, fog, storm, wind, and intensity thresholds.
9. Preference endpoints enforce authentication when the target app has authenticated owners.
10. Storage failures return `WEATHER_STORAGE_FAILED` without leaking secrets.

Manual checks:

- The UI shows the default location before any custom preference exists.
- A valid custom location updates the visible location and weather state immediately.
- Invalid location input leaves the previous location visible.
- Background refresh does not interrupt the user on transient provider failure.

## Codex Implementation Instructions

When applying this spec in another repository:

1. Inspect the existing routing, persistence, config, test, and error-response patterns first.
2. Implement the smallest idiomatic version that fits that codebase.
3. Keep provider HTTP code, service rules, persistence, and UI rendering separated.
4. Add deterministic tests with a fake provider instead of relying on Open-Meteo availability.
5. Update that project's README or agent context only if setup, configuration, API behavior, or user-visible behavior changes.
