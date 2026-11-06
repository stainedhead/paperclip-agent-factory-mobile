package com.stainedhead.paperclipmobile.umbrella

/**
 * The platform a graph gets when the app passes none (`PaperclipCore.createLiveGraph(allowInsecureLocal, browser)`):
 * the iOS device services on iOS; on Android and the JVM the fail-closed pieces (ADR-069).
 */
internal expect fun defaultLivePlatform(): LivePlatform
