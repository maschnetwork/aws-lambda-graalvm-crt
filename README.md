# AWS Lambda Java GraalVM with AWS CRT client

Sample AWS Lambda application with GraalVM and AWS CRT client writing to Amazon DynamoDB.

## Load test results

0.. warm start

1.. cold start (init duration + duration)

## Prerequisites

- AWS Account
- Docker
- AWS SAM CLI
- Artillery for load testing

## Setup

1. Create the bundling image:

```bash
docker build -t al2-graalvm:maven .
```

2. Build the application via the bundling image:

```bash
sam build --build-image al2-graalvm:maven --use-container
```

3. Deploy the application

```bash
sam deploy ## --guided for the first run
```

## Test the application

Get the API-Gateway Url:

```bash
 export API_GW_URL=$(aws cloudformation describe-stacks --stack-name <YOUR-STACK_NAME> | jq -r '.Stacks[0].Outputs[] | select(.OutputKey == "RequestHeaderGraalApi").OutputValue')
```

Do a sample request:

```bash
curl -XPOST "$API_GW_URL/request-headers-graal" --header 'Content-Type: application/json'
```


## Run a load test

Run the test via:

```bash
artillery run -t $API_GW_URL  -v '{ "url": "/request-headers-graal" }' loadtest.yaml
```

Retrieve results in Cloudwatch-Log insights via:

```bash
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