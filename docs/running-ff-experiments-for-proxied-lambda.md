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
| `egress` | Triggers when calls are made to any dependency | Test all dependencies API failures, network timeouts, slow dependencies |
| `dependency-<hostname>` | One flag per external service called | Test single dependency API failures, network timeouts, slow dependencies |

### Example Scenario

For a Lambda function that:
- Receives requests via API Gateway
- Calls `api.example.com` and `database.internal.com`
- Returns JSON responses

The sidecar will automatically create:
- `ingress` - Controls Lambda invocation behavior
- `http-ingress` - Controls HTTP request processing  
- `response` - Controls response behavior
- `egress` - Controls all calls to HTTP(S) dependencies
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
  "latency": {
    "ms": 5000,
    "jitter": 1000
  }
}
```

**Applicable Flags:**
- `ingress` - Delays Lambda invocation processing
- `http-ingress` - Delays HTTP request parsing
- `response` - Delays response transmission
- `egress` - Delays outbound request transmission to all dependencies
- `dependency-<hostname>` - Delays outbound request transmission to a single dependency

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
  "exception": {
    "message": "Database connection failed"
  }
}
```

**Applicable Flags:**
- `ingress` - Simulates Lambda initialization/runtime failures
- `dependency-<hostname>` - Simulates external service failures
- `egress` - Simulates external service failures

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
  "httpResponse": {
    "code": 503,
    "body": "U2VydmljZSB0ZW1wb3JhcmlseSB1bmF2YWlsYWJsZQo=" // "Service temporarily unavailable" in base64
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

### Effect Combination Effect Statement Examples

**Slow Dependency with Eventual Error:**
```json
{
  "latency": {
    "ms": 2000
  }
}
```
Then follow with:
```json
{
  "httpResponse": {
    "code": 504,
    "body": "R2F0ZXdheSB0aW1lb3V0Cg==" // "Gateway timeout" in base64
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

**Example Experiment Effect Statement:**
```json
{
  "exception": {
    "message": "Cold start initialization failed"
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

**Example Experiment Effect Statement:**
```json
{
  "httpResponse": {
    "code": 400,
    "body": "QmFkIFJlcXVlc3QK", // "Bad Request" in base64
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

**Example Experiment Effect Statement:**
```json
{
  "latency": {
    "ms": 5000
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

**Example Experiment Effect Statement:**
```json
{
  "latency": {
    "ms": 3000,
    "jitter": 250
  },
  "httpResponse": {
    "code": 503,
    "body": "U2VydmljZSBVbmF2YWlsYWJsZQo=", // "Service Unavailable" in base64
  }
}
```

---

## Step 3: Creating Your First Experiment

### 1. Start with a Simple Dependency Failure

This is the safest way to begin experimenting:

1. **Go to Gremlin UI**: [app.gremlin.com/failure-flags](https://app.gremlin.com/failure-flags/list)
4. **Click "Create Experiment"**

**Recommended First Experiment:**
- **Target your Lambda service** in the application list
- **Select a flag** (e.g., `dependency-api.example.com`)
- **Create a latency effect**
  - **Type**: Latency
  - **Delay**: 2000ms (2 seconds)
- **Duration**: 5 minutes
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
