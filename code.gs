/**
 * تسک پلاس — بک‌اند Google Apps Script
 * ------------------------------------------------
 * نسخه API برای اتصال از Vercel/PWA
 * + یکپارچه‌سازی با ربات بله (ثبت پیام به‌عنوان تسک)
 * + upsertTask (ذخیرهٔ مجدد تسک ذخیره‌نشده)
 * + getTasksPage سبک‌تر
 */

// اگر اسکریپت به‌صورت Container-bound به شیت متصل است، این خط را خالی بگذارید.
// اگر اسکریپت مستقل (Standalone) است، شناسه شیت را اینجا وارد کنید:
var SPREADSHEET_ID = ''; // مثال: '1AbCDeFGhijkLMNoPQRstuVWxyZ...'

var TASKS_SHEET_NAME = 'Tasks';
var GROUPS_SHEET_NAME = 'Groups';
var USERS_SHEET_NAME = 'Users';
var SESSIONS_SHEET_NAME = 'Sessions';
var CALENDAR_EVENTS_SHEET_NAME = 'CalendarEvents';

var TASKS_HEADERS = ['id', 'userId', 'title', 'status', 'priority', 'date', 'created', 'group', 'tags', 'notes', 'mainTask', 'subtasks', 'doingAt', 'doneAt'];
var GROUPS_HEADERS = ['userId', 'key', 'name', 'color'];
// ستون baleChatId اضافه شد: شناسه چت بله کاربر را خودتان دستی در این ستون از جدول Users وارد می‌کنید
var USERS_HEADERS = ['username', 'passwordHash', 'salt', 'created', 'baleChatId'];
var SESSIONS_HEADERS = ['token', 'userId', 'created', 'expires'];
var CALENDAR_EVENTS_HEADERS = ['userId', 'taskId', 'eventId'];

var DEFAULT_GROUP = { key: 'none', name: 'بدون گروه', color: '#5B6B7A' };

// ✅ Sync کلندر فقط برای این username انجام می‌شود:
var CALENDAR_ONLY_USERNAME = 'farkhondei70@gmail.com';

var SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000; // ۳۰ روز
var LOCK_TIMEOUT_MS = 30000;
var LEGACY_OWNER_USERNAME = '';

