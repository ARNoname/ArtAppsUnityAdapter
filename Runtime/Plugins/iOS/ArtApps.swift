
import Foundation

@MainActor
public class ArtApps {
    public static let shared = ArtApps()
    
    public private(set) var partnerId: String?
    public private(set) var appId: String?
    public private(set) var isInitialized = false
    
    private let startTime = Date()
    
    private var lastAdShowTime: Date? {
        get {
            let interval = UserDefaults.standard.double(forKey: "ArtApps_lastShowTime")
            return interval > 0 ? Date(timeIntervalSince1970: interval) : nil
        }
        set {
            UserDefaults.standard.set(newValue?.timeIntervalSince1970 ?? 0, forKey: "ArtApps_lastShowTime")
        }
    }
    
    private var serverRestrictionsUpdatedAt: Date?
    
    public var frequencyCapSeconds: TimeInterval = 90
    
    public private(set) var serverCooldownSeconds: TimeInterval?
    public private(set) var serverTtlSeconds: TimeInterval?
    
    private init() {}
    
    public func initialize(partnerId: String, appId: String, baseURL: String? = nil) {
         
         self.partnerId = partnerId
         self.appId = appId
         
         if let baseURL = baseURL {
             ArtAppsNetworkManager.shared.baseURL = baseURL
         }
        
         self.isInitialized = true
        
         print("[ArtApps] Initialized at \(startTime). PartnerID: \(partnerId)")
         print("[ArtApps] Initialized at \(startTime). AppID: \(appId)")
     }

     public var baseURL: String {
         get { ArtAppsNetworkManager.shared.baseURL }
         set { ArtAppsNetworkManager.shared.baseURL = newValue }
     }
    
    public func canShowAd() -> Bool {
        let now = Date()
 
         let effectiveCooldownSeconds = currentServerCooldownSeconds(at: now) ?? frequencyCapSeconds
         if let lastShow = lastAdShowTime, now.timeIntervalSince(lastShow) < effectiveCooldownSeconds {
             let source = currentServerCooldownSeconds(at: now) == nil ? "Freq Cap" : "Server Cooldown"
             print("[ArtApps] Blocked by \(source) (need \(effectiveCooldownSeconds)s, passed \(Int(now.timeIntervalSince(lastShow)))s)")
             return false
         }
         
         return true
     }
    
    public func updateServerRestrictions(cooldownSeconds: Int?, sessionGateSeconds: Int?, ttlSeconds: Int?) {
          serverRestrictionsUpdatedAt = Date()
          serverCooldownSeconds = cooldownSeconds.map { TimeInterval($0) }
          serverTtlSeconds = ttlSeconds.map { TimeInterval($0) }
      }
    
    public func didShowAd() {
        lastAdShowTime = Date()
    }
    
    private func currentServerCooldownSeconds(at now: Date) -> TimeInterval? {
        if checkTtl(at: now) { return nil }
        return serverCooldownSeconds
    }
    
    /// Returns true if TTL has expired and resets server restrictions.
    private func checkTtl(at now: Date) -> Bool {
        guard let updatedAt = serverRestrictionsUpdatedAt,
              let ttlSeconds = serverTtlSeconds,
              ttlSeconds > 0 else {
            return false
        }
        
        if now.timeIntervalSince(updatedAt) > ttlSeconds {
            print("[ArtApps] Server restrictions expired (TTL: \(ttlSeconds)s)")
            serverCooldownSeconds = nil
            serverTtlSeconds = nil
            serverRestrictionsUpdatedAt = nil
            return true
        }
        return false
    }
}
