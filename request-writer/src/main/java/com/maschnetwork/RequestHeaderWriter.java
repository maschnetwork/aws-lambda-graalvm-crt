package com.maschnetwork;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * AWS Lambda handler that writes API Gateway request headers to DynamoDB.
 * Demonstrates usage of AWS CRT HTTP client with both sync and async DynamoDB clients.
 */
public class RequestHeaderWriter implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LogManager.getLogger(RequestHeaderWriter.class);
    private static final String TABLE_NAME = "request-headers";
    
    private final DynamoDbAsyncClient dynamoDbClient;
    private final DynamoDbClient dynamoDbSyncClient;

    public RequestHeaderWriter() {
        logger.info("Initializing RequestHeaderWriter Lambda handler");
        
        String region = System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable());
        logger.debug("Configured AWS region: {}", region);
        
        logger.debug("Building async DynamoDB client with AWS CRT HTTP client");
        this.dynamoDbClient = DynamoDbAsyncClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(Region.of(region))
                .httpClientBuilder(AwsCrtAsyncHttpClient.builder())
                .build();
        
        logger.debug("Building sync DynamoDB client with AWS CRT HTTP client");
        this.dynamoDbSyncClient = DynamoDbClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(Region.of(region))
                .httpClientBuilder(AwsCrtHttpClient.builder())
                .build();
        
        logger.info("RequestHeaderWriter initialization complete");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        // Set up logging context with request-specific information
        setupLoggingContext(context, input);
        
        logger.info("Starting request processing");
        long startTime = System.currentTimeMillis();
        
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        
        try {
            // Log request details at debug level
            logRequestDetails(input);
            
            // Process async write
            processAsyncWrite(input, context);
            
            // Process sync write
            processSyncWrite(input, context);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Request processed successfully in {} ms", duration);
            
            return response.withBody("successful").withStatusCode(200);
            
        } catch (DynamoDbException e) {
            logger.error("DynamoDB operation failed: {}", e.getMessage(), e);
            return response.withBody("error").withStatusCode(500);
        } catch (InterruptedException e) {
            logger.error("Request interrupted during async operation", e);
            Thread.currentThread().interrupt();
            return response.withBody("error").withStatusCode(500);
        } catch (ExecutionException e) {
            logger.error("Async operation execution failed: {}", e.getCause().getMessage(), e);
            return response.withBody("error").withStatusCode(500);
        } catch (Exception e) {
            logger.error("Unexpected error during request processing: {}", e.getMessage(), e);
            return response.withBody("error").withStatusCode(500);
        } finally {
            // Clean up logging context
            clearLoggingContext();
        }
    }
    
    /**
     * Sets up the logging context with request-specific information.
     * This enables request correlation in CloudWatch Logs.
     */
    private void setupLoggingContext(Context context, APIGatewayProxyRequestEvent input) {
        ThreadContext.put("awsRequestId", context.getAwsRequestId());
        ThreadContext.put("functionName", context.getFunctionName());
        ThreadContext.put("functionVersion", context.getFunctionVersion());
        
        if (input.getRequestContext() != null) {
            ThreadContext.put("apiRequestId", input.getRequestContext().getRequestId());
            ThreadContext.put("httpMethod", input.getHttpMethod());
            ThreadContext.put("path", input.getPath());
        }
        
        logger.debug("Logging context initialized for request: {}", context.getAwsRequestId());
    }
    
    /**
     * Clears the logging context after request processing.
     */
    private void clearLoggingContext() {
        ThreadContext.clearAll();
        logger.debug("Logging context cleared");
    }
    
    /**
     * Logs request details at debug level for troubleshooting.
     */
    private void logRequestDetails(APIGatewayProxyRequestEvent input) {
        if (logger.isDebugEnabled()) {
            int headerCount = input.getHeaders() != null ? input.getHeaders().size() : 0;
            logger.debug("Processing request with {} headers", headerCount);
            
            if (input.getHeaders() != null) {
                // Log header names only (not values for security)
                String headerNames = String.join(", ", input.getHeaders().keySet());
                logger.debug("Header names: {}", headerNames);
            }
        }
    }
    
    /**
     * Processes the asynchronous DynamoDB write operation.
     */
    private void processAsyncWrite(APIGatewayProxyRequestEvent input, Context context) 
            throws InterruptedException, ExecutionException {
        
        logger.debug("Starting async DynamoDB write operation");
        long asyncStartTime = System.currentTimeMillis();
        
        Map<String, AttributeValue> itemAttributes = new HashMap<>();
        itemAttributes.put("id", AttributeValue.builder().s(context.getAwsRequestId()).build());
        itemAttributes.put("value", AttributeValue.builder().s(buildHeaderString(input)).build());

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(itemAttributes)
                .build()).get();
        
        long asyncDuration = System.currentTimeMillis() - asyncStartTime;
        logger.info("Async DynamoDB write completed in {} ms, id: {}", asyncDuration, context.getAwsRequestId());
    }
    
    /**
     * Processes the synchronous DynamoDB write operation.
     */
    private void processSyncWrite(APIGatewayProxyRequestEvent input, Context context) {
        logger.debug("Starting sync DynamoDB write operation");
        long syncStartTime = System.currentTimeMillis();
        
        String syncId = "sync-" + context.getAwsRequestId();
        
        Map<String, AttributeValue> itemAttributesSync = new HashMap<>();
        itemAttributesSync.put("id", AttributeValue.builder().s(syncId).build());
        itemAttributesSync.put("value", AttributeValue.builder().s(buildHeaderString(input)).build());

        dynamoDbSyncClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(itemAttributesSync)
                .build());
        
        long syncDuration = System.currentTimeMillis() - syncStartTime;
        logger.info("Sync DynamoDB write completed in {} ms, id: {}", syncDuration, syncId);
    }
    
    /**
     * Builds a string representation of the request headers.
     */
    private String buildHeaderString(APIGatewayProxyRequestEvent input) {
        if (input.getHeaders() == null || input.getHeaders().isEmpty()) {
            logger.debug("No headers present in request");
            return "";
        }
        
        return input.getHeaders().entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining());
    }
}