// ==================== API Endpoint ====================
function doGet(e) {
  // اگر درخواست برای صفحه HTML است
  if (!e || !e.parameter || !e.parameter.action) {
    return HtmlService.createHtmlOutputFromFile('TaskPluss')
      .setTitle('تسک پلاس | مدیریت تسک')
      .addMetaTag('viewport', 'width=device-width, initial-scale=1.0')
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
  }

  try {
    const action = e.parameter.action;
    const data = e.parameter.data ? JSON.parse(e.parameter.data) : null;
    const token = e.parameter.token || null;

    let result;

    switch (action) {
      case 'login':
        result = loginUser(data.username, data.password);
        break;

      case 'register':
        result = registerUser(data.username, data.password);
        break;

      case 'logout':
        result = logoutUser(token);
        break;

      case 'validateSession':
        result = validateSession(token);
        break;

      case 'getGroups':
        requireSession_(token);
        result = getGroupsForUser(token);
        break;

      case 'getTasks':
        requireSession_(token);
        const page = data.page || 0;
        const limit = data.limit || 40;
        result = getTasksPage(token, page * limit, limit);
        break;

      case 'addTask':
        requireSession_(token);
        result = addTask(token, JSON.stringify(data));
        break;

      case 'addTasksBulk':
        requireSession_(token);
        result = addTasksBulk(token, JSON.stringify(data.tasks || []));
        break;

      case 'updateTask':
        requireSession_(token);
        result = updateTask(token, JSON.stringify(data));
        break;

      case 'upsertTask':
        requireSession_(token);
        result = upsertTask(token, JSON.stringify(data));
        break;

      case 'deleteTask':
        requireSession_(token);
        result = deleteTask(token, data.id);
        break;

      case 'saveGroups':
        requireSession_(token);
        result = saveAllGroupsForUser(token, JSON.stringify(data.groups));
        break;

      case 'test':
        result = testConnection();
        break;

      default:
        throw new Error('Action not found: ' + action);
    }

    // اطمینان از اینکه result یک رشته JSON است
    let jsonResult;
    if (typeof result === 'string') {
      jsonResult = result;
    } else {
      jsonResult = JSON.stringify(result);
    }

    return ContentService
      .createTextOutput(jsonResult)
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService
      .createTextOutput(JSON.stringify({
        success: false,
        message: error.message || 'خطای داخلی سرور'
      }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// ==================== تابع تست اتصال ====================
function testConnection() {
  return JSON.stringify({
    success: true,
    message: 'اتصال برقرار است!',
    timestamp: new Date().toISOString(),
    version: '1.1.0'
  });
}

// ==================== دسترسی به شیت ====================

function getSpreadsheet_() {
  return SPREADSHEET_ID
    ? SpreadsheetApp.openById(SPREADSHEET_ID)
    : SpreadsheetApp.getActiveSpreadsheet();
}

function getOrCreateSheet_(name, headers) {
  var ss = getSpreadsheet_();
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
  }
  if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
    sheet.setFrozenRows(1);
    var dateColumns = [];
    var dateIdx = headers.indexOf('date');
    var createdIdx = headers.indexOf('created');
    var doingAtIdx = headers.indexOf('doingAt');
    var doneAtIdx = headers.indexOf('doneAt');
    if (dateIdx !== -1) dateColumns.push(dateIdx + 1);
    if (createdIdx !== -1) dateColumns.push(createdIdx + 1);
    if (doingAtIdx !== -1) dateColumns.push(doingAtIdx + 1);
    if (doneAtIdx !== -1) dateColumns.push(doneAtIdx + 1);
    dateColumns.forEach(function (col) {
      sheet.getRange(1, col, sheet.getMaxRows(), 1).setNumberFormat('@');
    });
  } else if (sheet.getLastColumn() < headers.length) {
    // شیت از قبل وجود داشته (مثلاً قبل از اضافه‌شدن doingAt/doneAt)؛ فقط ستون‌های
    // سرستون جدید را در انتهای ردیف اول اضافه می‌کنیم، داده‌های موجود دست نمی‌خورد.
    var existingCount = sheet.getLastColumn();
    var missingHeaders = headers.slice(existingCount);
    sheet.getRange(1, existingCount + 1, 1, missingHeaders.length).setValues([missingHeaders]);
    missingHeaders.forEach(function (h, i) {
      if (h === 'date' || h === 'created' || h === 'doingAt' || h === 'doneAt') {
        sheet.getRange(1, existingCount + 1 + i, sheet.getMaxRows(), 1).setNumberFormat('@');
      }
    });
  }
  return sheet;
}

function setupSheets() {
  getOrCreateSheet_(USERS_SHEET_NAME, USERS_HEADERS);
  getOrCreateSheet_(SESSIONS_SHEET_NAME, SESSIONS_HEADERS);
  getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
  getOrCreateSheet_(GROUPS_SHEET_NAME, GROUPS_HEADERS);
  getOrCreateSheet_(CALENDAR_EVENTS_SHEET_NAME, CALENDAR_EVENTS_HEADERS);
  SpreadsheetApp.getUi().alert('شیت‌های Users، Sessions، Tasks و Groups با موفقیت آماده شدند.');
}

function migrateLegacyData() {
  var owner = LEGACY_OWNER_USERNAME || 'legacy';
  var ss = getSpreadsheet_();
  [TASKS_SHEET_NAME, GROUPS_SHEET_NAME].forEach(function (name) {
    var sheet = ss.getSheetByName(name);
    if (!sheet) return;
    var lastCol = sheet.getLastColumn();
    if (lastCol === 0) return;
    var headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
    if (headerRow.indexOf('userId') !== -1) return;
    sheet.insertColumnAfter(1);
    sheet.getRange(1, 2).setValue('userId');
    var lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      var values = [];
      for (var i = 0; i < lastRow - 1; i++) values.push([owner]);
      sheet.getRange(2, 2, lastRow - 1, 1).setValues(values);
    }
  });
  SpreadsheetApp.getUi().alert('مهاجرت انجام شد. مالک داده‌های قدیمی: ' + owner);
}

// ==================== مهاجرت: افزودن ستون شناسه چت بله به جدول کاربران ====================
// این تابع را فقط یک‌بار از منوی Apps Script اجرا کنید (اگر جدول Users از قبل ساخته شده)
function migrateAddBaleChatIdColumn() {
  var sheet = getOrCreateSheet_(USERS_SHEET_NAME, USERS_HEADERS);
  var lastCol = sheet.getLastColumn();
  var header = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  if (header.indexOf('baleChatId') === -1) {
    sheet.getRange(1, lastCol + 1).setValue('baleChatId');
    SpreadsheetApp.getUi().alert('ستون baleChatId به جدول کاربران اضافه شد. حالا برای هر کاربر، شناسه چت بله‌اش را در همین ستون وارد کنید.');
  } else {
    SpreadsheetApp.getUi().alert('ستون baleChatId از قبل در جدول کاربران وجود دارد.');
  }
}

// ==================== ابزار قفل ====================

function withLock_(fn) {
  var lock = LockService.getScriptLock();
  lock.waitLock(LOCK_TIMEOUT_MS);
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

function findRowIndex_(sheet, matchColIndexes, matchValues) {
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return -1;
  var numRows = lastRow - 1;
  var numCols = Math.max.apply(null, matchColIndexes);
  var data = sheet.getRange(2, 1, numRows, numCols).getValues();
  for (var i = 0; i < data.length; i++) {
    var match = true;
    for (var j = 0; j < matchColIndexes.length; j++) {
      if (String(data[i][matchColIndexes[j] - 1]) !== String(matchValues[j])) {
        match = false;
        break;
      }
    }
    if (match) return i + 2;
  }
  return -1;
}

// ==================== تبدیل شیت <-> شیء ====================

function sheetToObjects_(sheet, headers) {
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return [];

  var range = sheet.getRange(2, 1, lastRow - 1, headers.length);
  var values = range.getValues();
  var displayValues = range.getDisplayValues();

  return values
    .filter(function (row) {
      return row[0] !== '' && row[0] !== null;
    })
    .map(function (row, index) {
      var obj = {};
      headers.forEach(function (h, i) {
        if (h === 'date' || h === 'created' || h === 'doingAt' || h === 'doneAt') {
          obj[h] = String(displayValues[index][i] || '');
        } else {
          obj[h] = row[i];
        }
      });
      return obj;
    });
}

function taskToRow_(task, userId) {
  return TASKS_HEADERS.map(function (h) {
    switch (h) {
      case 'id': return task.id;
      case 'userId': return userId;
      case 'title': return task.title || '';
      case 'status': return task.status || 'todo';
      case 'priority': return Number(task.priority || 0);
      case 'date': return String(task.date || '');
      case 'created': return String(task.created || '');
      case 'group': return task.group || 'none';
      case 'tags': return Array.isArray(task.tags) ? task.tags.join('|') : (task.tags || '');
      case 'notes': return task.notes || '';
      case 'mainTask': return (task.mainTask === null || task.mainTask === undefined || task.mainTask === '') ? '' : task.mainTask;
      case 'subtasks': return Array.isArray(task.subtasks) ? task.subtasks.join('|') : (task.subtasks || '');
      case 'doingAt': return String(task.doingAt || '');
      case 'doneAt': return String(task.doneAt || '');
      default: return '';
    }
  });
}

function rowObjToTask_(t) {
  return {
    id: String(t.id || ''),
    title: String(t.title || ''),
    status: String(t.status || 'todo'),
    priority: Number(t.priority || 0),
    date: String(t.date || ''),
    created: String(t.created || ''),
    group: String(t.group || 'none'),
    tags: t.tags ? String(t.tags).split('|').filter(Boolean) : [],
    notes: String(t.notes || ''),
    mainTask: t.mainTask === '' || t.mainTask === null || t.mainTask === undefined ? null : String(t.mainTask),
    subtasks: t.subtasks ? String(t.subtasks).split('|').filter(Boolean) : [],
    doingAt: String(t.doingAt || ''),
    doneAt: String(t.doneAt || '')
  };
}

// ==================== توابع کاربر ====================

function hashPassword_(password, salt) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, password + '::' + salt);
  return bytes.map(function (b) {
    var v = (b < 0 ? b + 256 : b).toString(16);
    return v.length === 1 ? '0' + v : v;
  }).join('');
}

