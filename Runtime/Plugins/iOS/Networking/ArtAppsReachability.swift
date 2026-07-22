import Foundation
import Network

@MainActor
final class ArtAppsReachability {
    static let shared = ArtAppsReachability()

    private let monitor = NWPathMonitor()
    private let monitorQueue = DispatchQueue(label: "com.artapps.sdk.reachability")
    private var hasReceivedPathUpdate = false
    private var isPathSatisfied = false

    var isConnectedToNetwork: Bool {
        hasReceivedPathUpdate && isPathSatisfied
    }

    var statusDescription: String {
        guard hasReceivedPathUpdate else { return "waiting-for-initial-path" }
        return isPathSatisfied ? "satisfied" : "unsatisfied"
    }

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let isSatisfied = path.status == .satisfied
            Task { @MainActor [weak self] in
                self?.updatePath(isSatisfied: isSatisfied)
            }
        }

        // NWPathMonitor requires a DispatchQueue for delivery. State is forwarded
        // immediately to MainActor before it is read by the UI presentation flow.
        monitor.start(queue: monitorQueue)
    }

    func start() {
        print("[ArtApps] Reachability monitor active. State: \(statusDescription)")
    }

    private func updatePath(isSatisfied: Bool) {
        let didChange = !hasReceivedPathUpdate || isPathSatisfied != isSatisfied
        hasReceivedPathUpdate = true
        isPathSatisfied = isSatisfied

        if didChange {
            print("[ArtApps] Network path changed: \(statusDescription)")
        }
    }
}
