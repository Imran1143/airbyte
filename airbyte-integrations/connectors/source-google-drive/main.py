#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#


import sys
import traceback
from datetime import datetime
from typing import List

from airbyte_cdk.entrypoint import AirbyteEntrypoint, launch
from airbyte_cdk.models import AirbyteErrorTraceMessage, AirbyteMessage, AirbyteTraceMessage, TraceType, Type
from airbyte_cdk.sources.file_based.file_based_source import FileBasedSource
from airbyte_cdk.sources.file_based.stream.cursor.default_file_based_cursor import DefaultFileBasedCursor
from source_google_drive.spec import SourceGoogleDriveSpec as Config
from source_google_drive.stream_reader import SourceGoogleDriveStreamReader


def get_source(args: List[str]):
    catalog_path = AirbyteEntrypoint.extract_catalog(args)
    try:
        return FileBasedSource(SourceGoogleDriveStreamReader(), Config, catalog_path, cursor_cls=DefaultFileBasedCursor)
    except Exception:
        print(
            AirbyteMessage(
                type=Type.TRACE,
                trace=AirbyteTraceMessage(
                    type=TraceType.ERROR,
                    emitted_at=int(datetime.now().timestamp() * 1000),
                    error=AirbyteErrorTraceMessage(
                        message="Error starting the sync. This could be due to an invalid configuration or catalog. Please contact Support for assistance.",
                        stack_trace=traceback.format_exc(),
                    ),
                ),
            ).json()
        )
        return None


if __name__ == "__main__":
    _args = sys.argv[1:]
    source = get_source(_args)

    if source:
        launch(source, _args)