import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject private var library: MediaLibrary
    @State private var showingImporter = false

    var body: some View {
        NavigationSplitView {
            List(selection: $library.selected) {
                Section("Library") {
                    if library.items.isEmpty {
                        ContentUnavailableView(
                            "No Media",
                            systemImage: "play.rectangle",
                            description: Text("Import videos or audio files to start.")
                        )
                    } else {
                        ForEach(library.items) { item in
                            Label(item.title, systemImage: "play.rectangle")
                                .tag(item)
                        }
                        .onDelete { offsets in
                            offsets.map { library.items[$0] }.forEach(library.remove)
                        }
                    }
                }
            }
            .navigationTitle("MHS Player")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showingImporter = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Import media")
                }
            }
        } detail: {
            if let item = library.selected {
                PlayerView(item: item)
            } else {
                ContentUnavailableView(
                    "Select Media",
                    systemImage: "play.circle",
                    description: Text("Choose a file from your library.")
                )
            }
        }
        .fileImporter(
            isPresented: $showingImporter,
            allowedContentTypes: [.movie, .audio, .video, .mpeg4Movie, .mp3, .mpeg4Audio],
            allowsMultipleSelection: true
        ) { result in
            if case .success(let urls) = result {
                let secured = urls.compactMap { url -> URL? in
                    guard url.startAccessingSecurityScopedResource() else { return nil }
                    defer { url.stopAccessingSecurityScopedResource() }
                    return url
                }
                library.importFiles(secured)
            }
        }
    }
}