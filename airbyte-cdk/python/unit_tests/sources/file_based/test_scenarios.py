#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#

import json
from pathlib import Path
from typing import Any, Dict
from unittest import mock

import pytest
from airbyte_cdk.entrypoint import launch
from unit_tests.sources.file_based.scenarios.csv_scenarios import (
    MultiCsvNFilesExceedsLimitForSchemaInferenceTestScenario,
    MultiCsvTestScenario,
    SingleCsvTestScenario,
)

scenarios = [
    SingleCsvTestScenario(),
    MultiCsvTestScenario(),
]


@pytest.mark.parametrize("scenario", scenarios, ids=[s.name for s in scenarios])
def test_discover(capsys, tmp_path, json_spec, scenario):
    assert discover(capsys, tmp_path, scenario) == scenario.expected_catalog


@pytest.mark.parametrize("scenario", scenarios, ids=[s.name for s in scenarios])
def test_read(capsys, tmp_path, json_spec, scenario):
    records = read(capsys, tmp_path, scenario)
    expected_records = scenario.expected_records
    assert len(records) == len(expected_records)
    for actual, expected in zip(records, expected_records):
        assert actual["record"]["data"] == expected


def test_discover_with_n_files_exceeding_limit(capsys, tmp_path, json_spec):
    scenario = MultiCsvNFilesExceedsLimitForSchemaInferenceTestScenario()
    with mock.patch(
        "airbyte_cdk.sources.file_based.file_based_stream.MAX_N_FILES_FOR_STREAM_SCHEMA_INFERENCE",
        scenario.schema_inference_limit,
    ):
        assert discover(capsys, tmp_path, scenario) == scenario.expected_catalog


def discover(capsys, tmp_path, scenario) -> Dict[str, Any]:
    launch(
        scenario.source,
        ["discover", "--config", make_file(tmp_path / "config.json", scenario.config)],
    )
    captured = capsys.readouterr()
    return json.loads(captured.out.splitlines()[0])["catalog"]


def read(capsys, tmp_path, scenario):
    launch(
        scenario.source,
        [
            "read",
            "--config",
            make_file(tmp_path / "config.json", scenario.config),
            "--catalog",
            make_file(tmp_path / "catalog.json", scenario.configured_catalog()),
        ],
    )
    captured = capsys.readouterr()
    return [
        msg
        for msg in (json.loads(line) for line in captured.out.splitlines())
        if msg["type"] == "RECORD"
    ]


def make_file(path: Path, file_contents: dict) -> str:
    path.write_text(json.dumps(file_contents))
    return str(path)
