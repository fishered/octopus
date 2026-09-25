import sys
from pathlib import Path

from playwright.sync_api import sync_playwright


BASE_URL = sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:4173"
OUTPUT_DIR = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path("site/.artifacts").resolve()
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def assert_no_horizontal_overflow(page):
    overflow = page.evaluate("document.documentElement.scrollWidth - document.documentElement.clientWidth")
    assert overflow <= 1, f"horizontal overflow: {overflow}px"


def collect_errors(page, errors):
    page.on(
        "console",
        lambda message: errors.append(f"console:{message.type}:{message.text}")
        if message.type == "error"
        else None,
    )
    page.on("pageerror", lambda error: errors.append(f"pageerror:{error}"))


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(
        headless=True,
        executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    )
    errors = []

    desktop = browser.new_context(
        viewport={"width": 1440, "height": 1000},
        device_scale_factor=1,
        locale="en-US",
    )
    page = desktop.new_page()
    collect_errors(page, errors)
    page.goto(BASE_URL, wait_until="networkidle")
    assert page.title() == "Octopus — Operate every connected device"
    assert "Connect every device." in page.locator("h1").inner_text()
    assert page.locator("img[src='./octopus-logo.png']").first.is_visible()
    assert_no_horizontal_overflow(page)

    page.get_by_role("button", name="Dispatch command").click()
    assert "durable dispatch queue" in page.locator("#console-toast").inner_text()
    page.get_by_role("button", name="Acknowledge").click()
    assert page.get_by_role("button", name="Acknowledged").is_disabled()

    page.locator("button[data-capability='telemetry']").click()
    assert page.locator("#capability-title").inner_text() == "Turn noisy messages into dependable telemetry."
    page.screenshot(path=str(OUTPUT_DIR / "octopus-v2-desktop-en.png"), full_page=True)

    page.locator("button[data-locale='zh-CN']").click()
    assert page.locator("html").get_attribute("lang") == "zh-CN"
    assert page.title() == "Octopus — 连接、观察并控制每一台设备"
    assert "连接每台设备" in page.locator("h1").inner_text()
    assert page.locator("#capability-title").inner_text() == "把嘈杂消息变成可靠遥测。"
    page.get_by_role("button", name="下发命令").click()
    assert "可靠下发队列" in page.locator("#console-toast").inner_text()
    page.reload(wait_until="networkidle")
    assert page.locator("html").get_attribute("lang") == "zh-CN"
    assert_no_horizontal_overflow(page)
    page.screenshot(path=str(OUTPUT_DIR / "octopus-v2-desktop-zh.png"), full_page=True)
    desktop.close()

    mobile = browser.new_context(
        viewport={"width": 390, "height": 844},
        device_scale_factor=1,
        locale="zh-CN",
    )
    page = mobile.new_page()
    collect_errors(page, errors)
    page.goto(BASE_URL, wait_until="networkidle")
    assert page.locator("html").get_attribute("lang") == "zh-CN"
    assert "连接每台设备" in page.locator("h1").inner_text()
    assert page.get_by_role("button", name="下发命令").is_visible()
    assert_no_horizontal_overflow(page)
    page.locator("button[data-capability='alarms']").click()
    assert page.locator("#capability-title").inner_text() == "让异常真正触发行动。"
    page.locator("button[data-locale='en']").click()
    assert page.locator("html").get_attribute("lang") == "en"
    assert "Connect every device." in page.locator("h1").inner_text()
    page.locator("button[data-locale='zh-CN']").click()
    page.screenshot(path=str(OUTPUT_DIR / "octopus-v2-mobile-zh.png"), full_page=True)
    mobile.close()

    browser.close()
    assert not errors, "browser errors:\n" + "\n".join(errors)

print(f"v2 visual checks passed; screenshots: {OUTPUT_DIR}")