function findUserRow_(sheet, username) {
  return findRowIndex_(sheet, [1], [username]);
}

function registerUser(username, password) {
  username = String(username || '').trim();
  password = String(password || '');

  if (!username || !password) {
    return JSON.stringify({ success: false, message: 'نام کاربری و رمز عبور الزامی است.' });
  }
  if (username.length < 3) {
    return JSON.stringify({ success: false, message: 'نام کاربری باید حداقل ۳ کاراکتر باشد.' });
  }
  if (password.length < 4) {
    return JSON.stringify({ success: false, message: 'رمز عبور باید حداقل ۴ کاراکتر باشد.' });
  }

  return withLock_(function () {
    var sheet = getOrCreateSheet_(USERS_SHEET_NAME, USERS_HEADERS);
    if (findUserRow_(sheet, username) !== -1) {
      return JSON.stringify({ success: false, message: 'این نام کاربری قبلاً ثبت شده است.' });
    }
    var salt = Utilities.getUuid();
    var hash = hashPassword_(password, salt);
    sheet.appendRow([username, hash, salt, new Date().toISOString()]);
    SpreadsheetApp.flush();

    var token = createSession_(username);
    return JSON.stringify({ success: true, token: token, username: username });
  });
}

function loginUser(username, password) {
  username = String(username || '').trim();
  password = String(password || '');

  var sheet = getOrCreateSheet_(USERS_SHEET_NAME, USERS_HEADERS);
  var row = findUserRow_(sheet, username);
  if (row === -1) {
    return JSON.stringify({ success: false, message: 'نام کاربری یا رمز عبور اشتباه است.' });
  }
  var data = sheet.getRange(row, 1, 1, USERS_HEADERS.length).getValues()[0];
  var storedHash = data[1];
  var salt = data[2];
  var hash = hashPassword_(password, salt);
  if (hash !== storedHash) {
    return JSON.stringify({ success: false, message: 'نام کاربری یا رمز عبور اشتباه است.' });
  }

  var token = createSession_(username);
  return JSON.stringify({ success: true, token: token, username: username });
}

function logoutUser(token) {
  return withLock_(function () {
    var sheet = getOrCreateSheet_(SESSIONS_SHEET_NAME, SESSIONS_HEADERS);
    var row = findRowIndex_(sheet, [1], [token]);
    if (row !== -1) sheet.deleteRow(row);
    return JSON.stringify({ success: true });
  });
}

function createSession_(userId) {
  var sheet = getOrCreateSheet_(SESSIONS_SHEET_NAME, SESSIONS_HEADERS);
  var token = Utilities.getUuid();
  var now = new Date();
  var expires = new Date(now.getTime() + SESSION_TTL_MS);
  sheet.appendRow([token, userId, now.toISOString(), expires.toISOString()]);
  return token;
}

function requireSession_(token) {
  if (!token) throw new Error('نشست معتبر نیست. لطفاً دوباره وارد شوید.');
  var sheet = getOrCreateSheet_(SESSIONS_SHEET_NAME, SESSIONS_HEADERS);
  var row = findRowIndex_(sheet, [1], [token]);
  if (row === -1) throw new Error('نشست معتبر نیست. لطفاً دوباره وارد شوید.');

  var data = sheet.getRange(row, 1, 1, SESSIONS_HEADERS.length).getValues()[0];
  var expires = new Date(data[3]);
  if (isNaN(expires.getTime()) || expires.getTime() < Date.now()) {
    sheet.deleteRow(row);
    throw new Error('نشست منقضی شده. لطفاً دوباره وارد شوید.');
  }
  return String(data[1]);
}

function validateSession(token) {
  try {
    var userId = requireSession_(token);
    return JSON.stringify({ success: true, username: userId });
  } catch (e) {
    return JSON.stringify({ success: false, message: e.message });
  }
}

// ==================== توابع تسک‌ها ====================

function getAllData(token) {
  var userId = requireSession_(token);

  var tasksSheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
  var groupsSheet = getOrCreateSheet_(GROUPS_SHEET_NAME, GROUPS_HEADERS);

  var taskRows = sheetToObjects_(tasksSheet, TASKS_HEADERS)
    .filter(function (t) { return String(t.userId) === userId; })
    .map(rowObjToTask_)
    .sort(function(a, b) {
      var dateA = a.created || '';
      var dateB = b.created || '';
      if (dateA !== dateB) {
        return String(dateB).localeCompare(String(dateA), 'fa');
      }
      return String(a.id).localeCompare(String(b.id));
    });

  var groupRows = sheetToObjects_(groupsSheet, GROUPS_HEADERS)
    .filter(function (g) { return String(g.userId) === userId; });

  var groups = {};
  groupRows.forEach(function (g) {
    groups[g.key] = { name: g.name, color: g.color };
  });
  if (!groups[DEFAULT_GROUP.key]) {
    groups[DEFAULT_GROUP.key] = { name: DEFAULT_GROUP.name, color: DEFAULT_GROUP.color };
  }

  return JSON.stringify({ tasks: taskRows, groups: groups, username: userId });
}

function getGroupsForUser(token) {
  var userId = requireSession_(token);
  var groupsSheet = getOrCreateSheet_(GROUPS_SHEET_NAME, GROUPS_HEADERS);

  var groupRows = sheetToObjects_(groupsSheet, GROUPS_HEADERS)
    .filter(function (g) { return String(g.userId) === userId; });

  var groups = {};
  groupRows.forEach(function (g) {
    groups[g.key] = { name: g.name, color: g.color };
  });
  if (!groups[DEFAULT_GROUP.key]) {
    groups[DEFAULT_GROUP.key] = { name: DEFAULT_GROUP.name, color: DEFAULT_GROUP.color };
  }

  return JSON.stringify({ success: true, groups: groups, username: userId });
}

/**
 * صفحه‌بندی سبک‌تر:
 * فقط ستون‌های id/userId/created برای فیلتر و مرتب‌سازی خوانده می‌شوند،
 * سپس دادهٔ کامل فقط برای ردیف‌های صفحه جاری واکشی می‌شود.
 */
