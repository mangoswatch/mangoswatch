# Mangos Watch

<p>
  <img src="https://img.shields.io/badge/Platform-Android-green" alt="Platform Android">
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-blueviolet" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Kotlin-2.x-purple" alt="Kotlin">
  <img src="https://img.shields.io/badge/License-GPLv3-blue" alt="License GPLv3">
</p>

Mangos Watch is a FOSS tool for Android, aimed at raising awareness about privacy and data sharing on Android phones.
It monitors the device's network traffic and shows which endpoints owned by Big Tech are contacted by the apps and services on the phone, whether those connections are legitimate and acknowledged or not.
In addition, it classifies these "calls home" against a curated taxonomy, providing privacy-related insights about the host that is reached.



## Disclaimer

**IMPORTANT PLEASE READ CAREFULLY:**

### 1. No Liability & Informational Purpose ("Use at Your Own Risk")
Mangos Watch is provided as an open-source, educational, and informational tool designed to raise awareness about network activity and privacy on Android devices. 
- **No Guarantees**: The endpoint classifications, taxonomy matching, and traffic insights provided by the app are based on heuristics, public knowledge bases, and best-effort analysis. They may contain inaccuracies, false positives, or incomplete data.
- **Sole User Responsibility**: The authors, maintainers, and contributors of Mangos Watch accept **zero liability** for any decisions, actions, financial losses, operational disruptions, or consequences resulting from the use of this app or reliance on its data. If you make personal, technical, or legal choices based on insights shown by Mangos Watch, **that is entirely your own responsibility**. Always verify network behavior independently.

### 2. Trademarks, Big Tech & Fair Use
- **Nominative Fair Use**: All corporate names, brands, trademarks, and logos referenced within Mangos Watch (including Meta, Google, Apple, Microsoft, Amazon, etc.) belong exclusively to their respective owners. Their mention is strictly for identification, research, transparency, and educational purposes under nominative fair use.
- **No Affiliation**: This project is an independent, community-driven FOSS initiative. It is **not** affiliated with, endorsed by, sponsored by, or officially connected to any of the Big Tech companies or entities monitored or referenced by the app.
- **Non-Disparagement & Freedom of Expression**: The app merely observes and classifies local network connections and publicly documented endpoints. Nothing in this software or repository constitutes commercial disparagement, defamation, or official legal/technical certification of corporate practices.



## Why the Mango?

The MANGOS acronym (Meta, Anthropic, Nvidia, Google, OpenAI, SpaceX) worked well when it came time to choose a name (and it also inspired the cute puppet used as the app icon).
That said, the app classifies endpoints owned by several other Big Tech companies, such as FAANG members, smartphone manufacturers, data brokers, etc.

Here's the up-to-date list of the big boys we watch at the moment:

- AMAZON
- ANTHROPIC
- APPLE
- APPSFLYER
- GOOGLE
- META
- MICROSOFT
- NETFLIX
- NVIDIA
- OPENAI
- XIAOMI
- X_TWITTER



## A Word on the Data Monitored

By no means is this app promoting a witch hunt against Big Tech.<br>Well... let's say not entirely.<br>Although some (most?) of their business tactics fall under the definition of monopoly and/or oligarchy, it's important to note that some of the classified streams may very well be legitimate, and the related data sharing fully acknowledged by the user.

That is the case for proprietary services that, by nature, rely on sharing such data: for example, a Gmail account connecting to a Google-owned server, or a GPS position sent for the normal operation of a mapping service.
Once users are aware that they're giving away some of their info in exchange for a service, that is completely fine (kinda).

The goal of this application is to show the massive impact Big Tech has on everyday Android use, especially on branded phones, and to point out the constant data collection these companies carry out relentlessly.
The problem arises when data collection relies on fine-print ToS that users merely "accept", or when apps give away part of the sovereignty of the data they collect by relying on bigger infrastructures for their operation.

On this note, the app doesn't show or even read the encrypted data that is sent or received. The endpoint classification is based on the known function of company endpoints that are publicly documented.
For a reliable classification we don't need more than that: an app calling the Firebase logging endpoint is most surely sending usage statistics, just as a game app talking to a known data broker endpoint most surely collects juicy stuff about the user.



