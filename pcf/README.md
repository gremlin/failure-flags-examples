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

Click `Save and Run`

## Verify the latency injection


Before the experiment, www.example.com takes ~5ms to load
```bash
cf logs myapp --recent
   2025-08-01T09:57:05.84-0600 [APP/PROC/WEB/0] OUT Request to www.example.com - Status: 200 OK | Duration: 5.393351ms
```

During the experiment, www.example.com takes 2s to load
```bash
cf logs myapp --recent
  2025-08-01T09:53:41.11-0600 [APP/PROC/WEB/0] OUT Request to www.example.com - Status: 200 OK | Duration: 2.006431396s
```

## Cleanup

To remove your application and sidecar, run:
```bash
cf delete myapp
```
