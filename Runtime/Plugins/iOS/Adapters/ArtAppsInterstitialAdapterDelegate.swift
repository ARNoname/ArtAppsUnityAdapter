import Foundation
import UIKit
import AppLovinSDK

@MainActor
class ArtAppsInterstitialAdapterDelegate: ArtAppsInterstitialDelegate {
    
    private weak var parentAdapter: ArtAppsMaxAdapter?
    private let maxDelegate: MAInterstitialAdapterDelegate
    
    init(parentAdapter: ArtAppsMaxAdapter, delegate: MAInterstitialAdapterDelegate) {
        self.parentAdapter = parentAdapter
        self.maxDelegate = delegate
    }
    
    func artAppsInterstitialDidLoad(_ ad: ArtAppsInterstitial) {
        print("[ArtAppsMaxAdapter] Delegate received: artAppsInterstitialDidLoad 🤡")
        maxDelegate.didLoadInterstitialAd()
    }
    
    func artAppsInterstitial(_ ad: ArtAppsInterstitial, didFailToLoad error: Error) {
        print("[ArtAppsMaxAdapter] Delegate received: didFailToLoad (\(error.localizedDescription)) 🤡")
        maxDelegate.didFailToLoadInterstitialAdWithError(mapError(error))
        parentAdapter?.clearInterstitialAd(ad)
    }
    
    func artAppsInterstitial(_ ad: ArtAppsInterstitial, didFailToDisplay error: Error) {
        print("[ArtAppsMaxAdapter] Delegate received: didFailToDisplay (\(error.localizedDescription)) 🤡")
        maxDelegate.didFailToDisplayInterstitialAdWithError(mapError(error))
        parentAdapter?.clearInterstitialAd(ad)
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
        print("[ArtAppsMaxAdapter] Delegate received: artAppsInterstitialDidDisplay 🤡")
        maxDelegate.didDisplayInterstitialAd()
    }
    
    func artAppsInterstitialDidHide(_ ad: ArtAppsInterstitial) {
        print("[ArtAppsMaxAdapter] Delegate received: artAppsInterstitialDidHide 🤡")
        maxDelegate.didHideInterstitialAd()
        parentAdapter?.clearInterstitialAd(ad)
    }
    
    func artAppsInterstitialDidClick(_ ad: ArtAppsInterstitial) {
        print("[ArtAppsMaxAdapter] Delegate received: artAppsInterstitialDidClick 🤡")
        maxDelegate.didClickInterstitialAd()
    }
}

struct UncheckedSendable<T>: @unchecked Sendable {
    let value: T
}
