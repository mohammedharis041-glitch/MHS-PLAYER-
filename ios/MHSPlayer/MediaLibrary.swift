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

    func importFiles(_ urls: [URL]) {
        let newItems = urls.map(MediaItem.init)
        items.append(contentsOf: newItems)
        if selected == nil { selected = newItems.first }
    }

    func remove(_ item: MediaItem) {
        items.removeAll { $0.id == item.id }
        if selected?.id == item.id { selected = items.first }
    }
}