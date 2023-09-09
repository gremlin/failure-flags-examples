import os
import logging
import jsonpickle
import json
import time
from failureflags import FailureFlag 
from aws_xray_sdk.core import xray_recorder
from aws_xray_sdk.core import patch_all

logger = logging.getLogger()
logger.setLevel(logging.INFO)
patch_all()

def handler(event, context):
    now = time.gmtime()
    start = time.time()

    # Note: This is an example of a line you need to add to
    # your application code for each failure flag.
    active, impacted, _ = FailureFlag(
            "http-ingress", # name of the failure flag
            {}, # dict of labels with dynamic invocation context
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
