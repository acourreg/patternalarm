"""
AWS Lambda handler for event generation
Invoked by Dashboard with domain/load_level parameters
"""
import json
import logging

from main import run_test, LoadLevel

logger = logging.getLogger()
logger.setLevel(logging.INFO)


def lambda_handler(event, context):
    """Lambda entry point"""
    try:
        test_id = event.get('test_id', f"test-{context.request_id[:8]}")
        domain = event.get('domain', 'gaming')
        load_level_str = event.get('load_level', 'normal')

        logger.info(f"Starting test: {test_id}, domain: {domain}, load: {load_level_str}")

        load_level = LoadLevel.from_string(load_level_str)

        # Pass None - KafkaPublisher will read env var
        result = run_test(
            test_id=test_id,
            domain=domain,
            load_level=load_level,
            duration_seconds=60,
            kafka_bootstrap=None,  # Let KafkaPublisher handle it
            kafka_topic='fraud-events-raw'
        )

        return {
            'statusCode': 200,
            'body': json.dumps(result)
        }

    except Exception as e:
        logger.error(f"Lambda execution failed: {e}", exc_info=True)
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }