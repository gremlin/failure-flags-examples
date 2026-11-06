# Failure Flags Go Lambda Example

1. This directory contains a prebuilt `bootstrap` (a Linux arm64 binary) and `function.zip`, built from the source in this directory. To rebuild them after changing the source, run the following with Go 1.26 or newer (older Go toolchains download 1.26 automatically):

   ```sh
   GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -tags lambda.norpc -ldflags="-s -w" -o bootstrap .
   zip function.zip bootstrap
   ```

   The `bootstrap` file must stay executable inside the zip, so build the zip on Linux or macOS, or with a tool that keeps Unix file permissions.
2. Deploy using `serverless` from the parent directory
