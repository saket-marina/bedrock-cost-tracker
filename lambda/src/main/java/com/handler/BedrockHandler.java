package com.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BedrockHandler implements RequestHandler<Map<String, String>, String> {

    // These clients are declared outside the handler method so they get
    // reused across Lambda invocations (better performance, lower cost)
    private final BedrockRuntimeClient bedrockClient = BedrockRuntimeClient.builder()
            .region(Region.US_EAST_1)
            .build();

    private final DynamoDbClient dynamoClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    // Table name pulled from environment variable (we'll set this in CDK)
    private final String TABLE_NAME = System.getenv("TABLE_NAME");

    @Override
    public String handleRequest(Map<String, String> event, Context context) {
        try {
            // Get the prompt from the incoming request
            String prompt = event.get("prompt");
            if (prompt == null || prompt.isEmpty()) {
                return "Error: no prompt provided";
            }

            // Build the request body for Claude
            // This is the format Bedrock expects for Claude models
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", 500);

            // System prompt marked for caching - this gets cached after first call
            requestBody.put("system", new Object[] {
                    Map.of(
                            "type", "text",
                            "text", "You are an expert AWS solutions architect. " +
                                    "AWS Lambda is a serverless compute service. " +
                                    "Amazon S3 is object storage. " +
                                    "DynamoDB is a NoSQL database. " +
                                    "IAM controls access. " +
                                    "CloudFormation is infrastructure as code. " +
                                    "Bedrock provides foundation models. " +
                                    "Athena queries S3 with SQL. " +
                                    "API Gateway manages APIs. " +
                                    "CloudWatch monitors resources. " +
                                    "SQS is a message queue. " +
                                    "SNS is a notification service. " +
                                    "EC2 provides virtual machines. " +
                                    "RDS is a managed relational database. " +
                                    "ECS runs containers. " +
                                    "EKS manages Kubernetes. " +
                                    "Route53 is DNS. " +
                                    "CloudFront is a CDN. " +
                                    "ElastiCache provides caching. " +
                                    "Secrets Manager stores secrets. " +
                                    "Step Functions orchestrates workflows. " +
                                    "EventBridge routes events. " +
                                    "Kinesis processes streaming data. " +
                                    "Glue is an ETL service. " +
                                    "Redshift is a data warehouse. " +
                                    "CodePipeline automates deployments. " +
                                    "CodeBuild compiles code. " +
                                    "CodeDeploy deploys applications. " +
                                    "X-Ray traces requests. " +
                                    "WAF protects against attacks. " +
                                    "Shield protects against DDoS. " +
                                    "GuardDuty detects threats. " +
                                    "Macie protects sensitive data. " +
                                    "Config tracks resource changes. " +
                                    "Trusted Advisor recommends optimizations. " +
                                    "Cost Explorer analyzes spending. " +
                                    "Budgets alerts on costs. " +
                                    "Organizations manages multiple accounts. " +
                                    "Control Tower governs accounts. " +
                                    "Service Catalog manages approved products. " +
                                    "Systems Manager manages infrastructure. " +
                                    "Parameter Store stores configuration. " +
                                    "AppConfig manages application configuration. " +
                                    "Amplify builds web and mobile apps. " +
                                    "AppSync is a GraphQL service. " +
                                    "Cognito manages user authentication. " +
                                    "Pinpoint sends targeted messages. " +
                                    "Connect is a contact center service. " +
                                    "Lex builds conversational interfaces. " +
                                    "Polly converts text to speech. " +
                                    "Rekognition analyzes images and video. " +
                                    "Textract extracts text from documents. " +
                                    "Comprehend analyzes text. " +
                                    "Translate translates text. " +
                                    "Forecast predicts future values. " +
                                    "Personalize recommends items. " +
                                    "SageMaker builds and trains ML models. " +
                                    "Answer all questions clearly and concisely as an AWS expert would." +
                                    "When helping with AWS architecture questions, consider the following principles from the AWS Well-Architected Framework: "
                                    +
                                    "The Operational Excellence pillar includes the ability to support development and run workloads effectively, gain insight into their operations, and to continuously improve supporting processes and procedures to deliver business value. "
                                    +
                                    "The Security pillar encompasses the ability to protect data, systems, and assets to take advantage of cloud technologies to improve your security. "
                                    +
                                    "The Reliability pillar encompasses the ability of a workload to perform its intended function correctly and consistently when it's expected to. "
                                    +
                                    "The Performance Efficiency pillar includes the ability to use computing resources efficiently to meet system requirements, and to maintain that efficiency as demand changes and technologies evolve. "
                                    +
                                    "The Cost Optimization pillar includes the ability to run systems to deliver business value at the lowest price point. "
                                    +
                                    "The Sustainability pillar focuses on minimizing the environmental impacts of running cloud workloads. "
                                    +
                                    "Always consider these six pillars when recommending AWS solutions. " +
                                    "For cost optimization specifically, consider using reserved instances for predictable workloads, spot instances for fault-tolerant workloads, and right-sizing resources to avoid over-provisioning. "
                                    +
                                    "For security, always recommend least-privilege IAM policies, encryption at rest and in transit, and enabling CloudTrail for audit logging. "
                                    +
                                    "For reliability, recommend multi-AZ deployments, automated backups, and health checks. "
                                    +
                                    "Answer all questions clearly and concisely as an AWS expert would." +
                                    "When designing AWS architectures, always consider the following best practices: " +
                                    "Use Auto Scaling groups to maintain application availability and scale EC2 capacity up or down automatically. "
                                    +
                                    "Implement VPCs with public and private subnets to isolate resources and control network access. "
                                    +
                                    "Use Elastic Load Balancing to distribute incoming traffic across multiple targets. "
                                    +
                                    "Implement caching strategies using ElastiCache or CloudFront to reduce latency and database load. "
                                    +
                                    "Use SQS and SNS to decouple application components and improve fault tolerance. " +
                                    "Store application state in DynamoDB or ElastiCache rather than on EC2 instances to enable stateless architectures. "
                                    +
                                    "Use S3 for durable, scalable object storage and enable versioning for important data. "
                                    +
                                    "Implement CI/CD pipelines using CodePipeline, CodeBuild, and CodeDeploy for automated deployments. "
                                    +
                                    "Monitor applications using CloudWatch metrics, logs, and alarms to detect and respond to operational issues. "
                                    +
                                    "Use AWS X-Ray for distributed tracing to identify performance bottlenecks in microservices architectures. "
                                    +
                                    "Use AWS Config and CloudTrail to maintain compliance, audit resource configurations, and track all API activity across your AWS infrastructure for security and governance purposes. "
                                    +
                                    "Implement disaster recovery strategies using AWS Backup, cross-region replication, and multi-region architectures to meet your RTO and RPO requirements. "
                                    +
                                    "Use AWS Organizations and Service Control Policies to enforce governance across multiple AWS accounts and prevent accidental or unauthorized changes to critical resources. "
                                    +
                                    "Implement blue-green deployments and canary releases using CodeDeploy or Elastic Beanstalk to minimize downtime and risk during application updates. "
                                    +
                                    "Use Amazon Route 53 health checks and DNS failover to automatically route traffic away from unhealthy endpoints and improve application availability. "
                                    +
                                    "Leverage AWS Savings Plans and Reserved Instances for predictable workloads to reduce compute costs by up to 72% compared to on-demand pricing. "
                                    +
                                    "Use Amazon CloudFront as a CDN to cache content at edge locations worldwide, reducing latency for global users and offloading traffic from origin servers. "
                                    +
                                    "Implement AWS WAF rules to protect applications from common web exploits like SQL injection and cross-site scripting attacks. "
                                    +
                                    "Use Amazon SQS dead-letter queues to capture and analyze messages that fail processing, improving application reliability and debugging capabilities. "
                                    +
                                    "Always tag AWS resources with consistent naming conventions for cost allocation, operational management, and compliance tracking across your organization. ",
                            "cache_control", Map.of("type", "ephemeral"))
            });

            requestBody.put("messages", new Object[] {
                    Map.of("role", "user", "content", prompt)
            });

            String requestJson = mapper.writeValueAsString(requestBody);

            // Call Bedrock (Claude Haiku - cheapest model, good for testing)
            long startTime = System.currentTimeMillis();

            InvokeModelResponse response = bedrockClient.invokeModel(
                    InvokeModelRequest.builder()
                            .modelId("us.anthropic.claude-sonnet-4-5-20250929-v1:0")
                            .contentType("application/json")
                            .body(SdkBytes.fromUtf8String(requestJson))
                            .build());

            long latencyMs = System.currentTimeMillis() - startTime;

            // Parse the response
            Map<String, Object> responseBody = mapper.readValue(
                    response.body().asUtf8String(), Map.class);

            // Extract the actual text response
            String responseText = (String) ((Map<String, Object>) ((java.util.List<?>) responseBody.get("content"))
                    .get(0)).get("text");

            // Extract token usage from response
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            int inputTokens = (int) usage.get("input_tokens");
            int outputTokens = (int) usage.get("output_tokens");

            // Cache tokens - present means it was a cache hit
            int cacheReadTokens = usage.containsKey("cache_read_input_tokens")
                    ? (int) usage.get("cache_read_input_tokens")
                    : 0;
            int cacheWriteTokens = usage.containsKey("cache_creation_input_tokens")
                    ? (int) usage.get("cache_creation_input_tokens")
                    : 0;
            boolean cacheHit = cacheReadTokens > 0;

            // Recalculate cost with caching
            // Cache writes cost 25% more, cache reads cost 10% of normal
            double inputCost = (inputTokens / 1_000_000.0) * 1.00;
            double outputCost = (outputTokens / 1_000_000.0) * 5.00;
            double cacheWriteCost = (cacheWriteTokens / 1_000_000.0) * 1.25;
            double cacheReadCost = (cacheReadTokens / 1_000_000.0) * 0.10;
            double totalCost = inputCost + outputCost + cacheWriteCost + cacheReadCost;

            // Log to DynamoDB
            logToDynamo(prompt, responseText, inputTokens, outputTokens,
                    cacheReadTokens, cacheWriteTokens, cacheHit, totalCost, latencyMs);

            return responseText;

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private void logToDynamo(String prompt, String response, int inputTokens,
            int outputTokens, int cacheReadTokens, int cacheWriteTokens,
            boolean cacheHit, double totalCost, long latencyMs) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("requestId", AttributeValue.fromS(UUID.randomUUID().toString()));
        item.put("timestamp", AttributeValue.fromS(Instant.now().toString()));
        item.put("model", AttributeValue.fromS("claude-sonnet-4"));
        item.put("prompt", AttributeValue.fromS(prompt));
        item.put("response", AttributeValue.fromS(response));
        item.put("inputTokens", AttributeValue.fromN(String.valueOf(inputTokens)));
        item.put("outputTokens", AttributeValue.fromN(String.valueOf(outputTokens)));
        item.put("cacheReadTokens", AttributeValue.fromN(String.valueOf(cacheReadTokens)));
        item.put("cacheWriteTokens", AttributeValue.fromN(String.valueOf(cacheWriteTokens)));
        item.put("cacheHit", AttributeValue.fromBool(cacheHit));
        item.put("totalCostUsd", AttributeValue.fromN(String.format("%.8f", totalCost)));
        item.put("latencyMs", AttributeValue.fromN(String.valueOf(latencyMs)));

        dynamoClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
    }
}