## How It Works & Architecture

Mangos Watch implements a local VPN service (`VpnTrafficManager`) on the phone that hijacks all network traffic and routes it through a structured pipeline:

1. **VPN Capture**: Intercepts packets at the network layer (`VpnService`).
2. **Proxy & Tunnel**: Forwards traffic via the native `hev-socks5-tunnel` library (`SocksProxyRelay`) ensuring proper routing to the internet.
3. **Traffic Analysis**: Positioned between the VPN and proxy, the `TrafficAnalyzer` and `StreamTrafficProcessor` parse incoming and outgoing packets.
4. **Handshake & Protocol Parsing**: Inspects TLS (`TlsParser`) and QUIC (`QuicParser`) handshakes along with DNS queries.
5. **Taxonomy & IP Resolution**: Matches destination IPs and endpoints against curated asset lists (`IpRanges`, taxonomy repositories) to identify the owner and purpose.



## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose & Material 3
- **Dependency Injection**: Koin (`io.insert-koin`)
- **Native Tunnel**: `hev-socks5-tunnel` AAR library
- **Platform**: Android (Min SDK 29, Target SDK 37)



## Getting Started & Building from Source

To build and run Mangos Watch from source:

1. Clone the repository:
   ```bash
   git clone https://github.com/mangoswatch/mangoswatch.git
   ```
2. Open the project in Android Studio.
3. Ensure you have JDK 11+ configured.
4. Build and run the `:app` module on a physical device or emulator (API 29+).



## A Word on AI Usage

The development of this app relied to some extent on LLMs, especially for editing the asset files (compiling such an extensive list of endpoints would have taken me MONTHS, not to mention classifying them).

While it may seem counterintuitive, the stance of the project on the current split between antis and pros sits kinda in the middle: the technology is here and it most likely won't disappear. And it's useful, sometimes.

I nonetheless feel that its concentration in the hands of a few players is a perfect example of the crooked state of late-stage capitalism that technology embodies nowadays. This last assertion should be the center of the discussion.



## Technical Transparency & Known Limitations

To ensure full transparency regarding security, privacy, and architecture, the following design decisions and limitations should be noted:

### 1. Native Tunnel Library (`hev-socks5-tunnel.aar`)
- **Source**: [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel).
- **Binary Provenance & SHA256**:
   - File: `app/libs/hev-socks5-tunnel.aar`
   - SHA256: `ab6c581e6157680cfe5687ee7bbdcf95405c006c2db3701879ec44b9e5f83836`

### 2. Hardcoded DNS Routing
- The local VPN service routes DNS requests through reliable public resolvers (Cloudflare `1.1.1.1`, `1.0.0.1`, and IPv6 `2606:4700:4700::1111`, `2606:4700:4700::1001`) to ensure consistent domain name resolution and interception.

### 3. Remote Asset Synchronization
- Mangos Watch periodically fetches classification assets and IP ranges from GitHub Pages (`https://mangoswatch.github.io/mangoswatch/data/index.json`).
- **Security Note**: These files contain JSON taxonomy definitions and plain-text IP ranges used solely for offline classification.

### 4. `QUERY_ALL_PACKAGES` Permission
- **Usage**: Required to query installed application packages on the device, enabling attribution of network connections and streams to specific apps.



## Contributing

All contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for reference.

While we encourage the old way of writing your fucking code by hand, we understand that a certain level of assistance is tolerable.
Please keep your PR small and specific: a flood of AI slop that changes 3/4 of the app won't even be considered.

> **Note on Assets**: If you notice any inaccuracies, missing endpoints, or misclassifications, corrections and pull requests are warmly welcomed!



## Acknowledgements

- hev-socks5-tunnel: [https://github.com/heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- PCAPdroid for the inspiration: [https://github.com/emanuele-f/PCAPdroid](https://github.com/emanuele-f/PCAPdroid)



## License

Mangos Watch is free software, released under the [GNU General Public License v3.0](LICENSE).

You are free to use, study, modify and share it, as long as derivative works stay free too.
