package com.meituan.android.walle

class WalleConfig {

    companion object {
        /**
         * strategy:
         * 1. ifNone (默认适用此策略) : 仅当对应channel没有extraInfo时生效
         * 2. always : 所有channel都生效，channel中extraInfo的key与defaultExtraInfo重复时，覆盖defaultExtraInfo中的内容。
         */
        const val STRATEGY_IF_NONE = "ifNone"
        const val STRATEGY_ALWAYS = "always"
    }

    var defaultExtraInfoStrategy: String = STRATEGY_IF_NONE

    var defaultExtraInfo: Map<String, String>? = null

    var channelInfoList: List<ChannelInfo>? = null

    class ChannelInfo {
        var channel: String? = null
        var alias: String? = null

        /**
         * 强制声明不使用defaultExtraInfo参数
         */
        var isExcludeDefaultExtraInfo: Boolean = false

        var extraInfo: Map<String, String>? = null
    }
}
