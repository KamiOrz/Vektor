@preconcurrency import AVFoundation
import UIKit

@MainActor
final class CameraScannerService: NSObject, ObservableObject {
    @Published private(set) var session = AVCaptureSession()
    @Published private(set) var permissionDenied = false
    @Published var onCode: ((String) -> Void)?

    private let output = AVCaptureMetadataOutput()
    private var isConfigured = false

    func start() {
        Task {
            let granted = await requestAccess()
            guard granted else {
                permissionDenied = true
                return
            }
            permissionDenied = false
            configureIfNeeded()
            if !session.isRunning {
                DispatchQueue.global(qos: .userInitiated).async { [session] in
                    session.startRunning()
                }
            }
        }
    }

    func stopAndRelease() {
        if session.isRunning {
            session.stopRunning()
        }
        session.inputs.forEach { session.removeInput($0) }
        session.outputs.forEach { session.removeOutput($0) }
        isConfigured = false
    }

    private func requestAccess() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return true
        case .notDetermined:
            return await AVCaptureDevice.requestAccess(for: .video)
        default:
            return false
        }
    }

    private func configureIfNeeded() {
        guard !isConfigured else { return }
        session.beginConfiguration()
        session.sessionPreset = .high
        defer {
            session.commitConfiguration()
            isConfigured = true
        }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input),
              session.canAddOutput(output) else {
            return
        }

        session.addInput(input)
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
    }
}

extension CameraScannerService: AVCaptureMetadataOutputObjectsDelegate {
    nonisolated func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let value = metadataObjects
            .compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
            .compactMap(\.stringValue)
            .first else { return }

        Task { @MainActor in
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
            onCode?(value)
        }
    }
}
