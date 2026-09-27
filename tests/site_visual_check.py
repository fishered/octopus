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

    assert page.title() == "Octopus — Bring your first AIoT device online"
    assert "Bring your first device" in page.locator("h1").inner_text()
    assert page.locator("img[src='./octopus-logo.png']").first.is_visible()
    assert page.locator("#protocol-status").inner_text() == "BUILT IN"
    assert_no_horizontal_overflow(page)

    page.get_by_role("button", name="Run connection demo").click()
    page.wait_for_timeout(1500)
    assert "Connected" in page.locator("#demo-status").inner_text()
    assert page.locator(".connection-steps .is-ready").count() == 3

    page.locator("button[data-protocol='http']").click()
    assert page.locator("#protocol-name").inner_text() == "HTTP / CoAP"
    assert page.locator("#protocol-status").inner_text() == "PLUGIN CONTRACT"
    page.get_by_role("button", name="Run connection demo").click()
    page.wait_for_timeout(1500)
    assert "Contract ready" in page.locator("#demo-status").inner_text()
    assert page.locator(".connection-steps .is-planned").count() == 2

    page.get_by_role("button", name="Preview guarded command").click()
    assert "Preview only" in page.locator("#agent-note").inner_text()
    page.screenshot(path=str(OUTPUT_DIR / "octopus-aiot-desktop-en.png"), full_page=True)

    page.locator("button[data-locale='zh-CN']").click()
    assert page.locator("html").get_attribute("lang") == "zh-CN"
    assert page.title() == "Octopus — 让第一台 AIoT 设备今天上线"
    assert "让第一台设备" in page.locator("h1").inner_text()
    assert page.locator("#protocol-status").inner_text() == "插件契约"
    page.get_by_role("button", name="模拟接入").click()
    page.wait_for_timeout(1500)
    assert "插件契约已就绪" in page.locator("#demo-status").inner_text()
    page.reload(wait_until="networkidle")
    assert page.locator("html").get_attribute("lang") == "zh-CN"
    assert_no_horizontal_overflow(page)
    page.screenshot(path=str(OUTPUT_DIR / "octopus-aiot-desktop-zh.png"), full_page=True)
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
    assert "让第一台设备" in page.locator("h1").inner_text()
    assert page.get_by_role("button", name="模拟接入").is_visible()
    assert_no_horizontal_overflow(page)

    page.locator("button[data-protocol='fieldbus']").click()
    assert page.locator("#protocol-name").inner_text() == "Modbus / OPC UA / BACnet"
    assert page.locator("#protocol-status").inner_text() == "边缘插件模式"
    page.locator("button[data-locale='en']").click()
    assert page.locator("html").get_attribute("lang") == "en"
    assert "Bring your first device" in page.locator("h1").inner_text()
    page.locator("button[data-locale='zh-CN']").click()
    page.screenshot(path=str(OUTPUT_DIR / "octopus-aiot-mobile-zh.png"), full_page=True)
    mobile.close()

    browser.close()
    assert not errors, "browser errors:\n" + "\n".join(errors)

print(f"AIoT redesign checks passed; screenshots: {OUTPUT_DIR}")
