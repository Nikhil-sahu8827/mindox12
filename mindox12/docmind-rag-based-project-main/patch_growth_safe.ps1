# ==============================================================================
# Robust Index-Based Safe Patcher for Growth Dashboard
# ==============================================================================

$sourcePath = 'frontend/docmind-frontend/dist/assets/growth-dashboard.js.bak'
$destPath   = 'frontend/docmind-frontend/dist/assets/growth-dashboard.js'

$txt = [System.IO.File]::ReadAllText($sourcePath, [System.Text.Encoding]::UTF8)

# Normalize CRLF to LF
$hasCRLF = $txt.Contains("`r`n")
if ($hasCRLF) {
    $txt = $txt.Replace("`r`n", "`n")
}

Write-Output "Initial length: $($txt.Length)"

# ------------------------------------------------------------------------------
# 1. Add handleUpdateCourseDeadline after handleUpdateCourseDailyHours
# ------------------------------------------------------------------------------
$p1 = $txt.IndexOf('Completion date recalculated!')
if ($p1 -lt 0) { throw "Cannot find handleUpdateCourseDailyHours toast" }
$p1End = $txt.IndexOf("};", $p1) + 2

$funcDeadline = @'


    // Update Course Target Deadline (Persisted to PostgreSQL)
    const handleUpdateCourseDeadline = async (courseId, newDeadlineStr) => {
      if (!newDeadlineStr) return;
      setStudyData((prev) => {
        if (!prev || !prev.courses) return prev;
        const updated = prev.courses.map((c) =>
          c.id === courseId ? { ...c, targetDeadline: newDeadlineStr, deadline: newDeadlineStr } : c
        );
        return { ...prev, courses: updated };
      });

      try {
        const token = getToken();
        if (token) {
          const course = studyData?.courses?.find((c) => c.id === courseId);
          await fetch(`/api/v1/study/courses/${courseId}`, {
            method: "PUT",
            headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              title: course?.title || "Course",
              category: course?.category || "Computer Science",
              dailyTargetHours: parseFloat(courseDailyHours[courseId]) || parseFloat(course?.dailyTargetHours) || 2.0,
              targetDeadline: newDeadlineStr
            })
          });
        }
      } catch (e) {
        console.warn("Deadline sync error:", e);
      }
      toast?.success?.(`🎯 Target deadline updated to ${newDeadlineStr}. Course forecast updated!`);
    };
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p1End) + $funcDeadline + $txt.Substring($p1End)
Write-Output "1. Added handleUpdateCourseDeadline"

# ------------------------------------------------------------------------------
# 2. Add targetDeadline setup before topicSchedules in getCourseForecast
# ------------------------------------------------------------------------------
$p2 = $txt.IndexOf("const finishDateFormatted = finishDate.toLocaleDateString")
if ($p2 -lt 0) { throw "Cannot find finishDateFormatted" }
$p2Close = $txt.IndexOf("});", $p2) + 3

$deadlineSetup = @'


      // Target deadline setup & comparison
      const targetDeadlineRaw = course.targetDeadline || course.deadline || "2026-12-15";
      let targetD = null;
      let targetDateZero = null;
      try {
        const parts = targetDeadlineRaw.split("-");
        targetD = parts.length === 3
          ? new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10), 23, 59, 59)
          : new Date(targetDeadlineRaw);
        targetDateZero = new Date(targetD);
        targetDateZero.setHours(0, 0, 0, 0);
      } catch (e) {}
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p2Close) + $deadlineSetup + $txt.Substring($p2Close)
Write-Output "2. Added targetDeadline setup in getCourseForecast"

# ------------------------------------------------------------------------------
# 3. Add isPastDeadline inside topicSchedules[t.id || idx]
# ------------------------------------------------------------------------------
$p3 = $txt.IndexOf("topicSchedules[t.id || idx] = {")
if ($p3 -lt 0) { throw "Cannot find topicSchedules entry" }
$p3TargetKey = $txt.IndexOf("const targetDateKey =", $p3 - 300)
if ($p3TargetKey -lt 0) { throw "Cannot find targetDateKey" }
$p3TargetKeyEnd = $txt.IndexOf(";", $p3TargetKey) + 1

