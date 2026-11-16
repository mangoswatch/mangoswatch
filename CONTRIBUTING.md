# Contributing to Mangos Watch

First off, thank you for your interest in contributing to Mangos Watch!

## Code of Conduct & Philosophy

Mangos Watch is a FOSS project dedicated to privacy awareness and transparency. We value thoughtful engineering, clear and concise code, and respect for user privacy.

While we encourage writing code by hand, we understand that AI tooling can assist with repetitive tasks (such as formatting taxonomy data). However, please adhere to these rules:
- **Keep PRs small and specific**: Do not submit giant refactors or unreviewed bulk changes that modify a large portion of the application.
- **Test your changes**: Ensure the app builds successfully (`./gradlew assembleRelease`) and passes unit tests (`./gradlew testDebugUnitTest`).
- **Maintain clean architecture**: Keep UI logic separated from domain and data layers (Jetpack Compose + Koin).

---

## Contributing Taxonomy Endpoints

Taxonomy JSON files categorize known Big Tech endpoints and data collection behaviors. If you discover missing endpoints or misclassifications:

1. Locate or create the corresponding JSON file in `docs/data/<provider>_taxonomy.json`.
2. Format your endpoint definition using the standard schema:

```json
{
  "provider": "PROVIDER_NAME",
  "endpoints": [
    {
      "id": "provider_feature_001",
      "hostname_pattern": "api\\.example\\.com|subdomain\\.example\\.com",
      "display_name": "Feature or Service Name",
      "category": "SEARCH_USAGE",
      "description": "Clear description of what this endpoint does.",
      "data_collected": [
        "User identifier",
        "Request payload"
      ],
      "opt_out_description": "Instructions for user opt-out or mitigation."
    }
  ]
}
```

3. Ensure valid categories from `EndpointTaxonomyCategory` (e.g. `SEARCH_USAGE`, `CLEARCUT_TELEMETRY`, `AUTHENTICATION`, `ANALYTICS`, `UNKNOWN`).

---

## Testing & Verification

Before submitting a Pull Request, run the automated test suite to ensure no regressions were introduced:

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Verify release build & R8 minification
./gradlew assembleRelease
```

---

## How to Submit a Pull Request

1. Fork the repository and create your branch from `main`.
2. Make your changes or additions.
3. Test thoroughly on an emulator or physical device (Min SDK 29 / Target SDK 37).
4. Open a Pull Request with a clear description of what your change accomplishes and why.

Thank you.
