# Putting Lambda Challenges Together

**Challenge** You want to build your own lambda layer, use that with failure flags by proxy (both Lambda ingress and dependency proxies), resolve a conflict with a Dynatrace lambda extension, and route external calls through a corporate proxy. This guide is for you.

### Step 2: Creating your own bootstrap to resolve the Dynatrace conflict

Both Failure Flags by Proxy and Dynatrace require bootstrap scripts. This means that both solutions require that you set the `AWS_LAMBDA_EXEC_WRAPPER` environment variable. Dynatrace requires that you set it to `/opt/dynatrace`. The Failure Flags bootstrap script is relatively simple and composing the two into one is simple.

The strategy to resolve this conflict involves writing your own script that will chain it with Dynatrace and set `AWS_LAMBDA_EXEC_WRAPPER` to the location of your script. The script shown below assumes that the runtime environment has `bash` available.

Write the following to a script named `custom-bootstrap.sh`:
```sh
#!/bin/bash
set -euo pipefail

# Configure Lambda Runtime API for Failure Flags by Proxy
port_to_use=${GREMLIN_LAMBDA_API_PROXY_PORT:-"localhost:5033"}
export AWS_LAMBDA_RUNTIME_API="http://$port_to_use"

# Set any additional custom environment variables that you always
# need or might be too big for the standard env var system.
# export GREMLIN_DEBUG=true

# Execute the next step (dynatrace) and then the original Lambda runtime with the modified environment
exec -- /opt/dynatrace "$@"
```

Make sure to set the executable permission on the file:

```sh
chmod a+x ./custom-bootstrap.sh
```

And add it to the root of your layer.

When you've added this to your custom lambda layer, make sure that it is attached last in the order of layers for functions where it is used. Finally, make sure to set `AWS_LAMBDA_EXEC_WRAPPER=/opt/custom-bootstrap.sh` in the functions where it is used.
