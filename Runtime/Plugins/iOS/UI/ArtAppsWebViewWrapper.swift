import SwiftUI
import WebKit

struct ArtAppsWebViewWrapper: UIViewRepresentable {
    let url: URL
    let onFail: (Error) -> Void
    
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {
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
        private var didFailInitialNavigation = false
        
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
        
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            didFinishInitialNavigation = true
        }
        
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            notifyFailureIfNeeded(error)
        }
        
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            notifyFailureIfNeeded(error)
        }
        
        private func notifyFailureIfNeeded(_ error: Error) {
            if (error as? URLError)?.code == .cancelled {
                return
            }
            
            guard !didFinishInitialNavigation, !didFailInitialNavigation else { return }
            didFailInitialNavigation = true
            parent.onFail(error)
        }
    }
}
