import UIKit

@MainActor
public protocol ArtAppsInterstitialDelegate: AnyObject {
    func artAppsInterstitialDidLoad(_ ad: ArtAppsInterstitial)
    func artAppsInterstitial(_ ad: ArtAppsInterstitial, didFailToLoad error: Error)
    func artAppsInterstitial(_ ad: ArtAppsInterstitial, didFailToDisplay error: Error)
    func artAppsInterstitialDidDisplay(_ ad: ArtAppsInterstitial)
    func artAppsInterstitialDidHide(_ ad: ArtAppsInterstitial)
    func artAppsInterstitialDidClick(_ ad: ArtAppsInterstitial) // Optional depending on WebView interaction
}

@MainActor
public class ArtAppsInterstitial: NSObject {

    public weak var delegate: ArtAppsInterstitialDelegate?
    public private(set) var isReady: Bool = false

    private let placementId: String
    private var adResponse: ArtAppsAdResponse?
    private var presenter: ArtAppsWebViewController?
    private var adDisplayStartTime: Date?
    private var didNotifyDisplay = false
    private var didCompleteDisplay = false

    public init(placementId: String) {
        self.placementId = placementId
        super.init()
    }

    public func load() {
        guard let partnerId = ArtApps.shared.partnerId, let appId = ArtApps.shared.appId else {
            print("[ArtApps] Error: SDK not initialized. Call ArtApps.initialize() first.")
            let error = NSError(domain: "com.artApps.sdk", code: 100, userInfo: [NSLocalizedDescriptionKey: "SDK not initialized"])
            delegate?.artAppsInterstitial(self, didFailToLoad: error)
            return
        }

        // Check Pilot Rules (Frequency Cap)
        if !ArtApps.shared.canShowAd() {
            let error = NSError(domain: "com.artApps.sdk", code: 205, userInfo: [NSLocalizedDescriptionKey: "Frequency/Session Cap"])
            delegate?.artAppsInterstitial(self, didFailToLoad: error)
            return
        }

        resetAdState()

        ArtAppsNetworkManager.shared.fetchAd(partnerId: partnerId, appId: appId, placementId: placementId) { [weak self] result in
            self?.handleLoadResult(result)
        }
    }

    public func show(from viewController: UIViewController) {

        guard isReady else {
            let error = NSError(domain: "com.artApps.sdk", code: 301, userInfo: [NSLocalizedDescriptionKey: "Ad not ready"])
            print("[ArtApps] Error: Ad not ready.")
            notifyDisplayFailure(error)
            return
        }

        guard let finalUrlString = adResponse?.finalUrl, !finalUrlString.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            let error = NSError(domain: "com.artApps.sdk", code: 302, userInfo: [NSLocalizedDescriptionKey: "Missing ad URL"])
            print("[ArtApps] Error: Missing ad URL.")
            notifyDisplayFailure(error)
            return
        }

        guard let url = adURL(from: adResponse) else {
            let error = NSError(domain: "com.artApps.sdk", code: 303, userInfo: [NSLocalizedDescriptionKey: "Invalid ad URL"])
            print("[ArtApps] Error: Invalid ad URL.")
            notifyDisplayFailure(error)
            return
        }

        guard viewController.view.window != nil else {
            let error = NSError(domain: "com.artApps.sdk", code: 304, userInfo: [NSLocalizedDescriptionKey: "No presenting window"])
            print("[ArtApps] Error: No presenting window.")
            notifyDisplayFailure(error)
            return
        }

        let reachability = ArtAppsReachability.shared
        print("[ArtApps] Interstitial show requested. Network: \(reachability.statusDescription)")

        guard reachability.isConnectedToNetwork else {
            let error = NSError(domain: "com.artApps.sdk", code: 305, userInfo: [NSLocalizedDescriptionKey: "No internet connection"])
            print("[ArtApps] Error: Cannot show interstitial while offline.")
            notifyDisplayFailure(error)
            return
        }

        let duration = TimeInterval(adResponse?.sessionGate ?? 20)
        let adPresenter = ArtAppsWebViewController(url: url, adDuration: duration)
        adPresenter.delegate = self
        adPresenter.modalPresentationStyle = .fullScreen

        presenter = adPresenter
        didNotifyDisplay = false
        didCompleteDisplay = false
        adDisplayStartTime = nil

