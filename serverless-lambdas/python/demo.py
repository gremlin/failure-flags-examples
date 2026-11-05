import json
import logging
import time
from failureflags import FailureFlag

logger = logging.getLogger()
logger.setLevel(logging.INFO)
# The SDK writes debug output to the "failureflags" logger. Lower its level so
# that debug=True below is visible in CloudWatch Logs.
logging.getLogger("failureflags").setLevel(logging.DEBUG)

def handler(event, context):
    now = time.gmtime()
    start = time.time()

    http = (event or {}).get('requestContext', {}).get('http', {})

    # Note: This is an example of a line you need to add to
    # your application code for each failure flag.
    active, impacted, _ = FailureFlag(
            "http-ingress", # name of the failure flag
            { # dict of labels with dynamic invocation context
                "method": http.get("method", ""),
                "path": http.get("path", ""),
            },
            debug=True).invoke()
    end = time.time()
    return {
        'statusCode': 200,
        'headers': {
            'Content-Type': 'application/json'
        },
        'body': json.dumps({
            'processingTime': round((end*1000) - (start*1000)),
            'isActive': active,
            'isImpacted': impacted,
            'timestamp': time.strftime('%Y-%m-%dT%H:%M:%S', now)
        }, sort_keys=True, indent=2)
    }
