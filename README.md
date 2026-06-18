# :zap: AWS Lambda on Steroids: GraalVM Native + AWS CRT

[![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![GraalVM CE 25.0.2](https://img.shields.io/badge/GraalVM%20CE-25.0.2-blue?logo=oracle)](https://www.graalvm.org/)
[![AWS SDK 2.23.3](https://img.shields.io/badge/AWS%20SDK-2.23.3-FF9900?logo=amazonaws)](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
[![Runtime](https://img.shields.io/badge/Runtime-provided.al2023-232F3E?logo=amazonwebservices)](https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> *"What if your Lambda cold-started so fast that users thought it was always warm?"*

This project takes a Java Lambda function, compiles it into a **screaming-fast native binary** with GraalVM, and swaps out the default HTTP clients for the **AWS Common Runtime (CRT)** -- a C-based networking beast purpose-built for talking to AWS services. The result? Cold starts that make other Java Lambdas weep.

---

## :thinking: What Does It Actually Do?

It's deceptively simple (the best kind of simple):

1. :inbox_tray: Receives an HTTP POST via API Gateway
2. :mag: Grabs all the request headers
3. :writing_hand: Writes them to DynamoDB **twice** -- once with the async CRT client, once with the sync CRT client
4. :white_check_mark: Returns `"successful"` (because it is)

Why write twice? To prove that *both* the async (`AwsCrtAsyncHttpClient`) and sync (`AwsCrtHttpClient`) flavors work beautifully as native images. Science!

---

## :racing_car: Why This Stack?

### The Problem with "Normal" Java Lambdas

Java + Lambda = great developer experience, *painful* cold starts. The JVM needs to load classes, JIT-compile hot paths, and generally wake up like a teenager on a Monday morning.

### The Solution

| Ingredient | What It Does |
|---|---|
| **GraalVM Native Image** | Compiles your Java ahead-of-time into a standalone binary. No JVM startup. No class loading. Just *go*. |
| **AWS CRT HTTP Client** | Replaces Netty and Apache HTTP with a lean, mean, C-based HTTP client optimized for AWS. Smaller binary, less memory, lower latency. |
| **provided.al2023** | Custom runtime on Amazon Linux 2023. Your binary IS the runtime. |

### What We Kicked Out :boot:

```xml
<!-- BYE Netty -->
<exclusion>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>netty-nio-client</artifactId>
</exclusion>
<!-- BYE Apache -->
<exclusion>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>apache-client</artifactId>
</exclusion>
```

No Netty. No Apache. Just CRT. *chef's kiss*

---

## :building_construction: Architecture

```
    You (or Artillery going brrrr)
              |
              v
    +-------------------+
    |   API Gateway     |
    |   POST /request-  |
    |   headers-graal   |
    +--------+----------+
             |
             v
    +-------------------+
    |   AWS Lambda      |        _____
    |   (Native Binary) |       /     \
    |   1024 MB / 20s   |  --> | Dynamo |
    |   provided.al2023 |  --> |   DB   |
    +-------------------+       \_____/
         |         |
         v         v
     [async]    [sync]
      write      write
```

Both writes hit the same `request-headers` table:
- **Async write**: id = `<request-id>`
- **Sync write**: id = `sync-<request-id>`

---

## :file_folder: Project Structure

```
aws-lambda-graalvm-crt/
|-- Dockerfile                    # AL2023 + GraalVM CE 25.0.2 + Maven 3.9.14
|-- template.yaml                 # SAM: Lambda + API Gateway + DynamoDB
|-- loadtest.yml                  # Artillery: 150 req/s for 30s (4,500 requests)
|-- result.png                    # The sweet, sweet results
|
`-- request-writer/               # Where the magic happens
    |-- pom.xml                   # Maven with native + jvm profiles
    |-- Makefile                  # SAM build hook
    `-- src/main/
        |-- java/.../RequestHeaderWriter.java   # The Lambda handler
        `-- resources/
            |-- bootstrap                       # Custom runtime entrypoint
            `-- META-INF/native-image/          # GraalVM configs (reflect, JNI, resources)
```

---

## :rocket: Getting Started

### Prerequisites

You'll need:

- **Docker** -- the native image builds inside a container (because compiling for AL2023 on your MacBook would be... interesting)
- **AWS SAM CLI** -- for building and deploying
- **AWS CLI** -- configured with credentials
- **An AWS Account** -- with permissions for Lambda, API Gateway, DynamoDB, and IAM
- **Artillery** *(optional)* -- for when you want to go full send on load testing

### 1. Build the Docker Image

This creates your build environment: Amazon Linux 2023 with GraalVM CE 25.0.2 and Maven 3.9.14:

```bash
docker build -t al2023-graalvm:maven .
```

### 2. Build the Native Image

SAM uses the Docker image to compile everything into a native binary:

```bash
sam build --build-image al2023-graalvm:maven --use-container
```

Under the hood, this runs `mvn clean install -P native` which:
1. Compiles Java 25 source code
2. Creates an uber-JAR via Maven Shade
3. Feeds that JAR to `native-maven-plugin` with `--no-fallback`
4. Out pops a native binary ready for Lambda

### 3. Deploy

First time (guided):
```bash
sam deploy --guided
```

After that:
```bash
sam deploy
```

This creates:
- :zap: Lambda function (`dynamodb-request-writer-graal`) -- 1024 MB, 20s timeout
- :globe_with_meridians: API Gateway with `POST /request-headers-graal`
- :floppy_disk: DynamoDB table (`request-headers`) with on-demand capacity

---

## :test_tube: Testing

### Grab Your API URL

```bash
export API_GW_URL=$(aws cloudformation describe-stacks \
  --stack-name <YOUR-STACK-NAME> \
  | jq -r '.Stacks[0].Outputs[] | select(.OutputKey == "RequestHeaderGraalApi").OutputValue')
```

### Fire a Request

```bash
curl -XPOST "$API_GW_URL/request-headers-graal" \
  --header 'Content-Type: application/json'
```

Expected response: `successful`

Check DynamoDB and you'll find two new items -- your headers, written by both the async and sync CRT clients. Teamwork!

---

## :chart_with_upwards_trend: Load Testing

Want to see this thing sweat? (Spoiler: it barely breaks a sweat.)

### Run It

```bash
artillery run -t $API_GW_URL -v '{ "url": "/request-headers-graal" }' loadtest.yml
```

This hammers the endpoint with **150 requests per second** for **30 seconds** = **4,500 total requests**.

### Analyze with CloudWatch Logs Insights

```
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

| `coldstart` | Meaning |
|---|---|
| `0` | Warm invocation -- just execution time |
| `1` | Cold start -- init + execution (the number everyone cares about) |

---

## :toolbox: Tech Stack

| Component | Version |
|---|---|
| Java | 25 |
| GraalVM Community Edition | 25.0.2 |
| AWS SDK for Java v2 | 2.23.3 |
| HTTP Client (sync) | `AwsCrtHttpClient` |
| HTTP Client (async) | `AwsCrtAsyncHttpClient` |
| native-maven-plugin | 0.11.5 |
| Maven Shade Plugin | 3.2.4 |
| Lambda Runtime | `provided.al2023` |
| Build Image | `amazonlinux:2023` |
| Maven | 3.9.14 |
| IaC | AWS SAM |

---

## :broom: Cleanup

Done playing? Remove everything:

```bash
sam delete --stack-name <YOUR-STACK-NAME>
```

---

## :brain: Key Takeaways

1. **GraalVM + CRT = cold start killer combo** for Java Lambdas
2. **Excluding Netty and Apache** keeps your native binary lean and avoids GraalVM compatibility headaches
3. The `provided.al2023` runtime lets you ship a **raw binary** -- no JVM, no overhead, no kidding
4. Both sync and async DynamoDB operations work flawlessly as native images with the right reflection/JNI configs

---

## :scroll: License

[MIT](LICENSE) -- go wild.
