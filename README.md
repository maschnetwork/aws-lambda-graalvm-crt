# AWS Lambda GraalVM Native Image with AWS CRT Client

[![Build](https://github.com/maschnetwork/aws-lambda-graalvm-crt/actions/workflows/docker-build-test.yml/badge.svg)](https://github.com/maschnetwork/aws-lambda-graalvm-crt/actions/workflows/docker-build-test.yml)
[![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![GraalVM CE 25.0.2](https://img.shields.io/badge/GraalVM%20CE-25.0.2-blue?logo=oracle)](https://www.graalvm.org/)
[![AWS SDK 2.23.3](https://img.shields.io/badge/AWS%20SDK-2.23.3-FF9900?logo=amazonaws)](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
[![Runtime](https://img.shields.io/badge/Runtime-provided.al2023-232F3E?logo=amazonwebservices)](https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html)
[![License: MIT-0](https://img.shields.io/badge/License-MIT--0-green.svg)](LICENSE)

A high-performance AWS Lambda function compiled to a **GraalVM native image**, using the **AWS Common Runtime (CRT) HTTP client** for optimized DynamoDB operations. This project demonstrates how to achieve fast cold starts and low-latency execution by combining native ahead-of-time compilation with the AWS CRT, a high-performance HTTP client written in C.

---

## Table of Contents

- [Overview](#overview)
- [Why AWS CRT Client?](#why-aws-crt-client)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Build and Deploy](#build-and-deploy)
- [Testing](#testing)
- [Load Testing](#load-testing)
- [Tech Stack](#tech-stack)
- [CI/CD](#cicd)
- [Cleanup](#cleanup)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

This Lambda function:

- Receives **HTTP POST requests** via Amazon API Gateway
- Extracts all request headers from the incoming event
- Writes the headers to an Amazon **DynamoDB** table (`request-headers`) using both sync and async CRT clients
- Runs as a **GraalVM native binary** on the `provided.al2023` custom runtime for minimal cold start latency

---

## Why AWS CRT Client?

The default HTTP clients in the AWS SDK for Java v2 are [Netty NIO](https://netty.io/) (async) and [Apache HTTP Client](https://hc.apache.org/) (sync). This project replaces both with the **AWS Common Runtime (CRT) HTTP client**.

| Feature | Default Clients | AWS CRT Client |
|---------|----------------|----------------|
| GraalVM native image compatibility | Requires extensive reflection config | Purpose-built, fewer native image issues |
| Binary size | Larger due to Netty/Apache dependencies | Smaller footprint |
| Startup time | Slower (JNI, reflection overhead) | Faster cold starts |
| Performance | Good | Optimized specifically for AWS services |

The CRT client is purpose-built for AWS services and is better suited for GraalVM native image compilation, resulting in **smaller binaries**, **faster startup**, and **lower latency** in production.

---

## Architecture

```
                                +-----------------+
  Client (curl / Artillery)     |  API Gateway    |
  ───────────────────────────>  |  POST /request- |
                                |  headers-graal  |
                                +--------+--------+
                                         |
                                         v
                                +------------------+
                                |  AWS Lambda       |
                                |  (GraalVM Native) |
                                |  1024 MB / 20s    |
                                |  provided.al2023  |
                                +--------+---------+
                                         |
                           +-------------+-------------+
                           |                           |
                           v                           v
                   +---------------+           +---------------+
                   | DynamoDB Put  |           | DynamoDB Put  |
                   | (Async CRT)  |           | (Sync CRT)    |
                   +-------+-------+           +-------+-------+
                           |                           |
                           +-------------+-------------+
                                         |
                                         v
                                +------------------+
                                |  DynamoDB Table   |
                                |  "request-headers"|
                                +------------------+
```

---

## Project Structure

```
aws-lambda-graalvm-crt/
├── Dockerfile                          # Build image: Amazon Linux 2023 + GraalVM CE 25.0.2 + Maven 3.9.14
├── template.yaml                       # SAM template (Lambda, API Gateway, DynamoDB)
├── loadtest.yml                        # Artillery load test config (30s, 150 req/s)
├── result.png                          # Load test results screenshot
├── CONTRIBUTING.md                     # Contribution guidelines
├── LICENSE                             # MIT No Attribution License
├── README.md
│
└── request-writer/                     # Lambda function source
    ├── pom.xml                         # Maven build (native + jvm profiles)
    ├── Makefile                        # SAM build integration
    └── src/main/
        ├── java/com/maschnetwork/
        │   └── RequestHeaderWriter.java    # Lambda handler
        └── resources/
            ├── bootstrap                   # Custom runtime bootstrap script
            └── META-INF/native-image/      # GraalVM native image configuration
                ├── com.maschnetwork/           # App-level reflect + native-image props
                └── com.amazonaws/
                    ├── crt/aws-crt/            # CRT: reflect, JNI, resource configs
                    ├── aws-lambda-java-core/
                    ├── aws-lambda-java-events/
                    ├── aws-lambda-java-runtime-interface-client/
                    └── aws-lambda-java-serialization/
```

---

## Prerequisites

Before you begin, make sure you have the following installed:

| Tool | Purpose |
|------|---------|
| **Docker** | Required to build the GraalVM native image in an Amazon Linux 2023 environment |
| **AWS SAM CLI** | Building, packaging, and deploying the Lambda function |
| **AWS CLI** | Configured with valid credentials and a default region |
| **AWS Account** | Permissions for Lambda, API Gateway, DynamoDB, and IAM |
| **Artillery** *(optional)* | Running load tests |

---

## Build and Deploy

### 1. Build the Docker Image

The Dockerfile creates a build environment with GraalVM Community Edition 25.0.2, Maven 3.9.14, and the native-image tool on Amazon Linux 2023:

```bash
docker build -t al2023-graalvm:maven .
```

### 2. Build the Application

SAM uses the Docker image to compile the Java source into a GraalVM native binary:

```bash
sam build --build-image al2023-graalvm:maven --use-container
```

This runs `mvn clean install -P native` inside the container, which:

1. Compiles the Java 25 source code
2. Creates an uber-JAR via the Maven Shade plugin
3. Compiles the uber-JAR into a native binary using `native-maven-plugin`
4. Copies the native binary and bootstrap script to the SAM artifacts directory

### 3. Deploy to AWS

For the first deployment (interactive guided setup):

```bash
sam deploy --guided
```

For subsequent deployments:

```bash
sam deploy
```

This creates the following AWS resources:

| Resource | Details |
|----------|---------|
| **Lambda Function** | `dynamodb-request-writer-graal` with 1024 MB memory and 20s timeout |
| **API Gateway** | REST API with a `POST /request-headers-graal` endpoint |
| **DynamoDB Table** | `request-headers` with on-demand capacity |

---

## Testing

### Get the API Gateway URL

Retrieve the endpoint URL from the CloudFormation stack outputs:

```bash
export API_GW_URL=$(aws cloudformation describe-stacks \
  --stack-name <YOUR-STACK-NAME> \
  | jq -r '.Stacks[0].Outputs[] | select(.OutputKey == "RequestHeaderGraalApi").OutputValue')
```

### Send a Test Request

```bash
curl -XPOST "$API_GW_URL/request-headers-graal" \
  --header 'Content-Type: application/json'
```

A successful response returns:

```
successful
```

Both an async CRT write (id: `<request-id>`) and a sync CRT write (id: `sync-<request-id>`) are persisted to the DynamoDB table.

---

## Load Testing

### Run the Load Test

The included Artillery configuration sends **150 requests per second** for **30 seconds** (4,500 total requests):

```bash
artillery run -t $API_GW_URL -v '{ "url": "/request-headers-graal" }' loadtest.yml
```

### Analyze Results with CloudWatch Logs Insights

Run the following query in **CloudWatch Logs Insights** to get latency percentiles, grouped by cold start vs. warm start:

```sql
filter @type = "REPORT"
    | parse @log /\d+:\/aws\/lambda\/(?<function>.*)/
    | stats
    count(*) as invocations,
    pct(@duration+coalesce(@initDuration,0), 0) as p0,
    pct(@duration+coalesce(@initDuration,0), 25) as p25,
    pct(@duration+coalesce(@initDuration,0), 50) as p50,
    pct(@duration+coalesce(@initDuration,0), 75) as p75,
    pct(@duration+coalesce(@initDuration,0), 90) as p90,
    pct(@duration+coalesce(@initDuration,0), 95) as p95,
    pct(@duration+coalesce(@initDuration,0), 99) as p99,
    pct(@duration+coalesce(@initDuration,0), 100) as p100
    group by function, ispresent(@initDuration) as coldstart
    | sort by coldstart, function
```

### Results

![Load Test Results](result.png)

**Reading the results:**

| `coldstart` value | Meaning |
|---|---|
| `0` | Warm start - execution duration only |
| `1` | Cold start - init duration + execution duration |

---

## Tech Stack

| Component | Version / Details |
|---|---|
| Java | 25 |
| GraalVM Community Edition | 25.0.2 |
| Maven | 3.9.14 |
| AWS SDK for Java v2 | 2.23.3 |
| AWS CRT HTTP Client | `AwsCrtHttpClient` (sync) + `AwsCrtAsyncHttpClient` (async) |
| Native Image Plugin | `native-maven-plugin` 0.11.5 |
| Maven Shade Plugin | 3.2.4 |
| Lambda Runtime | `provided.al2023` (Amazon Linux 2023) |
| Build Base Image | `public.ecr.aws/amazonlinux/amazonlinux:2023` |
| Infrastructure as Code | AWS SAM |

---

## CI/CD

This project includes a GitHub Actions workflow that validates the Docker build environment on every pull request to `main`:

- **Workflow:** [Docker Image Build Test](.github/workflows/docker-build-test.yml)
- **Trigger:** Pull requests to `main` and manual dispatch
- **Steps:** Checkout, build Docker image, verify image creation

---

## Cleanup

To remove all deployed AWS resources:

```bash
sam delete --stack-name <YOUR-STACK-NAME>
```

---

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

This project is licensed under the [MIT No Attribution License](LICENSE).
