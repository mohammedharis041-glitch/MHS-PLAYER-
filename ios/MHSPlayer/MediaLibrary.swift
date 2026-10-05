import Foundation
import UniformTypeIdentifiers

struct MediaItem: Identifiable, Hashable {
    let id: UUID
    let url: URL
    let title: String

    init(url: URL) {
        id = UUID()
        self.url = url
        title = url.deletingPathExtension().lastPathComponent
    }
}

@MainActor
final class MediaLibrary: ObservableObject {
    @Published private(set) var items: [MediaItem] = []
    @Published var selected: MediaItem?

    private let folderName = "ImportedMedia"

    init() {
        loadPersistedItems()
    }

    func importFiles(_ urls: [URL]) {
        let imported = urls.compactMap(copyIntoLibrary)
        items.append(contentsOf: imported)
        if selected == nil { selected = imported.first }
    }

    func remove(_ item: MediaItem) {
        try? FileManager.default.removeItem(at: item.url)
        items.removeAll { $0.id == item.id }
        if selected?.id == item.id { selected = items.first }
    }

    private var libraryFolder: URL {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let folder = documents.appendingPathComponent(folderName, isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    private func copyIntoLibrary(_ url: URL) -> MediaItem? {
        let destination = libraryFolder.appendingPathComponent(url.lastPathComponent)
        let finalURL: URL
        do {
            if FileManager.default.fileExists(atPath: destination.path) {
                finalURL = libraryFolder.appendingPathComponent(UUID().uuidString + "-" + url.lastPathComponent)
            } else {
                finalURL = destination
            }
            try FileManager.default.copyItem(at: url, to: finalURL)
            return MediaItem(url: finalURL)
        } catch {
            return nil
        }
    }

    private func loadPersistedItems() {
        let urls = (try? FileManager.default.contentsOfDirectory(
            at: libraryFolder,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        )) ?? []

        items = urls.filter { url in
            let type = UTType(filenameExtension: url.pathExtension)
            return type?.conforms(to: .audiovisualContent) == true
                || type?.conforms(to: .audio) == true
        }.map(MediaItem.init)
        selected = items.first
    }
}
