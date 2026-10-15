# Exam Browser (Android, WebView Kiosk)

Aplikasi ujian ala Xambro / X-Assessment Browser. Peserta mengisi nama, NIS, dan kode ujian;
aplikasi mengambil URL ujian dari server Google Apps Script lalu membukanya di WebView terkunci.

## Fitur
- URL ujian, kode mulai, kode keluar, kode pengawas dikelola admin di Google Sheets (tanpa build ulang).
- Kode keluar diverifikasi di server, tidak tersimpan di APK.
- WebView hanya boleh membuka `https` pada host ujian (+ `allowed_hosts`); halaman lain diblokir.
- Lock task / screen pinning, tombol Back nonaktif, layar tetap menyala, `FLAG_SECURE` (anti screenshot).
- Deteksi keluar aplikasi (hilang fokus: notifikasi, recent apps, pindah app). Dicatat di sheet `Log`;
  setelah `max_violations` kali layar terkunci sampai pengawas memasukkan kode.
- Banyak perangkat sekaligus: tiap HP dicatat di sheet `Devices` (ANDROID_ID), config di-cache 20 detik di server.
- User-Agent ditambah ` ExamBrowser/1.0` sehingga web ujian dapat menolak browser biasa.

## 1. Siapkan server
1. Buat Google Spreadsheet baru -> **Extensions > Apps Script**, tempel isi `server/Code.gs`.
2. Jalankan fungsi `setup()` satu kali (setujui izin). Terbentuk sheet `Config`, `Devices`, `Log`.
3. Isi sheet **Config**:

| key | keterangan |
|---|---|
| `exam_active` | `TRUE` = ujian dibuka, `FALSE` = ditutup |
| `exam_title` | judul di bar atas |
| `exam_url` | alamat ujian (wajib `https`) |
| `allowed_hosts` | host tambahan dipisah koma (mis. `docs.google.com,script.google.com`) bila web ujian memanggil domain lain |
| `start_code` | kode yang diisi peserta untuk mulai (kosongkan jika tidak perlu) |
| `exit_code` | kode untuk tombol Keluar |
| `unlock_code` | kode pengawas membuka layar terkunci (default = `exit_code`) |
| `max_violations` | batas pelanggaran sebelum terkunci (`0` = tidak pernah terkunci) |

4. **Deploy > New deployment > Web app** -> Execute as: *Me*, Who has access: *Anyone*. Salin URL berakhiran `/exec`.
   Setiap mengubah `Code.gs`, buat versi deployment baru (URL tetap bila memilih "Manage deployments > Edit").

## 2. Build APK di GitHub
1. Buat repo GitHub, upload seluruh isi folder ini.
2. **Settings > Secrets and variables > Actions > New repository secret**: nama `GAS_URL`, isi URL `/exec` tadi.
3. Push ke `main` (atau **Actions > Build APK > Run workflow**). Unduh APK dari **Artifacts** -> `exam-browser-apk`.
4. Bagikan APK ke peserta. Aktifkan "Install dari sumber tidak dikenal".

> Release APK ditandatangani dengan debug key supaya langsung bisa diinstal. Untuk distribusi resmi,
> ganti `signingConfig` di `app/build.gradle.kts` dengan keystore sendiri (simpan di GitHub Secrets).

## 3. Mode kunci (penting)
**A. Tanpa Device Owner (mudah):** saat ujian dimulai Android menampilkan prompt *"Sematkan aplikasi / Screen pinning"*;
peserta harus menyetujui. Kunci ini masih bisa dilepas peserta yang tahu caranya (tahan Back + Recent), tetapi
pelanggaran tercatat dan layar terkunci oleh app.

**B. Device Owner (kiosk penuh, disarankan untuk HP sekolah):** HP harus baru reset / belum ada akun Google.
```
adb shell dpm set-device-owner com.example.exambrowser/.AdminReceiver
```
Lock task berjalan tanpa prompt dan peserta tidak bisa keluar. Untuk melepas (uji coba):
```
adb shell dpm remove-active-admin com.example.exambrowser/.AdminReceiver
```

## Catatan keamanan
- Aplikasi ini mengurangi kecurangan, bukan menjaminnya 100%. Pengawasan fisik tetap perlu.
- Endpoint Apps Script bersifat publik; gunakan `start_code` agar URL ujian tidak terbuka bagi sembarang orang.
- Kuota Apps Script (ribuan eksekusi/hari akun gratis) cukup untuk puluhan-ratusan peserta; untuk jumlah besar,
  kurangi pencatatan log.
- Belum ada pembatasan percobaan kode berulang; tambahkan bila perlu.
