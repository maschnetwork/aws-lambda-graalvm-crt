# Contributing to AWS Lambda GraalVM CRT

Thank you for your interest in contributing to this project! Whether you are reporting a bug, suggesting a feature, improving documentation, or submitting code, your contributions are welcome and appreciated.

This guide explains how to get involved and what to expect during the contribution process.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features and Enhancements](#suggesting-features-and-enhancements)
- [Contribution Workflow](#contribution-workflow)
- [Local Development Setup](#local-development-setup)
- [GraalVM Native Image Guidance](#graalvm-native-image-guidance)
- [Code Style and Conventions](#code-style-and-conventions)
- [Pull Request Guidelines](#pull-request-guidelines)
- [License](#license)

---

## Code of Conduct

We are committed to providing a welcoming and inclusive experience for everyone. When participating in this project, please:

- Be respectful and constructive in all interactions
- Provide helpful and actionable feedback
- Assume good intentions from other contributors
- Refrain from personal attacks, harassment, or discriminatory language
- Focus on the technical merits of contributions

Maintainers reserve the right to remove comments, commits, or contributions that violate these expectations.

---

## Reporting Bugs

If you find a bug, please open an issue on [GitHub Issues](https://github.com/maschnetwork/aws-lambda-graalvm-crt/issues). To help us investigate and resolve the problem quickly, include the following information:

- **Summary**: A clear and concise description of the bug
- **Steps to reproduce**: The exact sequence of steps that triggers the problem
- **Expected behavior**: What you expected to happen
- **Actual behavior**: What actually happened, including any error messages or stack traces
- **Environment details**:
  - Operating system and version
  - Docker version
  - AWS SAM CLI version
  - Java / GraalVM version
  - AWS SDK version (if relevant)
- **Logs**: Any relevant output from `sam build`, `sam deploy`, or Lambda CloudWatch logs
- **Screenshots**: If applicable, include screenshots or terminal output

Before opening a new issue, please search the existing issues to check if the problem has already been reported.

---

## Suggesting Features and Enhancements

Feature requests and ideas for improvement are welcome! To suggest a feature:

1. Open a new issue on [GitHub Issues](https://github.com/maschnetwork/aws-lambda-graalvm-crt/issues)
2. Use a clear and descriptive title (e.g., "Support for additional DynamoDB operations")
3. Describe the feature, why it would be useful, and how it fits into the project
4. If possible, outline a rough approach or implementation idea

Feature discussions are a great way to align on scope before writing code.

---

## Contribution Workflow

Follow these steps to contribute code or documentation:

1. **Fork the repository**: Click the "Fork" button on the [GitHub repository page](https://github.com/maschnetwork/aws-lambda-graalvm-crt)

2. **Clone your fork**:
   ```bash
   git clone https://github.com/<your-username>/aws-lambda-graalvm-crt.git
   cd aws-lambda-graalvm-crt
   ```

3. **Create a feature branch** from `main`:
   ```bash
   git checkout -b my-feature
   ```

4. **Make your changes**: Implement your feature or fix. See [Local Development Setup](#local-development-setup) for build and test instructions.

5. **Commit with a clear message**:
   ```bash
   git commit -m "Add support for batch write operations"
   ```

6. **Push to your fork**:
   ```bash
   git push origin my-feature
   ```

7. **Open a Pull Request**: Go to the original repository and open a PR against the `main` branch. Fill in the PR template with a description of your changes.

---

## Local Development Setup

### Prerequisites

Make sure you have the following tools installed:

| Tool | Purpose | Install Guide |
|---|---|---|
| **Docker** | Required for building the GraalVM native image in an Amazon Linux 2023 container | [docs.docker.com](https://docs.docker.com/get-docker/) |
| **AWS SAM CLI** | Builds, packages, and deploys the Lambda function | [docs.aws.amazon.com](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html) |
| **AWS CLI** | Configured with valid credentials and a default region | [docs.aws.amazon.com](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) |
| **Java 17** | Required if you want to run Maven commands outside Docker | [openjdk.org](https://openjdk.org/projects/jdk/17/) |
| **Artillery** *(optional)* | For running load tests | [artillery.io](https://www.artillery.io/docs/get-started/get-artillery) |

> **Note**: You do not need to install GraalVM or Maven locally. The Docker build image (`al2023-graalvm:maven`) includes GraalVM CE 17.0.9 and Maven 3.9.6.

### Step 1: Build the Docker Image

The Dockerfile creates a build environment based on Amazon Linux 2023 with GraalVM and Maven pre-installed:

```bash
docker build -t al2023-graalvm:maven .
```

### Step 2: Build the Application

Use SAM to compile the Java source into a GraalVM native binary inside the Docker container:

```bash
sam build --build-image al2023-graalvm:maven --use-container
```

This runs `mvn clean install -P native` inside the container, which:

1. Compiles the Java 17 source code
2. Creates an uber-JAR via the Maven Shade plugin
3. Compiles the uber-JAR into a native binary using `native-maven-plugin`
4. Copies the native binary and bootstrap script to the SAM artifacts directory

### Step 3: Deploy to AWS

For the first deployment (interactive guided setup):

```bash
sam deploy --guided
```

For subsequent deployments:

```bash
sam deploy
```

### Step 4: Test Your Changes

Retrieve the API Gateway endpoint URL and send a test request:

```bash
# Get the API Gateway URL
export API_GW_URL=$(aws cloudformation describe-stacks \
  --stack-name <YOUR-STACK-NAME> \
  | jq -r '.Stacks[0].Outputs[] | select(.OutputKey == "RequestHeaderGraalApi").OutputValue')

# Send a test request
curl -XPOST "$API_GW_URL/request-headers-graal" \
  --header 'Content-Type: application/json'
```

A successful response returns `successful`.

### Step 5: Run Load Tests (Optional)

```bash
artillery run -t $API_GW_URL -v '{ "url": "/request-headers-graal" }' loadtest.yml
```

---

## GraalVM Native Image Guidance

GraalVM native image compilation requires explicit configuration for features that rely on runtime reflection, JNI, or dynamic resource loading. If your changes introduce new dependencies or use reflection-based APIs, you may need to update the native image configuration files.

### Configuration File Locations

All native image configuration files live under:

```
request-writer/src/main/resources/META-INF/native-image/
```

The directory is organized by library:

| Directory | Contents |
|---|---|
| `com.maschnetwork/` | Application-level reflection config and native-image properties |
| `com.amazonaws/crt/aws-crt/` | AWS CRT client: reflect, JNI, and resource configs |
| `com.amazonaws/aws-lambda-java-core/` | Lambda Core library reflection config |
| `com.amazonaws/aws-lambda-java-events/` | Lambda Events library reflection config |
| `com.amazonaws/aws-lambda-java-runtime-interface-client/` | Lambda RIC: reflect, JNI, resource configs, and native-image properties |
| `com.amazonaws/aws-lambda-java-serialization/` | Lambda Serialization library reflection config |

### When to Update Configs

You need to update native image configuration if you:

- **Add a new dependency** that uses reflection (e.g., serialization libraries, AWS SDK service clients)
- **Add new model classes** that are serialized/deserialized at runtime
- **Use JNI** to call native code from a new library
- **Load resources dynamically** (e.g., configuration files, service descriptors)

### How to Generate Configs

The GraalVM `native-image-agent` can automatically trace reflection, JNI, and resource usage at runtime and produce the necessary configuration files. The general approach is:

1. Run the application on a standard JVM with the tracing agent enabled
2. Exercise all code paths (including new ones)
3. Collect the generated configuration files
4. Merge them into the appropriate directory under `META-INF/native-image/`

Refer to the [GraalVM Reachability Metadata documentation](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) for detailed instructions.

### Common Pitfalls

- **Missing reflection entries**: The native binary will throw `ClassNotFoundException` or similar errors at runtime if a class used via reflection is not registered
- **Missing JNI entries**: Native libraries (like the AWS CRT) require JNI configuration for any Java methods called from native code
- **Missing resources**: Files loaded via `Class.getResource()` or `ClassLoader.getResource()` must be listed in `resource-config.json`

Always test your changes by building the native image and running it end-to-end. Reflection-related issues only surface at runtime, not at compile time.

---

## Code Style and Conventions

- Follow standard **Java coding conventions** (naming, formatting, structure)
- Keep changes **focused**: one feature or bug fix per pull request
- Use meaningful variable and method names
- Write clear commit messages that explain *what* changed and *why*
- If your change modifies the Lambda handler logic, verify it works as a native image (not just on the JVM)

---

## Pull Request Guidelines

When submitting a pull request:

- **Provide a clear description**: Explain what your PR does, why the change is needed, and how it was tested
- **Reference related issues**: Link to any GitHub issues your PR addresses (e.g., "Fixes #42" or "Closes #15")
- **Keep the scope focused**: Avoid mixing unrelated changes in the same PR
- **Ensure the build succeeds**: Verify that `sam build --build-image al2023-graalvm:maven --use-container` completes without errors
- **Test your changes**: Confirm the deployed Lambda function works correctly by sending test requests
- **Update documentation**: If your change affects build steps, configuration, or usage, update the README or other relevant docs
- **Update native image configs**: If you add dependencies that require reflection, JNI, or resource metadata, include the updated configuration files in your PR

Maintainers will review your PR and may request changes. Please be responsive to feedback.

---

## License

By contributing to this project, you agree that your contributions will be licensed under the [MIT No Attribution License](LICENSE).
