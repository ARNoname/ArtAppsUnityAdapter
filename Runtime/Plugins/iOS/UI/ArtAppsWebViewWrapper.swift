import SwiftUI
import WebKit

struct ArtAppsWebViewWrapper: UIViewRepresentable {
    let url: URL
    let onDisplay: () -> Void
    let onFail: (Error) -> Void

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.parent = self

        if uiView.url == nil {
            let request = URLRequest(url: url)
            uiView.load(request)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    @MainActor
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: ArtAppsWebViewWrapper
        private var didFinishInitialNavigation = false
        private var didNotifyDisplay = false
        private var didNotifyFailure = false

        init(parent: ArtAppsWebViewWrapper) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
            if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
                return .cancel
            }
            return .allow
        }

        func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
            notifyDisplayIfNeeded()
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            didFinishInitialNavigation = true
            notifyDisplayIfNeeded()
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            notifyFailureIfNeeded(error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            notifyFailureIfNeeded(error)
        }

        private func notifyDisplayIfNeeded() {
            guard !didNotifyDisplay, !didNotifyFailure else { return }
            didNotifyDisplay = true
            parent.onDisplay()
        }

        private func notifyFailureIfNeeded(_ error: Error) {
            let code = urlErrorCode(from: error)
            if code == .cancelled {
                return
            }

            let shouldReportFailure = !didFinishInitialNavigation || isTerminalNetworkError(code)
            guard shouldReportFailure, !didNotifyFailure else { return }
            didNotifyFailure = true
            parent.onFail(error)
        }

        private func isTerminalNetworkError(_ code: URLError.Code?) -> Bool {
            switch code {
            case .notConnectedToInternet,
                    .networkConnectionLost,
                    .timedOut,
                    .cannotFindHost,
                    .cannotConnectToHost,
                    .dnsLookupFailed:
                return true
            default:
                return false
            }
        }

        private func urlErrorCode(from error: Error) -> URLError.Code? {
            if let urlError = error as? URLError {
                return urlError.code
            }

            let nsError = error as NSError
            guard nsError.domain == NSURLErrorDomain else {
                return nil
            }

            return URLError.Code(rawValue: nsError.code)
        }
    }
}
