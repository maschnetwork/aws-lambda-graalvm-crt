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

- **Java 17**
- **GraalVM Community Edition 17.0.9** (with `native-image` installed)
- **Maven 3.9.6+**
- **Docker** — required for building the native image in an Amazon Linux 2023 environment
- **AWS SAM CLI** — for building, packaging, and deploying
- **AWS CLI** — configured with valid credentials

### Build & Test

Build the Docker image that provides the GraalVM + Maven build environment:

```bash
docker build -t al2023-graalvm:maven .
```

Build the native image using SAM:

```bash
sam build --build-image al2023-graalvm:maven --use-container
```

This runs `mvn clean install -P native` inside the container, compiling the Java source into a GraalVM native binary.

### Deploy Locally

```bash
sam local start-api --template template.yaml
```

## CI Pipeline

Pull requests targeting `main` automatically trigger a Docker image build test via GitHub Actions. Ensure the Docker build passes before requesting a review.

## Reporting Issues

- Use [GitHub Issues](https://github.com/maschnetwork/aws-lambda-graalvm-crt/issues) to report bugs or request features.
- Include steps to reproduce, expected behavior, and actual behavior.

## Code Style

- Follow standard Java conventions.
- Keep changes focused — one feature or fix per PR.

## License

By contributing, you agree that your contributions will be licensed under the [MIT No Attribution License](LICENSE).
