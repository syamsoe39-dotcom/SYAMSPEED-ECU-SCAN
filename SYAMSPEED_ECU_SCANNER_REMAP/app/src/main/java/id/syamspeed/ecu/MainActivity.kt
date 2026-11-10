package id.syamspeed.ecu

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.security.MessageDigest

private const val ACTION_USB_PERMISSION = "id.syamspeed.ecu.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    private var usbStatus by mutableStateOf("belum terhubung")
    private var usbDetails by mutableStateOf("sambungkan kabel USB KKL/FTDI melalui OTG")
    private var binName by mutableStateOf("belum ada file BIN")
    private var binInfo by mutableStateOf("pilih file backup ECU untuk pemeriksaan offline")
    private var binBytes: ByteArray? = null
    private var serialPort: UsbSerialPort? = null

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device = intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE,
                android.hardware.usb.UsbDevice::class.java)
            if (intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                openUsbDevice(device)
            } else {
                usbStatus = "izin USB ditolak"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION), RECEIVER_NOT_EXPORTED)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen(
                        usbStatus = usbStatus,
                        usbDetails = usbDetails,
                        binName = binName,
                        binInfo = binInfo,
                        onScanUsb = { scanUsb() },
                        onDisconnect = { closeUsb() },
                        onReadBin = { uri -> loadBin(uri) },
                        onExportBin = { uri -> exportBin(uri) }
                    )
                }
            }
        }
    }

    private fun scanUsb() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            usbStatus = "adapter serial tidak ditemukan"
            usbDetails = "periksa OTG, kabel, chipset FTDI, dan izin USB Android"
            return
        }
        val driver = drivers.first()
        val device = driver.device
        if (!manager.hasPermission(device)) {
            val intent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            manager.requestPermission(device, intent)
            usbStatus = "menunggu izin USB"
            usbDetails = "perangkat ${device.deviceName}, VID=${device.vendorId}, PID=${device.productId}"
        } else {
            openUsbDevice(device)
        }
    }

    private fun openUsbDevice(device: android.hardware.usb.UsbDevice) {
        try {
            val manager = getSystemService(USB_SERVICE) as UsbManager
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
                .firstOrNull { it.device.deviceId == device.deviceId }
                ?: error("driver serial tidak tersedia")
            val connection = manager.openDevice(device) ?: error("gagal membuka USB device")
            val port = driver.ports.first()
            port.open(connection)
            // 10400 8N1 is a configurable transport starting point only.
            // It does NOT establish Honda ECU protocol compatibility by itself.
            port.setParameters(10400, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            serialPort = port
            usbStatus = "USB serial terbuka"
            usbDetails = "VID=${device.vendorId}, PID=${device.productId}, 10400 baud 8N1. belum mengirim perintah ECU."
        } catch (e: Exception) {
            usbStatus = "gagal membuka adapter"
            usbDetails = e.message ?: "kesalahan USB"
        }
    }

    private fun closeUsb() {
        try { serialPort?.close() } catch (_: Exception) {}
        serialPort = null
        usbStatus = "terputus"
        usbDetails = "port serial ditutup"
    }

    private fun loadBin(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("file tidak dapat dibaca")
            binBytes = bytes
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            binName = uri.lastPathSegment ?: "backup.bin"
            binInfo = "ukuran: ${bytes.size} byte | SHA-256: $digest\npreview: " +
                bytes.take(32).joinToString(" ") { "%02X".format(it) }
        } catch (e: Exception) {
            binInfo = "gagal membaca BIN: ${e.message}"
        }
    }

    private fun exportBin(uri: Uri) {
        try {
            val bytes = binBytes ?: error("belum ada file BIN")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("lokasi penyimpanan tidak tersedia")
            binInfo = "salinan BIN berhasil disimpan (${bytes.size} byte). file sumber tidak ditimpa."
        } catch (e: Exception) {
            binInfo = "gagal ekspor: ${e.message}"
        }
    }

    override fun onDestroy() {
        closeUsb()
        try { unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    usbStatus: String, usbDetails: String, binName: String, binInfo: String,
    onScanUsb: () -> Unit, onDisconnect: () -> Unit,
    onReadBin: (Uri) -> Unit, onExportBin: (Uri) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    val openBin = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) onReadBin(it)
    }
    val saveBin = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        if (it != null) onExportBin(it)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("SYAMSPEED", style = MaterialTheme.typography.headlineMedium)
        Text("ECU SCANNER & REMAP", style = MaterialTheme.typography.titleMedium)
        Text("HONDA ADV150 · 2019", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(16.dp))
        TabRow(selectedTabIndex = tab) {
            listOf("Koneksi", "Scanner", "Remap BIN").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> {
                Text("KONEKSI USB", style = MaterialTheme.typography.titleLarge)
                Text("Status: $usbStatus")
                Text(usbDetails)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onScanUsb, modifier = Modifier.fillMaxWidth()) { Text("Cari adapter USB") }
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Putuskan koneksi") }
                InfoCard("PENTING", "FTDI USB-TTL biasa bukan transceiver K-Line. Gunakan kabel USB KKL yang benar-benar memiliki interface K-Line/transceiver. Jangan hubungkan pin TTL langsung ke jalur K-Line ECU.")
                InfoCard("STATUS PROTOKOL", "Belum ada perintah ECU Honda ADV150 yang dikirim. Baud rate 10400 hanya parameter awal transport; inisialisasi, timing, pinout DLC, dan protokol harus diverifikasi.")
            }
            1 -> {
                Text("SCANNER ECU", style = MaterialTheme.typography.titleLarge)
                InfoCard("Kesiapan scanner", "Modul ini sudah mendeteksi adapter USB serial. Pembacaan RPM, TPS, suhu mesin, MAP, DTC dan data live memerlukan protokol serta definisi parameter ECU ADV150 yang tervalidasi.")
                InfoCard("Parameter target", "RPM · TPS · ECT · IAT · MAP · injector duration · ignition timing · tegangan sistem")
                Text("Belum ada data sensor: tidak ada permintaan ECU spekulatif yang dikirim.")
            }
            else -> {
                Text("REMAPPING BIN OFFLINE", style = MaterialTheme.typography.titleLarge)
                Text("File: $binName")
                Text(binInfo)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { openBin.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pilih file BIN")
                }
                OutlinedButton(onClick = { saveBin.launch("ADV150_backup_copy.bin") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ekspor salinan BIN")
                }
                InfoCard("Batasan versi awal", "Saat ini aplikasi hanya membaca metadata, SHA-256 dan preview byte BIN serta mengekspor salinan. Belum mengubah fuel map/ignition map atau menulis ke ECU. Decoder map, checksum dan prosedur write harus didasarkan pada file serta protokol ECU yang teridentifikasi.")
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("SYAMSPEED MOTOR · build awal 0.1.0", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
