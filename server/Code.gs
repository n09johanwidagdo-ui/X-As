/**
 * Server Exam Browser — Google Apps Script (Web App) + Google Sheets.
 *
 * Cara pakai:
 * 1. Buat Google Spreadsheet baru -> Extensions > Apps Script -> tempel file ini.
 * 2. Jalankan fungsi setup() sekali (beri izin). Sheet Config, Devices, Log akan dibuat.
 * 3. Isi sheet "Config" (lihat README).
 * 4. Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone.
 * 5. Salin URL /exec ke secret GAS_URL di GitHub.
 */

function doGet(e) {
  var p = (e && e.parameter) || {};
  var out;
  try {
    switch (p.action) {
      case 'start':      out = start_(p); break;
      case 'verifyExit': out = verifyExit_(p); break;
      case 'log':        log_(p.device, p.nis, p.nama, p.event, p.detail); out = { ok: true }; break;
      default:           out = { ok: false, error: 'Aksi tidak dikenal.' };
    }
  } catch (err) {
    out = { ok: false, error: 'Server error: ' + err.message };
  }
  return ContentService.createTextOutput(JSON.stringify(out))
    .setMimeType(ContentService.MimeType.JSON);
}

// ---------- Aksi ----------

function start_(p) {
  var cfg = getConfig_();
  if (!isTrue_(cfg.exam_active)) return { ok: false, error: 'Ujian belum dibuka oleh admin.' };
  if (!cfg.exam_url) return { ok: false, error: 'URL ujian belum diatur admin.' };

  if (cfg.start_code && norm_(p.code) !== norm_(cfg.start_code)) {
    log_(p.device, p.nis, p.nama, 'START_FAIL', 'kode ujian salah');
    return { ok: false, error: 'Kode ujian salah.' };
  }

  registerDevice_(p);
  log_(p.device, p.nis, p.nama, 'START', p.model || '');

  return {
    ok: true,
    title: cfg.exam_title || 'Ujian',
    url: cfg.exam_url,
    hosts: cfg.allowed_hosts || '',
    maxViolations: parseInt(cfg.max_violations || '3', 10)
  };
}

function verifyExit_(p) {
  var cfg = getConfig_();
  var purpose = p.purpose === 'unlock' ? 'unlock' : 'exit';
  var expected = purpose === 'unlock' ? (cfg.unlock_code || cfg.exit_code) : cfg.exit_code;
  if (!expected) return { ok: false, error: 'Kode belum diatur admin.' };

  var ok = norm_(p.code) === norm_(expected);
  log_(p.device, p.nis, p.nama, (purpose === 'unlock' ? 'UNLOCK_' : 'EXIT_') + (ok ? 'OK' : 'FAIL'), '');
  return ok ? { ok: true } : { ok: false, error: 'Kode salah.' };
}

// ---------- Helper ----------

function getConfig_() {
  var cache = CacheService.getScriptCache();
  var hit = cache.get('cfg');
  if (hit) return JSON.parse(hit);

  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Config');
  var rows = sh.getDataRange().getValues();
  var cfg = {};
  for (var i = 1; i < rows.length; i++) {
    if (rows[i][0] !== '') cfg[String(rows[i][0]).trim()] = String(rows[i][1]).trim();
  }
  cache.put('cfg', JSON.stringify(cfg), 20); // perubahan Config berlaku maks ~20 detik
  return cfg;
}

function registerDevice_(p) {
  withLock_(function () {
    var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Devices');
    var row = [new Date(), p.device || '', p.nis || '', p.nama || '', p.model || '', p.app || '', 'AKTIF'];
    var found = sh.getRange(2, 2, Math.max(sh.getLastRow() - 1, 1), 1)
      .createTextFinder(p.device || '#').matchEntireCell(true).findNext();
    if (found) sh.getRange(found.getRow(), 1, 1, row.length).setValues([row]);
    else sh.appendRow(row);
  });
}

function log_(device, nis, nama, event, detail) {
  withLock_(function () {
    SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Log')
      .appendRow([new Date(), device || '', nis || '', nama || '', event || '', detail || '']);
  });
}

function withLock_(fn) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try { fn(); } finally { lock.releaseLock(); }
}

function norm_(s) { return String(s || '').trim().toLowerCase(); }
function isTrue_(s) { return String(s || '').trim().toLowerCase() === 'true'; }

/** Jalankan sekali untuk menyiapkan sheet. */
function setup() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  function ensure(name, header, rows) {
    var sh = ss.getSheetByName(name) || ss.insertSheet(name);
    if (sh.getLastRow() === 0) {
      sh.appendRow(header);
      (rows || []).forEach(function (r) { sh.appendRow(r); });
    }
  }
  ensure('Config', ['key', 'value'], [
    ['exam_active', 'FALSE'],
    ['exam_title', 'Ujian Sekolah'],
    ['exam_url', 'https://contoh.netlify.app/'],
    ['allowed_hosts', ''],
    ['start_code', 'MULAI123'],
    ['exit_code', 'KELUAR123'],
    ['unlock_code', 'PENGAWAS123'],
    ['max_violations', '3']
  ]);
  ensure('Devices', ['waktu', 'device_id', 'nis', 'nama', 'model', 'versi_app', 'status']);
  ensure('Log', ['waktu', 'device_id', 'nis', 'nama', 'event', 'detail']);
}
