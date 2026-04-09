import Foundation
import UIKit
import AppLovinSDK

@objc(ArtAppsMaxAdapter)
class ArtAppsMaxAdapter: ALMediationAdapter, MAInterstitialAdapter {

    private var interstitialAd: ArtAppsInterstitial?
    private var adapterDelegate: ArtAppsInterstitialAdapterDelegate?
    
    private func resolvedParameter(_ key: String,
                                   customParameters: [String: Any],
                                   serverParameters: [String: Any]) -> String? {
        if let value = stringValue(customParameters[key]), !value.isEmpty {
            return value
        }
        
        if let nested = customParameters["custom_parameters"],
           let parsed = dictionaryValue(nested),
           let value = stringValue(parsed[key]),
           !value.isEmpty {
            return value
        }
        
        if let value = stringValue(serverParameters[key]), !value.isEmpty {
            return value
        }
        
        return nil
    }
    
    private func stringValue(_ value: Any?) -> String? {
        switch value {
        case let string as String:
            return string.trimmingCharacters(in: .whitespacesAndNewlines)
        case let number as NSNumber:
            return number.stringValue
        default:
            return nil
        }
    }
    
    private func dictionaryValue(_ value: Any?) -> [String: Any]? {
        if let dictionary = value as? [String: Any] {
            return dictionary
        }
        
        if let string = value as? String,
           let data = string.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data),
           let dictionary = object as? [String: Any] {
            return dictionary
        }
        
        return nil
    }
    
    // MARK: - MAAdapter Methods

    override func initialize(with parameters: MAAdapterInitializationParameters, completionHandler: @escaping (MAAdapterInitializationStatus, String?) -> Void) {
        
        let customParameters = parameters.customParameters as? [String: Any] ?? [:]
        let serverParameters = parameters.serverParameters as? [String: Any] ?? [:]
        
        let partnerId = resolvedParameter("partner_id",
                                          customParameters: customParameters,
                                          serverParameters: serverParameters) ?? "test_partner"
        let appId = resolvedParameter("app_id",
                                      customParameters: customParameters,
                                      serverParameters: serverParameters) ?? "test_app"
        
        let params = UncheckedSendable(value: (partnerId, appId, completionHandler))
    
        DispatchQueue.main.async {
            ArtApps.shared.initialize(partnerId: params.value.0, appId: params.value.1)
            params.value.2(.initializedSuccess, nil)
        }
    }

    override var sdkVersion: String {
        return "1.0.0"
    }

    override var adapterVersion: String {
        return "1.0.0.0"
    }

    override func destroy() {
        let capturedSelf = UncheckedSendable(value: self)
        DispatchQueue.main.async {
            capturedSelf.value.interstitialAd?.delegate = nil
            capturedSelf.value.interstitialAd = nil
            capturedSelf.value.adapterDelegate = nil
        }
    }

    // MARK: - MAInterstitialAdapter Methods

    func loadInterstitialAd(for parameters: MAAdapterResponseParameters, andNotify delegate: MAInterstitialAdapterDelegate) {
        print("[ArtAppsMaxAdapter]: loadInterstitialAd 👁️")
        let placementId = parameters.thirdPartyAdPlacementIdentifier
        let customParameters = parameters.customParameters as? [String: Any] ?? [:]
        let serverParameters = parameters.serverParameters as? [String: Any] ?? [:]
        
        let partnerId = resolvedParameter("partner_id",
                                          customParameters: customParameters,
                                          serverParameters: serverParameters) ?? "test_partner"
        let appId = resolvedParameter("app_id",
                                      customParameters: customParameters,
                                      serverParameters: serverParameters) ?? "test_app"
        
        let captured = UncheckedSendable(value: (self, delegate, placementId, partnerId, appId))
        
        DispatchQueue.main.async {
            let strongSelf = captured.value.0
            let delegate = captured.value.1
            let placementId = captured.value.2
            let partnerId = captured.value.3
            let appId = captured.value.4
            
            // Ensure SDK uses runtime placement-level values from MAX custom parameters.
            ArtApps.shared.initialize(partnerId: partnerId, appId: appId)
            
            strongSelf.interstitialAd = ArtAppsInterstitial(placementId: placementId)
            
            // Retain the delegate strongly
            let adDelegate = ArtAppsInterstitialAdapterDelegate(parentAdapter: strongSelf, delegate: delegate)
            strongSelf.adapterDelegate = adDelegate
            
            strongSelf.interstitialAd?.delegate = adDelegate
            strongSelf.interstitialAd?.load()
        }
    }

    func showInterstitialAd(for parameters: MAAdapterResponseParameters, andNotify delegate: MAInterstitialAdapterDelegate) {
        print("[ArtAppsMaxAdapter]: showInterstitialAd 👁️")
        
        let captured = UncheckedSendable(value: (self, delegate))
        
        DispatchQueue.main.async {
            let strongSelf = captured.value.0
            let delegate = captured.value.1
            
            guard let ad = strongSelf.interstitialAd, ad.isReady else {
                delegate.didFailToDisplayInterstitialAdWithError(MAAdapterError.adNotReady)
                return
            }
            
            // ALUtils.topViewControllerFromKeyWindow() is now non-optional in newer SDKs
            let presentingVC = ALUtils.topViewControllerFromKeyWindow()
            
            ad.show(from: presentingVC)
        }
    }
}