$pastDeadlineCalc = @'

          const isPastDeadline = targetDateZero ? tDate.getTime() > targetDateZero.getTime() : false;
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p3TargetKeyEnd) + $pastDeadlineCalc + $txt.Substring($p3TargetKeyEnd)

# Now insert `isPastDeadline,` inside topicSchedules object
$p3Obj = $txt.IndexOf("isRescheduled: offsetDays > 0,", $p3TargetKeyEnd)
if ($p3Obj -lt 0) { throw "Cannot find isRescheduled in topicSchedules" }
$p3ObjEnd = $txt.IndexOf("`n", $p3Obj) + 1

$isPastProp = @'
            isPastDeadline,
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p3ObjEnd) + $isPastProp + "`n" + $txt.Substring($p3ObjEnd)
Write-Output "3. Added isPastDeadline to topicSchedules"

# ------------------------------------------------------------------------------
# 4. Replace deadline comparison block with comprehensive delay calculation
# ------------------------------------------------------------------------------
$p4 = $txt.IndexOf("// Target deadline comparison")
if ($p4 -lt 0) { throw "Cannot find Target deadline comparison comment" }
$p4End = $txt.IndexOf("return {", $p4)
if ($p4End -lt 0) { throw "Cannot find return statement in getCourseForecast" }

$fullDeadlineCalc = @'
// Target deadline comparison & delay calculation
      let deadlineInfo = null;
      if (targetD && targetDateZero) {
        try {
          const finishDateZero = new Date(finishDate);
          finishDateZero.setHours(0, 0, 0, 0);

          const todayZero = new Date(today);
          todayZero.setHours(0, 0, 0, 0);

          const diffMs = targetDateZero.getTime() - finishDateZero.getTime();
          const diffDays = Math.round(diffMs / (1000 * 60 * 60 * 24));
          const daysToDeadline = Math.round((targetDateZero.getTime() - todayZero.getTime()) / (1000 * 60 * 60 * 24));

          const isAllDone = topics.length > 0 && pendingTopics.length === 0;
          const isDelayed = !isAllDone && (diffDays < 0 || daysToDeadline < 0 || (offsetDays > 0 && diffDays < 0));
          const delayDays = isDelayed ? Math.max(1, Math.abs(diffDays)) : 0;

          let requiredPace = null;
          if (isDelayed && daysToDeadline > 0 && remainingHours > 0) {
            requiredPace = Math.round((remainingHours / daysToDeadline) * 10) / 10;
          }

          deadlineInfo = {
            rawDeadline: targetDeadlineRaw,
            targetD,
            targetDateZero,
            deadlineStr: targetD.toLocaleDateString("en-US", { day: "numeric", month: "short", year: "numeric" }),
            diffDays,
            daysToDeadline,
            isDelayed,
            delayDays,
            isAhead: diffDays >= 0 && !isDelayed,
            isBehind: isDelayed,
            requiredPace,
            isDeadlinePassed: daysToDeadline < 0 && !isAllDone
          };
        } catch (e) {}
      }

      
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p4) + $fullDeadlineCalc + $txt.Substring($p4End)
Write-Output "4. Replaced deadlineInfo with comprehensive delay calculation"

# ------------------------------------------------------------------------------
# 5. Course title header: add DELAYED badge next to category
# ------------------------------------------------------------------------------
$p5 = $txt.LastIndexOf('children: course.category || "General"')
if ($p5 -lt 0) { throw "Cannot find course.category" }
# Find the closing of this jsx("span", ...)
$p5SpanEnd = $txt.IndexOf("})", $p5) + 2

