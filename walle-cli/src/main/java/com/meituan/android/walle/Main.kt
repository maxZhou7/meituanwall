package com.meituan.android.walle

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.meituan.android.walle.commands.Batch2Command
import com.meituan.android.walle.commands.BatchCommand
import com.meituan.android.walle.commands.IWalleCommand
import com.meituan.android.walle.commands.PutCommand
import com.meituan.android.walle.commands.RemoveCommand
import com.meituan.android.walle.commands.ShowCommand

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val subCommandList = mutableMapOf<String, IWalleCommand>()
        subCommandList["show"] = ShowCommand()
        subCommandList["rm"] = RemoveCommand()
        subCommandList["put"] = PutCommand()
        subCommandList["batch"] = BatchCommand()
        subCommandList["batch2"] = Batch2Command()

        val walleCommandLine = WalleCommandLine()
        val commander = JCommander(walleCommandLine)

        subCommandList.forEach { (key, value) ->
            commander.addCommand(key, value)
        }

        try {
            commander.parse(*args)
        } catch (e: ParameterException) {
            println(e.message)
            commander.usage()
            System.exit(1)
            return
        }

        walleCommandLine.parse(commander)

        val parseCommand = commander.parsedCommand
        if (parseCommand != null) {
            subCommandList[parseCommand]?.parse()
        }
    }
}
