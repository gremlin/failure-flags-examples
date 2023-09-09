package com.gremlin.demo.failureflags.demo;

public class Response {
  private final long processingTime;
  public Response(long processingTime) {
    this.processingTime = processingTime;
  }
  public String getProcessingTime() {
    return this.processingTime;
  }
}
