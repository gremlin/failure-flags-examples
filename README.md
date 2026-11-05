# failure-flags-examples

This repository contains simple examples of the Failure Flags SDK, Lambda extension, and coprocess (sidecar).

## AWS Lambda

The Lambda examples in this repository include example Lambda applications and are deployable with the Serverless Framework. To use that example you will need to have the Serverless Framework installed and provide AWS credentials in your environment. 

## Kubernetes

The Kubernetes example in the `kubernetes` directory uses Failure Flags by Proxy and the canonical Istio demo application. It runs the `gremlin/failure-flags-sidecar:v2` image, and uses a locally cached copy of that image if one is present. To use this example you will need to provide a completed configuration file in a Kubernetes secret named `example-gremlin-secret`, containing a file named `example-config.yaml`. Start from `kubernetes/example-config.yaml` and replace the values for `team_id`, `team_certificate`, and `team_private_key`.

Run the following from the `kubernetes` directory:

1. Copy `example-config.yaml` to `secrets/example-config.yaml` and edit the copy with your team ID, certificate, and private key. The `secrets` directory is ignored by git, so your credentials stay out of the repository.
2. Run `kubectl create secret generic example-gremlin-secret --from-file ./secrets/example-config.yaml`
3. Run `kubectl apply -f ./bookinfo.yaml`
4. Run `kubectl apply -f ./expose.yaml` to expose the product page on NodePort 30007
5. Open the example app at http://localhost:30007/
