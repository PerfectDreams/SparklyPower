package net.sparklypower.sparklyneonvelocity.utils

import com.google.gson.Gson
import java.time.ZoneId

object DreamUtils {
    val gson = Gson()

    val serverZoneId: ZoneId = ZoneId.of("America/Sao_Paulo")
}