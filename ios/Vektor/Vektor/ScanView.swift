import SwiftUI

struct ScanView: View {
    @EnvironmentObject private var model: AppModel
    @State private var scanOffset: CGFloat = -180
    @State private var showingHistory = false

    var body: some View {
        ZStack {
            ScannerPreviewView(session: model.scannerService.session)
                .ignoresSafeArea()
                .opacity(model.scannerService.permissionDenied ? 0 : 1)

            GridBackground()
                .ignoresSafeArea()

            VStack {
                Header()
                Spacer()
                scannerFrame
                Spacer()
                bottomAction
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 26)
        }
        .onAppear {
            model.scannerService.start()
            withAnimation(.linear(duration: 2.8).repeatForever(autoreverses: false)) {
                scanOffset = 180
            }
        }
        .onDisappear {
            model.scannerService.stopAndRelease()
        }
        .sheet(isPresented: $showingHistory) {
            ScanHistorySheet(
                items: model.scanHistory,
                onSelect: { item in
                    showingHistory = false
                    Task { await model.loadFromHistory(item) }
                },
                onDelete: model.deleteHistoryItem,
                onClear: {
                    model.clearHistory()
                    showingHistory = false
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .preferredColorScheme(.dark)
        }
    }

    private var scannerFrame: some View {
        VStack(spacing: 26) {
            ZStack {
                Rectangle()
                    .fill(Color.vektorGreen.opacity(0.06))
                    .overlay(Rectangle().stroke(Color.white.opacity(0.06), lineWidth: 1))

                Rectangle()
                    .fill(Color.vektorGreen)
                    .frame(height: 1)
                    .shadow(color: .vektorGreen, radius: 10)
                    .offset(y: scanOffset)

                CornerFrame()
            }
            .frame(width: 290, height: 290)

            HStack(spacing: 10) {
                Circle()
                    .fill(Color.vektorGreen)
                    .frame(width: 8, height: 8)
                Text(model.scannerService.permissionDenied ? "CAMERA ACCESS NEEDED" : "SCANNING...")
                    .font(.system(size: 13, weight: .semibold, design: .monospaced))
                    .tracking(4)
                    .foregroundStyle(Color.vektorGreen)
            }
        }
    }

    private var bottomAction: some View {
        VStack(spacing: 12) {
            Button {
                Task { await model.loadClipboard() }
            } label: {
                Text("LOAD CLIPBOARD M3U/HLS URL")
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .tracking(2)
                    .foregroundStyle(Color.vektorText)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .overlay(Rectangle().stroke(Color.white.opacity(0.22), lineWidth: 1))
            }

            if !model.scanHistory.isEmpty {
                Button {
                    showingHistory = true
                } label: {
                    Text("HISTORY")
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .tracking(2)
                        .foregroundStyle(Color.vektorGreen)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 8)
                        .overlay(Rectangle().stroke(Color.vektorGreen.opacity(0.45), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }

            Text("ALIGN QR CODE OR PASTE M3U/HLS URL")
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(2)
                .foregroundStyle(Color.vektorMuted.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }
}

private struct Header: View {
    var body: some View {
        VektorBrandHeader()
        .padding(.top, 18)
    }
}

private struct ScanHistorySheet: View {
    let items: [ScanHistoryItem]
    let onSelect: (ScanHistoryItem) -> Void
    let onDelete: (ScanHistoryItem) -> Void
    let onClear: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("SCAN HISTORY")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color.vektorText)
                Spacer()
                Button("CLEAR") {
                    onClear()
                }
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundStyle(Color.vektorGreen)
                .disabled(items.isEmpty)
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 14)

            Divider().overlay(Color.white.opacity(0.10))

            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(items) { item in
                        ScanHistoryRow(item: item, onSelect: onSelect, onDelete: onDelete)
                    }
                }
                .padding(20)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

private struct ScanHistoryRow: View {
    let item: ScanHistoryItem
    let onSelect: (ScanHistoryItem) -> Void
    let onDelete: (ScanHistoryItem) -> Void

    var body: some View {
        Button {
            onSelect(item)
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(item.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.vektorText)
                        .lineLimit(1)
                    Text(summary)
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundStyle(Color.vektorMuted.opacity(0.7))
                        .lineLimit(1)
                    Text(relativeTime)
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundStyle(Color.vektorGreen.opacity(0.85))
                }

                Spacer()

                Button {
                    onDelete(item)
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.vektorMuted.opacity(0.8))
                        .frame(width: 34, height: 34)
                        .overlay(Rectangle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            .padding(14)
            .background(Color.white.opacity(0.05))
            .overlay(Rectangle().stroke(Color.white.opacity(0.12), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var summary: String {
        let path = item.url.path.isEmpty ? "/" : item.url.path
        return "\(item.url.host ?? item.url.scheme ?? "link")\(path)"
    }

    private var relativeTime: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: item.lastUsedAt, relativeTo: Date())
    }
}

private struct CornerFrame: View {
    var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width
            let h = proxy.size.height
            Path { path in
                let l: CGFloat = 42
                path.move(to: .zero); path.addLine(to: CGPoint(x: l, y: 0)); path.move(to: .zero); path.addLine(to: CGPoint(x: 0, y: l))
                path.move(to: CGPoint(x: w, y: 0)); path.addLine(to: CGPoint(x: w - l, y: 0)); path.move(to: CGPoint(x: w, y: 0)); path.addLine(to: CGPoint(x: w, y: l))
                path.move(to: CGPoint(x: 0, y: h)); path.addLine(to: CGPoint(x: l, y: h)); path.move(to: CGPoint(x: 0, y: h)); path.addLine(to: CGPoint(x: 0, y: h - l))
                path.move(to: CGPoint(x: w, y: h)); path.addLine(to: CGPoint(x: w - l, y: h)); path.move(to: CGPoint(x: w, y: h)); path.addLine(to: CGPoint(x: w, y: h - l))
            }
            .stroke(Color.vektorGreen, lineWidth: 3)
        }
    }
}

private struct GridBackground: View {
    var body: some View {
        Canvas { context, size in
            let spacing: CGFloat = 40
            var path = Path()
            stride(from: CGFloat.zero, through: size.width, by: spacing).forEach {
                path.move(to: CGPoint(x: $0, y: 0))
                path.addLine(to: CGPoint(x: $0, y: size.height))
            }
            stride(from: CGFloat.zero, through: size.height, by: spacing).forEach {
                path.move(to: CGPoint(x: 0, y: $0))
                path.addLine(to: CGPoint(x: size.width, y: $0))
            }
            context.stroke(path, with: .color(Color.vektorGreen.opacity(0.06)), lineWidth: 1)
        }
        .background(Color.black.opacity(0.86))
    }
}
