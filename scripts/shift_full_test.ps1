# Full shift module API suite (Phases 1-5) with latency checks.
$ErrorActionPreference = "Continue"
$base = "http://localhost:8080/api/v1"
$plain = "Test@1234"
$companyId = 2
$slowMs = 1500
$results = @()

function Do-Login([string]$mobile) {
  $body = @{ mobile = $mobile; password = $plain } | ConvertTo-Json
  return [string](Invoke-RestMethod -Uri "$base/auth/login" -Method POST -ContentType "application/json" -Body $body).data.accessToken
}

function AuthHdr([string]$token) { @{ Authorization = ("Bearer " + $token) } }

function Call-Api {
  param(
    [string]$Name,
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers,
    [string]$Body = $null,
    [int[]]$ExpectStatus = @(200, 201),
    [scriptblock]$Assert = $null
  )
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $status = 0
  $data = $null
  $err = $null
  try {
    if ($Body) {
      $resp = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -ContentType "application/json" -Body $Body -UseBasicParsing
    } else {
      $resp = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -UseBasicParsing
    }
    $status = [int]$resp.StatusCode
    $data = $resp.Content | ConvertFrom-Json
  } catch {
    $r = $_.Exception.Response
    if ($r) {
      $status = [int]$r.StatusCode
      try {
        $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
        $raw = $reader.ReadToEnd()
        if ($raw) { $data = $raw | ConvertFrom-Json }
      } catch {}
    }
    $err = $_.Exception.Message
  }
  $sw.Stop()
  $ms = [int]$sw.ElapsedMilliseconds
  $ok = $ExpectStatus -contains $status
  $assertMsg = ""
  if ($ok -and $Assert) {
    try {
      & $Assert $data $status
    } catch {
      $ok = $false
      $assertMsg = $_.Exception.Message
    }
  }
  $slow = $ms -ge $slowMs
  $row = [pscustomobject]@{
    Name = $Name
    Status = $status
    Ms = $ms
    Ok = $ok
    Slow = $slow
    Detail = $(if ($assertMsg) { $assertMsg } elseif (-not $ok) { $err } else { "" })
  }
  $script:results += $row
  $mark = if ($ok) { "PASS" } else { "FAIL" }
  $slowMark = if ($slow) { " SLOW" } else { "" }
  Write-Host ("[{0}] {1} {2}ms status={3}{4} {5}" -f $mark, $Name, $ms, $status, $slowMark, $row.Detail)
  return $data
}

$adminTok = Do-Login "9000000001"
$hrTok = Do-Login "9000000002"
$empTok = Do-Login "9000000003"
$emp7Tok = Do-Login "9000000007"
Write-Host "logins ok"

$emps = Invoke-RestMethod -Uri "$base/employees?page=0&size=50" -Headers (AuthHdr $adminTok)
$content = $emps.data.content; if (-not $content) { $content = $emps.data }
$emp3 = ($content | Where-Object { $_.empCode -eq "EMP003" } | Select-Object -First 1).id
$emp4 = ($content | Where-Object { $_.empCode -eq "EMP004" } | Select-Object -First 1).id
$emp7 = ($content | Where-Object { $_.empCode -eq "EMP007" } | Select-Object -First 1).id
$emp8 = ($content | Where-Object { $_.empCode -eq "EMP008" } | Select-Object -First 1).id
Write-Host "ids EMP003=$emp3 EMP004=$emp4 EMP007=$emp7 EMP008=$emp8"

# ── Phase 1 master ─────────────────────────────────────────────
$unique = Get-Date -Format "HHmmss"
$createBody = @{
  name = "P5 Suite $unique"
  startTime = "08:00:00"
  endTime = "16:00:00"
  isOvernight = $false
  graceLateMinutes = 10
  fullDayHours = 8
  halfDayHours = 4
  breakMinutes = 30
  breakPaid = $false
  isDefault = $false
} | ConvertTo-Json

