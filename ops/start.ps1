param([string]$JavaHome=$env:JAVA_HOME,[switch]$Build)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if(!$JavaHome){throw '请设置 JAVA_HOME 为 JDK 17 或更新版本'}
$java=Join-Path $JavaHome 'bin/java.exe'
if(!(Test-Path $java)){throw '找不到 Java'}
if($Build){Push-Location $root;try { & mvn package; if($LASTEXITCODE){throw '构建失败'} }finally{Pop-Location}}
New-Item -ItemType Directory -Force (Join-Path $root '.local') | Out-Null
$secretPath=Join-Path $root '.local/cluster-secret'
if(!(Test-Path $secretPath)){
 $bytes=New-Object byte[] 48
 $rng=[System.Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($bytes);$rng.Dispose()
 Set-Content -LiteralPath $secretPath -Value ([Convert]::ToBase64String($bytes)) -NoNewline
}
$env:CF_SECRET=(Get-Content -LiteralPath $secretPath -Raw).Trim()
foreach($module in @('shipment-service','dispatch-service','tracking-service','billing-service','gateway')){
 $dir=Join-Path $root $module
 $local=Join-Path $dir '.local';New-Item -ItemType Directory -Force $local | Out-Null
 $pidFile=Join-Path $local 'server.pid'
 if(Test-Path $pidFile){$process=Get-Process -Id (Get-Content $pidFile) -ErrorAction SilentlyContinue;if($process){throw "$module 已有进程，请先停止"}}
 $jar=Join-Path $dir "target/$module-0.1.0.jar"
 if(!(Test-Path $jar)){throw "$module 尚未构建"}
 $process=Start-Process -FilePath $java -ArgumentList @('-jar',$jar) -WorkingDirectory $dir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $local 'server.log') -RedirectStandardError (Join-Path $local 'error.log') -PassThru
 Set-Content -LiteralPath $pidFile -Value $process.Id
}
Write-Output 'CargoFlow 正在启动：http://127.0.0.1:8100'
