// swift-tools-version: 6.0
import PackageDescription

// Wraps the Kotlin Multiplatform framework built by scripts/build-kit.sh. The framework is a build product
// (git-ignored), so run that script before resolving this package.
let package = Package(
    name: "PaperclipKit",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "PaperclipKit", targets: ["PaperclipKit"])
    ],
    targets: [
        .binaryTarget(name: "PaperclipKit", path: "../../Frameworks/PaperclipKit.xcframework")
    ]
)