$delayedBadge = @'
,
                                                   forecast?.deadlineInfo?.isDelayed
                                                     ? jsx("span", {
                                                         className: "text-[10px] px-2 py-0.5 rounded-full bg-red-500/25 text-red-300 border border-red-500/50 font-black animate-pulse shadow-sm shadow-red-500/20 flex items-center gap-1",
                                                         children: `🚨 DELAYED (+${forecast.deadlineInfo.delayDays}d)`
                                                       })
                                                     : null
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p5SpanEnd) + $delayedBadge + $txt.Substring($p5SpanEnd)
Write-Output "5. Added DELAYED badge to course title header"

# ------------------------------------------------------------------------------
# 6. Interactive Target Deadline input in Progress Bar row
# ------------------------------------------------------------------------------
$p6 = $txt.IndexOf('" topics covered"')
if ($p6 -lt 0) { throw "Cannot find topics covered" }
$p6Pace = $txt.IndexOf('jsxs("span", {', $p6)
if ($p6Pace -lt 0) { throw "Cannot find Pace span" }
# Find closing of this jsxs("span", ...)
$p6PaceEnd = $txt.IndexOf('2026-12-15', $p6Pace)
if ($p6PaceEnd -lt 0) { throw "Cannot find targetDeadline fallback in progress row" }
$p6StrongClose = $txt.IndexOf('})', $p6PaceEnd) + 2
$p6Close = $txt.IndexOf('})', $p6StrongClose) + 2

$replacementPace = @'
jsxs("div", {
                                                 className: "flex items-center gap-1.5 text-[11px]",
                                                 onClick: (e) => e.stopPropagation(),
                                                 children: [
                                                   jsx("span", { children: "Pace: " }),
                                                   jsx("strong", { className: "text-slate-200 mr-1.5", children: `${forecast.pace}h/day` }),
                                                   jsx("span", { children: "• Deadline: " }),
                                                   jsx("input", {
                                                     type: "date",
                                                     value: course.targetDeadline || course.deadline || "2026-12-15",
                                                     onChange: (e) => handleUpdateCourseDeadline(course.id, e.target.value),
                                                     className: "bg-slate-900 border border-slate-700 rounded px-1.5 py-0.5 text-white font-semibold text-[11px] focus:outline-none focus:border-indigo-500 cursor-pointer"
                                                   })
                                                 ]
                                               })
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p6Pace) + $replacementPace + $txt.Substring($p6Close)
Write-Output "6. Added interactive deadline input in progress row"

# ------------------------------------------------------------------------------
# 7. Insert HIGH-IMPACT RED ALERT BOX in Forecast container (Right above Pace Selector)
# ------------------------------------------------------------------------------
$p7 = $txt.IndexOf("// Interactive Daily Study Pace Selector")
if ($p7 -lt 0) { throw "Cannot find Interactive Daily Study Pace Selector comment" }

