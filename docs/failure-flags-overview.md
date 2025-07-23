# Failure Flags Overview

Failure Flags is a Chaos Engineering tool that enables targeted fault injection in modern applications, allowing teams to proactively test system resilience and identify potential failure scenarios before they impact production users.

## What are Failure Flags?

Failure Flags work similarly to feature flags, but instead of controlling feature rollouts, they allow you to inject controlled failures into your applications. This enables you to:

- **Test application resilience** by simulating real-world failure scenarios
- **Validate error handling** and fallback mechanisms
- **Build confidence** in your system's ability to handle unexpected conditions
- **Practice incident response** in a controlled environment

## Problems Solved by Failure Flags

Failure Flags address several critical challenges in modern software development:

### 1. **Limited Testing of Failure Scenarios**
Traditional testing often focuses on happy paths, leaving error conditions and edge cases under-tested. Failure Flags enable systematic testing of failure scenarios, including:
- Network timeouts and connection failures
- Incorrect or corrupt data responses
- Customer-specific failures
- Lock contention and race conditions
- Breaking API changes
- Partial service failures

### 2. **Complexity of Modern Architectures**
In distributed systems, microservices, and serverless environments, it's challenging to simulate realistic failure conditions. Failure Flags provide a way to inject failures at the application level, regardless of the underlying infrastructure.

### 3. **Production-like Testing**
Development and staging environments rarely match production complexity. Failure Flags allow you to test resilience directly in production or production-like environments safely.

### 4. **Incident Response Preparedness**
By regularly introducing controlled failures, teams can practice incident response procedures and validate monitoring and alerting systems.

## Architecture and Components

Failure Flags consists of three main components:

1. **Gremlin SaaS API**: The control plane that manages experiments, configurations, and coordinates failure injection
2. **Failure Flags Sidecar/Lambda Extension**: The local component that handles communication with Gremlin and proxies network traffic
3. **Failure Flags SDKs**: Language-specific libraries that integrate with your application code

### Safety by Design

Failure Flags is designed with multiple safety mechanisms:
- **Fail-safe operation**: Any partial or incorrect configuration won't break your application
- **No performance impact**: When experiments are disabled, there's no performance overhead
- **Multiple configuration checkpoints**: Failures must be explicitly enabled at multiple levels
- **Easy rollback**: Experiments can be stopped immediately from the Gremlin UI

## Two Approaches to Using Failure Flags

### 1. Code-Based Approach (Using SDKs)

The traditional approach involves integrating Failure Flags SDKs directly into your application code. This provides fine-grained control over when and how failures are injected.

**Supported Languages:**
- JavaScript/TypeScript/Node.js
- Python
- Go
- Java
- C#/.NET

**Example Integration:**
```javascript
const { FailureFlags } = require('@gremlin/failure-flags');

// Initialize the SDK
const failureFlags = new FailureFlags();

await gremlin.invokeFailureFlag({
  name: 'http-ingress',  // provide a human-friendly name for the point in code
  labels: {              // provide invocation-level additional metadata for specific targeting
    method: event.requestContext.http.method,
    path: event.requestContext.http.path }});

// Make normal API call
const response = await apiClient.getData();
```

**Benefits:**
- Precise control over failure injection points
- Can simulate complex application-level failures
- Integrates naturally with existing application logic

### 2. No-Code Approach (Failure Flags by Proxy)

The v2 sidecar introduces proxy-based failure injection, enabling Chaos Engineering without code changes. This approach intercepts network traffic and injects failures at the network level. The proxy is rolled out as sidecars to your workload. This minimizes any performace impact to your architecture outside of the scope of an experiment. This is the most scalable approach to rolling out fault injection capabilities in any environment.

**Available Proxy Modes:**

#### Ingress Proxy
- Intercepts inbound requests to your service
- Automatically creates `ingress` and `http-ingress` failure flags
- Can simulate response delays, errors, or corrupted responses

#### Dependency Proxy
- Intercepts outbound HTTP/HTTPS calls to external dependencies
- Creates a `dependency-<hostname>` failure flag for each HTTP(S) dependency
- Simulates network failures, slow responses, or API errors

#### Lambda Runtime API Proxy
- Specifically designed for AWS Lambda functions
- Intercepts Lambda runtime API calls
- Enables testing in serverless environments without code changes

**Benefits:**
- **Zero code changes required**
- **Automatic failure flag discovery** based on network traffic
- **Immediate deployment** to existing applications
- **Broad compatibility** with any HTTP-based application

## Platform Support

Failure Flags supports modern deployment platforms:

- **AWS Lambda**: Via the Gremlin Lambda Extension
- **AWS ECS**: As a sidecar container
- **Kubernetes**: As a sidecar container
- **Docker/Containers**: As a sidecar process
- **Virtual Machines**: As a companion process

## Getting Started

### For Code-Based Implementation:
1. Install the appropriate SDK for your language
2. Configure the Failure Flags Sidecar in your environment
3. Add Gremlin team credentials
4. Integrate SDK calls in your application code
5. Deploy and create experiments via the Gremlin UI

### For No-Code Implementation (FF by Proxy):
1. Deploy the Failure Flags Sidecar alongside your application
2. Configure proxy modes (ingress, dependency, or Lambda)
3. Set up network routing to use the sidecar proxies
4. Deploy and generate traffic to discover failure flags
5. Create experiments via the Gremlin UI

### Example Deployment (Kubernetes):
```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: app
        image: myapp:latest
        env:
        - name: HTTP_PROXY
          value: "localhost:5034"
        - name: HTTPS_PROXY
          value: "localhost:5034"
      - name: failure-flags-sidecar
        image: gremlin/failure-flags-sidecar:latest
        env:
        - name: GREMLIN_SIDECAR_ENABLED
          value: "true"
        - name: GREMLIN_CONFIG_FILE
          value: "/config/ff-config.yaml"
```

## Key Advantages

1. **Safe and Reversible**: All experiments can be stopped immediately
2. **No Vendor Lock-in**: SDKs are Apache-2.0 licensed
3. **Production Ready**: Designed for use in production environments
4. **Comprehensive Coverage**: Supports both application-level and network-level failures
5. **Easy Integration**: Works with existing CI/CD pipelines and deployment processes
6. **Automatic Discovery**: Proxy mode automatically discovers failure injection points

## Next Steps

To get started with Failure Flags:

1. **Sign up** for a Gremlin account at [app.gremlin.com](https://app.gremlin.com)
2. **Choose your approach** Code-based with SDKs or no-code with proxies
3. **Review platform-specific quickstart guides** 
4. **Deploy** the Failure Flags Sidecar in your environment
5. **Create your first experiment** to validate system resilience

For detailed implementation guides, see:
- [Configuration Guide](docs/configuration-guide.md)
- [AWS Lambda Quickstart](docs/quickstart-lambda.md)
- [Kubernetes Quickstart](docs/quickstart-kubernetes.md)
- [AWS ECS Quickstart](docs/quickstart-ecs.md)
