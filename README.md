# Bedrock Cost Tracker
 
A serverless AWS application that tracks the cost of every Amazon Bedrock (Claude) API call in real time, with prompt caching to reduce costs on repeated requests.
 
## What it does
- Accepts a prompt via Lambda invocation
- Calls Claude Sonnet 4.5 via Amazon Bedrock
- Caches the system prompt to reduce input token costs by up to 90%
- Logs token usage, cache hits/misses, cost, and latency to DynamoDB for every request
## Architecture
 
```
HTTP Request → Lambda (Java 21)
                    ↓
             Bedrock (Claude Sonnet 4.5)
             with prompt caching
                    ↓
             DynamoDB (cost logs)
```
 
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
 
4. Bootstrap and deploy:
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
# Full scan
aws dynamodb scan --table-name bedrock-cost-logs
 
# Cost and cache summary
aws dynamodb scan --table-name bedrock-cost-logs \
  --query "Items[*].{time:timestamp.S,inputTokens:inputTokens.N,cacheHit:cacheHit.BOOL,cacheWrite:cacheWriteTokens.N,cacheRead:cacheReadTokens.N,cost:totalCostUsd.N}" \
  --output table
```
 
## How prompt caching works
 
The system prompt is marked with `cache_control: ephemeral`. On the first request, Bedrock processes and caches it (cache write). On subsequent requests within 5 minutes, Bedrock reads from cache instead of reprocessing — reducing input token costs significantly.
 
| Call type | What happens | Cost impact |
|-----------|-------------|-------------|
| Cache write (first call) | System prompt cached | +25% on cached tokens |
| Cache hit (subsequent calls) | System prompt read from cache | -90% on cached tokens |
 
## DynamoDB schema
 
| Field | Description |
|-------|-------------|
| requestId | Unique ID per call |
| timestamp | When the call was made |
| model | Model used |
| inputTokens | Number of input tokens |
| outputTokens | Number of output tokens |
| cacheWriteTokens | Tokens written to cache |
| cacheReadTokens | Tokens read from cache |
| cacheHit | Whether cache was used |
| totalCostUsd | Estimated cost in USD |
| latencyMs | Response time in ms |
 
## Next steps
- Athena queries for cost trend analysis across many requests
- API Gateway to expose Lambda as an HTTP endpoint
- CloudWatch dashboard for real-time cost monitoring
 