$redAlertBanner = @'
// -------------------------------------------------------------
                                           // HIGH-IMPACT COURSE DEADLINE DELAY RED ALERT BANNER
                                           // -------------------------------------------------------------
                                           forecast.deadlineInfo
                                             ? forecast.deadlineInfo.isDelayed
                                               ? jsxs("div", {
                                                   className: "p-3.5 sm:p-4 rounded-2xl bg-gradient-to-br from-red-950/90 via-[#2a080c]/85 to-red-950/90 border-2 border-red-500/80 shadow-2xl shadow-red-900/50 space-y-3 cursor-default select-text ring-2 ring-red-500/20",
                                                   children: [
                                                     jsxs("div", {
                                                       className: "flex items-center justify-between flex-wrap gap-2",
                                                       children: [
                                                         jsxs("div", {
                                                           className: "flex items-center gap-2 flex-wrap",
                                                           children: [
                                                             jsx("span", { className: "text-xl animate-bounce", children: "🚨" }),
                                                             jsx("h5", {
                                                               className: "text-xs font-black text-red-200 tracking-wider uppercase flex items-center gap-1.5",
                                                               children: "CRITICAL DEADLINE DELAY ALERT!"
                                                             }),
                                                             jsx("span", {
                                                               className: "text-[10px] font-black px-2.5 py-0.5 rounded-full bg-red-600 text-white shadow-md animate-pulse",
                                                               children: `+${forecast.deadlineInfo.delayDays} DIN DELAY`
                                                             })
                                                           ]
                                                         }),
                                                         forecast.deadlineInfo.requiredPace
                                                           ? jsxs("button", {
                                                               onClick: (e) => {
                                                                 e.stopPropagation();
                                                                 handleUpdateCourseDailyHours(course.id, forecast.deadlineInfo.requiredPace);
                                                               },
                                                               className: "px-3 py-1.5 rounded-xl bg-gradient-to-r from-red-600 to-rose-600 hover:from-red-500 hover:to-rose-500 text-white font-extrabold text-xs shadow-lg shadow-red-600/40 transition cursor-pointer active:scale-95 flex items-center gap-1.5",
                                                               title: "Deadline meet karne ke liye daily study time badhayein",
                                                               children: [
                                                                 jsx("span", { children: "⚡" }),
                                                                 jsx("span", { children: `Catch-Up: Set ${forecast.deadlineInfo.requiredPace}h/day` })
                                                               ]
                                                             })
                                                           : null
                                                       ]
                                                     }),
                                                     jsxs("div", {
                                                       className: "grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs",
                                                       children: [
                                                         jsxs("div", {
                                                           className: "p-2.5 rounded-xl bg-slate-950/80 border border-red-500/40 flex items-center justify-between",
                                                           children: [
                                                             jsx("span", { className: "text-slate-400 font-semibold", children: "🎯 Target Deadline:" }),
                                                             jsx("span", { className: "font-extrabold text-amber-300 text-sm", children: forecast.deadlineInfo.deadlineStr })
                                                           ]
                                                         }),
                                                         jsxs("div", {
                                                           className: "p-2.5 rounded-xl bg-red-950/60 border border-red-500/60 flex items-center justify-between",
                                                           children: [
                                                             jsx("span", { className: "text-red-300 font-semibold", children: "⏩ Nayi Extended Finish Date:" }),
                                                             jsx("span", { className: "font-black text-red-100 text-sm underline decoration-red-400", children: forecast.finishDateFormatted })
                                                           ]
                                                         })
                                                       ]
                                                     }),
                                                     jsxs("div", {
                                                       className: "p-2 rounded-xl bg-red-950/40 border border-red-500/30 flex items-center gap-2 text-[11px] text-red-200",
                                                       children: [
                                                         jsx("span", { children: "⚠️" }),
                                                         jsx("span", {
                                                           children: `Aapki target deadline (${forecast.deadlineInfo.deadlineStr}) se course ${forecast.deadlineInfo.delayDays} din delay chal raha hai! Rozana ${forecast.pace}h ke hisaab se nayi completion date aage badhkar ${forecast.finishDateFormatted} ho gayi hai.`
                                                         })
                                                       ]
                                                     })
                                                   ]
                                                 })
                                               : jsxs("div", {
                                                   className: "p-3 rounded-2xl bg-gradient-to-r from-emerald-950/50 via-slate-950/70 to-emerald-950/50 border border-emerald-500/40 flex items-center justify-between flex-wrap gap-2 text-xs",
                                                   children: [
                                                     jsxs("div", {
                                                       className: "flex items-center gap-2 text-emerald-300",
                                                       children: [
                                                         jsx("span", { className: "text-base", children: "🟢" }),
                                                         jsx("span", { className: "font-extrabold", children: "Target Deadline On Track:" }),
                                                         jsx("span", { className: "text-slate-200 font-bold", children: forecast.deadlineInfo.deadlineStr })
                                                       ]
                                                     }),
                                                     jsx("span", {
                                                       className: "text-[11px] font-black px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/40",
                                                       children: `${forecast.deadlineInfo.diffDays} Din Pehle Finish Hoga! 🎉`
                                                     })
                                                   ]
                                                 })
                                             : null,

                                           
