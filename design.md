# AWS Lambda GraalVM CRT - Technical Design Document

## Overview

This project demonstrates a high-performance AWS Lambda function built with Java 17, GraalVM Native Image compilation, and the AWS Common Runtime (CRT) HTTP client. The function processes API Gateway requests and persists request headers to Amazon DynamoDB.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           AWS Cloud                                     │
│  ┌─────────────────┐    ┌──────────────────────────────────────────┐  │
│  │   API Gateway   │───▶│         AWS Lambda (provided.al2023)      │  │
│  │  POST /request- │    │  ┌────────────────────────────────────┐   │  │
│  │  headers-graal  │    │  │    GraalVM Native Image Binary     │   │  │
│  └─────────────────┘    │  │  ┌─────────────────────────────┐   │   │  │
│                         │  │  │   RequestHeaderWriter       │   │   │  │
│                         │  │  │   (Lambda Handler)          │   │   │  │
│                         │  │  └─────────────────────────────┘   │   │  │
│                         │  │              │                     │   │  │
│                         │  │              ▼                     │   │  │
│                         │  │  ┌─────────────────────────────┐   │   │  │
│                         │  │  │   AWS CRT HTTP Client       │   │   │  │
│                         │  │  │   (Async + Sync)            │   │   │  │
│                         │  │  └─────────────────────────────┘   │   │  │
│                         │  └────────────────────────────────────┘   │  │
│                         └───────────────────┬──────────────────────┘  │
│                                             │                          │
│                                             ▼                          │
│                         ┌──────────────────────────────────────────┐  │
│                         │     Amazon DynamoDB                       │  │
│                         │     Table: request-headers                │  │
│                         │     PK: id (AWS Request ID)               │  │
│                         └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | GraalVM Native Image | 17.0.9 (Community Edition) |
| Base OS | Amazon Linux 2023 | Latest |
| Lambda Runtime | Custom Runtime (`provided.al2023`) | - |
| AWS SDK | AWS SDK for Java v2 | 2.23.3 |
| HTTP Client | AWS CRT (Common Runtime) | Via aws-crt-client |
| Build Tool | Apache Maven | 3.9.6 |
| IaC | AWS SAM (Serverless Application Model) | - |
| Load Testing | Artillery | - |

---

## Project Structure

```
aws-lambda-graalvm-crt/
├── Dockerfile                    # Build environment image (AL2023 + GraalVM)
├── template.yaml                 # SAM/CloudFormation template
├── loadtest.yml                  # Artillery load test configuration
├── result.png                    # Load test results visualization
├── README.md                     # Usage documentation
└── request-writer/               # Lambda function source
    ├── Makefile                  # SAM build commands
    ├── pom.xml                   # Maven configuration
    └── src/
        └── main/
            ├── java/
            │   └── com/maschnetwork/
            │       └── RequestHeaderWriter.java  # Handler implementation
            └── resources/
                ├── bootstrap                     # Lambda bootstrap script
                └── META-INF/native-image/        # GraalVM native-image configs
                    ├── com.amazonaws/
                    │   ├── aws-lambda-java-core/
                    │   ├── aws-lambda-java-events/
                    │   ├── aws-lambda-java-runtime-interface-client/
                    │   ├── aws-lambda-java-serialization/
                    │   └── crt/aws-crt/
                    └── com.maschnetwork/
```

---

## Core Components

### 1. Lambda Handler (`RequestHeaderWriter.java`)

The handler implements `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` and initializes two DynamoDB clients:

```java
// Async client with AWS CRT
private final DynamoDbAsyncClient dynamoDbClient = DynamoDbAsyncClient.builder()
    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
    .httpClientBuilder(AwsCrtAsyncHttpClient.builder())
    .build();

// Sync client with AWS CRT
private final DynamoDbClient dynamoDbSyncClient = DynamoDbClient.builder()
    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
    .httpClientBuilder(AwsCrtHttpClient.builder())
    .build();
```

**Key Design Decisions:**
- Both async and sync clients are instantiated at class load time (outside handler method) to leverage Lambda container reuse
- Uses `EnvironmentVariableCredentialsProvider` for credential management (Lambda injects credentials via environment variables)
- AWS CRT HTTP client replaces default Netty/Apache clients for better native compilation and performance

