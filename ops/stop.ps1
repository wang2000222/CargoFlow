$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
foreach($module in @('gateway','shipment-service','dispatch-service','tracking-service','billing-service')){
 $dir=Join-Path $root $module
 $pidFile=Join-Path $dir '.local/server.pid'
 if(!(Test-Path $pidFile)){continue}
 $processId=[int](Get-Content $pidFile)
 $process=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
 $expected=Join-Path $dir "target/$module-0.1.0.jar"
 if($process -and $process.CommandLine.Contains($expected)){Stop-Process -Id $processId}
 elseif($process){throw "进程 $processId 不属于 $module，拒绝停止"}
 Remove-Item -LiteralPath $pidFile
}
