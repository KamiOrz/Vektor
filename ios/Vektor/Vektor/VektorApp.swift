import SwiftUI

@main
struct VektorApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .task {
                    model.bootstrap()
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            switch model.route {
            case .scan:
                ScanView()
            case .playback:
                PlaybackView()
            }

            if case .loading(let message) = model.loadingState {
                StatusOverlay(message: message)
            }
        }
        .background(Color.black)
        .preferredColorScheme(.dark)
    }
}

struct StatusOverlay: View {
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(Color.vektorGreen)
            Text(message.uppercased())
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(2)
                .foregroundStyle(Color.vektorGreen)
        }
        .padding(20)
        .background(.black.opacity(0.72))
        .overlay(Rectangle().stroke(.white.opacity(0.12), lineWidth: 1))
    }
}

extension Color {
    static let vektorGreen = Color(red: 42 / 255, green: 229 / 255, blue: 0)
    static let vektorText = Color(red: 229 / 255, green: 226 / 255, blue: 225 / 255)
    static let vektorMuted = Color(red: 207 / 255, green: 196 / 255, blue: 197 / 255)
}
