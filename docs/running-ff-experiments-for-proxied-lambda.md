# Running Failure Flags Experiments for Proxied Lambda Functions

This guide covers how to create, run, and manage Failure Flags experiments on AWS Lambda functions using the proxy approach (no code changes required). It assumes you've already set up the Failure Flags Sidecar following the [Lambda Quickstart Guide](quickstart-lambda.md).

---

## Overview: What the Lambda Proxy Enables

When you enable the Failure Flags Lambda Extension with proxy mode, it automatically creates several types of failure flags based on your Lambda function's behavior:

### Automatically Created Failure Flags

| Flag Type | Description | Use Cases |
|-----------|-------------|-----------|
| `ingress` | Triggers on Lambda invocation events | Test Lambda startup failures, invocation errors |
| `http-ingress` | Triggers on HTTP-specific events (API Gateway, ALB) | Test HTTP parsing errors, malformed requests |
| `response` | Triggers when Lambda returns responses | Test response corruption, timeout scenarios |
| `dependency-<hostname>` | One flag per external service called | Test API failures, network timeouts, slow dependencies |

### Example Scenario

For a Lambda function that:
- Receives requests via API Gateway
- Calls `api.example.com` and `database.internal.com`
- Returns JSON responses

The sidecar will automatically create:
- `ingress` - Controls Lambda invocation behavior
- `http-ingress` - Controls HTTP request processing  
- `response` - Controls response behavior
- `dependency-api.example.com` - Controls calls to api.example.com
- `dependency-database.internal.com` - Controls calls to database.internal.com

---

## Available Effects for FF by Proxy

The Failure Flags sidecar supports three main effect types that can be applied to proxy-generated flags:

### 1. Latency Effect

**Purpose**: Inject delays to simulate slow responses or network issues

**Configuration:**
```json
{
  "type": "latency",
  "config": {
    "delay_ms": 5000
  }
}
```

**Applicable Flags:**
- `ingress` - Delays Lambda invocation processing
- `http-ingress` - Delays HTTP request parsing
- `response` - Delays response transmission
- `dependency-<hostname>` - Delays outbound requests

**Use Cases:**
- Test timeout handling in clients
- Simulate network congestion
- Validate retry mechanisms
- Test user experience under slow conditions

### 2. Exception Effect

**Purpose**: Inject application-level exceptions and errors

**Configuration:**
```json
{
  "type": "exception",
  "config": {
    "exception_type": "ConnectionError",
    "exception_message": "Database connection failed"
  }
}
```

**Common Exception Types:**
- `RuntimeError` - General runtime failures
- `ConnectionError` - Network connection issues  
- `TimeoutError` - Request timeout scenarios
- `MemoryError` - Memory exhaustion simulation
- `ValueError` - Invalid data/parameter errors

**Applicable Flags:**
- `ingress` - Simulates Lambda initialization/runtime failures
- `dependency-<hostname>` - Simulates external service failures

**Use Cases:**
- Test error handling and recovery
- Validate fallback mechanisms
- Simulate resource exhaustion
- Test exception propagation

### 3. HTTP Response Effect

**Purpose**: Return specific HTTP status codes and response bodies

**Configuration:**
```json
{
  "type": "httpResponse",
  "config": {
    "code": 503,
    "body": "Service temporarily unavailable",
    "headers": {
      "Retry-After": "60"
    }
  }
}
```

**Common HTTP Codes:**
- `400` - Bad Request (client errors)
- `401` - Unauthorized (authentication failures)
- `403` - Forbidden (authorization failures)
- `404` - Not Found (resource missing)
- `429` - Too Many Requests (rate limiting)
- `500` - Internal Server Error (server failures)
- `502` - Bad Gateway (proxy errors)
- `503` - Service Unavailable (maintenance/overload)
- `504` - Gateway Timeout (upstream timeouts)