### 2. Build Environment (Dockerfile)

Creates a containerized build environment with:
- **Base**: Amazon Linux 2023
- **GraalVM**: Community Edition 17.0.9 with native-image tool
- **Maven**: 3.9.6
- **AWS Lambda Builders**: Python-based tooling for SAM

**Critical Build Tools:**
```dockerfile
RUN /usr/lib/graalvm/bin/gu install native-image
RUN ln -s /usr/lib/graalvm/bin/native-image /usr/bin/native-image
```

### 3. Maven Configuration (pom.xml)

**Dependency Strategy:**
- Uses AWS SDK BOM for version management
- **Explicitly excludes** default HTTP clients to reduce binary size and avoid native-image conflicts:
  ```xml
  <exclusions>
      <exclusion>
          <groupId>software.amazon.awssdk</groupId>
          <artifactId>netty-nio-client</artifactId>
      </exclusion>
      <exclusion>
          <groupId>software.amazon.awssdk</groupId>
          <artifactId>apache-client</artifactId>
      </exclusion>
  </exclusions>
  ```

**Build Profiles:**
- `native`: Produces GraalVM native binary via `native-maven-plugin` (v0.9.28)
- `jvm`: Produces shaded JAR for standard JVM execution

**Native Image Build Configuration:**
```xml
<configuration>
    <skip>false</skip>
    <imageName>native</imageName>
    <buildArgs>
        <buildArg>--no-fallback</buildArg>
    </buildArgs>
    <classpath>
        <param>${project.build.directory}/${project.artifactId}-${project.version}-shaded.jar</param>
    </classpath>
</configuration>
```

### 4. GraalVM Native Image Configuration

Located in `src/main/resources/META-INF/native-image/`, these JSON configurations enable reflection, JNI, and resource access required at runtime:

| Configuration File | Purpose |
|-------------------|---------|
| `reflect-config.json` | Defines classes requiring reflective access (Lambda runtime, SDK internals, API Gateway events) |
| `jni-config.json` | Configures JNI access for AWS CRT native libraries |
| `resource-config.json` | Includes native shared libraries (`.so`, `.dylib`, `.dll`) in the binary |

**AWS CRT Resource Patterns:**
```json
{
  "resources": {
    "includes": [
      {"pattern":".*libaws-crt-jni.jnilib"},
      {"pattern":".*libaws-crt-jni.dylib"},
      {"pattern":".*libaws-crt-jni.so"},
      {"pattern":".*libaws-crt-jni.dll"}
    ]
  }
}
```

### 5. Lambda Bootstrap Script

The `bootstrap` script is required for custom runtimes:
```bash
#!/bin/bash
set -e
./native $_HANDLER
```

This executes the native binary, passing the handler name via Lambda's `_HANDLER` environment variable.

### 6. SAM Template (template.yaml)

Defines the serverless infrastructure:

```yaml
Resources:
  RequestHeaderWriterFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: dynamodb-request-writer-graal
      CodeUri: request-writer
      Runtime: provided.al2023
      MemorySize: 1024
      Handler: com.maschnetwork.RequestHeaderWriter
      Events:
        RequestHeaderEvent:
          Type: Api
          Properties:
            Path: /request-headers-graal
            Method: post
      Policies:
        - DynamoDBWritePolicy:
            TableName: !Ref RequestHeaderTable
    Metadata:
      BuildMethod: makefile
```

**Key Configuration:**
- Runtime: `provided.al2023` (custom runtime with AL2023 base)
- Memory: 1024 MB (affects CPU allocation)
- Build: Uses Makefile for native compilation

---

## Build Process

### Build Pipeline Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         BUILD PIPELINE                                   │
│                                                                         │
│  1. docker build -t al2023-graalvm:maven .                             │
│     ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Creates AL2023 container with GraalVM 17 + Maven 3.9.6         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  2. sam build --build-image al2023-graalvm:maven --use-container       │
│     ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Executes Makefile inside container:                            │   │
│  │  a) mvn clean install -P native                                 │   │
│  │     - Compiles Java sources                                     │   │
│  │     - Creates shaded uber-JAR                                   │   │
│  │     - Runs native-image AOT compilation (~2-5 min)              │   │
│  │  b) Copies 'native' binary to ARTIFACTS_DIR                     │   │
│  │  c) Copies 'bootstrap' script to ARTIFACTS_DIR                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  3. sam deploy                                                          │
│     ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Deploys via CloudFormation:                                    │   │
│  │  - Creates/Updates Lambda function                              │   │
│  │  - Creates/Updates DynamoDB table                               │   │
│  │  - Creates/Updates API Gateway                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Performance Characteristics

