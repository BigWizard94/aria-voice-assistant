package com.bigwizard.aria

import com.bigwizard.aria.ai.CommandParser
import com.bigwizard.aria.data.model.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private fun parse(s: String) = CommandParser.parse(s)

    @Test fun `call commands`() {
        assertEquals(VoiceCommand.PhoneCall("mom"), parse("call mom"))
        assertEquals(VoiceCommand.PhoneCall("John Smith"), parse("Hey Aria, please call John Smith"))
        assertEquals(VoiceCommand.PhoneCall("dad"), parse("can you call dad"))
    }

    @Test fun `command words inside a sentence go to the AI`() {
        assertTrue(parse("can you recall what I said") is VoiceCommand.AiQuery)
        assertTrue(parse("bring me some ideas for dinner") is VoiceCommand.AiQuery)
        assertTrue(parse("what is the capital of japan") is VoiceCommand.AiQuery)
        assertTrue(parse("who is the president of france") is VoiceCommand.AiQuery)
        assertTrue(parse("how do I display the time on my watch") is VoiceCommand.AiQuery)
        assertTrue(parse("tell me a joke") is VoiceCommand.AiQuery)
    }

    @Test fun `sms commands`() {
        assertEquals(VoiceCommand.SendSms("john", "I'll be late"), parse("text john saying I'll be late"))
        assertEquals(VoiceCommand.SendSms("mom", "dinner is ready"), parse("send a message to mom that dinner is ready"))
    }

    @Test fun `timers and alarms`() {
        assertEquals(VoiceCommand.SetTimer("10 minutes"), parse("set a timer for 10 minutes"))
        assertEquals(VoiceCommand.SetTimer("five minutes"), parse("timer for five minutes"))
        assertEquals(VoiceCommand.SetTimer("five minute"), parse("set a five minute timer"))
        assertEquals(VoiceCommand.SetTimer(""), parse("start a timer"))
        assertEquals(VoiceCommand.SetAlarm("seven thirty a m"), parse("set an alarm for seven thirty a m"))
        assertEquals(VoiceCommand.SetAlarm("6 am"), parse("wake me up at 6 am"))
    }

    @Test fun `apps music and search`() {
        assertEquals(VoiceCommand.OpenApp("youtube"), parse("open youtube"))
        assertEquals(VoiceCommand.OpenApp("camera"), parse("launch the camera app"))
        assertEquals(VoiceCommand.OpenApp("spotify"), parse("play spotify"))
        assertEquals(VoiceCommand.PlayMusic("jazz"), parse("play some jazz"))
        assertEquals(VoiceCommand.WebSearch("pizza near me"), parse("search for pizza near me"))
        assertEquals(VoiceCommand.WebSearch("cheap flights"), parse("look up cheap flights"))
    }

    @Test fun `stop commands`() {
        assertEquals(VoiceCommand.StopListening, parse("stop"))
        assertEquals(VoiceCommand.StopListening, parse("never mind"))
        assertEquals(VoiceCommand.StopListening, parse("that's all"))
    }

    @Test fun `blank input`() {
        assertEquals(VoiceCommand.Unknown, parse("   "))
    }
}
