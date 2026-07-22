import UIKit
import SwiftUI

@MainActor
protocol ArtAppsWebViewControllerDelegate: AnyObject {
    func webViewControllerDidFinish(_ controller: ArtAppsWebViewController)
    func webViewControllerDidDisplay(_ controller: ArtAppsWebViewController)
    func webViewController(_ controller: ArtAppsWebViewController, didFailWithError error: Error)
}

@MainActor
class ArtAppsWebViewController: UIViewController {

    weak var delegate: ArtAppsWebViewControllerDelegate?
    private let url: URL
    private let adDuration: TimeInterval
    private var didStartLoading = false
    private var didNotifyDisplay = false
    private var didFinishOrFail = false
    private var loadWatchdogTimer: Timer?
    private static let loadTimeout: TimeInterval = 5

    init(url: URL, adDuration: TimeInterval = 20) {
        self.url = url
        self.adDuration = adDuration
        super.init(nibName: nil, bundle: nil)
        self.modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)

        guard !didStartLoading, !didFinishOrFail else { return }
        didStartLoading = true

        let reachability = ArtAppsReachability.shared
        guard reachability.isConnectedToNetwork else {
            let error = NSError(
                domain: "com.artApps.sdk",
                code: 305,
                userInfo: [NSLocalizedDescriptionKey: "No internet connection"]
            )
            print("[ArtApps] Network became unavailable before WebView presentation completed. State: \(reachability.statusDescription)")
            handleFailure(error)
            return
        }

        setupSwiftUI()
        startLoadWatchdog()
    }

    override var prefersStatusBarHidden: Bool {
        return true
    }

    deinit {
        loadWatchdogTimer?.invalidate()
    }

    private func setupSwiftUI() {
        let adView = ArtAppsAdView(
            url: url,
            onClose: { [weak self] in
                self?.handleClose()
            },
            onDisplay: { [weak self] in
                self?.handleDisplay()
            },
            onFail: { [weak self] error in
                self?.handleFailure(error)
            },
            adDuration: adDuration
        )

        let hostingController = UIHostingController(rootView: adView)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        hostingController.view.backgroundColor = .clear

        addChild(hostingController)
        view.addSubview(hostingController.view)

        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        hostingController.didMove(toParent: self)
    }

    private func startLoadWatchdog() {
        loadWatchdogTimer?.invalidate()
        loadWatchdogTimer = Timer.scheduledTimer(withTimeInterval: Self.loadTimeout, repeats: false) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handleLoadTimeout()
            }
        }
    }

    private func handleDisplay() {
        guard !didNotifyDisplay, !didFinishOrFail else { return }
        didNotifyDisplay = true
        loadWatchdogTimer?.invalidate()
        loadWatchdogTimer = nil
        delegate?.webViewControllerDidDisplay(self)
    }

    private func handleFailure(_ error: Error) {
        guard !didFinishOrFail else { return }
        didFinishOrFail = true
        loadWatchdogTimer?.invalidate()
        loadWatchdogTimer = nil
        delegate?.webViewController(self, didFailWithError: error)
    }

    private func handleLoadTimeout() {
        let error = NSError(domain: "com.artApps.sdk", code: 306, userInfo: [NSLocalizedDescriptionKey: "Ad web view load timed out"])
        print("[ArtApps] WebView load timed out.")
        handleFailure(error)
    }

    private func handleClose() {
        guard !didFinishOrFail else { return }
        didFinishOrFail = true
        loadWatchdogTimer?.invalidate()
        loadWatchdogTimer = nil

        dismiss(animated: true) { [weak self] in
            guard let self = self else { return }
            self.delegate?.webViewControllerDidFinish(self)
        }
    }
}
