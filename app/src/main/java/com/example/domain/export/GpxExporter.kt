package com.example.domain.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    fun exportGpxFile(context: Context, ride: RideEntity, points: List<TrackPointEntity>): File {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val dateStr = timeFormat.format(Date(ride.startTime))
        val cleanFileName = "VeloSense_${dateStr}.gpx"

        val gpxDir = File(context.cacheDir, "gpx").apply {
            if (!exists()) mkdirs()
        }
        val file = File(gpxDir, cleanFileName)
        val xmlContent = generateGpxString(ride, points)
        file.writeText(xmlContent, Charsets.UTF_8)
        return file
    }

    fun createShareIntent(context: Context, gpxFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, gpxFile)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, gpxFile.name)
            clipData = ClipData.newRawUri(gpxFile.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun generateGpxString(ride: RideEntity, points: List<TrackPointEntity>): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="VeloSense V3 Bike Computer" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>${escapeXml(ride.title)}</name>
    <time>${isoFormat.format(Date(ride.startTime))}</time>
  </metadata>
  <trk>
    <name>${escapeXml(ride.title)}</name>
    <type>${escapeXml(ride.activityType)}</type>
    <trkseg>
""")

        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${String.format(Locale.US, "%.1f", pt.altitude)}</ele>\n")
            sb.append("        <time>${isoFormat.format(Date(pt.timestamp))}</time>\n")
            sb.append("        <extensions>\n")
            sb.append("          <speed>${String.format(Locale.US, "%.2f", pt.speedKmh / 3.6)}</speed>\n")
            if (pt.estimatedPowerWatts > 0) {
                sb.append("          <power>${pt.estimatedPowerWatts}</power>\n")
            }
            if (pt.estimatedCadenceRpm > 0) {
                sb.append("          <cadence>${pt.estimatedCadenceRpm}</cadence>\n")
            }
            sb.append("        </extensions>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("""    </trkseg>
  </trk>
</gpx>
""")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