$created = Call-Api -Name "P1 createShift" -Method POST -Url "$base/shifts/company/$companyId" `
  -Headers (AuthHdr $adminTok) -Body $createBody -ExpectStatus @(201) -Assert {
  param($d,$s)
  if (-not $d.success) { throw "success=false" }
  if (-not $d.data.id) { throw "missing id" }
}
$shiftId = $created.data.id

Call-Api -Name "P1 listShifts" -Method GET -Url "$base/shifts/company/$companyId" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.Count -lt 1) { throw "empty list" }
}

Call-Api -Name "P1 getShift" -Method GET -Url "$base/shifts/company/$companyId/$shiftId" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.id -ne $shiftId) { throw "id mismatch" }
}

$updBody = @{
  name = "P5 Suite $unique Upd"
  startTime = "08:30:00"
  endTime = "16:30:00"
  isOvernight = $false
  graceLateMinutes = 12
  fullDayHours = 8
  halfDayHours = 4
  breakMinutes = 30
  breakPaid = $true
  isDefault = $false
} | ConvertTo-Json
Call-Api -Name "P1 updateShift" -Method PUT -Url "$base/shifts/company/$companyId/$shiftId" `
  -Headers (AuthHdr $adminTok) -Body $updBody -Assert {
  param($d,$s)
  if ($d.data.graceLateMinutes -ne 12) { throw "grace not updated" }
  if ($d.data.breakPaid -ne $true) { throw "breakPaid not updated" }
}

Call-Api -Name "P1 deactivateShift" -Method DELETE -Url "$base/shifts/company/$companyId/$shiftId" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.isActive -ne $false) { throw "still active" }
}

Call-Api -Name "P1 reactivateShift" -Method POST -Url "$base/shifts/company/$companyId/$shiftId/reactivate" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.isActive -ne $true) { throw "not active" }
}

Call-Api -Name "P1 listShifts HR" -Method GET -Url "$base/shifts/company/$companyId" `
  -Headers (AuthHdr $hrTok) -Assert { param($d,$s) if (-not $d.success) { throw "fail" } }

Call-Api -Name "P1 createShift EMP forbidden" -Method POST -Url "$base/shifts/company/$companyId" `
  -Headers (AuthHdr $empTok) -Body $createBody -ExpectStatus @(403)

# ── Phase 2 assignments / resolve ──────────────────────────────
$today = (Get-Date).ToString("yyyy-MM-dd")
$assignBody = @{ userId = $emp8; shiftId = $shiftId; effectiveFrom = $today } | ConvertTo-Json
Call-Api -Name "P2 assignShift" -Method POST -Url "$base/shifts/company/$companyId/assignments" `
  -Headers (AuthHdr $adminTok) -Body $assignBody -ExpectStatus @(200,201) -Assert {
  param($d,$s)
  if ($d.data.shiftId -ne $shiftId) { throw "wrong shift" }
  if ($d.data.userId -ne $emp8) { throw "wrong user" }
}

Call-Api -Name "P2 assignmentHistory" -Method GET `
  -Url "$base/shifts/company/$companyId/assignments/employee/$emp8" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.Count -lt 1) { throw "no history" }
}

$resAssign = Call-Api -Name "P2 resolve ASSIGNMENT" -Method GET `
  -Url "$base/shifts/company/$companyId/resolve?userId=$emp8&date=$today" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.source -ne "ASSIGNMENT") { throw "source=$($d.data.source)" }
  if ($d.data.shift.id -ne $shiftId) { throw "shift mismatch" }
}

# EMP008 without using assignment path for default: use a date before assignment? or clear - better pick user with no assignment
# Force EMP004 resolve - may already have Night from rotation confirm. Check source not COMPANY_SETTINGS.
Call-Api -Name "P5 resolve never SETTINGS" -Method GET `
  -Url "$base/shifts/company/$companyId/resolve?userId=$emp4&date=$today" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.source -eq "COMPANY_SETTINGS") { throw "settings still used" }
  if ($d.data.source -notin @("ASSIGNMENT","COMPANY_DEFAULT","NONE")) { throw "bad source $($d.data.source)" }
}

