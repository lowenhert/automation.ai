"""pytest plugin loaded for every execution (-p automation_ai.pytest_plugin): provides the `device` fixture
and saves a screenshot when a test fails."""

from __future__ import annotations

import pytest

from automation_ai.device import Device, DeviceError


@pytest.fixture(scope="session")
def device() -> Device:
    return Device()


@pytest.hookimpl(wrapper=True)
def pytest_runtest_makereport(item, call):
    report = yield
    if report.when == "call" and report.failed and "device" in item.fixturenames:
        try:
            item.funcargs["device"].screenshot(f"failed_{item.name}")
        except (DeviceError, KeyError):
            pass
    return report
