// TaskPluss Widget - Google Apps Script Backend
// این فایل باید در پروژه Google Apps Script شما کپی شود

const SHEET_NAME_TASKS = "Tasks";
const SHEET_NAME_USERS = "Users";
const SHEET_NAME_GROUPS = "Groups";

function doGet(e) {
  return handleRequest(e);
}

function doPost(e) {
  return handleRequest(e);
}

function handleRequest(e) {
  try {
    const params = e.parameter;
    
    // پشتیبانی از فرمت RPC جدید
    if (params.fn && params.args) {
      const fnName = params.fn;
      const args = JSON.parse(params.args);
      
      if (fnName === 'loginUser') {
        return jsonResponse(loginUser(args[0], args[1]));
      } else if (fnName === 'getGroupsForUser') {
        return jsonResponse(getGroupsForUser(args[0]));
      } else if (fnName === 'getTasksPage') {
        return jsonResponse(getTasksPage(args[0], args[1], args[2]));
      } else if (fnName === 'upsertTask') {
        return jsonResponse(upsertTask(args[0], args[1], args[2], args[3], args[4], args[5]));
      } else if (fnName === 'toggleTaskDone') {
        return jsonResponse(toggleTaskDone(args[0], args[1]));
      } else {
        return jsonResponse({ success: false, error: 'Unknown function: ' + fnName });
      }
    }
    
    // پشتیبانی از فرمت قدیمی action-based
    const action = params.action;
    const data = params.data ? JSON.parse(params.data) : null;
    
    if (!action) {
      return jsonResponse({ success: false, error: 'No action provided' });
    }
    
    if (action === 'login') {
      return jsonResponse(loginUser(data.username, data.password));
    } else if (action === 'getGroups') {
      return jsonResponse(getGroupsForUser(data.username));
    } else if (action === 'getTasks') {
      return jsonResponse(getTasksPage(data.username, data.groupFilter, data.page || 0));
    } else if (action === 'addTask') {
      return jsonResponse(upsertTask(null, data.title, data.description, data.priority, data.groupId, data.username));
    } else if (action === 'toggleTask') {
      return jsonResponse(toggleTaskDone(data.taskId, data.isDone));
    } else {
      return jsonResponse({ success: false, error: 'Unknown action: ' + action });
    }
    
  } catch (error) {
    return jsonResponse({ success: false, error: error.toString() });
  }
}

function jsonResponse(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

function getSheet(sheetName) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    initializeSheet(sheet);
  }
  return sheet;
}

function initializeSheet(sheet) {
  const sheetName = sheet.getName();
  
  if (sheetName === SHEET_NAME_USERS) {
    sheet.appendRow(['Username', 'Password', 'Token', 'CreatedAt']);
  } else if (sheetName === SHEET_NAME_GROUPS) {
    sheet.appendRow(['GroupId', 'GroupName', 'Username', 'CreatedAt']);
  } else if (sheetName === SHEET_NAME_TASKS) {
    sheet.appendRow(['TaskId', 'Title', 'Description', 'Priority', 'GroupId', 'Username', 'IsDone', 'CreatedAt', 'CompletedAt']);
  }
}

function loginUser(username, password) {
  try {
    const sheet = getSheet(SHEET_NAME_USERS);
    const data = sheet.getDataRange().getValues();
    
    // Skip header row
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (row[0] === username && row[1] === password) {
        const token = Utilities.getUuid();
        sheet.getRange(i + 1, 3).setValue(token);
        return { success: true, token: token, username: username };
      }
    }
    
    // Create new user if not exists
    const token = Utilities.getUuid();
    sheet.appendRow([username, password, token, new Date()]);
    return { success: true, token: token, username: username };
    
  } catch (error) {
    return { success: false, error: error.toString() };
  }
}

function getGroupsForUser(username) {
  try {
    const sheet = getSheet(SHEET_NAME_GROUPS);
    const data = sheet.getDataRange().getValues();
    const groups = [];
    
    // Add default "All" group
    groups.push({ groupId: 'all', groupName: 'همه' });
    
    // Skip header row
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (row[2] === username) {
        groups.push({
          groupId: row[0],
          groupName: row[1]
        });
      }
    }
    
    return { success: true, groups: groups };
    
  } catch (error) {
    return { success: false, error: error.toString() };
  }
}

function getTasksPage(username, groupFilter, page) {
  try {
    const sheet = getSheet(SHEET_NAME_TASKS);
    const data = sheet.getDataRange().getValues();
    const tasks = [];
    const pageSize = 6;
    const startIndex = page * pageSize;
    
    // Skip header row
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      const taskUsername = row[5];
      const groupId = row[4];
      
      if (taskUsername !== username) continue;
      if (groupFilter && groupFilter !== 'all' && groupId !== groupFilter) continue;
      
      tasks.push({
        taskId: row[0],
        title: row[1],
        description: row[2],
        priority: row[3],
        groupId: row[4],
        isDone: row[6] === true || row[6] === 'TRUE',
        createdAt: row[7]
      });
    }
    
    // Sort tasks
    if (groupFilter === 'all' || !groupFilter) {
      // Sort by creation date (newest first)
      tasks.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    } else {
      // Sort by priority (high to low)
      tasks.sort((a, b) => b.priority - a.priority);
    }
    
    // Paginate
    const paginatedTasks = tasks.slice(startIndex, startIndex + pageSize);
    
    return { 
      success: true, 
      tasks: paginatedTasks,
      hasMore: tasks.length > startIndex + pageSize
    };
    
  } catch (error) {
    return { success: false, error: error.toString() };
  }
}

function upsertTask(taskId, title, description, priority, groupId, username) {
  try {
    const sheet = getSheet(SHEET_NAME_TASKS);
    const data = sheet.getDataRange().getValues();
    
    if (taskId) {
      // Update existing task
      for (let i = 1; i < data.length; i++) {
        if (data[i][0] == taskId) {
          sheet.getRange(i + 1, 2).setValue(title);
          sheet.getRange(i + 1, 3).setValue(description);
          sheet.getRange(i + 1, 4).setValue(priority);
          sheet.getRange(i + 1, 5).setValue(groupId);
          return { success: true, taskId: taskId };
        }
      }
    }
    
    // Create new task
    const newTaskId = Utilities.getUuid();
    sheet.appendRow([newTaskId, title, description, priority, groupId, username, false, new Date(), null]);
    return { success: true, taskId: newTaskId };
    
  } catch (error) {
    return { success: false, error: error.toString() };
  }
}

function toggleTaskDone(taskId, isDone) {
  try {
    const sheet = getSheet(SHEET_NAME_TASKS);
    const data = sheet.getDataRange().getValues();
    
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] == taskId) {
        sheet.getRange(i + 1, 7).setValue(isDone);
        if (isDone) {
          sheet.getRange(i + 1, 9).setValue(new Date());
        } else {
          sheet.getRange(i + 1, 9).clearContent();
        }
        return { success: true, taskId: taskId, isDone: isDone };
      }
    }
    
    return { success: false, error: 'Task not found' };
    
  } catch (error) {
    return { success: false, error: error.toString() };
  }
}