Call-Api -Name "P2 resolveMine EMP" -Method GET -Url "$base/shifts/my/resolve" `
  -Headers (AuthHdr $empTok) -Assert {
  param($d,$s)
  if (-not $d.success) { throw "fail" }
  if ($d.data.source -eq "COMPANY_SETTINGS") { throw "settings" }
}

Call-Api -Name "P2 resolve other EMP forbidden" -Method GET `
  -Url "$base/shifts/company/$companyId/resolve?userId=$emp7" `
  -Headers (AuthHdr $empTok) -ExpectStatus @(403)

Call-Api -Name "P2 assign EMP forbidden" -Method POST -Url "$base/shifts/company/$companyId/assignments" `
  -Headers (AuthHdr $empTok) -Body $assignBody -ExpectStatus @(403)

# Upsert same effectiveFrom
$assignBody2 = @{ userId = $emp8; shiftId = 1; effectiveFrom = $today } | ConvertTo-Json
Call-Api -Name "P2 assign upsert" -Method POST -Url "$base/shifts/company/$companyId/assignments" `
  -Headers (AuthHdr $adminTok) -Body $assignBody2 -ExpectStatus @(200,201) -Assert {
  param($d,$s)
  if ($d.data.shiftId -ne 1) { throw "upsert failed" }
}

# ── Phase 4 rotation full flow ─────────────────────────────────
$rotName = "P5 Rot $unique"
$rotBody = @{
  name = $rotName
  periodUnit = "DAYS"
  periodLength = 7
  shiftIds = @(1, 2)
} | ConvertTo-Json

$rot = Call-Api -Name "P4 createRotation" -Method POST -Url "$base/shifts/company/$companyId/rotations" `
  -Headers (AuthHdr $adminTok) -Body $rotBody -ExpectStatus @(201) -Assert {
  param($d,$s)
  if ($d.data.steps.Count -ne 2) { throw "steps!=2" }
  if ($d.data.periodUnit -ne "DAYS") { throw "unit" }
}
$rotationId = $rot.data.id

Call-Api -Name "P4 listRotations" -Method GET -Url "$base/shifts/company/$companyId/rotations" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.Count -lt 1) { throw "empty" }
}

Call-Api -Name "P4 getRotation" -Method GET -Url "$base/shifts/company/$companyId/rotations/$rotationId" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.id -ne $rotationId) { throw "id" }
}

$rotUpd = @{
  name = "$rotName v2"
  periodUnit = "WEEKLY"
  periodLength = 1
  shiftIds = @(2, 1)
} | ConvertTo-Json
Call-Api -Name "P4 updateRotation" -Method PUT -Url "$base/shifts/company/$companyId/rotations/$rotationId" `
  -Headers (AuthHdr $adminTok) -Body $rotUpd -Assert {
  param($d,$s)
  if ($d.data.periodUnit -ne "WEEKLY") { throw "unit not weekly" }
  if ($d.data.steps[0].id -ne 2) { throw "step order" }
}

# Clean membership conflicts for emp7/emp8
$listR = Invoke-RestMethod -Uri "$base/shifts/company/$companyId/rotations?activeOnly=false" -Headers (AuthHdr $adminTok)
foreach ($r in $listR.data) {
  foreach ($uid in @($emp7, $emp8)) {
    try {
      Invoke-WebRequest -Uri "$base/shifts/company/$companyId/rotations/$($r.id)/members/$uid" -Method DELETE -Headers (AuthHdr $adminTok) -UseBasicParsing | Out-Null
    } catch {}
  }
}

$memBody = @{ userIds = @($emp7, $emp8) } | ConvertTo-Json
Call-Api -Name "P4 addMembers" -Method POST -Url "$base/shifts/company/$companyId/rotations/$rotationId/members" `
  -Headers (AuthHdr $adminTok) -Body $memBody -Assert {
  param($d,$s)
  if ($d.data.Count -lt 2) { throw "members<$($d.data.Count)" }
}

Call-Api -Name "P4 listMembers" -Method GET -Url "$base/shifts/company/$companyId/rotations/$rotationId/members" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.Count -lt 2) { throw "list members" }
}

