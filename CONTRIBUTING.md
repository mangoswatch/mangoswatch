# Contributing to Mangos Watch

First off, thank you for your interest in contributing to Mangos Watch! 

## Code of Conduct & Philosophy

Mangos Watch is a FOSS project dedicated to privacy awareness and transparency. We value thoughtful engineering, clear and concise code, and respect for user privacy.

While we encourage writing code by hand, we understand that AI tooling can assist with repetitive tasks (such as formatting taxonomy data). However, please adhere to these rules:
- **Keep PRs small and specific**: Do not submit giant refactors or a flood of unreviewed AI-generated slop that modifies 3/4 of the application.
- **Test your changes**: Ensure the app builds successfully and that network packet inspection / proxy logic remains stable.
- **Maintain clean architecture**: Keep UI logic separated from domain and data layers (Jetpack Compose + Koin).

## How to Contribute

1. Fork the repository and create your branch from `main`.
2. Make your changes or additions.
3. Test thoroughly on an emulator or physical device (Min SDK 29 / Target SDK 37).
4. Open a Pull Request with a clear description of what your change accomplishes and why.

Thank you.
