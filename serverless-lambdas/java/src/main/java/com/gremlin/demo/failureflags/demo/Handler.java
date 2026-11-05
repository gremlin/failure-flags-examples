package com.gremlin.demo.failureflags.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.gremlin.failureflags.FailureFlags;
import com.gremlin.failureflags.FailureFlag;
import com.gremlin.failureflags.GremlinFailureFlags;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

  private final FailureFlags gremlin;

  public Handler() {
    gremlin = new GremlinFailureFlags();
  }

  @Override
  public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
    Instant start = Instant.now();

    Map<String, Object> http = httpContext(input);
    Map<String, String> labels = new HashMap<>();
    labels.put("method", String.valueOf(http.getOrDefault("method", "")));
    labels.put("path", String.valueOf(http.getOrDefault("path", "")));
    gremlin.invoke(new FailureFlag("http-ingress", labels));

    Duration processingTime = Duration.between(start, Instant.now());

    Response responseBody = new Response(processingTime.toMillis());
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return ApiGatewayResponse.builder()
        .setStatusCode(200)
        .setObjectBody(responseBody)
        .setHeaders(headers)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> httpContext(Map<String, Object> input) {
    if (input != null && input.get("requestContext") instanceof Map) {
      Object http = ((Map<String, Object>) input.get("requestContext")).get("http");
      if (http instanceof Map) {
        return (Map<String, Object>) http;
      }
    }
    return Collections.emptyMap();
  }
}
