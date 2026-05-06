# Bedrock Cost Tracker

A serverless AWS application that tracks the cost of every Amazon Bedrock (Claude) API call in real time.

## What it does
- Accepts a prompt via Lambda invocation
- Calls Claude Haiku via Amazon Bedrock
- Logs token usage, cost, and latency to DynamoDB for every request

## Architecture

HTTP Request -> Lambda (Java 21)

HTTP Request -> Bedrock (Claude Haiku) -> DynamoDB (cost logs)

## Stack
- **Language:** Java 21
- **Infrastructure:** AWS CDK
- **Services:** Lambda, Bedrock, DynamoDB

## Setup
1. Install prerequisites: Java 21, Maven, AWS CLI, AWS CDK
2. Configure AWS credentials: `aws configure`
3. Build the Lambda jar:
```bash
cd lambda && mvn clean package -q
cp target/bedrock-cost-tracker-lambda-1.0-SNAPSHOT.jar target/bedrock-cost-tracker-lambda-1.0-SNAPSHOT-shaded.jar
cd ..
```
4. Deploy:
```bash
cdk bootstrap
cdk deploy
```

## Testing
```bash
aws lambda invoke \
  --function-name bedrock-cost-tracker \
  --payload '{"prompt": "Your prompt here"}' \
  --cli-binary-format raw-in-base64-out \
  output.json && cat output.json
```

## Viewing logs
```bash
aws dynamodb scan --table-name bedrock-cost-logs
```

## Next steps
- Prompt caching to reduce costs by up to 90% on repeated inputs
- Athena queries for cost analysis across many requests
