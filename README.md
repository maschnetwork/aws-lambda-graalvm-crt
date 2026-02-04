# ⚡ AWS Lambda Java GraalVM + AWS CRT Client

> **Blazing-fast serverless Java** — Achieve near-instant cold starts and high-throughput DynamoDB operations by combining GraalVM native images with AWS's high-performance CRT HTTP client.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![GraalVM](https://img.shields.io/badge/GraalVM-Native%20Image-blue?style=flat-square)
![AWS Lambda](https://img.shields.io/badge/AWS-Lambda-FF9900?style=flat-square&logo=aws-lambda)
![DynamoDB](https://img.shields.io/badge/Amazon-DynamoDB-4053D6?style=flat-square&logo=amazon-dynamodb)

---

## 📖 Overview

This project demonstrates how to build **production-ready, high-performance AWS Lambda functions in Java** that rival the speed of interpreted languages like Python and Node.js.

### The Challenge

Traditional Java Lambda functions suffer from:
- 🐢 **Slow cold starts** (often 3-10+ seconds)
- 📦 **Large deployment packages**
- 💰 **Higher costs** due to longer execution times

### The Solution

This sample application combines two powerful technologies:

| Technology | Benefit |
|------------|---------|
| **GraalVM Native Image** | Compiles Java ahead-of-time to a native executable — eliminating JVM startup overhead and reducing cold starts to **milliseconds** |
| **AWS CRT HTTP Client** | AWS's Common Runtime client written in C — provides **lower latency** and **higher throughput** than traditional Java HTTP clients (Netty, Apache) |

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   API Gateway   │────▶│  AWS Lambda     │────▶│   DynamoDB      │
│   (REST API)    │     │  (GraalVM Native)│     │  (request-headers)│
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │
        │                       │ AWS CRT HTTP Client
        │                       │ (High-performance C runtime)
        ▼                       ▼
   POST /request-headers-graal
   → Stores request headers in DynamoDB
```

**What the Lambda does:**
1. Receives incoming HTTP requests via API Gateway
2. Extracts all request headers
3. Stores them in DynamoDB using both **async** and **sync** CRT clients
4. Returns a success/error response

---

## 📊 Performance Results

![Load Test Results](result.png)

| Metric | Description |
|--------|-------------|
| **0** | Warm start (pre-initialized container) |
| **1** | Cold start (init duration + execution duration) |

> 💡 **Key Insight**: GraalVM native images dramatically reduce the "cold start penalty" that typically plagues Java Lambda functions.

---

## 🚀 Quick Start

### Prerequisites

Before you begin, ensure you have:

- ☁️ **AWS Account** with appropriate permissions
- 🐳 **Docker** installed and running
- 🛠️ **AWS SAM CLI** ([installation guide](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html))
- 🎯 **Artillery** for load testing (`npm install -g artillery`)

### Step 1: Build the GraalVM Container Image

Create a custom build environment with GraalVM and Maven:

```bash
docker build -t al2023-graalvm:maven .
```

### Step 2: Build the Application

Compile the Java code into a native executable using SAM:

```bash
sam build --build-image al2023-graalvm:maven --use-container
```

> ⏱️ **Note**: Native image compilation can take several minutes — this is normal!

### Step 3: Deploy to AWS

Deploy all resources (Lambda, API Gateway, DynamoDB table):

```bash
# First deployment (interactive configuration)
sam deploy --guided

# Subsequent deployments
sam deploy
```

---

## 🧪 Testing

### Manual Testing

1. **Get your API Gateway URL:**

```bash
export API_GW_URL=$(aws cloudformation describe-stacks \
  --stack-name <YOUR-STACK-NAME> \
  | jq -r '.Stacks[0].Outputs[] | select(.OutputKey == "RequestHeaderGraalApi").OutputValue')
```

2. **Send a test request:**

```bash
curl -XPOST "$API_GW_URL/request-headers-graal" \
  --header 'Content-Type: application/json' \
  --header 'X-Custom-Header: test-value'
```

### Load Testing with Artillery

Run a 30-second load test with 150 requests/second:

```bash
artillery run -t $API_GW_URL -v '{ "url": "/request-headers-graal" }' loadtest.yml
```

### Analyzing Results

Query CloudWatch Logs Insights to see detailed latency percentiles:

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

---

## 📁 Project Structure

```
.
├── Dockerfile              # GraalVM + Maven build environment
├── template.yaml           # SAM template (Lambda, API Gateway, DynamoDB)
├── loadtest.yml            # Artillery load test configuration
├── result.png              # Sample load test results
└── request-writer/
    ├── pom.xml             # Maven dependencies (AWS SDK, CRT client)
    ├── Makefile            # Native image build commands
    └── src/
        └── main/
            ├── java/       # Lambda handler code
            └── resources/  # GraalVM native-image configuration
```

---

## 🔧 Key Dependencies

| Dependency | Purpose |
|------------|---------|
| `aws-lambda-java-core` | Lambda runtime interfaces |
| `aws-lambda-java-events` | API Gateway event types |
| `software.amazon.awssdk:dynamodb` | DynamoDB SDK v2 |
| `software.amazon.awssdk:aws-crt-client` | High-performance HTTP client |

---

## 📚 Learn More

- [GraalVM Native Image Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [AWS CRT HTTP Client](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-crt.html)
- [AWS Lambda Custom Runtimes](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html)
- [AWS SAM Documentation](https://docs.aws.amazon.com/serverless-application-model/)

---

## 📄 License

This project is provided as a sample for educational purposes.
