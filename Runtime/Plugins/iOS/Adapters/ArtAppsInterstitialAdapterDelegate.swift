import Foundation
import UIKit
import AppLovinSDK

@MainActor
class ArtAppsInterstitialAdapterDelegate: ArtAppsInterstitialDelegate {
    enum Phase: String {
        case load
        case display
    }

    private enum State: String {
        case pending
        case loaded
        case displayed
        case completed
    }
    
    private weak var parentAdapter: ArtAppsMaxAdapter?
    private let maxDelegate: MAInterstitialAdapterDelegate
    private let phase: Phase
    private var state: State = .pending
    
    init(parentAdapter: ArtAppsMaxAdapter, delegate: MAInterstitialAdapterDelegate, phase: Phase) {
        self.parentAdapter = parentAdapter
        self.maxDelegate = delegate
        self.phase = phase
    }
    
    func artAppsInterstitialDidLoad(_ ad: ArtAppsInterstitial) {
        guard phase == .load, state == .pending else {
            logIgnoredCallback("didLoad")
            return
        }

        state = .loaded
        print("[ArtAppsMaxAdapter] State: loaded")
        maxDelegate.didLoadInterstitialAd()
    }
    
    func artAppsInterstitial(_ ad: ArtAppsInterstitial, didFailToLoad error: Error) {
        guard phase == .load, state == .pending else {
            logIgnoredCallback("didFailToLoad")
            return
        }

        state = .completed
        print("[ArtAppsMaxAdapter] State: load-failed (\(error.localizedDescription))")
        maxDelegate.didFailToLoadInterstitialAdWithError(mapError(error))
        parentAdapter?.clearInterstitialAd(ad)
    }
    
    func artAppsInterstitial(_ ad: ArtAppsInterstitial, didFailToDisplay error: Error) {
        guard phase == .display else {
            logIgnoredCallback("didFailToDisplay")
            return
        }

        switch state {
        case .pending:
            state = .completed
            print("[ArtAppsMaxAdapter] State: display-failed (\(error.localizedDescription))")
            maxDelegate.didFailToDisplayInterstitialAdWithError(mapError(error))
            parentAdapter?.clearInterstitialAd(ad)

        case .displayed:
            state = .completed
            print("[ArtAppsMaxAdapter] Normalized failure-after-display to didHide")
            maxDelegate.didHideInterstitialAd()
            parentAdapter?.clearInterstitialAd(ad)

        case .loaded, .completed:
            logIgnoredCallback("didFailToDisplay")
        }
    }
    
    private func mapError(_ error: Error) -> MAAdapterError {
        if let sdkError = error as NSError?, sdkError.domain == "com.artApps.sdk" {
            switch sdkError.code {
            case 100:
                return MAAdapterError.notInitialized
            case 204, 205:
                return MAAdapterError.noFill
            case 301:
                return MAAdapterError.adNotReady
            default:
                break
            }
        }
        
        if let urlError = error as? URLError {
            switch urlError.code {
            case .timedOut,
                    .notConnectedToInternet,
                    .networkConnectionLost,
                    .cannotFindHost,
                    .cannotConnectToHost,
                    .dnsLookupFailed:
                return MAAdapterError.unspecified
            default:
                break
            }
        }
        
        return MAAdapterError.unspecified
    }
    
    func artAppsInterstitialDidDisplay(_ ad: ArtAppsInterstitial) {
        guard phase == .display, state == .pending else {
            logIgnoredCallback("didDisplay")
            return
        }

        state = .displayed
        print("[ArtAppsMaxAdapter] State: displayed")
        maxDelegate.didDisplayInterstitialAd()
    }
    
    func artAppsInterstitialDidHide(_ ad: ArtAppsInterstitial) {
        guard phase == .display else {
            logIgnoredCallback("didHide")
            return
        }

        switch state {
        case .displayed:
            state = .completed
            print("[ArtAppsMaxAdapter] State: hidden")
            maxDelegate.didHideInterstitialAd()
            parentAdapter?.clearInterstitialAd(ad)

        case .pending:
            state = .completed
            print("[ArtAppsMaxAdapter] Received didHide before didDisplay; reporting display failure")
            maxDelegate.didFailToDisplayInterstitialAdWithError(MAAdapterError.unspecified)
            parentAdapter?.clearInterstitialAd(ad)

        case .loaded, .completed:
            logIgnoredCallback("didHide")
        }
    }
    
    func artAppsInterstitialDidClick(_ ad: ArtAppsInterstitial) {
        guard phase == .display, state == .displayed else {
            logIgnoredCallback("didClick")
            return
        }

        maxDelegate.didClickInterstitialAd()
    }

    private func logIgnoredCallback(_ callback: String) {
        print("[ArtAppsMaxAdapter] Ignored \(callback) in phase=\(phase.rawValue), state=\(state.rawValue)")
    }
}

/// AppLovin callback values are accessed only after hopping to MainActor and are
/// never read concurrently. Remove this bridge when AppLovin SDK adds Sendable annotations.
struct UncheckedSendable<T>: @unchecked Sendable {
    let value: T
}