'@.Replace("`r`n", "`n")

$txt = $txt.Substring(0, $p7) + $redAlertBanner + $txt.Substring($p7)
Write-Output "7. Added HIGH-IMPACT RED ALERT BANNER"

# ------------------------------------------------------------------------------
# 8. Replace old tiny deadline indicator inside pace row with summary
# ------------------------------------------------------------------------------
$p8 = $txt.IndexOf("// Target Deadline Status Indicator")
if ($p8 -ge 0) {
    $p8End = $txt.IndexOf("// 4th Row: Smart Dynamic Auto-Reschedule", $p8)
    $replacementSummary = @'
// Target Deadline Summary Indicator
                                               forecast.deadlineInfo &&
                                                 jsxs("div", {
                                                   className: "flex items-center gap-1.5 text-[11px]",
                                                   children: [
                                                     jsx("span", { children: forecast.deadlineInfo.isDelayed ? "🚨" : "🟢" }),
                                                     jsx("span", {
                                                       className: forecast.deadlineInfo.isDelayed ? "text-red-400 font-bold" : "text-emerald-400 font-semibold",
                                                       children: forecast.deadlineInfo.isDelayed
                                                         ? `+${forecast.deadlineInfo.delayDays} din delay (Deadline: ${forecast.deadlineInfo.deadlineStr})`
                                                         : `${forecast.deadlineInfo.diffDays} din pehle finish (Deadline: ${forecast.deadlineInfo.deadlineStr})`
                                                     })
                                                   ]
                                                 }),

                                            
'@.Replace("`r`n", "`n")
    $txt = $txt.Substring(0, $p8) + $replacementSummary + $txt.Substring($p8End)
    Write-Output "8. Replaced deadline indicator in pace row"
}

# ------------------------------------------------------------------------------
# 9. Add isPastDeadline highlight to individual topic milestone target badge
# ------------------------------------------------------------------------------
$p9 = $txt.IndexOf('topicForecast?.isRescheduled ? "text-amber-300 bg-amber-500/15 border-amber-500/30"')
if ($p9 -ge 0) {
    $p9Rep = 'topicForecast?.isPastDeadline ? "text-red-300 bg-red-500/25 border-red-500/50 animate-pulse" : topicForecast?.isRescheduled ? "text-amber-300 bg-amber-500/15 border-amber-500/30"'
    $txt = $txt.Substring(0, $p9) + $p9Rep + $txt.Substring($p9 + 'topicForecast?.isRescheduled ? "text-amber-300 bg-amber-500/15 border-amber-500/30"'.Length)
    Write-Output "9. Added topic milestone isPastDeadline style"
}

# Also update label in topic target
$p9Icon = $txt.IndexOf('jsx("span", { children: "\uD83D\uDCC5 Target:" })')
if ($p9Icon -ge 0) {
    $p9IconRep = 'jsx("span", { children: topicForecast?.isPastDeadline ? "🚨 Target:" : "\uD83D\uDCC5 Target:" })'
    $txt = $txt.Substring(0, $p9Icon) + $p9IconRep + $txt.Substring($p9Icon + 'jsx("span", { children: "\uD83D\uDCC5 Target:" })'.Length)
    Write-Output "9b. Added topic milestone isPastDeadline icon"
}

# Restore CRLF if source had CRLF
if ($hasCRLF) {
    $txt = $txt.Replace("`n", "`r`n")
}

# ------------------------------------------------------------------------------
# Save modified file in full UTF8
# ------------------------------------------------------------------------------
[System.IO.File]::WriteAllText($destPath, $txt, [System.Text.Encoding]::UTF8)
Write-Output "SUCCESS: Saved patched file to $destPath (Length: $($txt.Length))"