        viewController.present(adPresenter, animated: true)
    }

    private func handleLoadResult(_ result: Result<ArtAppsAdResponse, Error>) {
        switch result {
        case .success(let response):
            ArtApps.shared.updateServerRestrictions(
                cooldownSeconds: response.cooldownSec,
                sessionGateSeconds: response.sessionGate,
                ttlSeconds: response.ttl
            )

            guard response.allow == true else {
                let error = NSError(domain: "com.artApps.sdk", code: 204, userInfo: [NSLocalizedDescriptionKey: "No Fill"])
                print("[ArtApps] No fill for placement: \(placementId)")
                delegate?.artAppsInterstitial(self, didFailToLoad: error)
                return
            }

            guard adURL(from: response) != nil else {
                let error = NSError(domain: "com.artApps.sdk", code: 206, userInfo: [NSLocalizedDescriptionKey: "Invalid ad response"])
                print("[ArtApps] Load failed: invalid ad URL for placement: \(placementId)")
                delegate?.artAppsInterstitial(self, didFailToLoad: error)
                return
            }

            adResponse = response
            isReady = true
            print("[ArtApps] Interstitial loaded for placement: \(placementId)")
            delegate?.artAppsInterstitialDidLoad(self)

        case .failure(let error):
            print("[ArtApps] Load failed: \(error.localizedDescription)")
            delegate?.artAppsInterstitial(self, didFailToLoad: error)
        }
    }

    private func adURL(from response: ArtAppsAdResponse?) -> URL? {
        guard let finalUrlString = response?.finalUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !finalUrlString.isEmpty else {
            return nil
        }

        guard let url = URL(string: finalUrlString),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else {
            return nil
        }

        return url
    }

    private func notifyDisplayFailure(_ error: Error) {
        guard !didCompleteDisplay else { return }
        didCompleteDisplay = true
        print("[ArtApps] Display state: failed-before-display (\(error.localizedDescription))")
        delegate?.artAppsInterstitial(self, didFailToDisplay: error)
        resetAdState()
    }

    private func notifyDidDisplay(for controller: ArtAppsWebViewController) {
        guard presenter === controller, !didNotifyDisplay, !didCompleteDisplay else { return }

        didNotifyDisplay = true
        adDisplayStartTime = Date()
        print("[ArtApps] Display state: displayed")
        ArtApps.shared.didShowAd() // Record impression timestamp for freq cap
        delegate?.artAppsInterstitialDidDisplay(self)
    }

    private func notifyDidHide(trackImpression: Bool) {
        guard !didCompleteDisplay else { return }
        didCompleteDisplay = true
        print("[ArtApps] Display state: hidden")

        if trackImpression, let startTime = adDisplayStartTime {
            let duration = Date().timeIntervalSince(startTime)
            print("[ArtApps] Ad was visible for \(Int(duration)) seconds")

            if let requestId = adResponse?.requestId {
                ArtAppsNetworkManager.shared
                    .trackImpression(
                        requestId: requestId,
                        trackUrl: adResponse?.trackUrl,
                        visible: Int(duration)
                    )
            }
        }

        delegate?.artAppsInterstitialDidHide(self)
        resetAdState()
    }

    private func resetAdState() {
        isReady = false
        adResponse = nil
        presenter = nil
        adDisplayStartTime = nil
        didNotifyDisplay = false
        didCompleteDisplay = false
    }
}

// MARK: - ArtAppsWebViewControllerDelegate
extension ArtAppsInterstitial: ArtAppsWebViewControllerDelegate {

    func webViewControllerDidDisplay(_ controller: ArtAppsWebViewController) {
        notifyDidDisplay(for: controller)
    }

    func webViewControllerDidFinish(_ controller: ArtAppsWebViewController) {
        guard presenter === controller else { return }
        notifyDidHide(trackImpression: true)
    }

    func webViewController(_ controller: ArtAppsWebViewController, didFailWithError error: Error) {
        print("[ArtApps] WebView failed: \(error.localizedDescription)")

        guard presenter === controller, !didCompleteDisplay else { return }

        if didNotifyDisplay {
            controller.dismiss(animated: false) { [weak self] in
                self?.notifyDidHide(trackImpression: false)
            }
        } else if controller.presentingViewController != nil {
            controller.dismiss(animated: false) { [weak self] in
                self?.notifyDisplayFailure(error)
            }
        } else {
            notifyDisplayFailure(error)
        }
    }
}