function getTasksPage(token, offset, limit) {
  var userId = requireSession_(token);
  offset = Math.max(0, Number(offset) || 0);
  limit = Math.max(1, Math.min(100, Number(limit) || 40));

  var sheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) {
    return JSON.stringify({
      success: true,
      tasks: [],
      total: 0,
      hasMore: false,
      nextOffset: offset
    });
  }

  var numRows = lastRow - 1;
  var idCol = TASKS_HEADERS.indexOf('id') + 1;
  var userCol = TASKS_HEADERS.indexOf('userId') + 1;
  var createdCol = TASKS_HEADERS.indexOf('created') + 1;

  var ids = sheet.getRange(2, idCol, numRows, 1).getDisplayValues();
  var users = sheet.getRange(2, userCol, numRows, 1).getDisplayValues();
  var createds = sheet.getRange(2, createdCol, numRows, 1).getDisplayValues();

  var matched = [];
  for (var i = 0; i < numRows; i++) {
    if (String(users[i][0]) !== String(userId)) continue;
    if (ids[i][0] === '' || ids[i][0] === null) continue;
    matched.push({
      sheetRow: i + 2,
      id: String(ids[i][0]),
      created: String(createds[i][0] || '')
    });
  }

  matched.sort(function (a, b) {
    if (a.created !== b.created) {
      return String(b.created).localeCompare(String(a.created), 'fa');
    }
    return String(a.id).localeCompare(String(b.id));
  });

  var total = matched.length;
  var pageMeta = matched.slice(offset, offset + limit);
  var hasMore = offset + pageMeta.length < total;

  if (!pageMeta.length) {
    return JSON.stringify({
      success: true,
      tasks: [],
      total: total,
      hasMore: false,
      nextOffset: offset
    });
  }

  var minRow = pageMeta[0].sheetRow;
  var maxRow = pageMeta[0].sheetRow;
  for (var p = 1; p < pageMeta.length; p++) {
    if (pageMeta[p].sheetRow < minRow) minRow = pageMeta[p].sheetRow;
    if (pageMeta[p].sheetRow > maxRow) maxRow = pageMeta[p].sheetRow;
  }

  var blockRows = maxRow - minRow + 1;
  var range = sheet.getRange(minRow, 1, blockRows, TASKS_HEADERS.length);
  var values = range.getValues();
  var displayValues = range.getDisplayValues();

  var rowBySheetRow = {};
  for (var r = 0; r < values.length; r++) {
    var sheetRow = minRow + r;
    var obj = {};
    for (var h = 0; h < TASKS_HEADERS.length; h++) {
      var key = TASKS_HEADERS[h];
      if (key === 'date' || key === 'created' || key === 'doingAt' || key === 'doneAt') {
        obj[key] = String(displayValues[r][h] || '');
      } else {
        obj[key] = values[r][h];
      }
    }
    rowBySheetRow[sheetRow] = obj;
  }

  var tasks = pageMeta.map(function (m) {
    return rowObjToTask_(rowBySheetRow[m.sheetRow] || {
      id: m.id,
      userId: userId,
      created: m.created
    });
  });

  return JSON.stringify({
    success: true,
    tasks: tasks,
    total: total,
    hasMore: hasMore,
    nextOffset: offset + tasks.length
  });
}

function addTask(token, taskJson) {
  // سازگاری عقب‌رو: از upsert استفاده می‌کند
  return upsertTask(token, taskJson);
}

function updateTask(token, taskJson) {
  // سازگاری عقب‌رو: از upsert استفاده می‌کند
  return upsertTask(token, taskJson);
}

/**
 * درج یا به‌روزرسانی تسک بر اساس (id + userId).
 * اگر وجود نداشت append؛ اگر بود overwrite.
 * مشکل «تسک یافت نشد» هنگام ذخیرهٔ مجدد تسک ذخیره‌نشده را حل می‌کند.
 */
function upsertTask(token, taskJson) {
  var userId = requireSession_(token);
  var task = JSON.parse(taskJson);
  if (task == null || task.id === undefined || task.id === null || task.id === '') {
    return JSON.stringify({ success: false, message: 'شناسه تسک نامعتبر است.' });
  }

  var result = withLock_(function () {
    var sheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
    var row = findRowIndex_(sheet, [1, 2], [task.id, userId]);
    var created = false;
    if (row === -1) {
      sheet.appendRow(taskToRow_(task, userId));
      created = true;
    } else {
      sheet.getRange(row, 1, 1, TASKS_HEADERS.length).setValues([taskToRow_(task, userId)]);
    }
    SpreadsheetApp.flush();
    return { success: true, id: String(task.id), created: created };
  });

  if (result.success) {
    try {
      syncTaskCalendarEvent_(userId, task);
    } catch (e) {
      Logger.log('خطا در همگام‌سازی کلندر (upsertTask): ' + e);
    }
  }

  return JSON.stringify(result);
}

function addTasksBulk(token, tasksJson) {
  var userId = requireSession_(token);
  var incoming = JSON.parse(tasksJson);
  if (!Array.isArray(incoming) || !incoming.length) {
    return JSON.stringify({ success: true, tasks: [] });
  }

  return withLock_(function () {
    var sheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
    var existing = sheetToObjects_(sheet, TASKS_HEADERS).filter(function (t) {
      return String(t.userId) === userId;
    });
    var usedIds = {};
    existing.forEach(function (t) {
      usedIds[String(t.id)] = true;
    });

    var rows = [];
    var finalTasks = [];
    incoming.forEach(function (task) {
      var id = String(task.id);
      if (!id || usedIds[id]) {
        id = Utilities.getUuid();
      }
      usedIds[id] = true;
      var normalized = Object.assign({}, task, { id: id });
      rows.push(taskToRow_(normalized, userId));
      finalTasks.push(rowObjToTask_(Object.assign({}, normalized, { userId: userId })));
    });

    if (rows.length) {
      sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, TASKS_HEADERS.length).setValues(rows);
      SpreadsheetApp.flush();
    }

    return JSON.stringify({ success: true, tasks: finalTasks });
  });
}

