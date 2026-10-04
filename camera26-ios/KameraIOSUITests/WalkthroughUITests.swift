import XCTest

/// End-to-end walkthrough of Kamera iOS, driven through the real UI in
/// the iOS Simulator and recorded from outside by `simctl io recordVideo`
/// (see `.github/workflows/camera-ios-walkthrough.yml`).
///
/// Route, mirroring the Android walkthrough: onboarding -> camera
/// permission -> PHOTO -> carousel to VIDEO -> back to PHOTO -> GRID
/// sheet -> CONFIG panel -> carousel to PORTRAIT -> zoom dial (long
/// press) -> Settings. The Simulator's camera is a simulated feed, so
/// the preview shows the system test scene — that is expected and is
/// reported as such; every control interaction is real.
final class WalkthroughUITests: XCTestCase {

    private var app: XCUIApplication!
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUpWithError() throws {
        // Keep going step by step even if one check fails, so the
        // recording still shows how far the app honestly gets; every
        // failed check is reported in the test results.
        continueAfterFailure = true
        app = XCUIApplication()
        addUIInterruptionMonitor(withDescription: "System permission alert") { alert in
            for label in ["Allow", "OK"] {
                let button = alert.buttons[label]
                if button.exists {
                    button.tap()
                    return true
                }
            }
            return false
        }
        app.launch()
    }

    override func tearDownWithError() throws {
        app?.terminate()
        app = nil
    }

    func testWalkthrough() {
        // 1 — First-run onboarding (developer intro), if it is shown.
        let startButton = app.buttons["Mulai"]
        if startButton.waitForExistence(timeout: 8) {
            startButton.tap()
            pause(1)
        }

        // 2 — Camera permission: the app's own explanation screen first,
        // then the system alert is allowed via SpringBoard.
        let permissionButton = app.buttons["Izinkan Akses Kamera"]
        if permissionButton.waitForExistence(timeout: 5) {
            permissionButton.tap()
            pause(1)
            app.tap() // nudge the interruption monitor
            allowSystemAlertIfPresent()
            pause(1)
        }

        // 3 — Camera UI ready, resting on PHOTO.
        let shutter = app.buttons["shutterButton"]
        XCTAssertTrue(shutter.waitForExistence(timeout: 25),
                      "Camera UI (shutter) never appeared")
        pause(3)

        // 4 — Carousel: PHOTO -> VIDEO (swipe right, one hop).
        swipeCarousel(toLeft: false)
        pause(1)
        allowSystemAlertIfPresent() // microphone prompt on entering VIDEO
        let fpsPill = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "FPS")).firstMatch
        XCTAssertTrue(fpsPill.waitForExistence(timeout: 5),
                      "VIDEO format pill (… FPS) never appeared")
        pause(2)

        // 5 — Carousel back: VIDEO -> PHOTO (swipe left, one hop).
        swipeCarousel(toLeft: true)
        pause(2)

        // 6 — GRID sheet open, then into the CONFIG panel.
        let gridPill = app.buttons["pill_grid"]
        XCTAssertTrue(gridPill.waitForExistence(timeout: 5),
                      "Grid pill never appeared")
        gridPill.tap()
        pause(1)
        let configTile = app.buttons["tile_config"]
        XCTAssertTrue(configTile.waitForExistence(timeout: 5),
                      "CONFIG tile never appeared")
        configTile.tap()
        XCTAssertTrue(app.staticTexts["Ketajaman"].waitForExistence(timeout: 5),
                      "CONFIG panel never appeared")
        pause(2)
        closeSheet()
        pause(1)

        // 7 — Carousel: PHOTO -> PORTRAIT (swipe left, one hop).
        swipeCarousel(toLeft: true)
        pause(2)

        // 8 — Zoom dial: long-press a zoom stop, then drag on the dial.
        let stop = app.buttons.matching(identifier: "zoomStopButton").firstMatch
        XCTAssertTrue(stop.waitForExistence(timeout: 5),
                      "Zoom stop button never appeared")
        stop.press(forDuration: 0.7)
        let dialLabel = app.staticTexts.matching(
            NSPredicate(format: "label MATCHES %@", "[0-9]+\\.[0-9]x")).firstMatch
        XCTAssertTrue(dialLabel.waitForExistence(timeout: 4),
                      "Zoom dial ratio label never appeared")
        pause(1)
        dragZoomDial()
        pause(2)

        // 9 — Settings via the sheet's PENGATURAN tile.
        gridPill.tap()
        pause(1)
        let settingsTile = app.buttons["tile_settings"]
        XCTAssertTrue(settingsTile.waitForExistence(timeout: 5),
                      "PENGATURAN tile never appeared")
        settingsTile.tap()
        XCTAssertTrue(app.staticTexts["Pengaturan & Info"].waitForExistence(timeout: 5),
                      "Settings screen never appeared")
        pause(3)
    }

    // MARK: - Helpers

    private func pause(_ seconds: TimeInterval) {
        Thread.sleep(forTimeInterval: seconds)
    }

    /// Allow a pending SpringBoard permission alert (camera/microphone),
    /// if one is on screen. The camera alert's affirmative button reads
    /// "Allow" on current iOS ("OK" on older naming).
    private func allowSystemAlertIfPresent() {
        for label in ["Allow", "OK"] {
            let button = springboard.buttons[label]
            if button.waitForExistence(timeout: 1.5) {
                button.tap()
                return
            }
        }
    }

    /// One carousel hop. The settle logic moves at most one mode per
    /// gesture: swipe left = next mode, swipe right = previous mode.
    private func swipeCarousel(toLeft: Bool) {
        let start = app.coordinate(withNormalizedOffset:
            CGVector(dx: toLeft ? 0.80 : 0.20, dy: 0.92))
        let end = app.coordinate(withNormalizedOffset:
            CGVector(dx: toLeft ? 0.20 : 0.80, dy: 0.92))
        start.press(forDuration: 0.06, thenDragTo: end)
    }

    /// The sheet closes on a downward drag past ~90pt.
    private func closeSheet() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.52))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95))
        start.press(forDuration: 0.06, thenDragTo: end)
    }

    /// Small horizontal drag across the zoom dial (right = zoom in).
    private func dragZoomDial() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.75))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.75))
        start.press(forDuration: 0.06, thenDragTo: end)
    }
}
