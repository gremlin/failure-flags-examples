# failure-flags-examples

This repository contains simple examples of the Failure Flags SDK, Lambda extension, and coprocess (sidecar).

## AWS Lambda

The Lambda examples in this repository include example Lambda applications and are deployable with the Serverless Framework. To use that example you will need to have the Serverless Framework installed and provide AWS credentials in your environment. 

## Kubernetes

The Kubernetes example uses Failure Flags by Proxy and the canonical Istio demo application. As is this example will pull the latest release of the Failure Flags Sidecar, however it will prefer local cached images for `gremlin/failure-flags-sidecar:latest`. To use this example you will need to provide a completed configuration file in a Kubernetes secret named, `exaxmple-gremlin-secret` and contain a file named, `example-config.yaml`. You can use the following configuration file and replace the values for `team_id`, `team_certificate`, and `team_private_key`.

1. Edit your copy of example-config.yaml with your team ID, certificate, and private key.
2. Run `kubectl create secret generic --from-file ./example-config.yaml example-gremlin-secret`
3. Run `kubectl apply -f ./bookinfo.yaml`
4. Open the example app at http://localhost:30007/
