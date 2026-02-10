# Injecting Latency with Failure Flags Sidecar by Proxy on Pivotal Cloud Foundry (PCF): A Step-by-Step Guide

Follow these steps to run a sample application that loads its dependency www.example.com and injects latency into that dependency using a Failure Flag. No changes to your application code are required to introduce latency.

## Prerequisites

- A Pivotal Cloud Foundry (PCF) environment with access to deploy applications
- CF CLI installed and configured

## Download the failure flags sidecar

amd64:
```
wget https://assets.gremlin.com/packages/failure-flags-sidecar/latest/x86_64/failure-flags-sidecar-linux.tar.gz
tar -xzf failure-flags-sidecar-linux.tar.gz
rm failure-flags-sidecar-linux.tar.gz
```

arm64:
```
wget https://assets.gremlin.com/packages/failure-flags-sidecar/latest/arm64/failure-flags-sidecar-linux.tar.gz
tar -xzf failure-flags-sidecar-linux.tar.gz
rm failure-flags-sidecar-linux.tar.gz
```

## Build the sample application (myapp)

amd64:
```
GOOS=linux GOARCH=amd64 go build -o myapp
```

arm64:
```
GOOS=linux GOARCH=arm64 go build -o myapp
```

## Update the Gremlin configuration file

Update [config.yaml](config.yaml) making sure to replace `team_id`, `team_certificate`, and `team_private_key` with your Gremlin team information.
You can find these at [Settings](https://app.gremlin.com/settings) > Team.

## Configure the Manifest to deploy the sample application and sidecar to PCF

Use [manifest.yaml](manifest.yaml) as-is for `amd64`.  
For `arm64`, update it to use the `arm64` sidecar.

## Deploy the sample application along with the Gremlin sidecar

```bash
cf push myapp -f manifest.yaml
```

## Verify the installation

Navigate to https://app.gremlin.com/failure-flags/list, you should see:
- your service `myapp`
- a dependency named `dependency-www.example.com`

## Inject Latency with a Failure Flag

Create a [Gremlin experiment](https://app.gremlin.com/failure-flags/new) with the following:
- Experiment Name: my-latency-experiment
- Failure Flag Selector: dependency-www.example.com
- Service Selector: myapp
- Effects: latency with 1000ms delay
- Impact Probability: 100%
- Experiment duration: 1m

Click `Save and Run`

## Verify the latency injection


Before the experiment, www.example.com takes ~5ms to load
```bash
cf logs myapp --recent
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [30a6a1ce-ae06-4a5d-b158-edc5549e80a0] Processing outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [30a6a1ce-ae06-4a5d-b158-edc5549e80a0] Call result, code: 200
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [30a6a1ce-ae06-4a5d-b158-edc5549e80a0] Finished outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/0] OUT Request to www.example.com - Status: 200 OK | Duration: 5.089182ms
```

During the experiment, www.example.com takes 2s to load
```bash
cf logs myapp --recent
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [3a26b92b-3d45-4f78-b01e-4812d7e62cc9] Processing outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [3a26b92b-3d45-4f78-b01e-4812d7e62cc9] Adding 1s to the connection for dependency-www.example.com, before, experiment: 67629077-f021-47cd-a290-77f02197cddf
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [3a26b92b-3d45-4f78-b01e-4812d7e62cc9] Call result, code: 200
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [3a26b92b-3d45-4f78-b01e-4812d7e62cc9] Adding 1s to the connection for dependency-www.example.com, after, experiment: 67629077-f021-47cd-a290-77f02197cddf
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [3a26b92b-3d45-4f78-b01e-4812d7e62cc9] Finished outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/0] OUT Request to www.example.com - Status: 200 OK | Duration: 2.016184518s
```

## Inject an Exception with a Failure Flag

Create a [Gremlin experiment](https://app.gremlin.com/failure-flags/new) with the following:
- Experiment Name: my-exception-experiment
- Failure Flag Selector: dependency-www.example.com
- Service Selector: myapp
- Effects: exception
- Impact Probability: 100%
- Experiment duration: 1m

Click `Save and Run`

## Verify the exception injection

Before the experiment, your dependency returns a `200 OK`
```bash
cf logs myapp --recent
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [20ac4b65-9e7e-43b3-addd-9a62cb502dc0] Processing outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [20ac4b65-9e7e-43b3-addd-9a62cb502dc0] Call result, code: 200
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [20ac4b65-9e7e-43b3-addd-9a62cb502dc0] Finished outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/0] OUT Request to www.example.com - Status: 200 OK | Duration: 97.207249ms
```

During the experiment, your dependency returns a `500 Internal Server Error`
```bash
cf logs myapp --recent
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [6872c310-31b7-4bb7-a8af-13a0d54fcf58] Processing outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [6872c310-31b7-4bb7-a8af-13a0d54fcf58] Responding with 500 for request to dependency-www.example.com, experiment: 6ee9fc3e-c38d-4fd1-a9fc-3ec38dafd156
   [APP/PROC/WEB/SIDECAR/GREMLIN-SIDECAR/0] OUT [failure-flags-sidecar-amd64-linux] [http-dependency-proxy] [debug] [6872c310-31b7-4bb7-a8af-13a0d54fcf58] Finished outbound request for URL: http://www.example.com/
   [APP/PROC/WEB/0] OUT Request to www.example.com - Status: 500 Internal Server Error | Duration: 2.543772ms
```

## Cleanup

To remove your application and sidecar, run:
```bash
cf delete myapp
```

## Troubleshooting
 
```
[STG/0] [ERR] Unable to interpolate credhub refs: Unable to interpolate credhub references: Post "https://credhub:8844/api/v1/interpolate": proxyconnect tcp: dial tcp 127.0.0.1:5034: connect: connection refused
```

If the staging steps fails to connect to a dependency (e.g., credhub), configure the `HTTP_PROXY`/`HTTPS_PROXY` environment variables for the runtime container (not globally), as follows:

1. Create a file called `.profile` in the root directory of your application with this content:
```
   export HTTP_PROXY="http://localhost:5034"
   export HTTPS_PROXY="http://localhost:5034"
```

2. Remove `HTTP_PROXY`/`HTTPS_PROXY` as global variables from the `env` block in the manifest file:
```yaml
   env:
     # HTTP_PROXY: "http://localhost:5034"   # remove
     # HTTPS_PROXY: "http://localhost:5034"  # remove
```
