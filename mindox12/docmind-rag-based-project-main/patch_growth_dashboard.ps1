# ==============================================================================
# Precision Patch Script for Growth Dashboard: Course Deadline & Red Alert
# ==============================================================================

$sourcePath = 'frontend/docmind-frontend/dist/assets/growth-dashboard.js.bak'
$destPath   = 'frontend/docmind-frontend/dist/assets/growth-dashboard.js'

$content = [System.IO.File]::ReadAllText($sourcePath, [System.Text.Encoding]::UTF8)

# Normalize CRLF to LF in content for uniform matching
$hasCRLF = $content.Contains("`r`n")
if ($hasCRLF) {
    $content = $content.Replace("`r`n", "`n")
}

# ------------------------------------------------------------------------------
# 1. Add handleUpdateCourseDeadline after handleUpdateCourseDailyHours
# ------------------------------------------------------------------------------
$target1 = @'
      toast?.success?.(`Study pace set to ${h}h/day. Completion date recalculated!`);
    };

    // Update topic estimated hours (Persisted to PostgreSQL)
'@.Replace("`r`n", "`n")

$replacement1 = @'
      toast?.success?.(`Study pace set to ${h}h/day. Completion date recalculated!`);
    };

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

    // Update topic estimated hours (Persisted to PostgreSQL)
'@.Replace("`r`n", "`n")

if ($content.Contains($target1)) {
    $content = $content.Replace($target1, $replacement1)
    Write-Output "Applied Patch 1: handleUpdateCourseDeadline"
} else {
    Write-Error "Failed to find Target 1 in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 2. Update getCourseForecast with targetDeadline setup
# ------------------------------------------------------------------------------
$target2 = @'
      const finishDateFormatted = finishDate.toLocaleDateString("en-US", {
        weekday: "short",
        day: "numeric",
        month: "short",
        year: "numeric"
      });

      // Topic-by-topic milestone target dates (Rolling dynamically from today + offsetDays)
'@.Replace("`r`n", "`n")

$replacement2 = @'
      const finishDateFormatted = finishDate.toLocaleDateString("en-US", {
        weekday: "short",
        day: "numeric",
        month: "short",
        year: "numeric"
      });

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

      // Topic-by-topic milestone target dates (Rolling dynamically from today + offsetDays)
'@.Replace("`r`n", "`n")

if ($content.Contains($target2)) {
    $content = $content.Replace($target2, $replacement2)
    Write-Output "Applied Patch 2a: targetDeadline parsing in getCourseForecast"
} else {
    Write-Error "Failed to find Target 2 in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 2b. Add isPastDeadline and delay calculation
# ------------------------------------------------------------------------------
$target2b = @'
          const targetDateKey = `${y}-${m}-${day}`;
          topicSchedules[t.id || idx] = {
            isCompleted: false,
            dayOffset,
            hours: h,
            isRescheduled: offsetDays > 0,
            targetDate: tDate,
            targetDateKey,
            dateFormatted: tDate.toLocaleDateString("en-US", {
              weekday: "short",
              day: "numeric",
              month: "short"
            })
          };
        }
      });

      // Target deadline comparison
      let deadlineInfo = null;
      if (course.targetDeadline || course.deadline) {
        try {
          const targetD = new Date(course.targetDeadline || course.deadline);
          const diffMs = targetD.getTime() - finishDate.getTime();
          const diffDays = Math.round(diffMs / (1000 * 60 * 60 * 24));
          deadlineInfo = {
            deadlineStr: targetD.toLocaleDateString("en-US", { day: "numeric", month: "short", year: "numeric" }),
            diffDays,
            isAhead: diffDays >= 0,
            isBehind: diffDays < 0
          };
        } catch (e) {}
      }
'@.Replace("`r`n", "`n")

$replacement2b = @'
          const targetDateKey = `${y}-${m}-${day}`;
          const isPastDeadline = targetDateZero ? tDate.getTime() > targetDateZero.getTime() : false;
          topicSchedules[t.id || idx] = {
            isCompleted: false,
            dayOffset,
            hours: h,
            isRescheduled: offsetDays > 0,
            isPastDeadline,
            targetDate: tDate,
            targetDateKey,
            dateFormatted: tDate.toLocaleDateString("en-US", {
              weekday: "short",
              day: "numeric",
              month: "short"
            })
          };
        }
      });

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

