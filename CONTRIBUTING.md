# Contributing

Thank you for your interest in contributing to this project! Here's how to get started.

## How to Contribute

1. Fork the repository.
2. Create a feature branch from `main`: `git checkout -b my-feature`
3. Make your changes.
4. Test your changes locally (see below).
5. Commit with a clear message: `git commit -m "Add my feature"`
6. Push to your fork: `git push origin my-feature`
7. Open a Pull Request against `main`.

## Local Development

### Prerequisites

- Java 17
- GraalVM CE 17
- AWS SAM CLI
- Docker (for native image builds)

### Build & Test

```bash
# Build the native image using Docker
docker build -t lambda-graalvm .

# Deploy locally with SAM
sam local start-api --template template.yaml
```

## Reporting Issues

- Use [GitHub Issues](https://github.com/maschnetwork/aws-lambda-graalvm-crt/issues) to report bugs or request features.
- Include steps to reproduce, expected behavior, and actual behavior.

## Code Style

- Follow standard Java conventions.
- Keep changes focused — one feature or fix per PR.

## License

By contributing, you agree that your contributions will be licensed under the [MIT No Attribution License](LICENSE).
