"""Robot Framework library for Automation.ai test scripts (Library    AutomationAiLibrary)."""

from __future__ import annotations

from automation_ai.device import Device


class AutomationAiLibrary:
    """Remote-key, screenshot and OCR keywords for the selected device."""

    ROBOT_LIBRARY_SCOPE = "SUITE"

    def __init__(self):
        self._device = Device()

    def press_key(self, key: str, times: int = 1):
        """Presses DPAD_UP/DOWN/LEFT/RIGHT, SELECT, BACK or HOME ``times`` times."""
        self._device.press(key, int(times))

    def wait_seconds(self, seconds: float):
        self._device.sleep(float(seconds))

    def take_screenshot(self, name: str = "screen"):
        return self._device.screenshot(name)

    def get_visible_text(self):
        return self._device.visible_text()

    def text_should_be_visible(self, text: str, timeout: float = 10):
        self._device.assert_text_visible(text, float(timeout))

    def text_should_not_be_visible(self, text: str, timeout: float = 10):
        self._device.assert_text_not_visible(text, float(timeout))

    def any_text_should_be_visible(self, *texts: str, timeout: float = 10):
        self._device.assert_any_text_visible(list(texts), float(timeout))