### Cold Start Optimization

GraalVM Native Image provides significant cold start improvements:

| Metric | JVM (estimated) | Native Image |
|--------|-----------------|--------------|
| Cold Start | 3-8 seconds | 200-500 ms |
| Memory Footprint | Higher | Lower |
| Warm Invocation | Similar | Similar |

### AWS CRT Benefits

The AWS Common Runtime (CRT) HTTP client offers:
- **Native performance**: Written in C, with Java bindings
- **Better GraalVM compatibility**: Simpler native-image configuration vs Netty
- **Lower memory overhead**: Compared to Netty NIO
- **Connection pooling**: Efficient for high-throughput scenarios

---

## Load Testing

Artillery configuration (`loadtest.yml`):
```yaml
config:
  phases:
    - duration: 30      # Test duration in seconds
      arrivalRate: 150  # Requests per second
scenarios:
  - flow:
      - post:
          url: "{{ url }}"
```

**Execution:**
```bash
artillery run -t $API_GW_URL -v '{ "url": "/request-headers-graal" }' loadtest.yml
```

**CloudWatch Insights Query for Results:**
```sql
filter @type = "REPORT"
| parse @log /\d+:\/aws\/lambda\/(?<function>.*)/
| stats
    count(*) as invocations,
    pct(@duration+coalesce(@initDuration,0), 0) as p0,
    pct(@duration+coalesce(@initDuration,0), 50) as p50,
    pct(@duration+coalesce(@initDuration,0), 95) as p95,
    pct(@duration+coalesce(@initDuration,0), 99) as p99,
    pct(@duration+coalesce(@initDuration,0), 100) as p100
    group by function, ispresent(@initDuration) as coldstart
| sort by coldstart, function
```

---

## Data Flow

```
POST /request-headers-graal
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  API Gateway (REST API)                                         │
│  - Receives HTTP request                                        │
│  - Extracts headers, query params, body                        │
│  - Creates APIGatewayProxyRequestEvent                         │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  Lambda Function (RequestHeaderWriter)                          │
│  1. Extracts headers from input event                          │
│  2. Creates DynamoDB item:                                     │
│     - id: <AWS Request ID>                                     │
│     - value: <concatenated headers>                            │
│  3. Writes via Async client (id = request-id)                  │
│  4. Writes via Sync client (id = sync-<request-id>)            │
│  5. Returns 200 OK or 500 Error                                │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  DynamoDB Table: request-headers                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  { id: "abc-123", value: "Host=...Accept=..." }         │   │
│  │  { id: "sync-abc-123", value: "Host=...Accept=..." }    │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Security Considerations

1. **IAM Permissions**: Function uses `DynamoDBWritePolicy` scoped to specific table
2. **Credential Management**: Uses Lambda's built-in environment variable injection
3. **Network**: Function operates within AWS VPC by default (optional explicit VPC config)
4. **Input Validation**: Headers are concatenated without sanitization (demo purposes)

---

## Limitations & Trade-offs

| Aspect | Trade-off |
|--------|-----------|
| Build Time | Native image compilation takes 2-5 minutes vs seconds for JVM |
| Debugging | Limited debugging capabilities in native binaries |
| Reflection | Must pre-configure all reflective access |
| Dynamic Features | Dynamic class loading not supported |
| Binary Size | ~50-100 MB native binary vs smaller JAR + JVM |

---

## Future Enhancements

1. **SnapStart Integration**: Combine with Lambda SnapStart for further cold-start reduction
2. **Observability**: Add X-Ray tracing with native-image compatible configuration
3. **Input Validation**: Add request schema validation
4. **Error Handling**: Implement retry logic with exponential backoff
5. **Multi-region**: Add DynamoDB Global Tables for disaster recovery

---

## References

- [GraalVM Native Image Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [AWS SDK for Java v2 - AWS CRT Client](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-crt.html)
- [AWS Lambda Custom Runtimes](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html)
- [AWS SAM Documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/)
