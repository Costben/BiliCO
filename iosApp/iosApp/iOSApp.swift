import UIKit
import AVFAudio
import shared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback, options: [])
        try? session.setActive(true)
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        print("DIAG|swift|didBecomeActive")
    }

    func applicationWillResignActive(_ application: UIApplication) {
        print("DIAG|swift|willResignActive")
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = BiliCoRootViewController()
        self.window = window
        window.makeKeyAndVisible()
    }
}

final class BiliCoRootViewController: UIViewController {
    private let composeVC = MainViewControllerKt.MainViewController()
    private var orientationMask: UIInterfaceOrientationMask = .portrait
    private var systemBarsVisible: Bool = true
    private var statusBarDarkIcons: Bool? = nil

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(composeVC)
        composeVC.view.frame = view.bounds
        composeVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(composeVC.view)
        composeVC.didMove(toParent: self)

        let nc = NotificationCenter.default
        nc.addObserver(
            self,
            selector: #selector(handleOrientationChanged(_:)),
            name: Notification.Name("BiliCoOrientationChanged"),
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleSystemBarsChanged(_:)),
            name: Notification.Name("BiliCoSystemBarsChanged"),
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleStatusBarStyleChanged(_:)),
            name: Notification.Name("BiliCoStatusBarStyleChanged"),
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    private var lastLayoutTrace = ""

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let trace = "rootLayout bounds=\(view.bounds.size) safe=\(view.safeAreaInsets) " +
            "orient=\(view.window?.windowScene?.interfaceOrientation.rawValue ?? -1) " +
            "bars=\(systemBarsVisible) mask=\(orientationMask.rawValue)"
        if trace != lastLayoutTrace {
            lastLayoutTrace = trace
            print("DIAG|swift|\(trace)")
        }
    }

    override func viewWillTransition(
        to size: CGSize,
        with coordinator: UIViewControllerTransitionCoordinator
    ) {
        super.viewWillTransition(to: size, with: coordinator)
        print("DIAG|swift|willTransition size=\(size)")
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        orientationMask
    }

    override var prefersStatusBarHidden: Bool {
        !systemBarsVisible
    }

    override var prefersHomeIndicatorAutoHidden: Bool {
        !systemBarsVisible
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        switch statusBarDarkIcons {
        case true: return .darkContent
        case false: return .lightContent
        default: return .default
        }
    }

    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation {
        .fade
    }

    @objc private func handleOrientationChanged(_ note: Notification) {
        guard let rawMask = (note.userInfo?["mask"] as? NSNumber)?.uintValue else { return }
        let nextMask = UIInterfaceOrientationMask(rawValue: rawMask)
        let maskChanged = orientationMask != nextMask
        if maskChanged {
            orientationMask = nextMask
            setNeedsUpdateOfSupportedInterfaceOrientations()
        }
        if let windowScene = view.window?.windowScene {
            let currentBit: UIInterfaceOrientationMask = switch windowScene.interfaceOrientation {
            case .portrait: .portrait
            case .portraitUpsideDown: .portraitUpsideDown
            case .landscapeLeft: .landscapeLeft
            case .landscapeRight: .landscapeRight
            default: []
            }
            if maskChanged || !nextMask.contains(currentBit) {
                print("DIAG|swift|requestGeometry mask=\(nextMask.rawValue) cur=\(windowScene.interfaceOrientation.rawValue)")
                windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: nextMask)) { error in
                    print("DIAG|swift|geometryError \(error.localizedDescription)")
                }
            }
        }
    }

    @objc private func handleSystemBarsChanged(_ note: Notification) {
        guard let visible = (note.userInfo?["visible"] as? NSNumber)?.boolValue else { return }
        if systemBarsVisible != visible {
            systemBarsVisible = visible
            UIView.animate(withDuration: 0.2) {
                self.setNeedsStatusBarAppearanceUpdate()
            }
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }

    @objc private func handleStatusBarStyleChanged(_ note: Notification) {
        guard let raw = (note.userInfo?["darkIcons"] as? NSNumber)?.intValue else { return }
        let next: Bool? = switch raw {
        case 1: true
        case 0: false
        default: nil
        }
        if statusBarDarkIcons != next {
            statusBarDarkIcons = next
            setNeedsStatusBarAppearanceUpdate()
        }
    }
}
