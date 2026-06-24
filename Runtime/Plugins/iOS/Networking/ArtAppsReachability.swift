import Foundation
import SystemConfiguration

enum ArtAppsReachability {
    static func isConnectedToNetwork() -> Bool {
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)

        return withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { socketAddress in
                guard let reachability = SCNetworkReachabilityCreateWithAddress(nil, socketAddress) else {
                    return false
                }

                var flags = SCNetworkReachabilityFlags()
                guard SCNetworkReachabilityGetFlags(reachability, &flags) else {
                    return false
                }

                let isReachable = flags.contains(.reachable)
                let needsConnection = flags.contains(.connectionRequired)
                let canConnectAutomatically = flags.contains(.connectionOnDemand) || flags.contains(.connectionOnTraffic)
                let canConnectWithoutUserInteraction = canConnectAutomatically && !flags.contains(.interventionRequired)

                return isReachable && (!needsConnection || canConnectWithoutUserInteraction)
            }
        }
    }
}
