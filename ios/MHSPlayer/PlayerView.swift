import SwiftUI
import AVKit

struct PlayerView: View {
    let item: MediaItem
    @StateObject private var playerModel = PlayerModel()

    var body: some View {
        VStack(spacing: 0) {
            VideoPlayer(player: playerModel.player)
                .background(.black)
                .ignoresSafeArea(edges: .bottom)

            HStack {
                Text(item.title)
                    .font(.headline)
                    .lineLimit(1)
                Spacer()
                Button {
                    playerModel.togglePlayPause()
                } label: {
                    Image(systemName: playerModel.isPlaying ? "pause.fill" : "play.fill")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
        .navigationTitle(item.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            playerModel.load(item.url)
        }
        .onDisappear {
            playerModel.stop()
        }
    }
}

@MainActor
final class PlayerModel: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var isPlaying = false

    func load(_ url: URL) {
        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        player.play()
        isPlaying = true
    }

    func togglePlayPause() {
        if isPlaying {
            player.pause()
        } else {
            player.play()
        }
        isPlaying.toggle()
    }

    func stop() {
        player.pause()
        isPlaying = false
    }
}