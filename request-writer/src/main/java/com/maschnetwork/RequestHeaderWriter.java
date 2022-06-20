package com.maschnetwork;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class RequestHeaderWriter implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final Logger logger = LoggerFactory.getLogger(RequestHeaderWriter.class);
    private final DynamoDbAsyncClient dynamoDbClient = DynamoDbAsyncClient.builder()
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
            .httpClientBuilder(AwsCrtAsyncHttpClient.builder())
            .build();

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            Map<String, AttributeValue> itemAttributes = new HashMap<>();
            itemAttributes.put("id", AttributeValue.builder().s(context.getAwsRequestId()).build());
            itemAttributes.put("value",
                    AttributeValue.builder().s(
                            input.getHeaders().entrySet()
                                    .stream()
                                    .map(a -> a.getKey()+"="+a.getValue())
                                    .collect(Collectors.joining())
                    ).build()
            );

            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName("request-headers")
                    .item(itemAttributes)
                    .build()).get();
            return response.withBody("successful").withStatusCode(200);
        } catch (DynamoDbException | InterruptedException | ExecutionException e) {
            logger.error("Error while executing request", e);
            return response.withBody("error").withStatusCode(500);
        }
    }
}