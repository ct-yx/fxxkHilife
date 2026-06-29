import re

with open('docs/index.html', 'r', encoding='utf-8') as f:
    h = f.read()

# === 1. Fix orphaned CSS line ===
h = h.replace(
    '  transition:left .6s cubic-bezier(.16,1,.3,1),transform .6s cubic-bezier(.16,1,.3,1);\n}\n.timeline-item{',
    '\n.timeline-item{'
)

# === 2. Fix timeline template: button INSIDE detail-wrap, AFTER detail ===
old_tpl = re.search(
    r'container\.innerHTML = items\.map\(\(item, i\) => `.*?`\)\.join\(\x27\x27\);',
    h, re.DOTALL
)

new_tpl = '''container.innerHTML = items.map((item, i) => `
      <div class="timeline-item ${i % 2 === 0 ? 'left' : 'right'}" style="animation:fadeUp .6s ${i * 0.08}s both cubic-bezier(.16,1,.3,1)">
        <div class="timeline-version-badge">${item.version}</div>
        <div class="timeline-dot"></div>
        <div class="timeline-item-inner">
          <div class="timeline-date">${item.date}</div>
          <div class="timeline-title">${item.title}</div>
          <div class="timeline-desc">${item.summary}</div>
          <div class="timeline-detail-wrap">
            <div class="timeline-detail">
              <div class="timeline-detail-medium">${item.medium}</div>
              <div class="timeline-detail-full">${item.detail}</div>
            </div>
            <span class="timeline-toggle" onclick="toggleTimeline(this)">展开详情 <svg viewBox='0 0 12 12' fill='currentColor'><polygon points='3,4.5 6,7.5 9,4.5'/></svg></span>
          </div>
        </div>
      </div>
    `).join('');'''

h = h.replace(old_tpl.group(), new_tpl)

# === 3. Fix toggleTimeline function ===
old_fn = re.search(r'function toggleTimeline\(btn\) \{.*?\n\}', h, re.DOTALL)
new_fn = '''function toggleTimeline(btn) {
  const item = btn.closest('.timeline-item');
  const wrap = btn.closest('.timeline-detail-wrap');
  if (item.classList.contains('full')) {
    item.classList.remove('open', 'full');
    wrap.classList.remove('open', 'full');
    btn.classList.remove('open', 'full');
    btn.innerHTML = '展开详情 <svg viewBox="0 0 12 12" fill="currentColor"><polygon points="3,4.5 6,7.5 9,4.5"/></svg>';
  } else if (item.classList.contains('open')) {
    item.classList.add('full');
    wrap.classList.add('full');
    btn.classList.add('full');
    btn.innerHTML = '收起 <svg viewBox="0 0 12 12" fill="currentColor"><polygon points="3,4.5 6,7.5 9,4.5"/></svg>';
  } else {
    item.classList.add('open');
    wrap.classList.add('open');
    btn.classList.add('open');
    btn.innerHTML = '完整日志 <svg viewBox="0 0 12 12" fill="currentColor"><polygon points="3,4.5 6,7.5 9,4.5"/></svg>';
  }
}'''
h = h.replace(old_fn.group(), new_fn)

with open('docs/index.html', 'w', encoding='utf-8') as f:
    f.write(h)
print('OK: template + toggle + CSS fixed')