function deleteTask(token, taskId) {
  var userId = requireSession_(token);

  var result = withLock_(function () {
    var sheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
    var row = findRowIndex_(sheet, [1, 2], [taskId, userId]);
    if (row === -1) {
      return { success: false, message: 'تسک یافت نشد یا قبلاً حذف شده است.' };
    }
    var check = sheet.getRange(row, 1, 1, 2).getValues()[0];
    if (String(check[0]) !== String(taskId) || String(check[1]) !== userId) {
      return { success: false, message: 'خطای هم‌زمانی؛ لطفاً دوباره تلاش کنید.' };
    }
    sheet.deleteRow(row);
    SpreadsheetApp.flush();
    return { success: true, id: taskId };
  });

  if (result.success) {
    try {
      deleteTaskCalendarEvent_(userId, taskId);
    } catch (e) {
      Logger.log('خطا در حذف رویداد کلندر (deleteTask): ' + e);
    }
  }

  return JSON.stringify(result);
}

function saveAllGroupsForUser(token, groupsJson) {
  var userId = requireSession_(token);
  var groups = JSON.parse(groupsJson);

  return withLock_(function () {
    var sheet = getOrCreateSheet_(GROUPS_SHEET_NAME, GROUPS_HEADERS);
    var lastRow = sheet.getLastRow();

    if (lastRow > 1) {
      var userCol = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (var i = userCol.length - 1; i >= 0; i--) {
        if (String(userCol[i][0]) === userId) {
          sheet.deleteRow(i + 2);
        }
      }
    }

    var rows = Object.keys(groups)
      .filter(function (key) { return key !== DEFAULT_GROUP.key; })
      .map(function (key) {
        return [userId, key, groups[key].name, groups[key].color];
      });

    if (rows.length) {
      sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, GROUPS_HEADERS.length).setValues(rows);
    }
    SpreadsheetApp.flush();
    return JSON.stringify({ success: true });
  });
}

// ==================== توابع کلندر ====================

function findCalendarEventRow_(sheet, userId, taskId) {
  return findRowIndex_(sheet, [1, 2], [userId, taskId]);
}

// ==================== یافتن نام واقعی گروه بر اساس ستون name ====================
function getGroupName_(userId, groupKey) {
  if (!groupKey || groupKey === DEFAULT_GROUP.key) return DEFAULT_GROUP.name;

  var sheet = getOrCreateSheet_(GROUPS_SHEET_NAME, GROUPS_HEADERS);
  var row = findRowIndex_(sheet, [1, 2], [userId, groupKey]);
  if (row === -1) return DEFAULT_GROUP.name;

  var nameCol = GROUPS_HEADERS.indexOf('name') + 1;
  var name = sheet.getRange(row, nameCol).getValue();
  return name ? String(name) : DEFAULT_GROUP.name;
}

function buildEventDescription_(task, groupName) {
  var lines = [];
  if (groupName && groupName !== DEFAULT_GROUP.name) lines.push('گروه: ' + groupName);
  if (task.priority) lines.push('اولویت: ' + task.priority);
  if (task.notes) lines.push('یادداشت: ' + task.notes);
  lines.push('ساخته‌شده توسط تسک پلاس (شناسه تسک: ' + task.id + ')');
  return lines.join('\n');
}

function syncTaskCalendarEvent_(userId, task) {
  // ✅ فقط برای کاربر مشخص رویداد بساز/آپدیت کن
  if (String(userId) !== String(CALENDAR_ONLY_USERNAME)) return;

  // با لاک مشترک اجرا می‌شود تا اگر doPost به هر دلیلی دو بار پشت‌سرهم صدا زده شود،
  // دو اجرا هم‌زمان رویداد جدید نسازند.
  withLock_(function () {
    var mapSheet = getOrCreateSheet_(CALENDAR_EVENTS_SHEET_NAME, CALENDAR_EVENTS_HEADERS);
    var row = findCalendarEventRow_(mapSheet, userId, task.id);
    var existingEventId = row !== -1 ? String(mapSheet.getRange(row, 3).getValue() || '') : '';
    var calendar = CalendarApp.getDefaultCalendar();
    var dueDate = parseTaskDateToJsDate_(task.date);

    if (!dueDate) {
      if (existingEventId) {
        try {
          var evToDelete = calendar.getEventById(existingEventId);
          if (evToDelete) evToDelete.deleteEvent();
        } catch (e) {
          Logger.log('خطا در حذف رویداد کلندر: ' + e);
        }
        mapSheet.deleteRow(row);
      }
      return;
    }

    var endDate = new Date(dueDate.getTime() + 30 * 60 * 1000);
    var title = task.title || 'تسک بدون عنوان';
    var groupName = getGroupName_(userId, task.group);
    var description = buildEventDescription_(task, groupName);

    if (existingEventId) {
      var ev = null;
      try {
        ev = calendar.getEventById(existingEventId);
      } catch (e) {
        ev = null;
      }
      if (ev) {
        ev.setTitle(title);
        ev.setTime(dueDate, endDate);
        ev.setDescription(description);
        return;
      }
    }

    var newEvent = calendar.createEvent(title, dueDate, endDate, { description: description });
    if (row !== -1) {
      mapSheet.getRange(row, 3).setValue(newEvent.getId());
    } else {
      mapSheet.appendRow([userId, task.id, newEvent.getId()]);
    }
  });
}

function deleteTaskCalendarEvent_(userId, taskId) {
  // ✅ فقط برای کاربر مشخص رویداد حذف کن
  if (String(userId) !== String(CALENDAR_ONLY_USERNAME)) return;

  var mapSheet = getOrCreateSheet_(CALENDAR_EVENTS_SHEET_NAME, CALENDAR_EVENTS_HEADERS);
  var row = findCalendarEventRow_(mapSheet, userId, taskId);
  if (row === -1) return;

  var eventId = String(mapSheet.getRange(row, 3).getValue() || '');
  if (eventId) {
    try {
      var calendar = CalendarApp.getDefaultCalendar();
      var ev = calendar.getEventById(eventId);
      if (ev) ev.deleteEvent();
    } catch (e) {
      Logger.log('خطا در حذف رویداد کلندر: ' + e);
    }
  }
  mapSheet.deleteRow(row);
}

// ==================== توابع تاریخ شمسی ====================

function jc_div_(a, b) { return ~~(a / b); }
function jc_mod_(a, b) { return a - ~~(a / b) * b; }