# Seed current for emp7 as Morning so next = Night (cycle Night,Morning after update? steps are 2 then 1 = Night then Morning)
# After update steps: Night(2), Morning(1). Current Morning(1) -> next Night(2) because Morning is index 1, next wraps to 0 Night.
# Actually cycle [Night, Morning]: current Morning -> next Night. Good.
$seedBody = @{ userId = $emp7; shiftId = 1; effectiveFrom = $today } | ConvertTo-Json
Invoke-RestMethod -Uri "$base/shifts/company/$companyId/assignments" -Method POST -Headers (AuthHdr $adminTok) -ContentType "application/json" -Body $seedBody | Out-Null

$periodStart = (Get-Date).AddDays(14).ToString("yyyy-MM-dd")
$genBody = @{ periodStart = $periodStart } | ConvertTo-Json
$draft = Call-Api -Name "P4 generateDraft" -Method POST `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/generate" `
  -Headers (AuthHdr $adminTok) -Body $genBody -ExpectStatus @(201) -Assert {
  param($d,$s)
  if ($d.data.status -ne "DRAFT") { throw "not draft" }
  if ($d.data.rows.Count -lt 2) { throw "rows" }
}

Call-Api -Name "P4 getCurrentDraft" -Method GET `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/current" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.status -ne "DRAFT") { throw "status" }
}

# Regenerate must replace the open draft (no unique-index 500).
Call-Api -Name "P4 regenerateDraft" -Method POST `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/generate" `
  -Headers (AuthHdr $adminTok) -Body $genBody -ExpectStatus @(201) -Assert {
  param($d,$s)
  if ($d.data.status -ne "DRAFT") { throw "not draft" }
  if ($d.data.rows.Count -lt 2) { throw "rows" }
}

Call-Api -Name "P4 deleteCurrentDraft" -Method DELETE `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/current" `
  -Headers (AuthHdr $adminTok) -Assert { param($d,$s) if (-not $d.success) { throw "fail" } }

Call-Api -Name "P4 no draft after delete" -Method GET `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/current" `
  -Headers (AuthHdr $adminTok) -ExpectStatus @(404)

$draft = Call-Api -Name "P4 generateDraft again" -Method POST `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/generate" `
  -Headers (AuthHdr $adminTok) -Body $genBody -ExpectStatus @(201) -Assert {
  param($d,$s)
  if ($d.data.status -ne "DRAFT") { throw "not draft" }
}

$row7 = $draft.data.rows | Where-Object { $_.userId -eq $emp7 } | Select-Object -First 1
if ($row7.currentShiftName -eq "Morning" -and $row7.proposedShiftName -eq "Night") {
  Write-Host "[PASS] P4 cycle Morning->Night (manual assert)"
  $script:results += [pscustomobject]@{ Name="P4 cycleNext"; Status=200; Ms=0; Ok=$true; Slow=$false; Detail="" }
} else {
  Write-Host "[FAIL] P4 cycle got current=$($row7.currentShiftName) proposed=$($row7.proposedShiftName)"
  $script:results += [pscustomobject]@{ Name="P4 cycleNext"; Status=0; Ms=0; Ok=$false; Slow=$false; Detail="cycle" }
}

$editBody = @{ proposedShiftId = 1 } | ConvertTo-Json
Call-Api -Name "P4 updateDraftRow" -Method PUT `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/current/rows/$emp8" `
  -Headers (AuthHdr $adminTok) -Body $editBody -Assert {
  param($d,$s)
  $r = $d.data.rows | Where-Object { $_.userId -eq $emp8 } | Select-Object -First 1
  if ($r.proposedShiftId -ne 1) { throw "edit failed" }
}

$confirmBody = @{ effectiveFrom = $periodStart } | ConvertTo-Json
Call-Api -Name "P4 confirmDraft" -Method POST `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/current/confirm" `
  -Headers (AuthHdr $adminTok) -Body $confirmBody -Assert {
  param($d,$s)
  if ($d.data.status -ne "CONFIRMED") { throw "not confirmed" }
}

