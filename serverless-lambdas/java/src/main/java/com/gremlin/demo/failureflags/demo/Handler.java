package com.gremlin.demo.failureflags.demo;

import java.time.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.gremlin.failureflags.FailureFlags;
import com.gremlin.failureflags.FailureFlag;
import com.gremlin.failureflags.GremlinFailureFlags;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

  private static final Logger LOG = Logger.getLogger(Handler.class);
  private final FailureFlags gremlin;

  public Handler() {
    gremlin = new GremlinFailureFlags();
  }

  @Override
  public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
    LocalDateTime start = LocalDateTime.now();

    gremlin.invoke(new FailureFlag("http-ingress", Map.of("method", "POST")));

    LocalDateTime end = LocalDateTime.now();
    Duration processingTime = Duration.between(start, end);

    Response responseBody = new Response(processingTime.toMillis());
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return ApiGatewayResponse.builder()
        .setStatusCode(200)
        .setObjectBody(responseBody)
        .setHeaders(headers)
        .build();
  }
}
