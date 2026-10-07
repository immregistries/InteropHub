(function () {
  var panel = document.querySelector('[data-meeting-attachments]');
  if (!panel) {
    return;
  }
  var pending = false;
  panel.addEventListener('submit', function (event) {
    var form = event.target.closest('[data-meeting-attachment-form]');
    if (!form) {
      return;
    }
    event.preventDefault();
    if (pending) {
      return;
    }
    pending = true;
    var message = panel.querySelector('[data-attachment-message]');
    var button = form.querySelector('button[type="submit"]');
    var data = new FormData(form);
    button.disabled = true;
    message.textContent = 'Saving attachment change...';
    message.className = 'aira-alert aira-alert--info';
    fetch(form.action, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { Accept: 'application/json', 'X-CSRF-Token': data.get('csrfToken') },
      body: data
    }).then(async function (response) {
      if (!(response.headers.get('Content-Type') || '').includes('application/json')) {
        throw new Error('Your session may have expired. Save your notes before reloading and signing in.');
      }
      var result = await response.json();
      if (!response.ok) {
        throw new Error(result.error || 'Attachment change failed.');
      }
      // Only replace the attachment panel. The active editor and unsaved notes remain untouched.
      panel.innerHTML = result.html;
      message = panel.querySelector('[data-attachment-message]');
      message.textContent = result.message;
      message.className = 'aira-alert aira-alert--success';
    }).catch(function (error) {
      message.textContent = error.message || 'Attachment request failed. Check your connection before retrying.';
      message.className = 'aira-alert aira-alert--danger';
    }).finally(function () {
      pending = false;
      button.disabled = false;
    });
  });
})();