Call-Api -Name "P4 resolve after confirm" -Method GET `
  -Url "$base/shifts/company/$companyId/resolve?userId=$emp7&date=$periodStart" `
  -Headers (AuthHdr $adminTok) -Assert {
  param($d,$s)
  if ($d.data.source -ne "ASSIGNMENT") { throw "source $($d.data.source)" }
  if ($d.data.shift.name -ne "Night") { throw "shift $($d.data.shift.name)" }
}

Call-Api -Name "P4 no open draft" -Method GET `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/current" `
  -Headers (AuthHdr $adminTok) -ExpectStatus @(404)

Call-Api -Name "P4 removeMember" -Method DELETE `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/members/$emp8" `
  -Headers (AuthHdr $adminTok) -Assert { param($d,$s) if (-not $d.success) { throw "fail" } }

Call-Api -Name "P4 deleteRotation" -Method DELETE `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId" `
  -Headers (AuthHdr $adminTok) -Assert { param($d,$s) if (-not $d.success) { throw "fail" } }

Call-Api -Name "P4 generate on deleted forbidden" -Method POST `
  -Url "$base/shifts/company/$companyId/rotations/$rotationId/drafts/generate" `
  -Headers (AuthHdr $adminTok) -Body "{}" -ExpectStatus @(404)

# ── Phase 5 home + attendance linkage ──────────────────────────
$homeResp = Call-Api -Name "P5 home shift" -Method GET -Url "$base/home" `
  -Headers (AuthHdr $emp7Tok) -Assert {
  param($d,$s)
  $src = $d.data.me.shift.source
  if ($src -eq "COMPANY_SETTINGS") { throw "home still settings" }
}

# Warm + second resolve timing
Call-Api -Name "P5 resolve warm1" -Method GET `
  -Url "$base/shifts/company/$companyId/resolve?userId=$emp3&date=$today" -Headers (AuthHdr $adminTok) | Out-Null
Call-Api -Name "P5 resolve warm2" -Method GET `
  -Url "$base/shifts/company/$companyId/resolve?userId=$emp3&date=$today" -Headers (AuthHdr $adminTok) | Out-Null

# Cross-company path reject
Call-Api -Name "P5 cross-company blocked" -Method GET `
  -Url "$base/shifts/company/999/resolve?userId=$emp3" `
  -Headers (AuthHdr $adminTok) -ExpectStatus @(401,403,404)

# Duplicate rotation name
$dupBody = @{ name = "$rotName v2"; periodUnit = "WEEKLY"; periodLength = 1; shiftIds = @(1,2) } | ConvertTo-Json
Call-Api -Name "P4 duplicate name conflict" -Method POST -Url "$base/shifts/company/$companyId/rotations" `
  -Headers (AuthHdr $adminTok) -Body $dupBody -ExpectStatus @(409)

# ── Summary ────────────────────────────────────────────────────
$pass = @($results | Where-Object { $_.Ok }).Count
$fail = @($results | Where-Object { -not $_.Ok }).Count
$slow = @($results | Where-Object { $_.Slow }).Count
$avg = [int](($results | Measure-Object -Property Ms -Average).Average)
$max = ($results | Measure-Object -Property Ms -Maximum).Maximum
Write-Host ""
Write-Host "==== SHIFT FULL SUITE ===="
Write-Host "PASS=$pass FAIL=$fail SLOW(>=${slowMs}ms)=$slow AVG=${avg}ms MAX=${max}ms TOTAL=$($results.Count)"
if ($fail -gt 0) {
  Write-Host "FAILURES:"
  $results | Where-Object { -not $_.Ok } | ForEach-Object { Write-Host (" - {0} status={1} {2}" -f $_.Name, $_.Status, $_.Detail) }
}
if ($slow -gt 0) {
  Write-Host "SLOW:"
  $results | Where-Object { $_.Slow } | ForEach-Object { Write-Host (" - {0} {1}ms" -f $_.Name, $_.Ms) }
}
exit $(if ($fail -gt 0) { 1 } else { 0 })