**Applicable Flags:**
- `http-ingress` - Returns errors for incoming HTTP requests
- `response` - Modifies Lambda response codes and content
- `dependency-<hostname>` - Simulates error responses from external APIs

**Use Cases:**
- Test client error handling for different HTTP status codes
- Simulate API rate limiting responses
- Test authentication/authorization failure scenarios
- Validate retry logic for specific error codes

### Effect Combination Examples

**Slow Dependency with Eventual Error:**
```json
{
  "type": "latency",
  "config": {
    "delay_ms": 2000
  }
}
```
Then follow with:
```json
{
  "type": "httpResponse", 
  "config": {
    "code": 504,
    "body": "Gateway timeout after delay"
  }
}
```

**Realistic Database Failure:**
```json
{
  "type": "exception",
  "config": {
    "exception_type": "ConnectionError",
    "exception_message": "Connection pool exhausted"
  }
}
```

**API Rate Limiting Simulation:**
```json
{
  "type": "httpResponse",
  "config": {
    "code": 429,
    "body": "Rate limit exceeded",
    "headers": {
      "Retry-After": "60",
      "X-RateLimit-Remaining": "0"
    }
  }
}
```

---

## Prerequisites

Before running experiments, ensure:

1. ✅ **Lambda Extension is installed** and configured (see [quickstart-lambda.md](quickstart-lambda.md))
2. ✅ **Debug logging is enabled** (`GREMLIN_DEBUG=true`)
3. ✅ **Function has been invoked** at least once to generate traffic-based flags
4. ✅ **Gremlin UI access** at [app.gremlin.com/failure-flags](https://app.gremlin.com/failure-flags/list)

---

## Step 1: Verify Your Setup

### Check Lambda Logs

Look for these startup messages in CloudWatch Logs:

```
[DEBUG] Gremlin Lambda Extension starting...
[DEBUG] Lambda proxy enabled on port 5032
[DEBUG] Dependency proxy enabled on localhost:5034
[DEBUG] Service registered: your-lambda-function-name
[DEBUG] Ready to serve experiments
```

### Invoke Your Function

Trigger your Lambda function through its normal invocation method:

**API Gateway:**
```bash
curl https://your-api-gateway-url/your-endpoint
```

**Direct Invocation:**
```bash
aws lambda invoke \
  --function-name your-function-name \
  --payload '{"test": "data"}' \
  response.json
```

**Event Source (SQS, S3, etc.):**
Trigger the event source normally (upload file, send SQS message, etc.)

### Verify Failure Flags Appear

1. Go to [Gremlin Failure Flags UI](https://app.gremlin.com/failure-flags/list)
2. Look for your Lambda function name in the services list
3. Confirm you see the expected failure flags

**If flags don't appear**, check the [troubleshooting guide](troubleshooting-guide.md).

---

## Step 2: Understanding Experiment Types

### 1. Ingress Experiments (Lambda Invocation)

**Target:** `ingress` flag  
**Simulates:** Problems with Lambda invocation, startup, or initialization

**Common Scenarios:**
- **Cold start failures:** Simulate Lambda runtime initialization errors
- **Memory issues:** Test behavior when Lambda runs out of memory
- **Timeout scenarios:** Test Lambda timeout handling

**Example Experiment:**
```json
{
  "name": "Lambda Cold Start Failure",
  "type": "exception",
  "config": {
    "exception_type": "RuntimeError",
    "exception_message": "Cold start initialization failed"
  }
}
```

### 2. HTTP Ingress Experiments (API Gateway/ALB)

**Target:** `http-ingress` flag  
**Simulates:** HTTP-level request processing issues

**Common Scenarios:**
- **Malformed requests:** Test parsing of corrupted HTTP requests
- **Header manipulation:** Simulate missing or corrupted headers
- **Body corruption:** Test handling of invalid request bodies

**Example Experiment:**
```json
{
  "name": "Corrupted API Request",
  "type": "http_response",
  "config": {
    "status_code": 400,
    "body": "Invalid request format",
    "corrupt_body": true
  }
}
```

### 3. Response Experiments

**Target:** `response` flag  
**Simulates:** Issues when Lambda returns responses

**Common Scenarios:**
- **Response delays:** Test timeout handling in clients
- **Response corruption:** Simulate network issues affecting response
- **Error injection:** Return error status codes

**Example Experiment:**
```json
{
  "name": "Slow Response Simulation",
  "type": "latency",
  "config": {
    "delay_ms": 5000
  }
}
```

### 4. Dependency Experiments

**Target:** `dependency-<hostname>` flags  
**Simulates:** External service failures

**Common Scenarios:**
- **API timeouts:** Test handling of slow external APIs
- **Service unavailable:** Simulate 503 errors from dependencies
- **Network failures:** Test connection refused scenarios
- **Data corruption:** Simulate corrupted responses from APIs

**Example Experiment:**
```json
{
  "name": "Database Timeout",
  "type": "latency",
  "config": {
    "delay_ms": 30000
  }
}
```

---

## Step 3: Creating Your First Experiment

### 1. Start with a Simple Dependency Failure

This is the safest way to begin experimenting:

1. **Go to Gremlin UI**: [app.gremlin.com/failure-flags](https://app.gremlin.com/failure-flags/list)
2. **Find your Lambda service** in the list
3. **Select a dependency flag** (e.g., `dependency-api.example.com`)
4. **Click "Create Experiment"**

**Recommended First Experiment:**
- **Type**: Latency
- **Duration**: 30 seconds
- **Delay**: 2000ms (2 seconds)
- **Percentage**: 50% (affects half of requests)

### 2. Test the Experiment

1. **Start the experiment** in Gremlin UI
2. **Invoke your Lambda function** multiple times
3. **Observe the behavior**:
   - Some requests should be slower (delay injected)
   - Some requests should behave normally
   - Check CloudWatch Logs for experiment activation

**Expected Log Output:**
```
[DEBUG] Experiment activated: dependency-api.example.com
[DEBUG] Injecting 2000ms delay for request to api.example.com
[DEBUG] Request completed with injected delay
```

### 3. Stop and Analyze

1. **Stop the experiment** immediately after testing
2. **Review metrics**:
   - Lambda duration metrics in CloudWatch
   - Error rates in your monitoring
   - Customer impact (if any)
3. **Document learnings**:
   - How did your function handle the delay?
   - Did timeouts occur as expected?
   - Were error messages helpful?

---

## Step 4: Progressive Experiment Strategy

### Phase 1: Dependency Testing (Safest)

Start with external dependencies as they're typically easier to handle:

1. **API Latency**: Test response time tolerance
   ```json
   {"type": "latency", "config": {"delay_ms": 1000}}
   ```

2. **API Errors**: Test error handling
   ```json
   {"type": "http_response", "config": {"status_code": 500}}
   ```

3. **Connection Failures**: Test network resilience
   ```json
   {"type": "exception", "config": {"exception_type": "ConnectionError"}}
   ```

### Phase 2: Response Testing (Medium Risk)

Test how clients handle your Lambda's responses:

1. **Slow Responses**: Test client timeout handling
   ```json
   {"type": "latency", "config": {"delay_ms": 3000}}
   ```

2. **Error Responses**: Test client error handling
   ```json
   {"type": "http_response", "config": {"status_code": 503, "body": "Service temporarily unavailable"}}
   ```

### Phase 3: Ingress Testing (Higher Risk)

Test Lambda invocation and initialization:

1. **Memory Pressure**: Test low-memory scenarios
   ```json
   {"type": "exception", "config": {"exception_type": "MemoryError", "exception_message": "Out of memory"}}
   ```

2. **Initialization Failures**: Test cold start resilience
   ```json
   {"type": "exception", "config": {"exception_type": "RuntimeError", "exception_message": "Initialization failed"}}
   ```

---

## Step 5: Advanced Experiment Patterns

### 1. Time-Based Experiments

Test behavior during specific conditions:

**Peak Traffic Simulation:**
- Schedule experiments during known high-traffic periods
- Combine with load testing for realistic scenarios

**Maintenance Window Simulation:**
- Test dependency failures during planned maintenance
- Validate fallback mechanisms work correctly

### 2. Cascading Failure Simulation

Test how multiple failures interact:

1. **Start with one dependency failure**
2. **Add a second dependency failure** 10 minutes later
3. **Inject response delays** to simulate system stress
4. **Monitor overall system behavior**

### 3. Customer-Specific Testing

Target specific request patterns:

**Use Labels/Conditions:**
```json
{
  "conditions": {
    "request_headers": {
      "customer-tier": "premium"
    }
  }
}
```

### 4. Gradual Escalation

Increase experiment intensity over time:

1. **Week 1**: 10% traffic, 1-second delays
2. **Week 2**: 25% traffic, 2-second delays  
3. **Week 3**: 50% traffic, 5-second delays
4. **Week 4**: 100% traffic, 10-second delays

---

## Step 6: Monitoring and Observability

### Key Metrics to Watch

**Lambda Metrics:**
- Duration (should increase with latency experiments)
- Error rate (should increase with error experiments)
- Throttles (might increase under stress)
- Memory utilization (watch for spikes)

**CloudWatch Logs:**
```bash
# Monitor experiment activation
aws logs filter-log-events \
  --log-group-name /aws/lambda/your-function \
  --filter-pattern "[DEBUG] Experiment activated"

# Monitor errors
aws logs filter-log-events \
  --log-group-name /aws/lambda/your-function \
  --filter-pattern "ERROR"
```

**Custom Application Metrics:**
- Business KPIs (orders, signups, etc.)
- Response times from client perspective
- Error rates by customer segment

### Setting Up Alerts

**CloudWatch Alarms:**
```json
{
  "MetricName": "Duration",
  "Threshold": 10000,
  "ComparisonOperator": "GreaterThanThreshold",
  "EvaluationPeriods": 2,
  "AlarmDescription": "Lambda duration spike during experiment"
}
```

**Application-Level Monitoring:**
- Set up alerts for unexpected error rates
- Monitor customer-facing metrics
- Create dashboards for experiment visibility

---

## Step 7: Experiment Safety and Best Practices

### Safety Checklist

Before running any experiment:

- [ ] **Start small**: Low percentage, short duration
- [ ] **Monitor actively**: Watch dashboards during experiments  
- [ ] **Have rollback ready**: Know how to stop experiments quickly
- [ ] **Test during safe hours**: Avoid peak business times initially
- [ ] **Communicate**: Notify team members about active experiments
- [ ] **Document**: Keep records of what you're testing and why

### Production Experiment Guidelines

**DO:**
- Start with non-critical dependencies
- Use gradual percentage increases (5% → 10% → 25%)
- Run short initial experiments (30 seconds to 2 minutes)
- Monitor business metrics closely
- Stop experiments immediately if issues arise

**DON'T:**
- Run experiments during peak business hours initially
- Test critical path failures without team coordination
- Run multiple experiments simultaneously when starting
- Ignore monitoring during active experiments
- Run experiments longer than necessary

### Emergency Procedures

**If Something Goes Wrong:**

1. **Stop the experiment immediately**:
   - Go to Gremlin UI → Active Experiments → Stop

2. **Check system health**:
   - Verify Lambda function returns to normal behavior
   - Monitor error rates return to baseline
   - Confirm customer-facing services recover

3. **Document the incident**:
   - What experiment was running?
   - What unexpected behavior occurred?
   - How long did it take to recover?
   - What would you do differently?

---

## Step 8: Common Experiment Scenarios for Lambda

### Scenario 1: Testing API Gateway Timeout Handling

**Objective**: Verify API Gateway timeout behavior when Lambda is slow

**Setup:**
```yaml
# Experiment Config
type: latency
target: response
config:
  delay_ms: 35000  # API Gateway timeout is 30s
percentage: 100
duration: 60s
```

**Expected Results:**
- API Gateway should return 504 Gateway Timeout
- Clients should handle timeout gracefully
- Retries should work correctly

### Scenario 2: Testing Database Failover

**Objective**: Validate database connection failover works correctly

**Setup:**
```yaml
# Experiment Config  
type: exception
target: dependency-database.example.com
config:
  exception_type: ConnectionError
  exception_message: Connection refused
percentage: 100
duration: 120s
```

**Expected Results:**
- Lambda should fall back to read replica
- Error handling should be graceful
- Recovery should be automatic

### Scenario 3: Testing SQS Processing Resilience

**Objective**: Verify SQS message processing handles failures correctly

**Setup:**
```yaml
# Experiment Config
type: exception  
target: ingress
config:
  exception_type: RuntimeError
  exception_message: Message processing failed
percentage: 30
duration: 300s
```

**Expected Results:**
- Failed messages should return to SQS queue
- Retry logic should work correctly
- Dead letter queue should capture persistent failures

### Scenario 4: Testing Memory Pressure

**Objective**: Understand behavior when Lambda approaches memory limits

**Setup:**
```yaml
# Experiment Config
type: exception
target: ingress
config:
  exception_type: MemoryError
  exception_message: Insufficient memory
percentage: 20
duration: 180s
```

**Expected Results:**
- Lambda should handle memory errors gracefully
- Monitoring should detect memory issues
- Function should recover after experiment

---

## Troubleshooting Experiment Issues

### Experiments Not Triggering

**Symptoms:**
- Experiment shows as "Active" in Gremlin UI
- No behavior changes observed
- No experiment logs in CloudWatch

**Debugging:**
1. **Enable debug logging**: `GREMLIN_DEBUG=true`
2. **Check proxy configuration**:
   ```yaml
   lambda_proxy_enabled: true
   dependency_proxy_enabled: true
   ```
3. **Verify traffic routing**:
   ```bash
   # Check HTTP_PROXY settings
   echo $HTTP_PROXY
   echo $HTTPS_PROXY
   ```
4. **Confirm flag registration**:
   - Look for "Service registered" in logs
   - Verify flags appear in Gremlin UI

### Experiments Causing Unexpected Behavior

**Symptoms:**
- More impact than expected
- Different behavior than configured
- System instability

**Immediate Actions:**
1. **Stop experiment immediately**
2. **Check experiment configuration**:
   - Percentage setting
   - Duration setting  
   - Target flag selection
3. **Monitor recovery**:
   - Function returns to normal
   - Error rates decrease
   - Performance metrics normalize

### Cannot Stop Experiments

**Symptoms:**
- "Stop" button doesn't work
- Experiment continues after stopping
- Sidecar not responding

**Actions:**
1. **Restart Lambda function**:
   ```bash
   aws lambda update-function-configuration \
     --function-name your-function \
     --description "Force restart - $(date)"
   ```

2. **Check sidecar logs** for errors
3. **Verify network connectivity** to Gremlin API

---

## Next Steps

After mastering Lambda experiments:

1. **Expand to other platforms**: Try [ECS](quickstart-ecs.md) or [Kubernetes](quickstart-kubernetes.md)
2. **Automate experiments**: Use Gremlin API to script regular testing
3. **Integrate with CI/CD**: Add chaos testing to deployment pipelines
4. **Team training**: Share knowledge and expand experiment practices
5. **Advanced scenarios**: Test multi-service failures and complex scenarios

For more advanced configurations and troubleshooting, see:
- [Configuration Guide](configuration-guide.md)
- [Troubleshooting Guide](troubleshooting-guide.md)
- [Building Lambda Layers](build-lambda-layer.md)