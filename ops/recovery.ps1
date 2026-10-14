param([string]$JavaHome=$env:JAVA_HOME,[string]$Base='http://127.0.0.1:8100')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$dir=Join-Path $root 'dispatch-service';$pidFile=Join-Path $dir '.local/server.pid'
$processId=[int](Get-Content -LiteralPath $pidFile)
$jar=Join-Path $dir 'target/dispatch-service-0.1.0.jar'
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
if(!$process -or !$process.CommandLine.Contains($jar)){throw '无法确认调度服务进程，拒绝停止'}
$java=Join-Path $JavaHome 'bin/java.exe'
if(!(Test-Path $java)){throw '请提供有效 JavaHome'}
function Login($account){$r=Invoke-RestMethod "$Base/api/demo/login" -Method Post -ContentType application/json -Body (@{account=$account}|ConvertTo-Json);return @{Authorization="Bearer $($r.token)"}}
$customer=Login 'customer-a';$owner=Login 'owner-a';$restarted=$false
function RestartDispatch {
 $env:CF_SECRET=(Get-Content (Join-Path $root '.local/cluster-secret') -Raw).Trim()
 $p=Start-Process -FilePath $java -ArgumentList @('-jar',$jar) -WorkingDirectory $dir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $dir '.local/server.log') -RedirectStandardError (Join-Path $dir '.local/error.log') -PassThru
 Set-Content -LiteralPath $pidFile -Value $p.Id
}
try {
 Stop-Process -Id $processId
 $body=@{requestKey=[guid]::NewGuid().ToString();origin='故障恢复验收仓';destination='恢复配送中心';cargo='恢复验收货物';weightKg=10;distanceKm=20}|ConvertTo-Json
 $order=Invoke-RestMethod "$Base/api/shipments" -Headers $customer -Method Post -ContentType 'application/json; charset=utf-8' -Body $body
 $failed=$false
 for($i=0;$i-lt 15;$i++){
   $rows=Invoke-RestMethod "$Base/api/outbox" -Headers $owner
   if(@($rows|Where-Object {$_.shipment_id-eq $order.id -and $_.target-eq 'dispatch' -and $_.state-eq 'FAILED'}).Count-gt 0){$failed=$true;break}
   Start-Sleep -Seconds 2
 }
 if(!$failed){throw '未观察到可重试的调度失败事件'}
 Write-Output 'PASS: 调度离线时运单保留，事件进入失败重试状态'
 RestartDispatch;$restarted=$true
 for($i=0;$i-lt 45;$i++){
   $current=Invoke-RestMethod "$Base/api/shipments/$($order.id)" -Headers $customer
   if($current.state-eq 'ASSIGNED'){Write-Output 'PASS: 调度恢复后原运单自动派车，无需重新下单';return}
   Start-Sleep -Seconds 2
 }
 throw '服务恢复后未在验收窗口内派车'
} finally {if(!$restarted){RestartDispatch}}
