# Contributing

Thank you for your interest in contributing to this project! We welcome contributions of all kinds.

## How to Contribute

1. Fork the repository and clone your fork.
2. Create a feature branch from `main`:
   ```bash
   git checkout -b my-feature
   ```
3. Make your changes.
4. Build and test locally:
   ```bash
   cd request-writer
   mvn clean package
   ```
5. Commit your changes with a clear message:
   ```bash
   git commit -m "Add my feature"
   ```
6. Push to your fork and open a pull request against `main`.

## Development Prerequisites

- Java 17
- Maven
- GraalVM CE 17 (for native image builds)
- Docker (for container-based builds)
- AWS SAM CLI (for local testing and deployment)

## Guidelines

- Keep changes focused — one feature or fix per pull request.
- Follow existing code style and project conventions.
- Update documentation (including `README.md`) if your change affects usage.
- Ensure the project builds successfully before submitting.

## Reporting Issues

Open a [GitHub issue](https://github.com/maschnetwork/aws-lambda-graalvm-crt/issues) with a clear description of the bug or feature request.

## License

By contributing, you agree that your contributions will be licensed under the [MIT No Attribution License](LICENSE).
