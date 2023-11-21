#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#

import sys

from airbyte_cdk.entrypoint import launch
from source_dbt_duckdb import SourceDbtDuckdb

def main():
    source = SourceDbtDuckdb()
    launch(source, sys.argv[1:])

if __name__ == "__main__":
    main()