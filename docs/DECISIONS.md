# Architectural Decisions

- **Decision 1**: Use unit tests with mock/stub Context or temporary cache directories for testing repository data loading and regex matching to avoid heavy instrumentation test overhead while ensuring high coverage.
- **Decision 2**: Initially used an internal test constructor for `EndpointTaxonomyRepositoryImpl`.
- **Decision 3**: Added Robolectric 4.18 to test dependencies and refactored `EndpointTaxonomyRepositoryImpl` back to taking a standard `Context` and loading assets via `AssetManager`, keeping production code clean and idiomatic while leveraging Robolectric for context/asset unit testing.
- **Decision 4**: Added `kotlinx-coroutines-test` to test dependencies to enable deterministic testing of ViewModel coroutine scopes and StateFlow emissions.
- **Decision 5**: Used Koin `startKoin` / `stopKoin` in `StreamTrafficProcessorTest` to satisfy `KoinComponent` injection of `StreamsRepository` during unit testing.
- **Decision 6**: Configured `isMinifyEnabled = true`, `isShrinkResources = true`, and linked `proguard-android-optimize.txt` + `src/main/keepRules/rules.keep` in `app/build.gradle.kts`. Verified release APK compilation (`assembleRelease`) succeeded with R8 minification and resource shrinking active.
- **Decision 7**: Expanded `CONTRIBUTING.md` with a step-by-step taxonomy contribution guide and unit testing standards, and verified GPLv3 compliance and third-party acknowledgements for `hev-socks5-tunnel`.
- **Decision 8**: Created root `NOTICE` file containing explicit MIT License copyright attribution for `hev-socks5-tunnel.aar` to ensure 100% open-source license compliance (MIT + GPLv3 compatibility).
- **Decision 9**: Implemented a thread-safe `BufferPool` using `ArrayBlockingQueue<ByteArray>` in `TrafficAnalyzer` to recycle 32KB packet buffers across worker threads and VPN restart cycles, reducing GC pressure during continuous traffic inspection.
- **Decision 10**: Added a thread-safe 1024-entry LRU cache (`LinkedHashMap` with access-order eviction and sentinel object for non-matches) in `EndpointTaxonomyRepositoryImpl` to make repeated hostname taxonomy lookups O(1).
- **Decision 11**: Overrode `onRevoke()` in `VpnTrafficManager`, wrapped individual resource releases in `stopVpn()` with `runCatching {}`, and added try-catch protection to socket protection callbacks to ensure robust VPN lifecycle resilience and prevent resource leaks.
- **Decision 12**: Implemented size-bounded memory eviction in `StreamsRepositoryImpl` executed on `updateStream` write calls when reaching capacity limits to prevent `_streams` from growing unboundedly during long VPN inspection sessions.
- **Decision 13**: Documented BigTech IP range extraction methodology and safety mechanisms in `scripts/bigtech_ranges.py`:
  - **Amazon "owned by"**: Includes prefixes tagged with `service` `AMAZON` and `GLOBAL` excluding customer-facing services (`EC2`, `EC2_INSTANCE_CONNECT`, `CLOUDFRONT`, `S3`, `ROUTE53_HEALTHCHECKS`).
  - **Microsoft "owned by"**: Aggregates M365 endpoints (`https://endpoints.office.com`) and Azure first-party services (`AzureActiveDirectory`, `AzureResourceManager`).
  - **Failure Isolation & Drop Threshold**: If any source for an organization fails, or if prefix counts drop by >30% relative to committed files, file overwrites for that organization are aborted and the script exits with error code 1 to prevent committing corrupted/incomplete asset files.