function jc_jalCal_(jy) {
  var breaks = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];
  var bl = breaks.length,
    gy = jy + 621,
    leapJ = -14,
    jp = breaks[0],
    jm, jump, leap, n, i;

  for (i = 1; i < bl; i += 1) {
    jm = breaks[i];
    jump = jm - jp;
    if (jy < jm) break;
    leapJ = leapJ + jc_div_(jump, 33) * 8 + jc_div_(jc_mod_(jump, 33), 4);
    jp = jm;
  }
  n = jy - jp;

  leapJ = leapJ + jc_div_(n, 33) * 8 + jc_div_(jc_mod_(n, 33) + 3, 4);
  if (jc_mod_(jump, 33) === 4 && jump - n === 4) leapJ += 1;

  var leapG = jc_div_(gy, 4) - jc_div_((jc_div_(gy, 100) + 1) * 3, 4) - 150;
  var march = 20 + leapJ - leapG;

  if (jump - n < 6) n = n - jump + jc_div_(jump, 33) * 33;
  leap = jc_mod_(jc_mod_(n + 1, 33) - 1, 4);
  if (leap === -1) leap = 4;

  return { leap: leap, gy: gy, march: march };
}

function jc_g2d_(gy, gm, gd) {
  var d = jc_div_((gy + jc_div_(gm - 8, 6) + 100100) * 1461, 4)
    + jc_div_(153 * jc_mod_(gm + 9, 12) + 2, 5)
    + gd - 34840408;
  d = d - jc_div_(jc_div_(gy + 100100 + jc_div_(gm - 8, 6), 100) * 3, 4) + 752;
  return d;
}

function jc_j2d_(jy, jm, jd) {
  var r = jc_jalCal_(jy);
  return jc_g2d_(r.gy, 3, r.march) + (jm - 1) * 31 - jc_div_(jm, 7) * (jm - 7) + jd - 1;
}

function jc_d2g_(jdn) {
  var j, i, gd, gm, gy;
  j = 4 * jdn + 139361631;
  j = j + jc_div_(jc_div_(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
  i = jc_div_(jc_mod_(j, 1461), 4) * 5 + 308;
  gd = jc_div_(jc_mod_(i, 153), 5) + 1;
  gm = jc_mod_(jc_div_(i, 153), 12) + 1;
  gy = jc_div_(j, 1461) - 100100 + jc_div_(8 - gm, 6);
  return { gy: gy, gm: gm, gd: gd };
}

function jalaliToGregorian_(jy, jm, jd) {
  var jdn = jc_j2d_(jy, jm, jd);
  return jc_d2g_(jdn);
}

function jc_d2j_(jdn) {
  var gy = jc_d2g_(jdn).gy;
  var jy = gy - 621;
  var r = jc_jalCal_(jy);
  var jdn1f = jc_g2d_(gy, 3, r.march);
  var k = jdn - jdn1f;

  if (k >= 0) {
    if (k <= 185) {
      return { jy: jy, jm: 1 + jc_div_(k, 31), jd: jc_mod_(k, 31) + 1 };
    }
    k -= 186;
  } else {
    jy -= 1;
    k += jc_jalCal_(jy).leap === 0 ? 366 : 365;
  }

  return { jy: jy, jm: 7 + jc_div_(k, 30), jd: jc_mod_(k, 30) + 1 };
}

function gregorianToJalali_(gy, gm, gd) {
  var jdn = jc_g2d_(gy, gm, gd);
  return jc_d2j_(jdn);
}

// ==================== تاریخ و ساعت فعلی به شمسی (برای ثبت created) ====================
function nowToJalaliString_() {
  var d = new Date();
  var gy = Number(Utilities.formatDate(d, 'Asia/Tehran', 'yyyy'));
  var gm = Number(Utilities.formatDate(d, 'Asia/Tehran', 'MM'));
  var gd = Number(Utilities.formatDate(d, 'Asia/Tehran', 'dd'));
  var hh = Utilities.formatDate(d, 'Asia/Tehran', 'HH');
  var mm = Utilities.formatDate(d, 'Asia/Tehran', 'mm');

  var j = gregorianToJalali_(gy, gm, gd);
  function pad_(n) {
    n = String(n);
    return n.length < 2 ? '0' + n : n;
  }
  return j.jy + '/' + pad_(j.jm) + '/' + pad_(j.jd) + ' ' + hh + ':' + mm;
}

function parseTaskDateToJsDate_(dateStr) {
  if (!dateStr) return null;
  var m = String(dateStr).trim().match(/^(\d{4})\/(\d{1,2})\/(\d{1,2})(?:\s+(\d{1,2}):(\d{1,2}))?$/);
  if (!m) return null;

  var jy = parseInt(m[1], 10);
  var jm = parseInt(m[2], 10);
  var jd = parseInt(m[3], 10);
  var hour = m[4] !== undefined ? parseInt(m[4], 10) : 0;
  var minute = m[5] !== undefined ? parseInt(m[5], 10) : 0;

  if (!jy || !jm || !jd) return null;

  var g = jalaliToGregorian_(jy, jm, jd);
  var d = new Date(g.gy, g.gm - 1, g.gd, hour, minute, 0, 0);
  return isNaN(d.getTime()) ? null : d;
}

function testCalendarConnection() {
  try {
    var calendar = CalendarApp.getDefaultCalendar();
    if (!calendar) {
      Logger.log('❌ تقویم پیش‌فرض یافت نشد.');
      return;
    }
    Logger.log('✅ تقویم پیش‌فرض: ' + calendar.getName());

    var now = new Date();
    var oneHourLater = new Date(now.getTime() + 60 * 60 * 1000);
    var event = calendar.createEvent('تست اتصال تقویم', now, oneHourLater);
    Logger.log('✅ رویداد تستی ایجاد شد. شناسه: ' + event.getId());

    event.deleteEvent();
    Logger.log('✅ رویداد تستی حذف شد. اتصال به تقویم سالم است.');
  } catch (e) {
    Logger.log('❌ خطا در اتصال به تقویم: ' + e.message);
  }
}

// ==================== یکپارچه‌سازی با ربات بله ====================
// توکن ربات و آدرس وب‌هوک دیگر داخل کد نوشته نمی‌شوند (چون این مخزن قرار است روی گیت‌هاب عمومی باشد).
// این دو مقدار را یک‌بار از Project Settings > Script Properties در Apps Script وارد کنید:
//   کلید: BALE_BOT_TOKEN      مقدار: توکنی که از botfather@ در بله گرفته‌اید
//   کلید: BALE_WEBHOOK_URL    مقدار: آدرس دیپلوی وب‌اپ با پسوند exec/ (بعد از Deploy)
function getBaleBotToken_() {
  return PropertiesService.getScriptProperties().getProperty('BALE_BOT_TOKEN') || '';
}
function getBaleWebhookUrl_() {
  return PropertiesService.getScriptProperties().getProperty('BALE_WEBHOOK_URL') || '';
}

// ==================== RPC عمومی برای فرانت‌اند خارجی (Vercel / PWA) ====================
// فرانت‌اندی که روی Vercel اجرا می‌شود به google.script.run دسترسی ندارد،
// پس با fetch به همین آدرس exec/ درخواست POST می‌زند با بدنه‌ای شامل نام تابع و آرگومان‌ها،
// و دقیقاً همان مقداری که خود تابع برمی‌گرداند را پس می‌گیرد (شبیه‌سازی رفتار google.script.run).
var RPC_ALLOWED_FUNCTIONS_ = {
  loginUser: loginUser,
  registerUser: registerUser,
  logoutUser: logoutUser,
  validateSession: validateSession,
  getGroupsForUser: getGroupsForUser,
  getTasksPage: getTasksPage,
  addTask: addTask,
  addTasksBulk: addTasksBulk,
  updateTask: updateTask,
  upsertTask: upsertTask,
  deleteTask: deleteTask,
  saveAllGroupsForUser: saveAllGroupsForUser
};

function handleRpc_(payload) {
  var fnName = payload.fn;
  var args = payload.args || [];
  var fn = RPC_ALLOWED_FUNCTIONS_[fnName];
  if (typeof fn !== 'function') {
    return JSON.stringify({ success: false, message: 'تابع مجاز نیست: ' + fnName });
  }
  try {
    return fn.apply(null, args);
  } catch (err) {
    return JSON.stringify({ success: false, message: err.message || 'خطای داخلی سرور' });
  }
}

function jsonTextOutput_(text) {
  return ContentService.createTextOutput(text).setMimeType(ContentService.MimeType.TEXT);
}

// ==================== دریافت درخواست POST: هم RPC عمومی و هم وب‌هوک بله ====================
function doPost(e) {
  if (!e || !e.postData || !e.postData.contents) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'no_data' }));
  }

  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (parseErr) {
    return jsonTextOutput_(JSON.stringify({ success: false, message: 'بدنه درخواست JSON معتبر نیست.' }));
  }

  if (body && typeof body.fn === 'string') {
    return jsonTextOutput_(handleRpc_(body));
  }

  try {
    var update = body;
    var message = update.message;

    if (!message) {
      return ContentService.createTextOutput(JSON.stringify({ status: 'no_message' }));
    }

    var chatId = message.chat && message.chat.id;
    var text = (message.text || message.caption || '').trim();

    if (!chatId) {
      return ContentService.createTextOutput(JSON.stringify({ status: 'no_chat' }));
    }

    var username = findUsernameByBaleChatId_(chatId);

    if (!username) {
      sendBaleMessage_(chatId,
        '⚠️ این چت هنوز به هیچ حساب کاربری تسک‌پلاس وصل نیست.\n' +
        'شناسه چت شما: ' + chatId + '\n' +
        'این عدد را در ستون baleChatId مربوط به حساب خودتان در جدول Users وارد کنید.');
      return ContentService.createTextOutput(JSON.stringify({ status: 'unlinked', chatId: chatId }));
    }

    if (!text) {
      sendBaleMessage_(chatId, '⚠️ فقط پیام متنی به‌عنوان تسک ثبت می‌شود.');
      return ContentService.createTextOutput(JSON.stringify({ status: 'empty_text' }));
    }

    var result = addTaskFromBale_(username, text);

    if (result.success) {
      sendBaleMessage_(chatId, 'تسک شما با موفقیت ثبت شد!');
    } else {
      sendBaleMessage_(chatId, '❌ ثبت تسک ناموفق بود: ' + (result.message || ''));
    }

    return ContentService.createTextOutput(JSON.stringify({ status: 'ok' }));

  } catch (error) {
    Logger.log('❌ خطا در doPost (بله): ' + error.toString());
    return ContentService.createTextOutput(JSON.stringify({ status: 'error', error: error.toString() }));
  }
}

