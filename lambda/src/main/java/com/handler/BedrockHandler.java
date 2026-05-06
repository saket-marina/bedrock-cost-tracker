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
            // 1. Get the prompt from the incoming request
            String prompt = event.get("prompt");
            if (prompt == null || prompt.isEmpty()) {
                return "Error: no prompt provided";
            }

            // 2. Build the request body for Claude
            // This is the format Bedrock expects for Claude models
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", 500);
            requestBody.put("messages", new Object[] {
                    Map.of("role", "user", "content", prompt)
            });

            String requestJson = mapper.writeValueAsString(requestBody);

            // 3. Call Bedrock (Claude Haiku - cheapest model, good for testing)
            long startTime = System.currentTimeMillis();

            InvokeModelResponse response = bedrockClient.invokeModel(
                    InvokeModelRequest.builder()
                            .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
                            .contentType("application/json")
                            .body(SdkBytes.fromUtf8String(requestJson))
                            .build());

            long latencyMs = System.currentTimeMillis() - startTime;

            // 4. Parse the response
            Map<String, Object> responseBody = mapper.readValue(
                    response.body().asUtf8String(), Map.class);

            // Extract the actual text response
            String responseText = (String) ((Map<String, Object>) ((java.util.List<?>) responseBody.get("content"))
                    .get(0)).get("text");

            // Extract token usage from response
            Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
            int inputTokens = (int) usage.get("input_tokens");
            int outputTokens = (int) usage.get("output_tokens");

            // 5. Calculate cost (Claude Haiku pricing: $1/$5 per million tokens)
            double inputCost = (inputTokens / 1_000_000.0) * 1.00;
            double outputCost = (outputTokens / 1_000_000.0) * 5.00;
            double totalCost = inputCost + outputCost;

            // 6. Log to DynamoDB
            logToDynamo(prompt, responseText, inputTokens, outputTokens, totalCost, latencyMs);

            return responseText;

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private void logToDynamo(String prompt, String response, int inputTokens,
            int outputTokens, double totalCost, long latencyMs) {
        Map<String, AttributeValue> item = new HashMap<>();

        // Each call gets a unique ID + timestamp
        item.put("requestId", AttributeValue.fromS(UUID.randomUUID().toString()));
        item.put("timestamp", AttributeValue.fromS(Instant.now().toString()));
        item.put("model", AttributeValue.fromS("claude-4.5-haiku"));
        item.put("prompt", AttributeValue.fromS(prompt));
        item.put("response", AttributeValue.fromS(response));
        item.put("inputTokens", AttributeValue.fromN(String.valueOf(inputTokens)));
        item.put("outputTokens", AttributeValue.fromN(String.valueOf(outputTokens)));
        item.put("totalCostUsd", AttributeValue.fromN(String.format("%.8f", totalCost)));
        item.put("latencyMs", AttributeValue.fromN(String.valueOf(latencyMs)));
        item.put("cacheHit", AttributeValue.fromBool(false)); // we'll use this in project 2

        dynamoClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
    }
}