if ($content.Contains($target2b)) {
    $content = $content.Replace($target2b, $replacement2b)
    Write-Output "Applied Patch 2b: topic isPastDeadline and delay calculation"
} else {
    Write-Error "Failed to find Target 2b in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 3. Add DELAYED badge to Course Title Header
# ------------------------------------------------------------------------------
$target3 = @'
                                               jsxs("div", {
                                                 className: "flex items-center gap-2.5 flex-wrap",
                                                 children: [
                                                   jsx("h3", { className: "text-base font-extrabold text-white tracking-tight", children: course.title }),
                                                   jsx("span", {
                                                     className: "text-[10px] px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 font-bold",
                                                     children: course.category || "General"
                                                   })
                                                 ]
                                               }),
'@.Replace("`r`n", "`n")

$replacement3 = @'
                                               jsxs("div", {
                                                 className: "flex items-center gap-2.5 flex-wrap",
                                                 children: [
                                                   jsx("h3", { className: "text-base font-extrabold text-white tracking-tight", children: course.title }),
                                                   jsx("span", {
                                                     className: "text-[10px] px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 font-bold",
                                                     children: course.category || "General"
                                                   }),
                                                   forecast?.deadlineInfo?.isDelayed
                                                     ? jsx("span", {
                                                         className: "text-[10px] px-2 py-0.5 rounded-full bg-red-500/25 text-red-300 border border-red-500/50 font-black animate-pulse shadow-sm shadow-red-500/20 flex items-center gap-1",
                                                         children: `🚨 DELAYED (+${forecast.deadlineInfo.delayDays}d)`
                                                       })
                                                     : null
                                                 ]
                                               }),
'@.Replace("`r`n", "`n")

if ($content.Contains($target3)) {
    $content = $content.Replace($target3, $replacement3)
    Write-Output "Applied Patch 3: Course Title DELAYED badge"
} else {
    Write-Error "Failed to find Target 3 in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 4. Interactive Target Deadline date input in Progress Bar row
# ------------------------------------------------------------------------------
$target4 = @'
                                               jsxs("span", {
                                                 children: [
                                                   "Pace: ",
                                                   jsx("strong", { className: "text-slate-200", children: `${forecast.pace}h/day` }),
                                                   " • Deadline: ",
                                                   jsx("strong", { className: "text-slate-200", children: course.targetDeadline || course.deadline || "2026-12-15" })
                                                 ]
                                               })
'@.Replace("`r`n", "`n")

$replacement4 = @'
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

if ($content.Contains($target4)) {
    $content = $content.Replace($target4, $replacement4)
    Write-Output "Applied Patch 4: Interactive Deadline Date input"
} else {
    Write-Error "Failed to find Target 4 in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 5. Insert HIGH-IMPACT RED ALERT BOX in Forecast container (Right above Pace Selector)
# ------------------------------------------------------------------------------
$target5 = @'
                                           // Interactive Daily Study Pace Selector (1h, 2h, 3h, 4h, custom)
'@.Replace("`r`n", "`n")

$replacement5 = @'
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

                                           // Interactive Daily Study Pace Selector (1h, 2h, 3h, 4h, custom)
'@.Replace("`r`n", "`n")

if ($content.Contains($target5)) {
    $content = $content.Replace($target5, $replacement5)
    Write-Output "Applied Patch 5: HIGH-IMPACT RED ALERT BANNER"
} else {
    Write-Error "Failed to find Target 5 in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 6. Replace old tiny deadline indicator inside pace selector row
# ------------------------------------------------------------------------------
$target6 = @'
                                               // Target Deadline Status Indicator
                                               forecast.deadlineInfo &&
                                                 jsxs("div", {
                                                   className: "flex items-center gap-1.5 text-[11px]",
                                                   children: [
                                                     jsx("span", { children: forecast.deadlineInfo.isAhead ? "🟢" : "⚠️" }),
                                                     jsx("span", {
                                                       className: forecast.deadlineInfo.isAhead ? "text-emerald-400 font-semibold" : "text-amber-400 font-semibold",
                                                       children: forecast.deadlineInfo.isAhead
                                                         ? `${forecast.deadlineInfo.diffDays} din pehle finish (Deadline: ${forecast.deadlineInfo.deadlineStr})`
                                                         : `${Math.abs(forecast.deadlineInfo.diffDays)} din late (Deadline: ${forecast.deadlineInfo.deadlineStr})`
                                                     })
                                                   ]
                                                 }),
'@.Replace("`r`n", "`n")

$replacement6 = @'
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

if ($content.Contains($target6)) {
    $content = $content.Replace($target6, $replacement6)
    Write-Output "Applied Patch 6: Deadline summary indicator in pace row"
} else {
    Write-Error "Failed to find Target 6 in source file!"
    exit 1
}

# ------------------------------------------------------------------------------
# 7. Add isPastDeadline indicator to topic milestone row
# ------------------------------------------------------------------------------
$target7 = @'
                                                                               jsxs("span", {
                                                                                 className: `font-bold px-2 py-0.5 rounded-md border flex items-center gap-1 ${
                                                                                   topicForecast?.isRescheduled
                                                                                     ? "text-amber-300 bg-amber-500/15 border-amber-500/30"
                                                                                     : "text-teal-300 bg-teal-500/10 border-teal-500/25"
                                                                                 }`,
                                                                                 children: [
                                                                                   jsx("span", { children: "📅 Target:" }),
                                                                                   jsx("span", { className: "text-white font-extrabold", children: topicForecast?.dateFormatted || "Upcoming" }),
                                                                                   topicForecast?.dayOffset
                                                                                     ? jsx("span", {
                                                                                         className: topicForecast?.isRescheduled ? "text-amber-400 font-medium" : "text-teal-400 font-normal",
                                                                                         children: `(Day ${topicForecast.dayOffset})`
                                                                                       })
                                                                                     : null,
                                                                                   topicForecast?.isRescheduled
                                                                                     ? jsx("span", { className: "text-[9px] bg-amber-500/30 text-amber-200 px-1 py-0.2 rounded font-bold ml-0.5", children: "Shifted" })
                                                                                     : null
                                                                                 ]
                                                                               }),
'@.Replace("`r`n", "`n")

$replacement7 = @'
                                                                               jsxs("span", {
                                                                                 className: `font-bold px-2 py-0.5 rounded-md border flex items-center gap-1 ${
                                                                                   topicForecast?.isPastDeadline
                                                                                     ? "text-red-300 bg-red-500/25 border-red-500/50 animate-pulse"
                                                                                     : topicForecast?.isRescheduled
                                                                                     ? "text-amber-300 bg-amber-500/15 border-amber-500/30"
                                                                                     : "text-teal-300 bg-teal-500/10 border-teal-500/25"
                                                                                 }`,
                                                                                 children: [
                                                                                   jsx("span", { children: topicForecast?.isPastDeadline ? "🚨 Target:" : "📅 Target:" }),
                                                                                   jsx("span", { className: "text-white font-extrabold", children: topicForecast?.dateFormatted || "Upcoming" }),
                                                                                   topicForecast?.dayOffset
                                                                                     ? jsx("span", {
                                                                                         className: topicForecast?.isPastDeadline ? "text-red-300 font-bold" : topicForecast?.isRescheduled ? "text-amber-400 font-medium" : "text-teal-400 font-normal",
                                                                                         children: `(Day ${topicForecast.dayOffset})`
                                                                                       })
                                                                                     : null,
                                                                                   topicForecast?.isPastDeadline
                                                                                     ? jsx("span", { className: "text-[9px] bg-red-600 text-white px-1.5 py-0.2 rounded font-black ml-0.5 shadow-sm", children: "Past Deadline" })
                                                                                     : topicForecast?.isRescheduled
                                                                                     ? jsx("span", { className: "text-[9px] bg-amber-500/30 text-amber-200 px-1 py-0.2 rounded font-bold ml-0.5", children: "Shifted" })
                                                                                     : null
                                                                                 ]
                                                                               }),
'@.Replace("`r`n", "`n")

if ($content.Contains($target7)) {
    $content = $content.Replace($target7, $replacement7)
    Write-Output "Applied Patch 7: Topic isPastDeadline indicator"
} else {
    Write-Error "Failed to find Target 7 in source file!"
    exit 1
}

# Restore CRLF if source had CRLF
if ($hasCRLF) {
    $content = $content.Replace("`n", "`r`n")
}

# ------------------------------------------------------------------------------
# Save modified file in full UTF8
# ------------------------------------------------------------------------------
[System.IO.File]::WriteAllText($destPath, $content, [System.Text.Encoding]::UTF8)
Write-Output "SUCCESS: Saved patched file to $destPath"
