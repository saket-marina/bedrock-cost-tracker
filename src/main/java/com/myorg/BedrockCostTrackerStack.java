package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.iam.PolicyStatement;

import java.util.Map;
import java.util.List;

public class BedrockCostTrackerStack extends Stack {

    public BedrockCostTrackerStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public BedrockCostTrackerStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // DynamoDB table to store every Bedrock call
        // requestId is the partition key - every call gets a unique ID
        Table costTable = Table.Builder.create(this, "BedrockCostTable")
                .tableName("bedrock-cost-logs")
                .partitionKey(Attribute.builder()
                        .name("requestId")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST) // no capacity planning needed
                .build();

        // Lambda function - points to our compiled jar
        Function bedrockHandler = Function.Builder.create(this, "BedrockHandler")
                .functionName("bedrock-cost-tracker")
                .runtime(Runtime.JAVA_21)
                .code(Code.fromAsset("lambda/target/bedrock-cost-tracker-lambda-1.0-SNAPSHOT-shaded.jar"))
                .handler("com.handler.BedrockHandler::handleRequest")
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .environment(Map.of(
                        "TABLE_NAME", costTable.getTableName()))
                .build();

        // Give Lambda permission to call Bedrock
        bedrockHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "bedrock:InvokeModel",
                        "aws-marketplace:ViewSubscriptions",
                        "aws-marketplace:Subscribe"))
                .resources(List.of("*"))
                .build());

        // Give Lambda permission to write to DynamoDB
        costTable.grantWriteData(bedrockHandler);
    }
}