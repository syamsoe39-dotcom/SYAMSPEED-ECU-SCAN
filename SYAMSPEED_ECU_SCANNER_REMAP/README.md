# SYAMSPEED ECU SCANNER & REMAP

Target awal: Honda ADV150 2019, Android USB host, USB FTDI/USB KKL.

## Isi versi awal
- Android app berbasis Kotlin + Jetpack Compose.
- Deteksi adapter USB serial melalui `usb-serial-for-android`.
- Membuka port serial pada 10400 8N1 sebagai konfigurasi transport awal.
- Membaca file BIN secara offline, menghitung SHA-256, menampilkan preview byte.
- Mengekspor salinan BIN tanpa menimpa file sumber.
- GitHub Actions untuk build APK debug.

## Batasan teknis yang disengaja
Versi ini BELUM mengirim perintah ECU, membaca sensor/DTC, mengubah fuel/ignition map,
menghitung checksum ECU, atau menulis file ke ECU. Protokol, pinout DLC, inisialisasi,
alamat memori, struktur map, checksum dan prosedur write ADV150 harus diverifikasi
dengan dokumentasi/hasil pengukuran dan backup ECU yang sah. Jangan mencoba write
firmware yang belum tervalidasi karena dapat membuat ECU tidak dapat digunakan.

FTDI USB-TTL biasa bukan interface K-Line. Untuk ECU K-Line, kabel harus memiliki
transceiver K-Line otomotif yang sesuai (misalnya interface USB KKL yang memang
memiliki rangkaian K-Line). Jangan sambungkan pin TTL langsung ke K-Line atau 12 V.

## Build lokal
1. Buka folder ini dengan Android Studio terbaru yang mendukung AGP 8.7.3.
2. Gunakan JDK 17 dan Android SDK 35.
3. Sync Gradle, lalu jalankan `:app:assembleDebug`.
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Build di GitHub
1. Buat repository baru bernama `SYAMSPEED-ECU-SCANNER-REMAP`.
2. Upload seluruh isi folder proyek ke branch `main`.
3. Buka tab Actions dan aktifkan workflow bila diminta.
4. Jalankan workflow `Build SYAMSPEED ECU APK` melalui `Run workflow`, atau push commit.
5. Unduh artifact `SYAMSPEED-ECU-SCANNER-REMAP-debug`.

## Tahapan pengembangan berikutnya
1. Identifikasi chipset dan rangkaian adapter USB yang sebenarnya.
2. Verifikasi pinout DLC ADV150 dan jalur K-Line/CAN dengan wiring diagram.
3. Identifikasi protokol serta sesi diagnostik ECU.
4. Implementasikan pembacaan identitas, DTC dan parameter live yang sudah terverifikasi.
5. Validasi format backup BIN, ukuran, ID ECU, struktur map dan checksum.
6. Tambahkan editor map dan write hanya setelah prosedur pemulihan/backup teruji.
