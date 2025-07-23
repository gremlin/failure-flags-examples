# Quickstart: Failure Flags Sidecar by Proxy

1. Signup for a Gremlin account and get your team certificate and key from https://app.gremlin.com/settings/company/options under Team Settings > Details.
2. Create a failure-flags.conf file from the example at https://assets.gremlin.com/ff-example.yaml.
3. Enable the ingress or lambda proxy in the configuration file. If using the ingress proxy provide the endpoint for your service.
4. Enable the dependency proxy 
5. Add that file into your deployment (as a volume, etc).
6. Add the Failure Flags Sidecar to your deployment (as a container, sidecar, etc) and make sure to set the environment variables:
  1. `GREMLIN_SIDECAR_ENABLED=true` to enable the sidecar.
  2. `GREMLIN_CONFIG_FILE` or `GREMLIN_CONFIG_ARN` environment variable so the sidecar can find the configuration.
  3. `GREMLIN_DEBUG=true` to enable debug logging for triage during your first setup.
7. Deploy your stack, watch the logs, and verify connection with Gremlin.
8. Drive traffic to your service and watch for the `ingress`, `http-ingress`, `response` and other Failure Flags to appear in the [Gremlin UI](https://app.gremlin.com/failure-flags/list).
9. Create your first experiment: crash your service using the `ingress` Failure Flag.
