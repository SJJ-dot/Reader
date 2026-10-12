/* 用户反馈 · 移动端
 * 数据面：WorkBuddy 云服务（PostgREST + SECURITY DEFINER RPC）
 * 所有读写都通过下面这些 RPC 完成，前端不直连任何数据表。
 */
(function () {
  'use strict';

  // ---------------------------------------------------------------- 配置
  // 由云服务开通时下发的 publicConfig（publishableKey 仅标识应用，本身不带权限）
  var CONFIG = {
    endpoint: 'https://user-feedback.app.workbuddy.host',
    oauthRelayBaseUrl: 'https://www.workbuddy.cn/v2/as/genie-baas/oauth',
    publishableKey: 'wbpk_GurkJIyVfddn7d1Fy97KW3_1Hxu1cMwwQTVPAxifhbyjErjtTLqquO4'
  };

  var SDK_URL = 'https://cdn.jsdelivr.net/npm/@tencent-ai/workbuddy-cloud-sdk@dev/lib/index.global.js';

  /** App 内置页面：原生注入 FeedbackBridge，RPC 全走原生（不需要 SDK，也不受跨域限制） */
  function hasNativeBridge() {
    return !!(window.FeedbackBridge && typeof window.FeedbackBridge.rpc === 'function');
  }

  var cloud = null;

  /** 只有浏览器里才需要 SDK；动态加载，避免 App 内置页面为首屏去下载它 */
  function ensureSdk() {
    if (hasNativeBridge()) return Promise.resolve();
    if (cloud) return Promise.resolve();
    return new Promise(function (resolve, reject) {
      var s = document.createElement('script');
      s.src = SDK_URL;
      s.async = true;
      s.onload = function () {
        if (typeof WorkBuddyCloud === 'undefined' || !WorkBuddyCloud.createWorkBuddyCloud) {
          reject(new Error('SDK_NOT_LOADED'));
          return;
        }
        cloud = WorkBuddyCloud.createWorkBuddyCloud({
          endpoint: CONFIG.endpoint,
          oauthRelayBaseUrl: CONFIG.oauthRelayBaseUrl,
          publishableKey: CONFIG.publishableKey
        });
        resolve();
      };
      s.onerror = function () { reject(new Error('SDK_NOT_LOADED')); };
      document.head.appendChild(s);
    });
  }

  // ---------------------------------------------------------------- 小工具
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function pad2(n) { return n < 10 ? '0' + n : '' + n; }

  function fmtTime(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    var diff = Date.now() - d.getTime();
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前';
    var hm = pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    var day = new Date();
    day.setHours(0, 0, 0, 0);
    if (d.getTime() >= day.getTime()) return hm;
    if (d.getTime() >= day.getTime() - 86400000) return '昨天 ' + hm;
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + hm;
  }

  var toastTimer = null;
  function toast(msg) {
    var el = $('#toast');
    el.textContent = msg;
    el.hidden = false;
    requestAnimationFrame(function () { el.classList.add('is-on'); });
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      el.classList.remove('is-on');
      setTimeout(function () { el.hidden = true; }, 220);
    }, 2400);
  }

  function errText(e) {
    var m = (e && (e.message || e.details || e.hint)) || '';
    if (/invalid_token/.test(m)) return '访问令牌已失效，请重新打开反馈页面';
    if (/title_required/.test(m)) return '请填写标题';
    if (/content_required/.test(m)) return '请填写详细描述';
    if (/code_generation_failed/.test(m)) return '生成查询码失败，请重试';
    if (/Failed to fetch|NetworkError|network|fetch/i.test(m)) return '网络异常，请稍后重试';
    if (/SDK_NOT_LOADED/.test(m)) return '资源未加载完成，请刷新页面';
    return m || '操作失败，请稍后重试';
  }

  // 原生桥：一次调用用一个自增 id，原生算完再回调 window.__fbResult
  var bridgeSeq = 0;
  var bridgePending = {};

  function bridgeRpc(fn, args) {
    return new Promise(function (resolve, reject) {
      var id = 'r' + (++bridgeSeq);
      bridgePending[id] = { resolve: resolve, reject: reject };
      try {
        window.FeedbackBridge.rpc(id, fn, JSON.stringify(args));
      } catch (e) {
        delete bridgePending[id];
        reject(e);
      }
    });
  }

  /** 原生回调入口：payload 是数据面返回的原始 JSON 文本（就是云函数的返回值） */
  window.__fbResult = function (id, payload) {
    var p = bridgePending[id];
    if (!p) return;
    delete bridgePending[id];
    try { p.resolve(JSON.parse(payload)); }
    catch (e) { p.reject(new Error('network_error')); }
  };

  function rpc(fn, args) {
    args = args || {};
    // 提交反馈必须携带访问令牌（令牌只由服务端校验，网页里不放任何明文）
    if (fn === 'fb_submit' && !args.p_token && TOKEN) args.p_token = TOKEN;

    if (hasNativeBridge()) return bridgeRpc(fn, args);

    if (!cloud) return Promise.reject(new Error('SDK_NOT_LOADED'));
    return Promise.resolve(cloud.database.rpc(fn, args)).then(function (res) {
      if (res && res.error) throw res.error;
      return res ? res.data : null;
    });
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).then(function () { return true; })
        .catch(function () { return legacyCopy(text); });
    }
    return Promise.resolve(legacyCopy(text));
  }
  function legacyCopy(text) {
    try {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', '');
      ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0';
      document.body.appendChild(ta);
      ta.select();
      ta.setSelectionRange(0, ta.value.length);
      var ok = document.execCommand('copy');
      document.body.removeChild(ta);
      return ok;
    } catch (e) { return false; }
  }

  function autoGrow(el) {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  }

  // ---------------------------------------------------------------- 状态
  var STATUS = {
    open: { t: '待处理', c: 'badge-open' },
    resolved: { t: '已解决', c: 'badge-ok' },
    closed: { t: '已关闭', c: 'badge-off' }
  };
  var LS_CODES = 'fb_codes_v1';
  var SS_PASS = 'fb_admin_pass_v1';

  var selectedCat = '问题反馈';
  var adminFilter = 'all';
  var sheetMode = 'user';
  var currentCode = null;
  var currentId = null;
  var currentAdminCode = null;   // 当前管理会话对应的查询码，删除后用它清理本机记录
  var lastSubmitted = null;

  // ---------------------------------------------------------------- 本机记录
  function loadCodes() {
    try { return JSON.parse(localStorage.getItem(LS_CODES) || '[]') || []; }
    catch (e) { return []; }
  }
  function saveCodes(list) {
    try { localStorage.setItem(LS_CODES, JSON.stringify(list.slice(0, 30))); } catch (e) { /* 忽略 */ }
  }
  function rememberCode(code, title) {
    var list = loadCodes().filter(function (x) { return x.code !== code; });
    list.unshift({ code: code, title: title, ts: Date.now() });
    saveCodes(list);
  }
  /** 从本机记录里移除某个查询码（该反馈已被删除时调用） */
  function forgetCode(code) {
    code = String(code || '').trim().toUpperCase();
    if (!code) return;
    var list = loadCodes();
    var next = list.filter(function (x) { return String(x.code || '').toUpperCase() !== code; });
    if (next.length !== list.length) saveCodes(next);
  }
  function renderCodeList() {
    var list = loadCodes();
    var box = $('#code-list');
    if (!list.length) {
      box.innerHTML = '<div class="empty">还没有提交记录</div>';
      return;
    }
    box.innerHTML = list.map(function (x) {
      return '<button class="item" type="button" data-code="' + esc(x.code) + '">' +
        '<span class="item-main">' +
          '<span class="item-title">' + esc(x.title || '反馈') + '</span>' +
          '<span class="item-sub">' + esc(x.code) + ' · ' + esc(fmtTime(new Date(x.ts).toISOString())) + '</span>' +
        '</span><span class="chev">›</span></button>';
    }).join('');
  }

  // ---------------------------------------------------------------- 视图切换
  function switchView(name) {
    $$('.view').forEach(function (v) { v.classList.toggle('is-active', v.id === 'view-' + name); });
    $$('.tab').forEach(function (t) { t.classList.toggle('is-on', t.getAttribute('data-view') === name); });
    window.scrollTo(0, 0);
    if (name === 'mine') renderCodeList();
    if (name === 'admin') refreshAdminEntry();
  }

  // ---------------------------------------------------------------- 提交反馈
  function setCat(cat) {
    selectedCat = cat;
    $$('#cat-chips .chip').forEach(function (c) { c.classList.toggle('is-on', c.getAttribute('data-cat') === cat); });
  }

  function onSubmit(e) {
    e.preventDefault();
    var title = $('#in-title').value.trim();
    var content = $('#in-content').value.trim();
    if (!title) { toast('请填写标题'); $('#in-title').focus(); return; }
    if (!content) { toast('请填写详细描述'); $('#in-content').focus(); return; }

    var btn = $('#btn-submit');
    btn.disabled = true;
    btn.textContent = '提交中…';

    rpc('fb_submit', {
      p_title: title,
      p_content: content,
      p_category: selectedCat,
      p_nickname: $('#in-nickname').value.trim(),
      p_contact: $('#in-contact').value.trim()
    }).then(function (res) {
      if (!res || !res.ok) throw new Error((res && res.error) || 'submit_failed');
      rememberCode(res.code, title);
      lastSubmitted = { code: res.code, title: title };
      $('#form-submit').reset();
      setCat('问题反馈');
      $('#content-count').textContent = '0';
      renderCodeList();
      showResult(res.code);
    }).catch(function (err) {
      toast(errText(err));
    }).then(function () {
      btn.disabled = false;
      btn.textContent = '提交反馈';
    });
  }

  function showResult(code) {
    $('#result-code').textContent = code;
    var el = $('#result-sheet');
    el.hidden = false;
    requestAnimationFrame(function () { el.classList.add('is-on'); });
  }
  function closeResult() {
    var el = $('#result-sheet');
    el.classList.remove('is-on');
    setTimeout(function () { el.hidden = true; }, 200);
  }

  // ---------------------------------------------------------------- 会话面板
  function openSheet(mode) {
    sheetMode = mode;
    $('#user-actions').hidden = (mode !== 'user');
    $('#admin-actions').hidden = (mode !== 'admin');
    var el = $('#sheet');
    el.hidden = false;
    document.body.classList.add('no-scroll');
    requestAnimationFrame(function () { el.classList.add('is-on'); });
  }
  function closeSheet() {
    var el = $('#sheet');
    el.classList.remove('is-on');
    document.body.classList.remove('no-scroll');
    setTimeout(function () { el.hidden = true; }, 240);
    currentCode = null;
    currentId = null;
    $('#msgs').innerHTML = '';
    var reply = $('#in-reply');
    reply.value = '';
    autoGrow(reply);
  }

  function setBadge(status) {
    var st = STATUS[status] || STATUS.open;
    var el = $('#sheet-badge');
    el.textContent = st.t;
    el.className = 'badge ' + st.c;
    el.hidden = false;
  }

  function renderMessages(msgs, myRole) {
    var box = $('#msgs');
    if (!msgs || !msgs.length) {
      box.innerHTML = '<div class="empty">暂无消息</div>';
      return;
    }
    box.innerHTML = msgs.map(function (m) {
      var mine = m.role === myRole;
      var who = m.role === 'user' ? '用户' : '客服';
      return '<div class="msg ' + (mine ? 'msg-me' : 'msg-them') + '">' +
        '<div class="bubble">' + esc(m.content) + '</div>' +
        '<div class="stamp">' + (mine ? '' : esc(who) + ' · ') + esc(fmtTime(m.created_at)) + '</div>' +
        '</div>';
    }).join('');
    box.scrollTop = box.scrollHeight;
  }

  function setComposerEnabled(enabled, placeholder) {
    var reply = $('#in-reply');
    reply.disabled = !enabled;
    $('#btn-send').disabled = !enabled;
    reply.placeholder = placeholder;
  }

  // ---------------------------------------------------------------- 用户：打开会话
  function openThreadByCode(code) {
    code = String(code || '').trim().toUpperCase();
    if (!code) { toast('请输入查询码'); return Promise.resolve(); }

    return rpc('fb_thread', { p_code: code }).then(function (res) {
      if (!res) {
        // 本机有记录、服务端却查不到 → 该反馈已被删除，顺手清掉本机残留
        forgetCode(code);
        renderCodeList();
        toast('该反馈不存在或已被删除');
        return;
      }
      currentCode = res.code;
      currentId = null;
      $('#sheet-title').textContent = res.title;
      $('#sheet-sub').textContent = res.category + ' · ' + res.code + ' · ' + fmtTime(res.created_at);
      setBadge(res.status);
      renderMessages(res.messages, 'user');
      setComposerEnabled(res.status !== 'closed',
        res.status === 'closed' ? '该反馈已关闭' : '补充说明或继续提问…');
      openSheet('user');
    }).catch(function (err) { toast(errText(err)); });
  }

  function sendUserReply() {
    var reply = $('#in-reply');
    var text = reply.value.trim();
    if (!text) return;
    if (!currentCode) { toast('会话已失效，请重新打开'); return; }

    var btn = $('#btn-send');
    btn.disabled = true;
    rpc('fb_reply', { p_code: currentCode, p_content: text }).then(function (res) {
      if (!res || !res.ok) {
        toast(res && res.error === 'closed' ? '该反馈已关闭，无法继续回复' : '发送失败，请重试');
        return;
      }
      reply.value = '';
      autoGrow(reply);
      return openThreadByCode(currentCode);
    }).catch(function (err) {
      toast(errText(err));
    }).then(function () {
      if (!reply.disabled) btn.disabled = false;
    });
  }

  // ---------------------------------------------------------------- 管理端
  function getPass() {
    try { return sessionStorage.getItem(SS_PASS) || ''; } catch (e) { return ''; }
  }
  function setPass(p) { try { sessionStorage.setItem(SS_PASS, p); } catch (e) { /* 忽略 */ } }
  function clearPass() { try { sessionStorage.removeItem(SS_PASS); } catch (e) { /* 忽略 */ } }

  function refreshAdminEntry() {
    if (getPass()) {
      $('#admin-login-card').hidden = true;
      $('#admin-panel').hidden = false;
      loadAdminList();
    } else {
      $('#admin-login-card').hidden = false;
      $('#admin-panel').hidden = true;
      $('#admin-stats').innerHTML = '';
      $('#admin-list').innerHTML = '';
    }
  }

  function adminLogin() {
    var pass = $('#in-pass').value.trim();
    if (!pass) { toast('请输入管理口令'); return; }

    var btn = $('#btn-admin-login');
    btn.disabled = true;
    btn.textContent = '验证中…';

    rpc('fb_admin_list', { p_pass: pass, p_status: adminFilter }).then(function (res) {
      if (!res || !res.ok) {
        toast(res && res.error === 'not_configured' ? '尚未设置管理口令' : '口令不正确');
        return;
      }
      setPass(pass);
      // 管理员口令即管理员令牌：记住它，下次进来自动登录
      TOKEN = pass;
      ROLE = 'admin';
      saveToken(pass);
      $('#in-pass').value = '';
      $('#admin-login-card').hidden = true;
      $('#admin-panel').hidden = false;
      renderStats(res.stats);
      renderAdminList(res.items);
    }).catch(function (err) {
      toast(errText(err));
    }).then(function () {
      btn.disabled = false;
      btn.textContent = '登录';
    });
  }

  function loadAdminList() {
    var pass = getPass();
    if (!pass) { refreshAdminEntry(); return; }
    return rpc('fb_admin_list', { p_pass: pass, p_status: adminFilter }).then(function (res) {
      if (!res || !res.ok) {
        if (res && res.error === 'unauthorized') {
          clearPass();
          toast('登录已失效，请重新登录');
          refreshAdminEntry();
          return;
        }
        toast('加载失败，请重试');
        return;
      }
      renderStats(res.stats);
      renderAdminList(res.items);
    }).catch(function (err) { toast(errText(err)); });
  }

  function statHtml(label, val, alert) {
    return '<div class="stat' + (alert ? ' is-alert' : '') + '"><b>' + esc(val) + '</b><span>' + esc(label) + '</span></div>';
  }
  function renderStats(s) {
    s = s || {};
    $('#admin-stats').innerHTML =
      statHtml('总数', s.total || 0, false) +
      statHtml('待处理', s.open || 0, false) +
      statHtml('已解决', s.resolved || 0, false) +
      statHtml('未读', s.unread || 0, (s.unread || 0) > 0);
  }

  function renderAdminList(items) {
    var box = $('#admin-list');
    if (!items || !items.length) {
      box.innerHTML = '<div class="empty">' +
        (adminFilter === 'all' ? '暂无反馈' : '该状态下没有反馈') + '</div>';
      return;
    }
    box.innerHTML = items.map(function (t) {
      var st = STATUS[t.status] || STATUS.open;
      var meta = [t.category];
      if (t.nickname) meta.push(t.nickname);
      meta.push(fmtTime(t.updated_at));
      var unread = t.staff_unread > 0 ? '<b class="dot"></b>' : '';
      return '<button class="item" type="button" data-id="' + t.id + '">' +
        '<span class="item-main">' +
          '<span class="item-title">' + esc(t.title) + unread + '</span>' +
          '<span class="item-sub">' + esc(meta.join(' · ')) + '</span>' +
          '<span class="item-prev">' + (t.last_role === 'staff' ? '我：' : '') + esc(t.last_message || '') + '</span>' +
        '</span>' +
        '<span class="badge ' + st.c + '">' + st.t + '</span>' +
        '</button>';
    }).join('');
  }

  function openAdminThread(id) {
    return rpc('fb_admin_thread', { p_pass: getPass(), p_id: id }).then(function (res) {
      if (!res || !res.ok) {
        if (res && res.error === 'unauthorized') {
          clearPass();
          toast('登录已失效，请重新登录');
          closeSheet();
          refreshAdminEntry();
          return;
        }
        toast(res && res.error === 'not_found' ? '该反馈已被删除' : '打开失败，请重试');
        return;
      }
      var t = res.thread;
      currentId = t.id;
      currentAdminCode = t.code;
      currentCode = null;
      var meta = [t.category, t.nickname || '匿名用户'];
      if (t.contact) meta.push(t.contact);
      meta.push(t.code);
      $('#sheet-title').textContent = t.title;
      $('#sheet-sub').textContent = meta.join(' · ');
      setBadge(t.status);
      renderMessages(res.messages, 'staff');
      setComposerEnabled(t.status !== 'closed',
        t.status === 'closed' ? '该反馈已关闭，可先设为待处理再回复' : '输入回复…');
      openSheet('admin');
      loadAdminList();
    }).catch(function (err) { toast(errText(err)); });
  }

  function sendAdminReply() {
    var reply = $('#in-reply');
    var text = reply.value.trim();
    if (!text) return;
    if (!currentId) { toast('会话已失效，请重新打开'); return; }

    var btn = $('#btn-send');
    btn.disabled = true;
    rpc('fb_admin_reply', { p_pass: getPass(), p_id: currentId, p_content: text }).then(function (res) {
      if (!res || !res.ok) {
        toast(res && res.error === 'content_required' ? '回复内容不能为空' : '发送失败，请重试');
        return;
      }
      reply.value = '';
      autoGrow(reply);
      return openAdminThread(currentId);
    }).catch(function (err) {
      toast(errText(err));
    }).then(function () {
      if (!reply.disabled) btn.disabled = false;
    });
  }

  function setThreadStatus(status) {
    if (!currentId) return;
    rpc('fb_admin_status', { p_pass: getPass(), p_id: currentId, p_status: status }).then(function (res) {
      if (!res || !res.ok) { toast('操作失败，请重试'); return; }
      toast('已更新为「' + (STATUS[status] || {}).t + '」');
      setBadge(status);
      setComposerEnabled(status !== 'closed',
        status === 'closed' ? '该反馈已关闭，可先设为待处理再回复' : '输入回复…');
      loadAdminList();
    }).catch(function (err) { toast(errText(err)); });
  }

  /**
   * 页面内确认框，返回 Promise<boolean>。
   * 不用原生 window.confirm —— 在 WebView 里它由系统弹窗承载，标题栏会显示当前
   * 页面网址（appassets.androidplatform.net 或线上域名），既难看又像是要跳转。
   */
  function confirmDialog(title, message, okText) {
    return new Promise(function (resolve) {
      var sheet = $('#confirm-sheet');
      var btnOk = $('#confirm-ok');
      var btnCancel = $('#confirm-cancel');
      $('#confirm-title').textContent = title || '确认操作';
      $('#confirm-msg').textContent = message || '';
      btnOk.textContent = okText || '确定';

      var settled = false;
      function finish(v) {
        if (settled) return;
        settled = true;
        btnOk.removeEventListener('click', onOk);
        btnCancel.removeEventListener('click', onCancel);
        sheet.removeEventListener('click', onBackdrop);
        document.removeEventListener('keydown', onKey);
        sheet.classList.remove('is-on');
        setTimeout(function () { sheet.hidden = true; }, 200);
        resolve(v);
      }
      function onOk() { finish(true); }
      function onCancel() { finish(false); }
      function onBackdrop(e) { if (e.target === sheet) finish(false); }   // 点遮罩＝取消
      function onKey(e) { if (e.key === 'Escape' || e.keyCode === 27) finish(false); }

      btnOk.addEventListener('click', onOk);
      btnCancel.addEventListener('click', onCancel);
      sheet.addEventListener('click', onBackdrop);
      document.addEventListener('keydown', onKey);
      sheet.hidden = false;
      // 强制一次回流，让过渡从初始态起算；同步加 class 而不等 rAF ——
      // 页面不可见时 rAF 会被节流，弹框就永远显不出来
      void sheet.offsetWidth;
      sheet.classList.add('is-on');
    });
  }

  function deleteThread() {
    if (!currentId) return;
    confirmDialog('删除这条反馈？', '该反馈及其全部对话都会被永久删除，无法恢复。', '删除')
      .then(function (yes) {
        if (!yes) return;
        rpc('fb_admin_delete', { p_pass: getPass(), p_id: currentId }).then(function (res) {
          if (!res || !res.ok) { toast('删除失败，请重试'); return; }
          toast('已删除');
          forgetCode(currentAdminCode);   // 本机「我的反馈」里的那条提交记录同步删掉
          renderCodeList();
          closeSheet();
          loadAdminList();
        }).catch(function (err) { toast(errText(err)); });
      });
  }

  // ---------------------------------------------------------------- 事件绑定
  function bind() {
    // 底部导航
    $$('.tab').forEach(function (t) {
      t.addEventListener('click', function () { switchView(t.getAttribute('data-view')); });
    });

    // 提交
    $('#form-submit').addEventListener('submit', onSubmit);
    $$('#cat-chips .chip').forEach(function (c) {
      c.addEventListener('click', function () { setCat(c.getAttribute('data-cat')); });
    });
    $('#in-content').addEventListener('input', function () {
      $('#content-count').textContent = String(this.value.length);
    });

    // 结果弹窗
    $('#btn-copy-result').addEventListener('click', function () {
      var code = $('#result-code').textContent;
      copyText(code).then(function (ok) { toast(ok ? '查询码已复制' : '复制失败，请手动记录'); });
    });
    $('#btn-goto-thread').addEventListener('click', function () {
      if (!lastSubmitted) return;
      var code = lastSubmitted.code;
      closeResult();
      switchView('mine');
      setTimeout(function () { openThreadByCode(code); }, 220);
    });
    $('#btn-result-close').addEventListener('click', closeResult);

    // 我的反馈
    $('#btn-open-code').addEventListener('click', function () { openThreadByCode($('#in-code').value); });
    $('#in-code').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); openThreadByCode(this.value); }
    });
    $('#in-code').addEventListener('input', function () { this.value = this.value.toUpperCase(); });
    $('#code-list').addEventListener('click', function (e) {
      var btn = e.target.closest('.item');
      if (btn) openThreadByCode(btn.getAttribute('data-code'));
    });

    // 会话面板
    $('#sheet-back').addEventListener('click', closeSheet);
    $('#btn-send').addEventListener('click', function () {
      if (sheetMode === 'admin') sendAdminReply();
      else sendUserReply();
    });
    $('#in-reply').addEventListener('input', function () { autoGrow(this); });
    $('#in-reply').addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        if (sheetMode === 'admin') sendAdminReply();
        else sendUserReply();
      }
    });
    $('#btn-copy-code').addEventListener('click', function () {
      if (!currentCode) return;
      copyText(currentCode).then(function (ok) { toast(ok ? '查询码已复制' : '复制失败'); });
    });
    $$('#admin-actions [data-st]').forEach(function (b) {
      b.addEventListener('click', function () { setThreadStatus(b.getAttribute('data-st')); });
    });
    $('#btn-del-thread').addEventListener('click', deleteThread);

    // 管理端
    $('#btn-admin-login').addEventListener('click', adminLogin);
    $('#in-pass').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); adminLogin(); }
    });
    $('#btn-admin-logout').addEventListener('click', function () {
      clearPass();
      refreshAdminEntry();
      toast('已退出登录');
    });
    $('#admin-filters').addEventListener('click', function (e) {
      var chip = e.target.closest('.chip');
      if (!chip) return;
      adminFilter = chip.getAttribute('data-f');
      $$('#admin-filters .chip').forEach(function (c) { c.classList.toggle('is-on', c === chip); });
      loadAdminList();
    });
    $('#admin-list').addEventListener('click', function (e) {
      var btn = e.target.closest('.item');
      if (btn) openAdminThread(Number(btn.getAttribute('data-id')));
    });
  }

  // ---------------------------------------------------------------- 访问令牌
  var LS_TOKEN = 'fb_token_v1';
  var TOKEN = '';
  var ROLE = '';

  function readUrlToken() {
    try {
      var m = /[?&]t=([^&#]+)/.exec(location.search || '');
      return m ? decodeURIComponent(m[1]).trim() : '';
    } catch (e) { return ''; }
  }

  function stripUrlToken() {
    try {
      if (/[?&]t=/.test(location.search || '')) history.replaceState(null, '', location.pathname);
    } catch (e) { /* 忽略 */ }
  }

  function loadToken() {
    try { return localStorage.getItem(LS_TOKEN) || ''; } catch (e) { return ''; }
  }

  function saveToken(t) {
    try { localStorage.setItem(LS_TOKEN, t); } catch (e) { /* 忽略 */ }
  }

  function showGate(title, showForm, err) {
    $('#view-gate').hidden = false;
    $('#app-shell').hidden = true;
    $('#app-tabbar').hidden = true;
    $('#gate-title').textContent = title;
    $('#gate-tip').hidden = !showForm;
    $('#gate-form').hidden = !showForm;
    $('#gate-err').hidden = !err;
    if (err) $('#gate-err').textContent = err;
  }

  /** 逐个校验候选令牌，返回权限最高的那一个 */
  function pickToken(list) {
    var best = null;
    return list.reduce(function (chain, t) {
      return chain.then(function () {
        if (best && best.role === 'admin') return;
        return rpc('fb_gate', { p_token: t }).then(function (res) {
          if (res && res.ok) {
            var role = res.role === 'admin' ? 'admin' : 'guest';
            if (!best || role === 'admin') best = { token: t, role: role };
          }
        }).catch(function () { /* 单个候选失败不影响其它候选 */ });
      });
    }, Promise.resolve()).then(function () { return best; });
  }

  function applyToken(token, role) {
    TOKEN = token;
    ROLE = role;
    saveToken(token);
    if (role === 'admin') setPass(token);
    else clearPass();
    $('#view-gate').hidden = true;
    $('#app-shell').hidden = false;
    $('#app-tabbar').hidden = false;
    // 管理员直接进后台；游客若本机提交过反馈，直接进「我的」；其余进提交页
    if (role === 'admin') switchView('admin');
    else if (loadCodes().length) switchView('mine');
    else switchView('submit');
  }

  function gateEnter() {
    var token = $('#in-gate-token').value.trim();
    if (!token) { toast('请输入访问令牌'); return; }

    var btn = $('#btn-gate-enter');
    btn.disabled = true;
    btn.textContent = '验证中…';

    rpc('fb_gate', { p_token: token }).then(function (res) {
      if (!res || !res.ok) {
        showGate('需要访问令牌', true, '令牌不正确，请重新输入');
        return;
      }
      applyToken(token, res.role === 'admin' ? 'admin' : 'guest');
    }).catch(function (err) {
      showGate('需要访问令牌', true, errText(err));
    }).then(function () {
      btn.disabled = false;
      btn.textContent = '进入';
    });
  }

  function bindGate() {
    $('#btn-gate-enter').addEventListener('click', gateEnter);
    $('#in-gate-token').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); gateEnter(); }
    });
  }

  /** 启动时决定：URL 里的令牌（App 注入）或上次记住的令牌，取权限更高者自动登录 */
  function bootGate() {
    var urlToken = readUrlToken();
    if (urlToken) stripUrlToken();

    var saved = loadToken();
    var candidates = [];
    if (urlToken) candidates.push(urlToken);
    if (saved && saved !== urlToken) candidates.push(saved);

    if (!candidates.length) {
      showGate('需要访问令牌', true, '');
      return;
    }

    pickToken(candidates).then(function (best) {
      if (!best) {
        showGate('需要访问令牌', true, '访问令牌已失效，请重新输入');
        return;
      }
      applyToken(best.token, best.role);
    });
  }

  // ---------------------------------------------------------------- 启动
  function init() {
    bind();
    bindGate();
    renderCodeList();
    // App 内置页面在这里立即返回（走原生桥）；浏览器才需要等 SDK 下载完
    ensureSdk().then(bootGate).catch(function () {
      $('#sdk-banner').hidden = false;
      showGate('需要访问令牌', true, '资源加载失败，请检查网络后刷新页面重试');
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
