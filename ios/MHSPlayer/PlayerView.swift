import SwiftUI
import AVKit
import AVFoundation
import MediaPlayer

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
        .onAppear { playerModel.load(item.url, title: item.title) }
        .onDisappear { playerModel.stop() }
    }
}

@MainActor
final class PlayerModel: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var isPlaying = false

    func load(_ url: URL, title: String) {
        configureAudioSession()
        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        player.play()
        isPlaying = true
        updateNowPlaying(title: title)
    }

    func togglePlayPause() {
        if isPlaying {
            player.pause()
        } else {
            player.play()
        }
        isPlaying.toggle()
        MPNowPlayingInfoCenter.default().playbackState = isPlaying ? .playing : .paused
    }

    func stop() {
        player.pause()
        isPlaying = false
        MPNowPlayingInfoCenter.default().playbackState = .stopped
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            // Playback still works when audio-session activation is unavailable.
        }
    }

    private func updateNowPlaying(title: String) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: title,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0
        ]
        MPNowPlayingInfoCenter.default().playbackState = .playing
    }
}
