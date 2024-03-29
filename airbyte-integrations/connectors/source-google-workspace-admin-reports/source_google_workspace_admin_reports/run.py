#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#


import sys

from airbyte_cdk.entrypoint import launch
from source_google_workspace_admin_reports import SourceGoogleWorkspaceAdminReports


def run():
    source = SourceGoogleWorkspaceAdminReports()
    launch(source, sys.argv[1:])