// ==================== یافتن نام کاربری بر اساس شناسه چت بله ====================
function findUsernameByBaleChatId_(chatId) {
  var sheet = getOrCreateSheet_(USERS_SHEET_NAME, USERS_HEADERS);
  var lastRow = sheet.getLastRow();
  var lastCol = sheet.getLastColumn();
  if (lastRow < 2) return null;

  var header = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  var chatIdCol = header.indexOf('baleChatId');
  if (chatIdCol === -1) return null; // ستون هنوز اضافه نشده -> migrateAddBaleChatIdColumn را اجرا کنید

  var data = sheet.getRange(2, 1, lastRow - 1, lastCol).getValues();
  for (var i = 0; i < data.length; i++) {
    var cell = data[i][chatIdCol];
    if (cell !== '' && cell !== null && String(cell) === String(chatId)) {
      return String(data[i][0]); // ستون username
    }
  }
  return null;
}

// ==================== ثبت تسک برای کاربر (مستقیم، بدون توکن نشست) ====================
function addTaskFromBale_(userId, title) {
  return withLock_(function () {
    var sheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
    var task = {
      id: Utilities.getUuid(),
      title: title,
      status: 'todo',
      priority: 0,
      date: '',
      created: nowToJalaliString_(),
      group: 'none',
      tags: [],
      notes: 'ثبت‌شده از طریق بله',
      mainTask: null,
      subtasks: []
    };
    sheet.appendRow(taskToRow_(task, userId));
    SpreadsheetApp.flush();
    return { success: true, id: task.id };
  });
}

