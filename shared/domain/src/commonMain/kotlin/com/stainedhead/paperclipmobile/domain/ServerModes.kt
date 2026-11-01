package com.stainedhead.paperclipmobile.domain

/** How the server authenticates people. `LocalTrusted` is refused (PRD FR-005); `Unknown` is treated the same way. */
enum class DeploymentMode {
    LocalTrusted,
    Authenticated,
    Unknown,
    ;

    companion object {
        fun fromServer(value: String?): DeploymentMode =
            when (value) {
                "local_trusted" -> LocalTrusted
                "authenticated" -> Authenticated
                else -> Unknown
            }
    }
}

/** Where the server says it is reachable from. */
enum class Exposure {
    Private,
    Public,
    Unknown,
    ;

    companion object {
        fun fromServer(value: String?): Exposure =
            when (value) {
                "private" -> Private
                "public" -> Public
                else -> Unknown
            }
    }
}

/** How the stored board key was obtained (PRD FR-001). */
enum class AuthMethod {
    PairedBoardKey,
    ManualBoardKey,
}

/** What the app knows about the credentials of one factory. */
enum class InstanceStatus {
    SignedIn,
    SignedOut,
    NeedsRePairing,
}
