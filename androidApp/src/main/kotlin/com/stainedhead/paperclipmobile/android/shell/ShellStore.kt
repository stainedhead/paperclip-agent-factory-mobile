package com.stainedhead.paperclipmobile.android.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Where the tab shell's navigation keeps the view models of its screens (their holders). It lives in the activity, so a
 * rotation keeps every holder, and [release] closes them all when the shell is left for good (R-021).
 */
internal class ShellStore :
    ViewModel(),
    ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun release() = viewModelStore.clear()

    override fun onCleared() = release()
}
