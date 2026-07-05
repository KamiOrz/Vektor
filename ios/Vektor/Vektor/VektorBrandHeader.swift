import SwiftUI

struct VektorBrandHeader: View {
    private let trailing: AnyView

    init() {
        self.trailing = AnyView(EmptyView())
    }

    init<Trailing: View>(@ViewBuilder trailing: () -> Trailing) {
        self.trailing = AnyView(trailing())
    }

    var body: some View {
        HStack(spacing: 14) {
            VektorLogo()
                .frame(width: 40, height: 40)
            Text("VEKTOR")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(Color.vektorText)
            Spacer()
            trailing
        }
    }
}

struct VektorLogo: View {
    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height
            Path { path in
                path.move(to: CGPoint(x: width * 0.18, y: height * 0.18))
                path.addLine(to: CGPoint(x: width * 0.5, y: height * 0.84))
                path.addLine(to: CGPoint(x: width * 0.82, y: height * 0.18))
            }
            .stroke(Color.vektorGreen, style: StrokeStyle(lineWidth: 6, lineCap: .square, lineJoin: .miter))
        }
    }
}
