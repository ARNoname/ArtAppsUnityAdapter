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
    private var didNotifyDisplay = false
    
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
        
        setupSwiftUI()
    }
    
    override var prefersStatusBarHidden: Bool {
        return true
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        guard !didNotifyDisplay else { return }
        didNotifyDisplay = true
        delegate?.webViewControllerDidDisplay(self)
    }
    
    private func setupSwiftUI() {
        let adView = ArtAppsAdView(
            url: url,
            onClose: { [weak self] in
                self?.handleClose()
            },
            onFail: { [weak self] error in
                guard let self = self else { return }
                self.delegate?.webViewController(self, didFailWithError: error)
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
    
    private func handleClose() {
        dismiss(animated: true) { [weak self] in
            guard let self = self else { return }
            self.delegate?.webViewControllerDidFinish(self)
        }
    }
}
