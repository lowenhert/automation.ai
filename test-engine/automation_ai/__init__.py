"""Public API for Automation.ai test scripts. Scripts may only import names listed in __all__."""

from automation_ai.device import KEYS, Device, DeviceError

__all__ = ["Device", "DeviceError", "KEYS"]
