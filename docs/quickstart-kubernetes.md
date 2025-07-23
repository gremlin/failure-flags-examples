# Quickstart: Failure Flags Sidecar by Proxy on Kubernetes

This guide shows how to add the Failure Flags Sidecar to your Kubernetes pod with **no code changes**. It enables both the **dependency proxy** (egress) and **ingress proxy**, so you can begin testing failure modes immediately.

---

## Before Enabling and Connecting the FF Sidecar Proxy

![Request routing without Failure Flags](./images/Without%20FF.png)

## After Enabling and Connecting the FF Sidecar Proxy

![Request routing with Failure Flags by Proxy](./images/With%20FFbP.png)

---

## 1. Create Your Configuration File

The Failure Flags Sidecar needs to authenticate with Gremlin's control plane and understand how your application is configured. This step creates a configuration file that tells the sidecar:

- **Who you are**: Your Gremlin team credentials
- **How to intercept traffic**: Proxy configuration for both inbound and outbound requests  
- **Where your app runs**: The port your application currently listens on

### Gather Required Information

Before creating the configuration, collect these details from your setup:

1. **Your Gremlin team ID (TEAM_ID)** - Found at https://app.gremlin.com/settings/teams
2. **Your Gremlin team certificate (CERTIFICATE)** - Downloaded from the same page
3. **Your Gremlin team private key (PRIVATE_KEY)** - Downloaded from the same page  
4. **Your application's current port (ORIGINAL_PORT)** - The port your app container currently exposes

### Create the Configuration File

Create a configuration file with your specific values. This configuration enables both proxies so the sidecar can intercept all network traffic to and from your application:

```yaml
# Your Gremlin team credentials - get these from https://app.gremlin.com/settings/teams
team_id: TEAM_ID

# Enable debug logging for easier troubleshooting during initial setup
debug: true

# Your team certificate (replace with your actual certificate)
team_certificate: |
    -----BEGIN CERTIFICATE-----
    ExampleXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXX
    -----END CERTIFICATE-----

# Your team private key (replace with your actual private key)
team_private_key: |
    -----BEGIN EC PRIVATE KEY-----
    ExampleXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
    XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX==
    -----END EC PRIVATE KEY-----

# Dependency proxy: intercepts outbound HTTP/HTTPS calls from your app
dependency_proxy_enabled: true
dependency_proxy_port: localhost:5034

# Ingress proxy: intercepts inbound traffic to your app
ingress_proxy_enabled: true
ingress_proxy_port: :5035                                    # Sidecar listens here for inbound traffic
ingress_proxied_endpoint: http://localhost:ORIGINAL_PORT     # Forward traffic to your app
```

**Configuration Explanation:**
- **dependency_proxy**: Captures outbound API calls your app makes to external services
- **ingress_proxy**: Intercepts incoming requests before they reach your application
- **ingress_proxied_endpoint**: Must point to your app's current port so the proxy can forward traffic

### Store Configuration as Kubernetes Secret

Save your configuration file (e.g., as `gremlin-config.yaml`) and create a Kubernetes secret:

```bash
# Create the secret in your target namespace
kubectl create secret generic gremlin-config --from-file=gremlin-config.yaml -n your-namespace

# Verify the secret was created
kubectl get secrets gremlin-config -n your-namespace
```

## 2. Add the Sidecar Container to Your Pod

The next step is adding the Failure Flags Sidecar as an additional container in your pod specification. This works with any Kubernetes workload type (Deployment, StatefulSet, DaemonSet, etc.).

### Understanding the Container Configuration

The sidecar container needs to:
1. **Access your configuration** via a mounted Kubernetes secret
2. **Expose the ingress proxy port** for intercepting inbound traffic  
3. **Have sufficient resources** to process network traffic
4. **Start before your application** to ensure proxy readiness

### Add to Your Pod Spec

Add this container definition to your existing pod specification:

```yaml
      # Add this container alongside your existing application container
      - name: failure-flags-sidecar
        image: docker.io/gremlin/failure-flags-sidecar:v2
        imagePullPolicy: IfNotPresent
        
        # Resource limits appropriate for a network proxy
        resources:
          requests:
            memory: "32Mi"
            cpu: "250m"
          limits:
            memory: "64Mi"
            cpu: "500m"
        
        # Expose the ingress proxy port (must match ingress_proxy_port in config)
        ports:
        - containerPort: 5035
          name: ingress-proxy
        
        # Mount the configuration secret
        volumeMounts:
        - name: gremlin-config-volume
          readOnly: true
          mountPath: "/etc/gremlin"
        
        # Environment variables to enable and configure the sidecar
        env:
          # Required: Enable the sidecar
          - name: GREMLIN_SIDECAR_ENABLED
            value: "true"
          
          # Required: Point to the mounted configuration file
          - name: GREMLIN_CONFIG_FILE
            value: "/etc/gremlin/gremlin-config.yaml"
          
          # Critical for Kubernetes: Service name must be set explicitly
          - name: SERVICE_NAME
            value: "your-service-name"  # Replace with your actual service name
          
          # Enable debug logging for initial setup
          - name: GREMLIN_DEBUG
            value: "true"
```

