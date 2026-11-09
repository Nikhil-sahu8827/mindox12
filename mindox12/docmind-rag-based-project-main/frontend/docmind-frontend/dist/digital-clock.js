// ============================================================================
// Quivora AI / DocMind - Luxury LED Digital Mirror Alarm Clock
// Modeled faithfully after the physical capsule mirror LED alarm clock:
// - Deep gloss metallic black chassis with rounded pill/capsule stadium shape
// - 100% Vector SVG 7-segment ultra-bright glowing white LED numerals
// - Subtle unlit segment ghosting under the mirror glass
// - Blinking colon separator with alarm & snooze micro-indicators
// - Realistic diagonal mirror glass reflection highlight
// - Tactile top control buttons (SET, ALARM, UP, DOWN, SNOOZE/LIGHT)
// - Optional Tabletop Mirror Reflection in Desk Mode (matches reference photo)
// - Responsive Header Dock (default) + Draggable Floating Desk Clock Mode
// - Realtime system clock synced to local time (12H / 24H toggleable)
// ============================================================================

(function () {
  'use strict';

  // --------------------------------------------------------------------------
  // SVG 7-Segment Definitions (viewBox: 0 0 36 64)
  // --------------------------------------------------------------------------
  var SEGMENTS = {
    a: "4,3 32,3 27,9 9,9",
    b: "33,4 33,30 28,26 28,9",
    c: "33,34 33,60 28,55 28,38",
    d: "9,55 27,55 32,61 4,61",
    e: "3,34 8,38 8,55 3,60",
    f: "3,4 8,9 8,26 3,30",
    g: "4,32 8,28 28,28 32,32 28,36 8,36"
  };

  var DIGIT_PATTERNS = {
    '0': ['a', 'b', 'c', 'd', 'e', 'f'],
    '1': ['b', 'c'],
    '2': ['a', 'b', 'g', 'e', 'd'],
    '3': ['a', 'b', 'g', 'c', 'd'],
    '4': ['f', 'g', 'b', 'c'],
    '5': ['a', 'f', 'g', 'c', 'd'],
    '6': ['a', 'f', 'e', 'd', 'c', 'g'],
    '7': ['a', 'b', 'c'],
    '8': ['a', 'b', 'c', 'd', 'e', 'f', 'g'],
    '9': ['a', 'b', 'c', 'd', 'f', 'g'],
    ' ': []
  };

  // --------------------------------------------------------------------------
  // Clock Settings & Persistence
  // --------------------------------------------------------------------------
  var SETTINGS_KEY = 'docmind_led_clock_settings';
  var settings = {
    is24Hour: true,
    showSeconds: false,
    mode: 'header', // 'header' or 'floating'
    brightness: 'high' // 'high', 'medium', 'night'
  };

  try {
    var saved = localStorage.getItem(SETTINGS_KEY);
    if (saved) {
      var parsed = JSON.parse(saved);
      Object.assign(settings, parsed);
    }
  } catch (e) {}

  function saveSettings() {
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    } catch (e) {}
  }

  // --------------------------------------------------------------------------
  // Inject Base CSS Styles for Animations
  // --------------------------------------------------------------------------
  (function injectStyles() {
    if (document.getElementById('quivora-clock-styles')) return;
    var style = document.createElement('style');
    style.id = 'quivora-clock-styles';
    style.textContent = [
      '@keyframes quivoraColonPulse {',
      '  0%, 100% { opacity: 1; }',
      '  50% { opacity: 0.18; }',
      '}',
      '@keyframes quivoraMenuFadeIn {',
      '  from { opacity: 0; transform: translateY(-4px) scale(0.98); }',
      '  to { opacity: 1; transform: translateY(0) scale(1); }',
      '}',
      '.quivora-header-clock-host {',
      '  display: flex;',
      '  align-items: center;',
      '  user-select: none;',
      '}',
      '@media (max-width: 639px) {',
      '  .quivora-header-clock-host .quivora-clock-chassis {',
      '    width: 98px !important;',
      '    height: 30px !important;',
      '  }',
      '  .quivora-header-clock-host svg {',
      '    transform: scale(0.85);',
      '  }',
      '}'
    ].join('\n');
    document.head.appendChild(style);
  })();

  // --------------------------------------------------------------------------
  // SVG Generation Helpers
  // --------------------------------------------------------------------------
  function createSvgDigit(digitChar, scale, glowClass) {
    scale = scale || 1;
    var activeSegs = DIGIT_PATTERNS[digitChar] || [];
    var activeSet = {};
    for (var i = 0; i < activeSegs.length; i++) {
      activeSet[activeSegs[i]] = true;
    }

    var w = Math.round(36 * scale);
    var h = Math.round(64 * scale);

    var pathsHtml = '';
    var segKeys = ['a', 'b', 'c', 'd', 'e', 'f', 'g'];
    for (var k = 0; k < segKeys.length; k++) {
      var key = segKeys[k];
      var pts = SEGMENTS[key];
      var isOn = !!activeSet[key];

      var fill = isOn ? '#ffffff' : 'rgba(255,255,255,0.035)';
      var filterStyle = '';
      if (isOn) {
        if (settings.brightness === 'high') {
          filterStyle = 'filter: drop-shadow(0 0 1.5px #ffffff) drop-shadow(0 0 5px rgba(255,255,255,0.85));';
        } else if (settings.brightness === 'medium') {
          filterStyle = 'filter: drop-shadow(0 0 2px rgba(255,255,255,0.6));';
        } else {
          fill = '#d1d5db';
          filterStyle = 'opacity: 0.75;';
        }
      }

      pathsHtml += '<polygon points="' + pts + '" fill="' + fill + '" style="' + filterStyle + ' transition: fill 0.12s ease;" />';
    }

    return '<svg viewBox="0 0 36 64" width="' + w + '" height="' + h + '" style="display:inline-block; vertical-align:middle; flex-shrink:0;">' + pathsHtml + '</svg>';
  }

  function createSvgColon(scale, isBlinkOn) {
    scale = scale || 1;
    var w = Math.round(16 * scale);
    var h = Math.round(64 * scale);
    var dotFill = isBlinkOn ? '#ffffff' : 'rgba(255,255,255,0.15)';
    var filter = isBlinkOn ? 'filter: drop-shadow(0 0 1.5px #ffffff) drop-shadow(0 0 5px rgba(255,255,255,0.85));' : '';

    return '<svg viewBox="0 0 16 64" width="' + w + '" height="' + h + '" style="display:inline-block; vertical-align:middle; flex-shrink:0;">' +
      // Top icon (alarm waves (((•))) from photo)
      '<path d="M 4,8 Q 8,4 12,8 M 5,11 Q 8,8 11,11 M 7,13 A 1 1 0 1 1 9,13" fill="none" stroke="rgba(255,255,255,0.28)" stroke-width="1.1" stroke-linecap="round"/>' +
      // Colon Dots
      '<circle cx="8" cy="23" r="3.2" fill="' + dotFill + '" style="' + filter + ' transition: fill 0.15s ease;" />' +
      '<circle cx="8" cy="41" r="3.2" fill="' + dotFill + '" style="' + filter + ' transition: fill 0.15s ease;" />' +
      // Bottom icon (snooze Zz indicator from photo)
      '<text x="8" y="55" text-anchor="middle" font-size="7" font-weight="bold" fill="rgba(255,255,255,0.28)" font-family="monospace">Zz</text>' +
    '</svg>';
  }

  // --------------------------------------------------------------------------
  // Core Clock HTML Generator
  // --------------------------------------------------------------------------
  function renderClockHtml(opts) {
    opts = opts || {};
    var isDesk = opts.isDesk || false;
    var now = new Date();
    var hours = now.getHours();
    var mins = now.getMinutes();
    var secs = now.getSeconds();
    var isBlinkOn = (secs % 2 === 0);

    var displayHours = hours;
    var ampm = '';
    if (!settings.is24Hour) {
      ampm = hours >= 12 ? 'PM' : 'AM';
      displayHours = hours % 12;
      if (displayHours === 0) displayHours = 12;
    }

    var hStr = String(displayHours).padStart(2, '0');
    var mStr = String(mins).padStart(2, '0');
    var sStr = String(secs).padStart(2, '0');

    // Sizing:
    // Header size: height ~36px, scale ~0.38
    // Desk size: height ~82px, scale ~0.84
    var scale = isDesk ? 0.84 : 0.38;
    var chassisW = isDesk ? 236 : 126;
    var chassisH = isDesk ? 82 : 36;
    var radius = isDesk ? 41 : 18;

    var digitsHtml =
      createSvgDigit(hStr[0], scale) +
      createSvgDigit(hStr[1], scale) +
      createSvgColon(scale, isBlinkOn) +
      createSvgDigit(mStr[0], scale) +
      createSvgDigit(mStr[1], scale);

    if (settings.showSeconds) {
      var secScale = scale * 0.62;
      digitsHtml +=
        '<div style="display:flex; flex-direction:column; margin-left:' + (isDesk ? '4px' : '2px') + '; align-self:center;">' +
          createSvgDigit(sStr[0], secScale) +
          createSvgDigit(sStr[1], secScale) +
        '</div>';
    }

    // Top physical button tabs (representing SET, ALARM, UP, DOWN, SNOOZE)
    var btnW = isDesk ? '15px' : '7.5px';
    var btnH = isDesk ? '3.5px' : '2px';
    var btnGap = isDesk ? '6px' : '3px';
    var topButtonsHtml =
      '<div style="position:absolute; top:-' + btnH + '; left:50%; transform:translateX(-50%); display:flex; gap:' + btnGap + '; z-index:4; pointer-events:none;">' +
        '<div style="width:' + btnW + '; height:' + btnH + '; background:linear-gradient(to bottom, #5d6373, #222630); border-radius:2px 2px 0 0; box-shadow:0 -0.5px 1px rgba(255,255,255,0.25);"></div>' +
        '<div style="width:' + btnW + '; height:' + btnH + '; background:linear-gradient(to bottom, #5d6373, #222630); border-radius:2px 2px 0 0; box-shadow:0 -0.5px 1px rgba(255,255,255,0.25);"></div>' +
        '<div style="width:' + (isDesk ? '26px' : '13px') + '; height:' + btnH + '; background:linear-gradient(to bottom, #6b7385, #2b303d); border-radius:2px 2px 0 0; box-shadow:0 -0.5px 1px rgba(255,255,255,0.35);"></div>' +
        '<div style="width:' + btnW + '; height:' + btnH + '; background:linear-gradient(to bottom, #5d6373, #222630); border-radius:2px 2px 0 0; box-shadow:0 -0.5px 1px rgba(255,255,255,0.25);"></div>' +
        '<div style="width:' + btnW + '; height:' + btnH + '; background:linear-gradient(to bottom, #5d6373, #222630); border-radius:2px 2px 0 0; box-shadow:0 -0.5px 1px rgba(255,255,255,0.25);"></div>' +
      '</div>';

    // 12H indicator if in 12H mode
    var ampmBadge = '';
    if (!settings.is24Hour && ampm) {
      ampmBadge = '<span style="position:absolute; right:' + (isDesk ? '14px' : '8px') + '; top:' + (isDesk ? '11px' : '5px') + '; font-size:' + (isDesk ? '9px' : '6px') + '; font-weight:700; color:rgba(255,255,255,0.7); font-family:monospace; letter-spacing:0.5px;">' + ampm + '</span>';
    }

    // Main capsule chassis with mirror styling
    var chassisHtml =
      '<div class="quivora-clock-chassis" style="' +
        'position:relative; ' +
        'width:' + chassisW + 'px; ' +
        'height:' + chassisH + 'px; ' +
        'border-radius:' + radius + 'px; ' +
        'background: linear-gradient(168deg, #383c48 0%, #181920 28%, #08090d 72%, #1e2028 100%); ' +
        'box-shadow: 0 ' + (isDesk ? '10px 28px' : '2px 8px') + ' rgba(0,0,0,0.88), inset 0 1px 2px rgba(255,255,255,0.42), inset 0 -2px 4px rgba(0,0,0,0.92); ' +
        'border: 1px solid rgba(255,255,255,0.16); ' +
        'display:flex; align-items:center; justify-content:center; ' +
        'cursor:pointer; user-select:none; ' +
        'transition: transform 0.15s ease, box-shadow 0.15s ease; ' +
        'overflow:visible;' +
      '">' +
        // Top physical buttons
        topButtonsHtml +
        // Inner mirror glass screen
        '<div style="' +
          'position:absolute; inset:' + (isDesk ? '3.5px' : '2px') + '; ' +
          'border-radius:' + (radius - 2) + 'px; ' +
          'background: radial-gradient(ellipse at 50% 120%, #12141c 0%, #040508 85%); ' +
          'box-shadow: inset 0 1.5px 5px rgba(0,0,0,0.95), inset 0 0 10px rgba(0,0,0,0.85); ' +
          'display:flex; align-items:center; justify-content:center; gap:' + (isDesk ? '3px' : '1px') + '; ' +
          'overflow:hidden;' +
        '">' +
          // Glowing white digits
          digitsHtml +
          ampmBadge +
          // Diagonal mirror reflection glare
          '<div style="' +
            'position:absolute; inset:0; pointer-events:none; ' +
            'background: linear-gradient(130deg, rgba(255,255,255,0.22) 0%, rgba(255,255,255,0.05) 32%, transparent 60%);' +
          '"></div>' +
        '</div>' +
      '</div>';

    // If desk mode, add the realistic tabletop mirror reflection underneath
    if (isDesk) {
      return (
        '<div style="display:flex; flex-direction:column; align-items:center; position:relative;">' +
          chassisHtml +
          // Tabletop mirror reflection
          '<div style="' +
            'width:' + chassisW + 'px; height:' + (chassisH * 0.45) + 'px; ' +
            'border-radius:' + radius + 'px; ' +
            'transform: scaleY(-1) translateY(4px); ' +
            'opacity: 0.28; filter: blur(1.5px); ' +
            'mask-image: linear-gradient(to top, transparent 15%, black 100%); ' +
            '-webkit-mask-image: linear-gradient(to top, transparent 15%, black 100%); ' +
            'pointer-events:none; overflow:hidden;' +
          '">' +
            chassisHtml +
          '</div>' +
        '</div>'
      );
    }

    return chassisHtml;
  }

  // --------------------------------------------------------------------------
  // Control Menu Popup
  // --------------------------------------------------------------------------
  var activeMenuEl = null;

  function closeMenu() {
    if (activeMenuEl) {
      activeMenuEl.remove();
      activeMenuEl = null;
    }
  }

  function openClockMenu(anchorRect) {
    closeMenu();

    var menu = document.createElement('div');
    menu.id = 'quivora-clock-menu';
    var topPos = Math.min(anchorRect.bottom + 8, window.innerHeight - 200);
    var leftPos = Math.max(16, Math.min(anchorRect.left - 40, window.innerWidth - 230));

    menu.style.cssText = [
      'position:fixed; z-index:999999;',
      'top:' + topPos + 'px;',
      'left:' + leftPos + 'px;',
      'width:220px;',
      'background:#111827; border:1px solid #334155; border-radius:12px;',
      'box-shadow:0 12px 30px rgba(0,0,0,0.7), 0 0 0 1px rgba(255,255,255,0.06);',
      'padding:10px; color:#e2e8f0; font-family:Inter,system-ui,sans-serif; font-size:12px;',
      'animation:quivoraMenuFadeIn 0.15s ease-out;'
    ].join(' ');

    var now = new Date();
    var dateStr = now.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });

    menu.innerHTML = [
      '<div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; padding-bottom:6px; border-bottom:1px solid #1e293b;">',
      '  <span style="font-weight:700; color:#f8fafc; font-size:11px; text-transform:uppercase; letter-spacing:0.5px;">LED Alarm Clock</span>',
      '  <span style="font-size:10px; color:#94a3b8;">' + dateStr + '</span>',
      '</div>',
      '<button id="clock-btn-format" style="width:100%; display:flex; align-items:center; justify-content:space-between; padding:7px 10px; margin-bottom:4px; background:#1e293b; border:1px solid #334155; border-radius:8px; color:#f1f5f9; cursor:pointer; font-size:11.5px; transition:all 0.15s;">',
      '  <span>Time Format</span>',
      '  <span style="font-weight:700; color:#818cf8;">' + (settings.is24Hour ? '24-Hour (22:08)' : '12-Hour (10:08 PM)') + '</span>',
      '</button>',
      '<button id="clock-btn-seconds" style="width:100%; display:flex; align-items:center; justify-content:space-between; padding:7px 10px; margin-bottom:4px; background:#1e293b; border:1px solid #334155; border-radius:8px; color:#f1f5f9; cursor:pointer; font-size:11.5px; transition:all 0.15s;">',
      '  <span>Show Seconds</span>',
      '  <span style="font-weight:700; color:' + (settings.showSeconds ? '#34d399' : '#94a3b8') + ';">' + (settings.showSeconds ? 'ON' : 'OFF') + '</span>',
      '</button>',
      '<button id="clock-btn-mode" style="width:100%; display:flex; align-items:center; justify-content:space-between; padding:7px 10px; background:#1e293b; border:1px solid #334155; border-radius:8px; color:#f1f5f9; cursor:pointer; font-size:11.5px; transition:all 0.15s;">',
      '  <span>Placement</span>',
      '  <span style="font-weight:700; color:#c084fc;">' + (settings.mode === 'floating' ? 'Desk Clock Mode' : 'Header Docked') + '</span>',
      '</button>'
    ].join('\n');

    document.body.appendChild(menu);
    activeMenuEl = menu;

    menu.querySelector('#clock-btn-format').onclick = function () {
      settings.is24Hour = !settings.is24Hour;
      saveSettings();
      updateAllClocks();
      openClockMenu(anchorRect);
    };

    menu.querySelector('#clock-btn-seconds').onclick = function () {
      settings.showSeconds = !settings.showSeconds;
      saveSettings();
      updateAllClocks();
      openClockMenu(anchorRect);
    };

    menu.querySelector('#clock-btn-mode').onclick = function () {
      settings.mode = (settings.mode === 'floating' ? 'header' : 'floating');
      saveSettings();
      syncFloatingWidget();
      updateAllClocks();
      closeMenu();
    };

    // Close on outside click
    setTimeout(function () {
      var handleOutside = function (ev) {
        if (!menu.contains(ev.target)) {
          closeMenu();
          document.removeEventListener('click', handleOutside);
        }
      };
      document.addEventListener('click', handleOutside);
    }, 50);
  }

  // --------------------------------------------------------------------------
  // Floating Desk Widget Manager
  // --------------------------------------------------------------------------
  var floatingContainer = null;

  function syncFloatingWidget() {
    if (settings.mode === 'floating') {
      if (!floatingContainer) {
        floatingContainer = document.createElement('div');
        floatingContainer.id = 'quivora-floating-desk-clock';
        floatingContainer.style.cssText = [
          'position:fixed; z-index:999990;',
          'right:24px; bottom:28px;',
          'cursor:grab;',
          'filter:drop-shadow(0 14px 28px rgba(0,0,0,0.85));',
          'transition:transform 0.15s ease;'
        ].join(' ');

        // Add drag & click handler
        var isDragging = false;
        var startX, startY, initLeft, initTop;

        floatingContainer.addEventListener('mousedown', function (e) {
          isDragging = false;
          startX = e.clientX;
          startY = e.clientY;
          var rect = floatingContainer.getBoundingClientRect();
          initLeft = rect.left;
          initTop = rect.top;

          var onMove = function (moveEv) {
            var dx = moveEv.clientX - startX;
            var dy = moveEv.clientY - startY;
            if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
              isDragging = true;
              floatingContainer.style.right = 'auto';
              floatingContainer.style.bottom = 'auto';
              floatingContainer.style.left = (initLeft + dx) + 'px';
              floatingContainer.style.top = (initTop + dy) + 'px';
            }
          };

          var onUp = function () {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
            if (!isDragging) {
              openClockMenu(floatingContainer.getBoundingClientRect());
            }
          };

          document.addEventListener('mousemove', onMove);
          document.addEventListener('mouseup', onUp);
        });

        document.body.appendChild(floatingContainer);
      }
      floatingContainer.style.display = 'block';
      floatingContainer.innerHTML = renderClockHtml({ isDesk: true });
    } else {
      if (floatingContainer) {
        floatingContainer.style.display = 'none';
      }
    }
  }

  // --------------------------------------------------------------------------
  // Update All Active Clock Instances
  // --------------------------------------------------------------------------
  function updateAllClocks() {
    // 1. Update all header clock elements
    var headerClocks = document.querySelectorAll('.quivora-header-clock-host');
    for (var i = 0; i < headerClocks.length; i++) {
      headerClocks[i].innerHTML = renderClockHtml({ isDesk: false });
    }

    // 2. Update floating desk clock if visible
    if (floatingContainer && settings.mode === 'floating') {
      floatingContainer.innerHTML = renderClockHtml({ isDesk: true });
    }
  }

  // Ticking loop: runs every 500ms for smooth colon blink and instant minute/second update
  setInterval(updateAllClocks, 500);

  // --------------------------------------------------------------------------
  // React Component: window.DigitalClock
  // --------------------------------------------------------------------------
  function DigitalClock(props) {
    var deps = window.__DocmindReact || null;
    var React = deps ? deps.React : null;

    if (React && React.useEffect && React.useRef) {
      var containerRef = React.useRef(null);

      React.useEffect(function () {
        var el = containerRef.current;
        if (!el) return;

        el.innerHTML = renderClockHtml({ isDesk: false });

        var handleClick = function (ev) {
          ev.stopPropagation();
          openClockMenu(el.getBoundingClientRect());
        };

        el.addEventListener('click', handleClick);
        return function () {
          el.removeEventListener('click', handleClick);
        };
      }, []);

      return (
        React.createElement('div', {
          ref: containerRef,
          className: 'quivora-header-clock-host flex items-center justify-center flex-shrink-0 cursor-pointer transition-transform hover:scale-105 active:scale-95',
          title: 'Quivora LED Digital Clock • Click for Settings',
          style: {
            margin: '0 4px',
            filter: 'drop-shadow(0 2px 6px rgba(0,0,0,0.4))'
          }
        })
      );
    }

    var host = document.createElement('div');
    host.className = 'quivora-header-clock-host flex items-center justify-center flex-shrink-0 cursor-pointer';
    host.innerHTML = renderClockHtml({ isDesk: false });
    host.onclick = function (e) {
      e.stopPropagation();
      openClockMenu(host.getBoundingClientRect());
    };
    return host;
  }

  window.DigitalClock = DigitalClock;
  window.__quivoraClock = {
    render: renderClockHtml,
    openMenu: openClockMenu,
    settings: settings,
    saveSettings: saveSettings
  };

  // --------------------------------------------------------------------------
  // Global Header Injection & Fallback Guardian
  // Ensures that across all pages (Home, Chat, Search, Chunks, Quiz, Growth),
  // the clock is dynamically anchored right in the header bar!
  // --------------------------------------------------------------------------
  function ensureHeaderClock() {
    var headerRight = document.querySelector('header .justify-end');
    if (!headerRight) return;

    var existing = headerRight.querySelector('.quivora-header-clock-host');
    if (!existing) {
      var host = document.createElement('div');
      host.className = 'quivora-header-clock-host flex items-center justify-center flex-shrink-0 cursor-pointer transition-transform hover:scale-105 active:scale-95';
      host.title = 'Quivora LED Digital Clock • Click for Settings';
      host.style.cssText = 'margin: 0 6px; filter: drop-shadow(0 2px 6px rgba(0,0,0,0.5));';
      host.innerHTML = renderClockHtml({ isDesk: false });
      host.onclick = function (e) {
        e.stopPropagation();
        openClockMenu(host.getBoundingClientRect());
      };

      // Insert before the Upload button
      var uploadBtn = headerRight.querySelector('button');
      if (uploadBtn) {
        headerRight.insertBefore(host, uploadBtn);
      } else {
        headerRight.appendChild(host);
      }
    }
  }

  // DOM ready check & observer
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      syncFloatingWidget();
      ensureHeaderClock();
    });
  } else {
    syncFloatingWidget();
    ensureHeaderClock();
  }

  var obs = new MutationObserver(function () {
    ensureHeaderClock();
  });
  obs.observe(document.body, { childList: true, subtree: true });

})();
