package com.example.imagegen.generator

import java.util.regex.Pattern

/**
 * 消息解析工具类，等价 Python 版 image_processor 的 parse_tag_and_port / extract_downlink_number / mask_phone_or_account
 */
object MessageParser {

    private val TAG_PATTERN: Pattern =
        Pattern.compile("标签[:：]\\s*(\\S+)|标签\\s+(\\S+)")
    private val PORT_PATTERN: Pattern =
        Pattern.compile("端口[:：]\\s*(\\S+)|端口\\s+(\\S+)")
    private val DOWNLINK_PATTERN: Pattern =
        Pattern.compile("1\\s*[:：]\\s*(\\d+)")

    /** 从一段消息里解析出 (tag, port)，找不到返回 null */
    fun parseTagAndPort(msg: String): Pair<String?, String?> {
        val tag = firstGroup(TAG_PATTERN.matcher(msg))
        val port = firstGroup(PORT_PATTERN.matcher(msg))
        return tag to port
    }

    private fun firstGroup(m: java.util.regex.Matcher): String? {
        if (!m.find()) return null
        for (i in 1..m.groupCount()) {
            val g = m.group(i)
            if (!g.isNullOrEmpty()) return g
        }
        return null
    }

    /** 从端口字符串里提取下行数（1:N 中的 N） */
    fun extractDownlinkNumber(portText: String?): Int? {
        if (portText.isNullOrEmpty()) return null
        val m = DOWNLINK_PATTERN.matcher(portText)
        return if (m.find()) m.group(1)?.toIntOrNull() else null
    }

    /**
     * 脱敏处理：11 位手机号 -> 前 3 + **** + 后 4；其他长度 -> 中间 4 位用 **** 替换
     * 等价 Python 版 mask_phone_or_account
     */
    fun maskPhoneOrAccount(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val length = text.length
        return when (length) {
            11 -> text.substring(0, 3) + "****" + text.substring(length - 4)
            else -> {
                if (length <= 4) text
                else {
                    val keep = (length - 4) / 2
                    text.substring(0, keep) + "****" + text.substring(length - keep)
                }
            }
        }
    }
}
