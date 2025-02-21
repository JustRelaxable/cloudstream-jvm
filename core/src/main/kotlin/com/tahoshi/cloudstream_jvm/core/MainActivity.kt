package com.tahoshi.cloudstream_jvm.core

import com.tahoshi.cloudstream_jvm.core.utils.Event


class MainActivity {
    companion object {
        /**
         * Fires every time a new batch of plugins have been loaded, no guarantee about how often this is run and on which thread
         * Boolean signifies if stuff should be force reloaded (true if force reload, false if reload when necessary).
         *
         * The force reloading are used for plugin development to instantly reload the page on deployWithAdb
         * */
        val afterPluginsLoadedEvent = Event<Boolean>()
    }
}