// ==================== ارسال پیام به کاربر در بله ====================
function sendBaleMessage_(chatId, text) {
  if (!chatId) {
    Logger.log('⚠️ sendBaleMessage_: chatId خالی است، پیام ارسال نشد.');
    return;
  }
  var botToken = getBaleBotToken_();
  if (!botToken) {
    Logger.log('❌ sendBaleMessage_: BALE_BOT_TOKEN در Script Properties تنظیم نشده. پیام ارسال نشد.');
    return;
  }
  try {
    var url = 'https://tapi.bale.ai/bot' + botToken + '/sendMessage';
    var options = {
      method: 'post',
      contentType: 'application/json',
      payload: JSON.stringify({ chat_id: chatId, text: text }),
      muteHttpExceptions: true
    };
    var response = UrlFetchApp.fetch(url, options);
    Logger.log('پاسخ ارسال پیام بله (chatId=' + chatId + '): ' + response.getContentText());
  } catch (error) {
    Logger.log('❌ خطا در ارسال پیام بله: ' + error.toString());
  }
}

// ==================== تنظیم / حذف / بررسی وب‌هوک بله ====================
// این‌ها را از منوی Apps Script به‌صورت دستی، فقط یک‌بار بعد از Deploy اجرا کنید
function setBaleWebhook() {
  var webhookUrl = getBaleWebhookUrl_();
  var botToken = getBaleBotToken_();
  if (!webhookUrl) {
    Logger.log('❌ ابتدا BALE_WEBHOOK_URL را در Script Properties با آدرس دیپلوی وب‌اپ (exec/) تنظیم کنید.');
    return;
  }
  var url = 'https://tapi.bale.ai/bot' + botToken + '/setWebhook?url=' + webhookUrl;
  var response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  Logger.log('پاسخ تنظیم وب‌هوک بله: ' + response.getContentText());
}

function deleteBaleWebhook() {
  var url = 'https://tapi.bale.ai/bot' + getBaleBotToken_() + '/deleteWebhook';
  var response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  Logger.log('پاسخ حذف وب‌هوک بله: ' + response.getContentText());
}

function getBaleWebhookInfo() {
  var url = 'https://tapi.bale.ai/bot' + getBaleBotToken_() + '/getWebhookInfo';
  var response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  Logger.log('وضعیت وب‌هوک بله: ' + response.getContentText());
}

function testBaleAPI() {
  var url = 'https://tapi.bale.ai/bot' + getBaleBotToken_() + '/getMe';
  var response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  Logger.log('پاسخ تست بله: ' + response.getContentText());
}

// ==================== یادآوری تسک در بله (اجرای زمان‌بندی‌شده) ====================
var REMINDERS_SHEET_NAME = 'RemindersSent';
var REMINDERS_HEADERS = ['userId', 'taskId', 'sentAt'];
var REMINDER_WINDOW_MINUTES = 15; // اگر زمان تسک تا این چند دقیقه قبل رسیده و هنوز یادآوری نشده، ارسال می‌شود

function findBaleChatIdByUsername_(username) {
  var sheet = getOrCreateSheet_(USERS_SHEET_NAME, USERS_HEADERS);
  var lastRow = sheet.getLastRow();
  var lastCol = sheet.getLastColumn();
  if (lastRow < 2) return null;

  var header = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  var chatIdCol = header.indexOf('baleChatId');
  if (chatIdCol === -1) return null;

  var data = sheet.getRange(2, 1, lastRow - 1, lastCol).getValues();
  for (var i = 0; i < data.length; i++) {
    if (String(data[i][0]) === String(username)) {
      var chatId = data[i][chatIdCol];
      return (chatId !== '' && chatId !== null) ? String(chatId) : null;
    }
  }
  return null;
}

function isTaskAlreadyReminded_(remindersSheet, userId, taskId) {
  return findRowIndex_(remindersSheet, [1, 2], [userId, taskId]) !== -1;
}

function markTaskReminded_(remindersSheet, userId, taskId) {
  remindersSheet.appendRow([userId, taskId, new Date().toISOString()]);
}

function buildTaskReminderText_(task) {
  var lines = ['⏰ یادآوری تسک: ' + (task.title || 'بدون عنوان')];
  if (task.date) lines.push('زمان: ' + task.date);
  var groupName = getGroupName_(task.userId, task.group);
  if (groupName && groupName !== DEFAULT_GROUP.name) lines.push('گروه: ' + groupName);
  if (task.notes) lines.push('یادداشت: ' + task.notes);
  return lines.join('\n');
}

// این تابع باید هر چند دقیقه یک‌بار توسط تریگر زمان‌بندی‌شده اجرا شود
function checkAndSendTaskReminders() {
  var tasksSheet = getOrCreateSheet_(TASKS_SHEET_NAME, TASKS_HEADERS);
  var remindersSheet = getOrCreateSheet_(REMINDERS_SHEET_NAME, REMINDERS_HEADERS);

  var allTasks = sheetToObjects_(tasksSheet, TASKS_HEADERS).map(function (row) {
    var t = rowObjToTask_(row);
    t.userId = String(row.userId || '');
    return t;
  });

  var now = new Date();
  var windowStart = new Date(now.getTime() - REMINDER_WINDOW_MINUTES * 60 * 1000);

  allTasks.forEach(function (task) {
    if (!task.date) return;
    if (task.status === 'done') return;

    var dueDate = parseTaskDateToJsDate_(task.date);
    if (!dueDate) return;

    // فقط تسک‌هایی که زمانشان در بازه [windowStart, now] است (یعنی همین الان رسیده)
    if (dueDate.getTime() > now.getTime() || dueDate.getTime() < windowStart.getTime()) return;

    if (isTaskAlreadyReminded_(remindersSheet, task.userId, task.id)) return;

    var chatId = findBaleChatIdByUsername_(task.userId);
    if (!chatId) return;

    try {
      sendBaleMessage_(chatId, buildTaskReminderText_(task));
      markTaskReminded_(remindersSheet, task.userId, task.id);
    } catch (e) {
      Logger.log('خطا در ارسال یادآوری تسک (' + task.id + '): ' + e);
    }
  });
}

// این تابع را فقط یک‌بار از منوی Apps Script اجرا کنید تا تریگر زمان‌بندی‌شده ساخته شود
function setupTaskReminderTrigger() {
  ScriptApp.getProjectTriggers().forEach(function (trigger) {
    if (trigger.getHandlerFunction() === 'checkAndSendTaskReminders') {
      ScriptApp.deleteTrigger(trigger);
    }
  });
  ScriptApp.newTrigger('checkAndSendTaskReminders')
    .timeBased()
    .everyMinutes(5)
    .create();
  SpreadsheetApp.getUi().alert('تریگر یادآوری تسک هر ۵ دقیقه فعال شد.');
}
