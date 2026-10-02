package com.orlauf.rook

/** Разобранный пакет Treadmill Data (0x2ACD). Берём только нужное. */
data class TreadmillData(
    val speedKmh: Double,
    val distanceM: Int?,
    val elapsedS: Int?,
)

object FtmsParser {
    /**
     * Формат по спецификации FTMS. Флаги (uint16 LE):
     * bit0 = 0 -> есть Instantaneous Speed (uint16, 0.01 км/ч)
     * bit1 Average Speed (2)      bit2 Total Distance (3)
     * bit3 Inclination+Ramp (2+2) bit4 Elevation Gain (2+2)
     * bit5 Inst. Pace (1)         bit6 Avg. Pace (1)
     * bit7 Energy (2+2+1)         bit8 Heart Rate (1)
     * bit9 METs (1)               bit10 Elapsed Time (2)
     * bit11 Remaining Time (2)    bit12 Force+Power (2+2)
     *
     * Проверено на пакете дорожки: 8C 05 B4 00 73 00 00 ... -> 1.80 км/ч, 115 м.
     */
    fun parse(b: ByteArray): TreadmillData? {
        if (b.size < 2) return null
        val flags = u16(b, 0)
        var p = 2

        fun need(n: Int) = p + n <= b.size

        var speed = 0.0
        if (flags and 0x0001 == 0) {
            if (!need(2)) return null
            speed = u16(b, p) / 100.0
            p += 2
        }
        if (flags and 0x0002 != 0) { if (!need(2)) return null; p += 2 }
        var dist: Int? = null
        if (flags and 0x0004 != 0) {
            if (!need(3)) return null
            dist = u16(b, p) or ((b[p + 2].toInt() and 0xFF) shl 16)
            p += 3
        }
        if (flags and 0x0008 != 0) { if (!need(4)) return null; p += 4 }
        if (flags and 0x0010 != 0) { if (!need(4)) return null; p += 4 }
        if (flags and 0x0020 != 0) { if (!need(1)) return null; p += 1 }
        if (flags and 0x0040 != 0) { if (!need(1)) return null; p += 1 }
        if (flags and 0x0080 != 0) { if (!need(5)) return null; p += 5 }
        if (flags and 0x0100 != 0) { if (!need(1)) return null; p += 1 }
        if (flags and 0x0200 != 0) { if (!need(1)) return null; p += 1 }
        var elapsed: Int? = null
        if (flags and 0x0400 != 0) {
            if (!need(2)) return null
            elapsed = u16(b, p)
            p += 2
        }
        return TreadmillData(speed, dist, elapsed)
    }

    private fun u16(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}
