# Contributing to AWS Lambda GraalVM CRT

Thank you for your interest in contributing! This document provides guidelines to help you get started.

## How to Contribute

1. **Fork** the repository and clone your fork locally.
2. **Create a branch** from `main` for your change:
   ```bash
   git checkout -b my-feature
   ```
3. **Make your changes** and ensure they follow the existing code style.
4. **Test your changes** locally:
   - Build the native image using the provided `Dockerfile`.
   - Deploy with `sam build && sam deploy` to verify Lambda functionality.
5. **Commit** with a clear, descriptive message.
6. **Push** your branch and open a **Pull Request** against `main`.

## Reporting Issues

- Use [GitHub Issues](https://github.com/maschnetwork/aws-lambda-graalvm-crt/issues) to report bugs or request features.
- Include steps to reproduce, expected behavior, and actual behavior.

## Code Guidelines

- Java source code lives in the `request-writer` directory.
- Follow existing code conventions and formatting.
- Keep dependencies minimal — only add what is necessary.

## License

By contributing, you agree that your contributions will be licensed under the [MIT No Attribution License](LICENSE).