### Add the Volume Definition

You also need to add a volume definition to make the secret available:

```yaml
      # Add this to your pod spec's volumes section
      volumes:
      - name: gremlin-config-volume
        secret:
          secretName: gremlin-config
```

**Important Notes:**
- **Service Name**: Unlike AWS Lambda/ECS, Kubernetes requires explicitly setting `SERVICE_NAME` since it cannot be auto-detected
- **Port Configuration**: The `containerPort` must match your `ingress_proxy_port` configuration
- **Resource Limits**: Adjust based on your traffic volume - higher traffic needs more CPU/memory

## 3. Configure Your Application Container

Your application container needs two important updates to work with the Failure Flags Sidecar:

### Add Proxy Environment Variables

Update your application container to route outbound HTTP/HTTPS traffic through the dependency proxy:

```yaml
      # Your existing application container
      - name: your-app
        image: your-app:latest
        
        # Add these environment variables to route outbound traffic through the proxy
        env:
        - name: HTTP_PROXY
          value: "http://localhost:5034"
        - name: HTTPS_PROXY  
          value: "http://localhost:5034"
        
        # Your existing environment variables...
        ports:
        - containerPort: 8080  # Your app's original port
```

**Why These Are Needed:**
- **HTTP_PROXY/HTTPS_PROXY**: Tell your application to route outbound API calls through the sidecar's dependency proxy
- This enables the creation of `dependency-<hostname>` Failure Flags for each external service your app calls

## 4. Update Your Kubernetes Service

The final step is redirecting inbound traffic through the ingress proxy. Update your Kubernetes Service to point to the sidecar instead of directly to your application:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: your-service
  namespace: your-namespace
spec:
  ports:
  - port: 80                # External port (unchanged)
    targetPort: 5035        # Changed: Now points to sidecar's ingress proxy
    protocol: TCP
    name: http
  selector:
    app: your-app            # Your existing selector (unchanged)
```

**What This Change Does:**
- **Before**: Traffic flows directly to your application container
- **After**: Traffic flows through the sidecar's ingress proxy, then to your application
- The sidecar can now intercept and create Failure Flags for inbound requests

### Traffic Flow Summary

With these changes, your traffic flow becomes:
1. **Inbound**: External → Service → Sidecar (port 5035) → Your App (port 8080)
2. **Outbound**: Your App → Sidecar (port 5034) → External Services

## 5. Deploy Your Updated Configuration

Apply your updated Kubernetes resources:

```bash
# Apply your updated deployment/pod spec
kubectl apply -f your-deployment.yaml -n your-namespace

# Apply your updated service
kubectl apply -f your-service.yaml -n your-namespace

# Wait for rollout to complete
kubectl rollout status deployment/your-deployment -n your-namespace
```

## 6. Verify the Setup

Check that everything is working correctly:

```bash
# Check pod status - both containers should be running
kubectl get pods -n your-namespace

# Check sidecar logs for successful startup
kubectl logs -f deployment/your-deployment -c failure-flags-sidecar -n your-namespace

# Look for these success messages:
# [DEBUG] Dependency proxy enabled on localhost:5034
# [DEBUG] Ingress proxy enabled on :5035
# [INFO] Service registered with Gremlin as 'your-service-name'
```

## 7. Result: Automatically Created Failure Flags

Once your deployment is running and receiving traffic, the sidecar will automatically create these Failure Flags:

### For Each Incoming Request:
- **ingress** – Flagged on all incoming requests to your service
- **http-ingress** – Flagged on HTTP-specific inbound behavior  
- **response** – Flagged when your service sends responses

### For Each Outgoing Dependency:
- **dependency-<hostname>** – One flag per external service your application calls

### Viewing Your Failure Flags

1. Visit the [Gremlin UI](https://app.gremlin.com/failure-flags/list)
2. Look for your service (named according to your `SERVICE_NAME` environment variable)
3. You should see flags appearing as traffic flows through your application

## 8. Testing Your First Experiment

Try running your first failure injection:

1. **Drive some traffic** to your service to ensure flags are being created
2. **In the Gremlin UI**, find the `ingress` flag for your service  
3. **Create an experiment** to add latency or errors to incoming requests
4. **Monitor your application** to see how it handles the injected failures

## Troubleshooting

If you don't see Failure Flags appearing:

1. **Check sidecar logs** for connection errors or misconfigurations
2. **Verify traffic flow** - flags only appear when traffic flows through the proxies
3. **Confirm service name** - check that `SERVICE_NAME` matches what you expect in the Gremlin UI  
4. **Test proxy connectivity** - ensure your app can reach `localhost:5034` for outbound calls

For detailed troubleshooting, see the [Troubleshooting Guide](troubleshooting-guide.md).
